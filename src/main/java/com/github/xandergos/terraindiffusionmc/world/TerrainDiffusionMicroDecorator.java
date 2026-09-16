package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.TreeConfiguredFeatures;

/**
 * Small, vanilla-material surface accents for blueprint v5 worlds.
 * This deliberately supplements rather than replaces each biome's normal features.
 */
public final class TerrainDiffusionMicroDecorator {
    private static final long SALT = 0x4D4943524F444543L;
    private TerrainDiffusionMicroDecorator() {}

    public record Plan(int coverClusters, boolean flowers, boolean tree, boolean rock, boolean hummock) {}

    /** Package-visible deterministic plan for tests; placement uses a separate stream. */
    static Plan plan(long worldSeed, int chunkX, int chunkZ, short centerBiome) {
        Random random = Random.create(mix(worldSeed, chunkX, chunkZ));
        boolean vegetated = supportsCover(centerBiome);
        int cover = vegetated ? 1 + random.nextInt(3) : 0;
        boolean flowers = supportsFlowers(centerBiome) && random.nextFloat() < flowerChance(centerBiome);
        boolean tree = supportsStrayTree(centerBiome) && random.nextFloat() < treeChance(centerBiome);
        boolean rock = supportsRock(centerBiome) && random.nextFloat() < rockChance(centerBiome);
        boolean hummock = supportsHummock(centerBiome) && random.nextFloat() < .045f;
        return new Plan(cover, flowers, tree, rock, hummock);
    }

    public static void decorate(StructureWorldAccess world, Chunk chunk, ChunkGenerator generator,
                                LocalTerrainProvider.HeightmapData data, int tileX, int tileZ) {
        int startX = chunk.getPos().getStartX(), startZ = chunk.getPos().getStartZ();
        short centerBiome = biomeAt(data, tileX, tileZ, startX + 8, startZ + 8);
        if(data.contexts!=null){
            // Legacy clustered footprints may cross biome boundaries. Require a single owner.
            for(int zz=0;zz<16;zz++)for(int xx=0;xx<16;xx++)
                if(biomeAt(data,tileX,tileZ,startX+xx,startZ+zz)!=centerBiome)return;
            if(centerBiome==BiomeIds.DESERT||ForestDistricts.woodland(centerBiome))return;
        }
        long seed = LocalTerrainProvider.getSeed();
        if(SurfaceTransitions.enabled()&&com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.sandSea(
                (startX+8)/(double)WorldScaleManager.getCurrentScale(),(startZ+8)/(double)WorldScaleManager.getCurrentScale())>.90)return;
        Plan plan = plan(seed, chunk.getPos().x, chunk.getPos().z, centerBiome);
        boolean expanded=com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=7;
        boolean densityEnabled=com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=10;
        if(expanded&&plan.coverClusters()==0&&extraCover(centerBiome))
            plan=new Plan(1,false,plan.tree(),plan.rock(),false);
        Random random = Random.create(mix(seed ^ 0x504C4143454D454EL, chunk.getPos().x, chunk.getPos().z));

        // A regional field creates broad lush/sparse runs instead of identical per-chunk density.
        double lushness = WorldHydrology.edgeNoise(startX + 8, startZ + 8, seed ^ SALT, 280);
        float density=densityEnabled?VegetationDensity.at(startX+8,startZ+8,centerBiome,seed):.5f;
        int densityDelta=densityEnabled?(density>.72?2:density>.55?1:density<.20?-2:density<.34?-1:0):0;
        int clusters = Math.max(0, plan.coverClusters() + (lushness > .38 ? 1 : lushness < -.55 ? -1 : 0)+densityDelta);
        for (int i = 0; i < clusters; i++) {
            int x = startX + 2 + random.nextInt(12), z = startZ + 2 + random.nextInt(12);
            scatterCover(world, chunk, data, tileX, tileZ, x, z, random, 3 + random.nextInt(4));
        }
        if (plan.flowers()&&( !densityEnabled||random.nextFloat()<.45f+density*.7f)) {
            int x = startX + 3 + random.nextInt(10), z = startZ + 3 + random.nextInt(10);
            scatterFlowers(world, chunk, data, tileX, tileZ, x, z, random, 3 + random.nextInt(4));
        }
        if (plan.hummock()) {
            int x = startX + 4 + random.nextInt(8), z = startZ + 4 + random.nextInt(8);
            placeHummock(world, chunk, data, tileX, tileZ, x, z);
        }
        if (plan.rock()) {
            int x = startX + 3 + random.nextInt(10), z = startZ + 3 + random.nextInt(10);
            placeRock(world, chunk, data, tileX, tileZ, x, z, random);
        }
        boolean ecotoneTree=false;
        if(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=6&&centerBiome==BiomeIds.DESERT){
            var blend=EcotoneDecorator.at(startX+8,startZ+8);
            ecotoneTree=blend.woodland()>0&&random.nextFloat()<blend.woodland()*.30f;
        }
        boolean densityTree=densityEnabled&&supportsStrayTree(centerBiome)&&random.nextFloat()<Math.max(0,(density-.40f)*.26f);
        if (plan.tree()||ecotoneTree||densityTree) {
            int x = startX + 4 + random.nextInt(8), z = startZ + 4 + random.nextInt(8);
            placeTree(world, chunk, generator, data, tileX, tileZ, x, z, random);
        }
        Heightmap.populateHeightmaps(chunk, java.util.Set.of(Heightmap.Type.WORLD_SURFACE_WG, Heightmap.Type.OCEAN_FLOOR_WG));
    }

