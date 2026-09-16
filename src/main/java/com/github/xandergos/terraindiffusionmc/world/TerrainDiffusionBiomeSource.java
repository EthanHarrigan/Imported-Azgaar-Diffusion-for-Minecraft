package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.HeightmapData;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Map.entry;

public class TerrainDiffusionBiomeSource extends BiomeSource {
    private static final RegistryKey<Biome> FOREST_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "forest_sparse"));
    private static final RegistryKey<Biome> TAIGA_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "taiga_sparse"));
    private static final RegistryKey<Biome> SNOWY_TAIGA_SPARSE = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("terrain-diffusion-mc", "snowy_taiga_sparse"));

    public static final MapCodec<TerrainDiffusionBiomeSource> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME)
            ).apply(instance, instance.stable(TerrainDiffusionBiomeSource::new)));


    private RegistryEntryLookup<Biome> biomeLookup;
    private Map<Short, RegistryEntry<Biome>> biomeIdMap = null;

    public TerrainDiffusionBiomeSource(RegistryEntryLookup<Biome> biomeLookup) {
        this.biomeLookup = biomeLookup;
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() {
        return CODEC;
    }

    private void requireBiomeIdMap() {
        if (biomeIdMap == null) {
            biomeIdMap = Map.ofEntries(
                    entry(BiomeIds.PLAINS,biomeLookup.getOrThrow(BiomeKeys.PLAINS)),
                    entry(BiomeIds.SUNFLOWER_PLAINS,biomeLookup.getOrThrow(BiomeKeys.SUNFLOWER_PLAINS)),
                    entry(BiomeIds.SNOWY_PLAINS,biomeLookup.getOrThrow(BiomeKeys.SNOWY_PLAINS)),
                    entry(BiomeIds.ICE_SPIKES,biomeLookup.getOrThrow(BiomeKeys.ICE_SPIKES)),
                    entry(BiomeIds.DESERT,biomeLookup.getOrThrow(BiomeKeys.DESERT)),
                    entry(BiomeIds.SWAMP,biomeLookup.getOrThrow(BiomeKeys.SWAMP)),
                    entry(BiomeIds.MANGROVE_SWAMP,biomeLookup.getOrThrow(BiomeKeys.MANGROVE_SWAMP)),
                    entry(BiomeIds.FOREST,biomeLookup.getOrThrow(BiomeKeys.FOREST)),
                    entry(BiomeIds.FLOWER_FOREST,biomeLookup.getOrThrow(BiomeKeys.FLOWER_FOREST)),
                    entry(BiomeIds.BIRCH_FOREST,biomeLookup.getOrThrow(BiomeKeys.BIRCH_FOREST)),
                    entry(BiomeIds.OLD_GROWTH_BIRCH_FOREST,biomeLookup.getOrThrow(BiomeKeys.OLD_GROWTH_BIRCH_FOREST)),
                    entry(BiomeIds.DARK_FOREST,biomeLookup.getOrThrow(BiomeKeys.DARK_FOREST)),
                    entry(BiomeIds.PALE_GARDEN,biomeLookup.getOrThrow(BiomeKeys.PALE_GARDEN)),
                    entry(BiomeIds.TAIGA,biomeLookup.getOrThrow(BiomeKeys.TAIGA)),
                    entry(BiomeIds.SNOWY_TAIGA,biomeLookup.getOrThrow(BiomeKeys.SNOWY_TAIGA)),
                    entry(BiomeIds.OLD_GROWTH_PINE_TAIGA,biomeLookup.getOrThrow(BiomeKeys.OLD_GROWTH_PINE_TAIGA)),
                    entry(BiomeIds.OLD_GROWTH_SPRUCE_TAIGA,biomeLookup.getOrThrow(BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA)),
                    entry(BiomeIds.SAVANNA,biomeLookup.getOrThrow(BiomeKeys.SAVANNA)),
                    entry(BiomeIds.SAVANNA_PLATEAU,biomeLookup.getOrThrow(BiomeKeys.SAVANNA_PLATEAU)),
                    entry(BiomeIds.WINDSWEPT_HILLS,biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_HILLS)),
                    entry(BiomeIds.WINDSWEPT_GRAVELLY_HILLS,biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_GRAVELLY_HILLS)),
                    entry(BiomeIds.WINDSWEPT_FOREST,biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_FOREST)),
                    entry(BiomeIds.WINDSWEPT_SAVANNA,biomeLookup.getOrThrow(BiomeKeys.WINDSWEPT_SAVANNA)),
                    entry(BiomeIds.JUNGLE,biomeLookup.getOrThrow(BiomeKeys.JUNGLE)),
                    entry(BiomeIds.SPARSE_JUNGLE,biomeLookup.getOrThrow(BiomeKeys.SPARSE_JUNGLE)),
                    entry(BiomeIds.BAMBOO_JUNGLE,biomeLookup.getOrThrow(BiomeKeys.BAMBOO_JUNGLE)),
                    entry(BiomeIds.BADLANDS,biomeLookup.getOrThrow(BiomeKeys.BADLANDS)),
                    entry(BiomeIds.ERODED_BADLANDS,biomeLookup.getOrThrow(BiomeKeys.ERODED_BADLANDS)),
                    entry(BiomeIds.WOODED_BADLANDS,biomeLookup.getOrThrow(BiomeKeys.WOODED_BADLANDS)),
                    entry(BiomeIds.MEADOW,biomeLookup.getOrThrow(BiomeKeys.MEADOW)),
                    entry(BiomeIds.CHERRY_GROVE,biomeLookup.getOrThrow(BiomeKeys.CHERRY_GROVE)),
                    entry(BiomeIds.GROVE,biomeLookup.getOrThrow(BiomeKeys.GROVE)),
                    entry(BiomeIds.SNOWY_SLOPES,biomeLookup.getOrThrow(BiomeKeys.SNOWY_SLOPES)),
                    entry(BiomeIds.FROZEN_PEAKS,biomeLookup.getOrThrow(BiomeKeys.FROZEN_PEAKS)),
                    entry(BiomeIds.JAGGED_PEAKS,biomeLookup.getOrThrow(BiomeKeys.JAGGED_PEAKS)),
                    entry(BiomeIds.STONY_PEAKS,biomeLookup.getOrThrow(BiomeKeys.STONY_PEAKS)),
                    entry(BiomeIds.RIVER,biomeLookup.getOrThrow(BiomeKeys.RIVER)),
                    entry(BiomeIds.FROZEN_RIVER,biomeLookup.getOrThrow(BiomeKeys.FROZEN_RIVER)),
                    entry(BiomeIds.BEACH,biomeLookup.getOrThrow(BiomeKeys.BEACH)),
                    entry(BiomeIds.SNOWY_BEACH,biomeLookup.getOrThrow(BiomeKeys.SNOWY_BEACH)),
                    entry(BiomeIds.STONY_SHORE,biomeLookup.getOrThrow(BiomeKeys.STONY_SHORE)),
                    entry(BiomeIds.WARM_OCEAN,biomeLookup.getOrThrow(BiomeKeys.WARM_OCEAN)),
                    entry(BiomeIds.LUKEWARM_OCEAN,biomeLookup.getOrThrow(BiomeKeys.LUKEWARM_OCEAN)),
                    entry(BiomeIds.DEEP_LUKEWARM_OCEAN,biomeLookup.getOrThrow(BiomeKeys.DEEP_LUKEWARM_OCEAN)),
                    entry(BiomeIds.OCEAN,biomeLookup.getOrThrow(BiomeKeys.OCEAN)),
                    entry(BiomeIds.DEEP_OCEAN,biomeLookup.getOrThrow(BiomeKeys.DEEP_OCEAN)),
                    entry(BiomeIds.COLD_OCEAN,biomeLookup.getOrThrow(BiomeKeys.COLD_OCEAN)),
                    entry(BiomeIds.DEEP_COLD_OCEAN,biomeLookup.getOrThrow(BiomeKeys.DEEP_COLD_OCEAN)),
                    entry(BiomeIds.FROZEN_OCEAN,biomeLookup.getOrThrow(BiomeKeys.FROZEN_OCEAN)),
                    entry(BiomeIds.DEEP_FROZEN_OCEAN,biomeLookup.getOrThrow(BiomeKeys.DEEP_FROZEN_OCEAN)),
                    entry(BiomeIds.MUSHROOM_FIELDS,biomeLookup.getOrThrow(BiomeKeys.MUSHROOM_FIELDS)),
                    entry(BiomeIds.FOREST_SPARSE,biomeLookup.getOrThrow(FOREST_SPARSE)),
                    entry(BiomeIds.TAIGA_SPARSE,biomeLookup.getOrThrow(TAIGA_SPARSE)),
                    entry(BiomeIds.SNOWY_TAIGA_SPARSE,biomeLookup.getOrThrow(SNOWY_TAIGA_SPARSE))
            );
        }
    }

    @Override
    public Stream<RegistryEntry<Biome>> biomeStream() {
        requireBiomeIdMap();
        return biomeIdMap.values().stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        requireBiomeIdMap();
        RegistryEntry<Biome> defaultEntry = biomeIdMap.get((short) 1);

        // x, y, z are in quart coordinates (block / 4)
        int blockX = BiomeCoords.toBlock(x);
        int blockZ = BiomeCoords.toBlock(z);

        int tileSize = TerrainDiffusionConfig.tileSize();
        int tileShift = Integer.numberOfTrailingZeros(tileSize);

        int tileX = blockX >> tileShift;
        int tileZ = blockZ >> tileShift;

        int blockStartX = tileX << tileShift;
        int blockStartZ = tileZ << tileShift;
        int blockEndX = blockStartX + tileSize;
        int blockEndZ = blockStartZ + tileSize;

        BiomeTileCache cache=BiomeTileCache.active();
        if(cache!=null){
            int side=tileSize/4;
            short[] ids=cache.get(tileX,tileZ,()->{
                HeightmapData generated=LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ,blockStartX,blockEndZ,blockEndX);
                if(generated==null||generated.biomeIds==null)throw new IllegalStateException("Missing generated biome tile");
                short[] result=new short[side*side];
                for(int dz=0;dz<side;dz++)for(int dx=0;dx<side;dx++)result[dz*side+dx]=generated.biomeIds[dz*4][dx*4];
                return result;
            });
            return biomeIdMap.getOrDefault(ids[((blockZ-blockStartZ)/4)*side+(blockX-blockStartX)/4],defaultEntry);
        }
        HeightmapData data = LocalTerrainProvider.getInstance().fetchHeightmap(blockStartZ, blockStartX, blockEndZ, blockEndX);
        if (data != null && data.biomeIds != null) {
            int localX = Math.max(0, Math.min(data.width  - 1, blockX - blockStartX));
            int localZ = Math.max(0, Math.min(data.height - 1, blockZ - blockStartZ));
            RegistryEntry<Biome> entry = biomeIdMap.get(data.biomeIds[localZ][localX]);
            if (entry != null) return entry;
        }

        return defaultEntry;
    }

    @Override
    public Pair<BlockPos, RegistryEntry<Biome>> locateBiome(BlockPos origin, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval, Predicate<RegistryEntry<Biome>> predicate, MultiNoiseUtil.MultiNoiseSampler noiseSampler, WorldView world) {
        var reserved=locateReserved(origin.getX(),origin.getY(),origin.getZ(),radius,predicate,noiseSampler);
        return reserved!=null?reserved:super.locateBiome(origin,radius,horizontalBlockCheckInterval,verticalBlockCheckInterval,predicate,noiseSampler,world);
    }

    @Override
    public Pair<BlockPos, RegistryEntry<Biome>> locateBiome(int x, int y, int z, int radius, int blockCheckInterval, Predicate<RegistryEntry<Biome>> predicate, Random random, boolean bl, MultiNoiseUtil.MultiNoiseSampler noiseSampler) {
        if(!bl)return super.locateBiome(x,y,z,radius,blockCheckInterval,predicate,random,false,noiseSampler);
        var reserved=locateReserved(x,y,z,radius,predicate,noiseSampler);
        return reserved!=null?reserved:super.locateBiome(x,y,z,radius,blockCheckInterval,predicate,random,bl,noiseSampler);
    }

    private Pair<BlockPos,RegistryEntry<Biome>> locateReserved(int x,int y,int z,int radius,
                                                                Predicate<RegistryEntry<Biome>> predicate,MultiNoiseUtil.MultiNoiseSampler noise){
        requireBiomeIdMap();Pair<BlockPos,RegistryEntry<Biome>> best=null;double bestDistance=Double.MAX_VALUE;
        for(var reservation:WorldBlueprintManager.biomeReservations()){
            RegistryEntry<Biome> entry=biomeIdMap.get(reservation.biomeId());if(entry==null||!predicate.test(entry))continue;
            var p=WorldBlueprintManager.reservationCenter(reservation);double distance=Math.hypot(p.x()-x,p.z()-z);
            if(distance<=radius&&distance<bestDistance){
                BlockPos pos=new BlockPos((int)Math.round(p.x()),y,(int)Math.round(p.z()));
                RegistryEntry<Biome> actual=getBiome(BiomeCoords.fromBlock(pos.getX()),BiomeCoords.fromBlock(y),BiomeCoords.fromBlock(pos.getZ()),noise);
                if(predicate.test(actual)){bestDistance=distance;best=Pair.of(pos,actual);}
            }
        }
        return best;
    }
}

