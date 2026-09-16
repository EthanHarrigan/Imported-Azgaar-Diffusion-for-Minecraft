package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.SharedConstants;
import net.minecraft.Bootstrap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.registry.*;
import net.minecraft.world.chunk.*;
import net.minecraft.util.math.*;
import net.minecraft.block.Blocks;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.*;
import com.github.xandergos.terraindiffusionmc.pipeline.*;

/** Fabric Loader JUnit transforms actual Minecraft classes, without starting a server or touching a save. */
class MinecraftMixinTest {
    @Test void generationHooksApplyToActualMinecraftClasses(){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        assertTrue(Arrays.stream(net.minecraft.server.world.ServerWorld.class.getDeclaredMethods()).anyMatch(m->m.getName().contains("terrainDiffusion$initializeBeforeStructures")),"Early world initialization mixin was not applied");
        assertTrue(Arrays.stream(ChunkGenerator.class.getDeclaredMethods()).anyMatch(m->m.getName().contains("terrainDiffusion$water")),"River decoration mixin was not applied");
        assertTrue(Arrays.stream(ChunkGenerator.class.getDeclaredMethods()).anyMatch(m->m.getName().contains("terrainDiffusion$finishMountainSurface")),"Mountain finishing mixin was not applied");
        assertTrue(Arrays.stream(Structure.class.getDeclaredMethods()).anyMatch(m->m.getName().contains("terrainDiffusion$guard")),"Structure footprint mixin was not applied");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={6000,22000})
    void inlandWaterAndIceAreWrittenIntoRealTallMinecraftChunks(int elevation){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        var lookup=BuiltinRegistries.createWrapperLookup();
        var biomes=new SimpleRegistry<Biome>(RegistryKeys.BIOME,com.mojang.serialization.Lifecycle.stable());
        Registry.register(biomes,BiomeKeys.PLAINS,lookup.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.PLAINS).value());biomes.freeze();
        var registries=new DynamicRegistryManager.ImmutableImpl(java.util.List.of(biomes));
        var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.NO_UPGRADE_DATA,HeightLimitView.create(VerticalProfile.BOTTOM_Y,VerticalProfile.HEIGHT),PalettesFactory.fromRegistryManager(registries),null);
        short[][] heights=new short[16][16],ids=new short[16][16],waters=new short[16][16];
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            heights[z][x]=(short)elevation;waters[z][x]=Short.MIN_VALUE;ids[z][x]=BiomeIds.PLAINS;
            int bed=HeightConverter.convertToMinecraftHeight((short)elevation)-1;
            for(int y=bed-3;y<=bed;y++)chunk.setBlockState(new BlockPos(x,y,z),Blocks.STONE.getDefaultState());
        }
        waters[8][7]=(short)(elevation+30);ids[8][7]=BiomeIds.RIVER;waters[8][8]=(short)(elevation+30);ids[8][8]=BiomeIds.FROZEN_RIVER;
        int intendedBed=HeightConverter.convertToMinecraftHeight((short)elevation)-1;
        int intendedSurface=HeightConverter.convertToMinecraftHeight((short)(elevation+30))-1;
        for(int y=intendedBed+1;y<=intendedSurface+4;y++)chunk.setBlockState(new BlockPos(7,y,8),Blocks.STONE.getDefaultState());
        chunk.setBlockState(new BlockPos(8,intendedBed,8),Blocks.AIR.getDefaultState());
        TerrainDiffusionWaterDecorator.decorate(chunk,new LocalTerrainProvider.HeightmapData(heights,ids,waters,16,16),0,0);
        int surface=HeightConverter.convertToMinecraftHeight((short)(elevation+30))-1;
        assertTrue(elevation==6000?surface< -64:surface>319,"Exercise both sides of vanilla's vertical range");
        assertTrue(chunk.getBlockState(new BlockPos(7,surface,8)).isOf(Blocks.WATER));
        assertTrue(chunk.getBlockState(new BlockPos(8,surface,8)).isOf(Blocks.ICE));
        assertTrue(chunk.getBlockState(new BlockPos(8,surface-1,8)).isOf(Blocks.WATER));
        assertTrue(chunk.getBlockState(new BlockPos(6,surface,8)).isAir());
        assertTrue(chunk.getBlockState(new BlockPos(7,surface+1,8)).isAir(),"Interpolated stone must not cap an open river");
        assertFalse(chunk.getBlockState(new BlockPos(8,intendedBed,8)).isAir(),"River liner must close sub-bed interpolation gaps");
        assertEquals(surface,chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,7,8));
    }
    @Test void riverbedMaterialsAreContextualRatherThanTwelveBlockTiles(){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        short[][] low={{100}},mountain={{1800}},ids={{BiomeIds.RIVER}},water={{150}};
        var lowData=new LocalTerrainProvider.HeightmapData(low,ids,water,1,1);
        var highData=new LocalTerrainProvider.HeightmapData(mountain,ids,new short[][]{{1850}},1,1);
        java.util.Set<net.minecraft.block.Block> lowland=new java.util.HashSet<>();
        for(int z=0;z<200;z+=7)for(int x=0;x<200;x+=7)
            lowland.add(TerrainDiffusionWaterDecorator.bedMaterial(lowData,0,0,x,z,(short)150));
        assertTrue(lowland.size()>1,"river material must vary in smooth terrain context, not one fixed square");
        for(int z=0;z<80;z+=7)for(int x=0;x<80;x+=7){
            var block=TerrainDiffusionWaterDecorator.bedMaterial(highData,0,0,x,z,(short)1850);
            assertTrue(block==Blocks.STONE||block==Blocks.ANDESITE||block==Blocks.DIORITE||block==Blocks.GRANITE||block==Blocks.GRAVEL);
        }
    }
    @Test void mountainSurfaceExposesRockAndMeadowsWithoutReplacingTrees() throws ReflectiveOperationException{
        SharedConstants.createGameVersion();Bootstrap.initialize();
        bindSurfaceTags();
        var lookup=BuiltinRegistries.createWrapperLookup();
        var biomes=new SimpleRegistry<Biome>(RegistryKeys.BIOME,com.mojang.serialization.Lifecycle.stable());
        Registry.register(biomes,BiomeKeys.PLAINS,lookup.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.PLAINS).value());biomes.freeze();
        var registries=new DynamicRegistryManager.ImmutableImpl(java.util.List.of(biomes));
        var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.NO_UPGRADE_DATA,HeightLimitView.create(VerticalProfile.BOTTOM_Y,VerticalProfile.HEIGHT),PalettesFactory.fromRegistryManager(registries),null);
        short[][] heights=new short[16][16],ids=new short[16][16];int y=600;
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            heights[z][x]=4000;ids[z][x]=x<8?BiomeIds.STONY_PEAKS:BiomeIds.MEADOW;
            chunk.setBlockState(new BlockPos(x,y-1,z),Blocks.STONE.getDefaultState());
            chunk.setBlockState(new BlockPos(x,y,z),Blocks.SNOW_BLOCK.getDefaultState());
            chunk.setBlockState(new BlockPos(x,y+1,z),Blocks.SNOW.getDefaultState());
        }
        chunk.setBlockState(new BlockPos(14,y+1,14),Blocks.SPRUCE_LOG.getDefaultState());
        TerrainDiffusionSurfaceDecorator.decorateMountainZones(chunk,new LocalTerrainProvider.HeightmapData(heights,ids,16,16),0,0,true);
        assertFalse(chunk.getBlockState(new BlockPos(2,y,2)).isOf(Blocks.SNOW_BLOCK));
        assertTrue(chunk.getBlockState(new BlockPos(2,y+1,2)).isAir());
        assertTrue(chunk.getBlockState(new BlockPos(10,y,10)).isOf(Blocks.GRASS_BLOCK));
        assertTrue(chunk.getBlockState(new BlockPos(10,y-1,10)).isOf(Blocks.DIRT));
        assertTrue(chunk.getBlockState(new BlockPos(14,y+1,14)).isOf(Blocks.SPRUCE_LOG));
    }
    @Test void finalSnowPassClearsNarrowStoneRidgesButKeepsShelteredSnow() throws ReflectiveOperationException{
        SharedConstants.createGameVersion();Bootstrap.initialize();
        bindSurfaceTags();
        var lookup=BuiltinRegistries.createWrapperLookup();
        var biomes=new SimpleRegistry<Biome>(RegistryKeys.BIOME,com.mojang.serialization.Lifecycle.stable());
        Registry.register(biomes,BiomeKeys.PLAINS,lookup.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.PLAINS).value());biomes.freeze();
        var registries=new DynamicRegistryManager.ImmutableImpl(java.util.List.of(biomes));
        var chunk=new ProtoChunk(new ChunkPos(0,0),UpgradeData.NO_UPGRADE_DATA,HeightLimitView.create(VerticalProfile.BOTTOM_Y,VerticalProfile.HEIGHT),PalettesFactory.fromRegistryManager(registries),null);
        short[][] heights=new short[16][16],ids=new short[16][16];int y=600;
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            heights[z][x]=(short)(x==8?4000:3800);ids[z][x]=BiomeIds.SNOWY_SLOPES;
            chunk.setBlockState(new BlockPos(x,y,z),Blocks.STONE.getDefaultState());
            chunk.setBlockState(new BlockPos(x,y+1,z),Blocks.SNOW.getDefaultState());
        }
        chunk.setBlockState(new BlockPos(8,y,14),Blocks.SPRUCE_LOG.getDefaultState());
        chunk.setBlockState(new BlockPos(8,y,15),Blocks.STONE_BRICKS.getDefaultState());
        var data=new LocalTerrainProvider.HeightmapData(heights,ids,16,16);
        TerrainDiffusionSurfaceDecorator.decorateMountainZones(chunk,data,0,0,true);
        for(int z=0;z<14;z++){
            assertTrue(chunk.getBlockState(new BlockPos(8,y+1,z)).isAir(),"No snow film on narrow ridge even though central slope is zero");
            assertFalse(chunk.getBlockState(new BlockPos(8,y,z)).isOf(Blocks.SNOW_BLOCK));
        }
        assertTrue(chunk.getBlockState(new BlockPos(8,y+1,14)).isOf(Blocks.SNOW),"Snow on trees is untouched");
        assertTrue(chunk.getBlockState(new BlockPos(8,y+1,15)).isOf(Blocks.SNOW),"Snow on built blocks is untouched");
        int sheltered=0;
        for(int z=0;z<16;z++)for(int x=0;x<5;x++)
            if(chunk.getBlockState(new BlockPos(x,y+1,z)).isOf(Blocks.SNOW))sheltered++;
        assertTrue(sheltered>0,"Gentle snowy terrain must retain snow");
    }
    @Test void cliffDetectionHandlesLipsDiagonalFacesTileEdgesAndWorldScales(){
        short[][] heights=new short[16][16],ids=new short[16][16];
        var data=new LocalTerrainProvider.HeightmapData(heights,ids,16,16);
        for(int scale=1;scale<=6;scale++){
            for(short[] row:heights)Arrays.fill(row,(short)4000);
            assertFalse(TerrainDiffusionSurfaceDecorator.steepSnowFace(data,8,8,scale));
            heights[8][7]=3800;
            assertTrue(TerrainDiffusionSurfaceDecorator.steepSnowFace(data,8,8,scale),"Cliff lip");
            heights[8][9]=3800;
            assertTrue(TerrainDiffusionSurfaceDecorator.steepSnowFace(data,8,8,scale),"Symmetric ridge");
            for(short[] row:heights)Arrays.fill(row,(short)4000);
            heights[10][10]=3600;
            assertTrue(TerrainDiffusionSurfaceDecorator.steepSnowFace(data,8,8,scale),"Diagonal ledge");
            heights[0][1]=3800;
            assertTrue(TerrainDiffusionSurfaceDecorator.steepSnowFace(data,0,0,scale),"Available tile-edge samples");
            for(int z=0;z<16;z++)for(int x=0;x<16;x++)heights[z][x]=(short)(4000+x*5);
            assertFalse(TerrainDiffusionSurfaceDecorator.steepSnowFace(data,8,8,scale),"Gentle one-block stairs");
        }
    }

    private static void bindSurfaceTags() throws ReflectiveOperationException{
        // Bootstrap does not load datapacks. Bind only the tags this isolated surface test needs.
        var bindTags=net.minecraft.registry.entry.RegistryEntry.Reference.class.getDeclaredMethod("setTags",java.util.Collection.class);
        bindTags.setAccessible(true);
        for(var block:Registries.BLOCK){
            java.util.List<net.minecraft.registry.tag.TagKey<net.minecraft.block.Block>> tags=new java.util.ArrayList<>();
            if(block==Blocks.DIRT||block==Blocks.GRASS_BLOCK)tags.add(net.minecraft.registry.tag.BlockTags.DIRT);
            if(block==Blocks.STONE||block==Blocks.ANDESITE||block==Blocks.GRANITE||block==Blocks.DIORITE)tags.add(net.minecraft.registry.tag.BlockTags.BASE_STONE_OVERWORLD);
            bindTags.invoke(block.getRegistryEntry(),tags);
        }
    }

}