    private static void scatterCover(StructureWorldAccess world, Chunk chunk, LocalTerrainProvider.HeightmapData data,
                                     int tileX, int tileZ, int cx, int cz, Random random, int count) {
        for (int n = 0; n < count; n++) {
            int x = cx + random.nextBetween(-3, 3), z = cz + random.nextBetween(-3, 3);
            if (!inside(chunk, x, z)) continue;
            short biome = biomeAt(data, tileX, tileZ, x, z);
            Block block = coverFor(biome, random);
            if (block != null) placePlant(world, chunk, data, tileX, tileZ, x, z, block);
        }
    }

    private static void scatterFlowers(StructureWorldAccess world, Chunk chunk, LocalTerrainProvider.HeightmapData data,
                                       int tileX, int tileZ, int cx, int cz, Random random, int count) {
        Block flower = switch (random.nextInt(6)) {
            case 0 -> Blocks.DANDELION; case 1 -> Blocks.POPPY; case 2 -> Blocks.AZURE_BLUET;
            case 3 -> Blocks.OXEYE_DAISY; case 4 -> Blocks.CORNFLOWER; default -> Blocks.WILDFLOWERS;
        };
        for (int n = 0; n < count; n++) {
            int x = cx + random.nextBetween(-3, 3), z = cz + random.nextBetween(-3, 3);
            if (inside(chunk, x, z) && supportsFlowers(biomeAt(data, tileX, tileZ, x, z)))
                placePlant(world, chunk, data, tileX, tileZ, x, z, flower);
        }
    }

    private static void placePlant(StructureWorldAccess world, Chunk chunk, LocalTerrainProvider.HeightmapData data,
                                   int tileX, int tileZ, int x, int z, Block block) {
        if(SurfaceTransitions.enabled()&&com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.sandSea(
                x/(double)WorldScaleManager.getCurrentScale(),z/(double)WorldScaleManager.getCurrentScale())>.90)return;
        if (slope(data, tileX, tileZ, x, z) > .34f) return;
        int lx = x - chunk.getPos().getStartX(), lz = z - chunk.getPos().getStartZ();
        int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, lx, lz);
        BlockPos ground = new BlockPos(x, y, z), pos = ground.up();
        BlockState below = world.getBlockState(ground), state = block.getDefaultState();
        if (!naturalSoil(below) || !world.getBlockState(pos).isAir() || !state.canPlaceAt(world, pos)) return;
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static void placeHummock(StructureWorldAccess world, Chunk chunk, LocalTerrainProvider.HeightmapData data,
                                     int tileX, int tileZ, int x, int z) {
        if (slope(data, tileX, tileZ, x, z) > .12f) return;
        int centerY = top(chunk, x, z);
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (Math.abs(dx) + Math.abs(dz) > 1 || !inside(chunk, x + dx, z + dz)) continue;
            int y = top(chunk, x + dx, z + dz);
            BlockPos ground = new BlockPos(x + dx, y, z + dz), above = ground.up();
            if (Math.abs(y - centerY) > 1 || !world.getBlockState(above).isAir() || !world.getBlockState(ground).isIn(BlockTags.DIRT)) return;
        }
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (Math.abs(dx) + Math.abs(dz) > 1) continue;
            int px = x + dx, pz = z + dz, y = top(chunk, px, pz);
            BlockPos ground = new BlockPos(px, y, pz);
            world.setBlockState(ground, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(ground.up(), Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }

    private static void placeRock(StructureWorldAccess world, Chunk chunk, LocalTerrainProvider.HeightmapData data,
                                  int tileX, int tileZ, int x, int z, Random random) {
        if (slope(data, tileX, tileZ, x, z) > .48f) return;
        int y = top(chunk, x, z); BlockPos ground = new BlockPos(x, y, z), pos = ground.up();
        if (!naturalGround(world.getBlockState(ground)) || !world.getBlockState(pos).isAir()) return;
        Block rock = com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=10
                ?SurfaceGeology.rock(x,y,z,biomeAt(data,tileX,tileZ,x,z),LocalTerrainProvider.getSeed())
                :random.nextFloat() < .24f ? Blocks.MOSSY_COBBLESTONE : random.nextBoolean() ? Blocks.ANDESITE : Blocks.STONE;
        if(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=7){
            BlockState soil=world.getBlockState(ground);
            if(soil.isOf(Blocks.RED_SAND))rock=Blocks.RED_SANDSTONE;
            else if(soil.isOf(Blocks.SAND))rock=Blocks.SANDSTONE;
        }
        world.setBlockState(pos, rock.getDefaultState(), Block.NOTIFY_LISTENERS);
        if (random.nextFloat() < .35f && world.getBlockState(pos.up()).isAir())
            world.setBlockState(pos.up(), rock.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static void placeTree(StructureWorldAccess world, Chunk chunk, ChunkGenerator generator,
                                  LocalTerrainProvider.HeightmapData data, int tileX, int tileZ,
                                  int x, int z, Random random) {
        short biome = biomeAt(data, tileX, tileZ, x, z);
        if(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=6){
            if(biome==BiomeIds.DESERT){
                var blend=EcotoneDecorator.at(x,z);
                if(blend.woodland()<=0)return;
                biome=blend.coldWoodland()>.15f?BiomeIds.TAIGA_SPARSE:BiomeIds.FOREST_SPARSE;
            }else if(biome==BiomeIds.MEADOW&&data.heightmap[Math.max(0,Math.min(data.height-1,z-tileZ))][Math.max(0,Math.min(data.width-1,x-tileX))]>1800)
                biome=BiomeIds.TAIGA_SPARSE;
        }
        if (!supportsStrayTree(biome)) return;
        if (slope(data, tileX, tileZ, x, z) > .20f) return;
        int y = top(chunk, x, z); BlockPos ground = new BlockPos(x, y, z), origin = ground.up();
        if (!world.getBlockState(ground).isIn(BlockTags.DIRT) || !world.getBlockState(origin).isAir()) return;
        RegistryKey<ConfiguredFeature<?, ?>> key = treeFor(biome, random);
        if (key == null) return;
        var configured = world.getRegistryManager().getOrThrow(RegistryKeys.CONFIGURED_FEATURE).getOrThrow(key).value();
        configured.generate(world, generator, random, origin);
    }

    private static RegistryKey<ConfiguredFeature<?, ?>> treeFor(short biome, Random random) {
        return switch (biome) {
            case BiomeIds.SAVANNA, BiomeIds.SAVANNA_PLATEAU -> TreeConfiguredFeatures.ACACIA;
            case BiomeIds.TAIGA, BiomeIds.TAIGA_SPARSE -> random.nextBoolean() ? TreeConfiguredFeatures.SPRUCE : TreeConfiguredFeatures.PINE;
            case BiomeIds.JUNGLE, BiomeIds.SPARSE_JUNGLE -> TreeConfiguredFeatures.JUNGLE_TREE_NO_VINE;
            case BiomeIds.BIRCH_FOREST, BiomeIds.MEADOW -> TreeConfiguredFeatures.BIRCH;
            case BiomeIds.PLAINS, BiomeIds.SUNFLOWER_PLAINS, BiomeIds.FOREST_SPARSE ->
                    random.nextFloat() < .30f ? TreeConfiguredFeatures.BIRCH : TreeConfiguredFeatures.OAK;
            default -> null;
        };
    }

    private static Block coverFor(short id, Random random) {
        if(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=7){
            if(id==BiomeIds.BEACH||id==BiomeIds.WOODED_BADLANDS)return random.nextFloat()<.2f?Blocks.DEAD_BUSH:Blocks.SHORT_DRY_GRASS;
            if(id==BiomeIds.MUSHROOM_FIELDS)return random.nextBoolean()?Blocks.BROWN_MUSHROOM:Blocks.RED_MUSHROOM;
            if(id==BiomeIds.PALE_GARDEN)return Blocks.PALE_MOSS_CARPET;
            if(id==BiomeIds.BIRCH_FOREST||id==BiomeIds.OLD_GROWTH_BIRCH_FOREST||id==BiomeIds.CHERRY_GROVE)
                return random.nextFloat()<.30f?Blocks.LEAF_LITTER:Blocks.SHORT_GRASS;
            if(extraCover(id))return random.nextBoolean()?Blocks.FERN:Blocks.SHORT_GRASS;
        }
        if (id == BiomeIds.DESERT || id == BiomeIds.BADLANDS || id == BiomeIds.ERODED_BADLANDS)
            return random.nextFloat() < .28f ? Blocks.DEAD_BUSH : Blocks.SHORT_DRY_GRASS;
        if (id == BiomeIds.SWAMP || id == BiomeIds.MANGROVE_SWAMP)
            return random.nextFloat() < .16f ? Blocks.FIREFLY_BUSH : random.nextBoolean() ? Blocks.FERN : Blocks.BUSH;
        if (id == BiomeIds.TAIGA || id == BiomeIds.TAIGA_SPARSE || id == BiomeIds.OLD_GROWTH_PINE_TAIGA || id == BiomeIds.OLD_GROWTH_SPRUCE_TAIGA)
            return random.nextFloat() < .22f ? Blocks.BUSH : Blocks.FERN;
        if (id == BiomeIds.JUNGLE || id == BiomeIds.SPARSE_JUNGLE || id == BiomeIds.BAMBOO_JUNGLE)
            return random.nextBoolean() ? Blocks.FERN : Blocks.BUSH;
        if (supportsCover(id)) return random.nextFloat() < .18f ? Blocks.BUSH : Blocks.SHORT_GRASS;
        return null;
    }

    private static boolean supportsCover(short id) {
        return switch (id) {
            case BiomeIds.PLAINS, BiomeIds.SUNFLOWER_PLAINS, BiomeIds.MEADOW,
                 BiomeIds.FOREST, BiomeIds.FOREST_SPARSE, BiomeIds.FLOWER_FOREST,
                 BiomeIds.BIRCH_FOREST, BiomeIds.OLD_GROWTH_BIRCH_FOREST,
                 BiomeIds.SAVANNA, BiomeIds.SAVANNA_PLATEAU, BiomeIds.SWAMP, BiomeIds.MANGROVE_SWAMP,
                 BiomeIds.TAIGA, BiomeIds.TAIGA_SPARSE, BiomeIds.OLD_GROWTH_PINE_TAIGA, BiomeIds.OLD_GROWTH_SPRUCE_TAIGA,
                 BiomeIds.JUNGLE, BiomeIds.SPARSE_JUNGLE, BiomeIds.BAMBOO_JUNGLE,
                 BiomeIds.DESERT, BiomeIds.BADLANDS, BiomeIds.ERODED_BADLANDS -> true;
            default -> false;
        };
    }
    static boolean extraCover(short id){
        return switch(id){
            case BiomeIds.BEACH,BiomeIds.BIRCH_FOREST,BiomeIds.OLD_GROWTH_BIRCH_FOREST,
                 BiomeIds.DARK_FOREST,BiomeIds.PALE_GARDEN,BiomeIds.MUSHROOM_FIELDS,
                 BiomeIds.OLD_GROWTH_PINE_TAIGA,BiomeIds.OLD_GROWTH_SPRUCE_TAIGA,
                 BiomeIds.CHERRY_GROVE,BiomeIds.WOODED_BADLANDS,BiomeIds.WINDSWEPT_FOREST,
                 BiomeIds.WINDSWEPT_HILLS,BiomeIds.WINDSWEPT_SAVANNA -> true;
            default -> false;
        };
    }
    private static boolean supportsFlowers(short id) { return id==BiomeIds.PLAINS||id==BiomeIds.SUNFLOWER_PLAINS||id==BiomeIds.MEADOW||id==BiomeIds.FLOWER_FOREST||id==BiomeIds.FOREST_SPARSE||id==BiomeIds.BIRCH_FOREST; }
    private static boolean supportsStrayTree(short id) { return id==BiomeIds.PLAINS||id==BiomeIds.SUNFLOWER_PLAINS||id==BiomeIds.MEADOW||id==BiomeIds.FOREST_SPARSE||id==BiomeIds.SAVANNA||id==BiomeIds.SAVANNA_PLATEAU||id==BiomeIds.TAIGA_SPARSE||id==BiomeIds.SPARSE_JUNGLE; }
    private static boolean supportsHummock(short id) { return id==BiomeIds.PLAINS||id==BiomeIds.MEADOW||id==BiomeIds.FOREST_SPARSE||id==BiomeIds.SAVANNA; }
    private static boolean supportsRock(short id) { return !BiomeIds.isOcean(id)&&id!=BiomeIds.RIVER&&id!=BiomeIds.FROZEN_RIVER&&id!=BiomeIds.SWAMP&&id!=BiomeIds.MANGROVE_SWAMP; }
    private static float flowerChance(short id) { return id==BiomeIds.MEADOW||id==BiomeIds.FLOWER_FOREST?.42f:id==BiomeIds.SUNFLOWER_PLAINS?.28f:.16f; }
    private static float treeChance(short id) { return id==BiomeIds.FOREST_SPARSE||id==BiomeIds.TAIGA_SPARSE||id==BiomeIds.SPARSE_JUNGLE?.14f:id==BiomeIds.SAVANNA?.11f:.075f; }
    private static float rockChance(short id) { return id==BiomeIds.WINDSWEPT_HILLS||id==BiomeIds.WINDSWEPT_GRAVELLY_HILLS||id==BiomeIds.STONY_PEAKS?.18f:.075f; }

    private static short biomeAt(LocalTerrainProvider.HeightmapData data, int tx, int tz, int x, int z) {
        int r=Math.max(0,Math.min(data.height-1,z-tz)),c=Math.max(0,Math.min(data.width-1,x-tx)); return data.biomeIds[r][c];
    }
    private static float slope(LocalTerrainProvider.HeightmapData data,int tx,int tz,int x,int z){
        int r=Math.max(1,Math.min(data.height-2,z-tz)),c=Math.max(1,Math.min(data.width-2,x-tx));
        float dx=data.heightmap[r][c+1]-data.heightmap[r][c-1],dz=data.heightmap[r+1][c]-data.heightmap[r-1][c];
        return (float)Math.hypot(dx,dz)/Math.max(1,60f/WorldScaleManager.getCurrentScale());
    }
    private static int top(Chunk chunk,int x,int z){return chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x-chunk.getPos().getStartX(),z-chunk.getPos().getStartZ());}
    private static boolean inside(Chunk chunk,int x,int z){return x>=chunk.getPos().getStartX()&&x<=chunk.getPos().getEndX()&&z>=chunk.getPos().getStartZ()&&z<=chunk.getPos().getEndZ();}
    private static boolean naturalSoil(BlockState state){return state.isIn(BlockTags.DIRT)||state.isOf(Blocks.SAND)||state.isOf(Blocks.RED_SAND)||state.isOf(Blocks.TERRACOTTA)||
            com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=7&&(state.isOf(Blocks.MOSS_BLOCK)||state.isOf(Blocks.PALE_MOSS_BLOCK));}
    private static boolean naturalGround(BlockState state){return naturalSoil(state)||state.isIn(BlockTags.BASE_STONE_OVERWORLD)||state.isOf(Blocks.GRAVEL);}
    private static long mix(long seed,int x,int z){long v=seed^SALT^(long)x*0x9E3779B97F4A7C15L^(long)z*0xC2B2AE3D27D4EB4FL;v^=v>>>30;v*=0xBF58476D1CE4E5B9L;v^=v>>>27;v*=0x94D049BB133111EBL;return v^(v>>>31);}
}
