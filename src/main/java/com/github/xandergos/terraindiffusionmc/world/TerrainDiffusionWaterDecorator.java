package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.*;
import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

/** Applies inland water before vanilla vegetation, only during initial chunk generation. */
public final class TerrainDiffusionWaterDecorator {
    private TerrainDiffusionWaterDecorator(){}
    public static void decorate(Chunk chunk){
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ(),tile=TerrainDiffusionConfig.tileSize();
        int tx=Math.floorDiv(sx,tile)*tile,tz=Math.floorDiv(sz,tile)*tile;
        var data=LocalTerrainProvider.getInstance().fetchHeightmap(tz,tx,tz+tile,tx+tile);
        decorate(chunk,data,tx,tz);
    }
    public static void decorate(Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        if(data.waterSurface==null)return;
        BlockPos.Mutable pos=new BlockPos.Mutable();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int r=sz+z-tz,c=sx+x-tx;short water=data.waterSurface[r][c];if(water==Short.MIN_VALUE)continue;
            int bed=HeightConverter.convertToMinecraftHeight(data.heightmap[r][c])-1;
            int top=HeightConverter.convertToMinecraftHeight(water)-1;
            int max=chunk.getTopYInclusive();
            if(top>max-2)throw new IllegalStateException("River water exceeds this world's build ceiling; refusing clipped water generation");
            bed=Math.max(chunk.getBottomY(),bed);
            int actualTop=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x,z);
            // Vanilla interpolates the density field horizontally. A narrow stream can
            // otherwise retain a stone cap above its intended water surface.
            for(int y=top+1;y<=Math.min(max,actualTop);y++){
                pos.set(sx+x,y,sz+z);BlockState old=chunk.getBlockState(pos);
                if(natural(old))chunk.setBlockState(pos,Blocks.AIR.getDefaultState());
            }
            BlockState material=bedMaterial(data,r,c,sx+x,sz+z,water).getDefaultState();
            for(int depth=0;depth<3&&bed-depth>=chunk.getBottomY();depth++){
                pos.set(sx+x,bed-depth,sz+z);BlockState old=chunk.getBlockState(pos);
                if(!old.isAir()&&old.getFluidState().isEmpty()&&!natural(old))break;
                // Seal interpolation gaps beneath the intended bed as well as recoloring stone.
                chunk.setBlockState(pos,material);
            }
            for(int y=bed+1;y<=top;y++){
                pos.set(sx+x,y,sz+z);BlockState old=chunk.getBlockState(pos);
                if(old.isAir()||!old.getFluidState().isEmpty()||natural(old)){
                    BlockState state=y==top&&data.biomeIds[r][c]==BiomeIds.FROZEN_RIVER?Blocks.ICE.getDefaultState():Blocks.WATER.getDefaultState();
                    chunk.setBlockState(pos,state);
                    if(y==top)chunk.markBlockForPostProcessing(pos);
                }
            }
        }
        Heightmap.populateHeightmaps(chunk,java.util.Set.of(Heightmap.Type.WORLD_SURFACE_WG,Heightmap.Type.OCEAN_FLOOR_WG));
    }
    static Block bedMaterial(LocalTerrainProvider.HeightmapData data,int r,int c,int worldX,int worldZ,short water){
        int h=data.heightmap.length,w=data.heightmap[0].length;
        float elevation=data.heightmap[r][c];
        float dx=data.heightmap[r][Math.min(w-1,c+1)]-data.heightmap[r][Math.max(0,c-1)];
        float dz=data.heightmap[Math.min(h-1,r+1)][c]-data.heightmap[Math.max(0,r-1)][c];
        float slope=(float)Math.hypot(dx,dz)/Math.max(1,60f/WorldScaleManager.getCurrentScale());
        var ecology=WorldBlueprintManager.ecologyAt(worldX,worldZ);
        int anchor=ecology.anchor();float depth=water-elevation;
        long seed=LocalTerrainProvider.getSeed();
        double broad=WorldHydrology.edgeNoise(worldX,worldZ,seed^0x5249564552424544L,72);
        double fine=WorldHydrology.edgeNoise(worldX,worldZ,seed^0x534544494D454E54L,27);
        boolean mountain=elevation>1200||slope>.42f;
        if(SurfaceTransitions.enabled()){
            double alpine=SurfaceTransitions.smooth((elevation-650)/1100)+SurfaceTransitions.smooth((slope-.2)/.45);
            alpine=Math.min(1,alpine);
            double dry=RegionalEcotones.weight(RegionalEcotones.DRY,worldX,worldZ);
            double coastal=1-SurfaceTransitions.smooth(Math.abs(ecology.coastDistanceKm())/7);
            double sand=(1-alpine)*Math.max(dry,coastal)*SurfaceTransitions.smooth(depth/60);
            return switch(SurfaceTransitions.choose(SurfaceTransitions.grain(worldX,worldZ,seed^0x4245443133L),alpine, .25, (1-alpine)*(1-sand)*.65,sand,(1-alpine)*.06)){
                case 0->SurfaceGeology.rock(worldX,HeightConverter.convertToMinecraftHeight((short)elevation),worldZ,data.biomeIds[r][c],seed);
                case 2->Blocks.DIRT;case 3->Blocks.SAND;case 4->Blocks.CLAY;default->Blocks.GRAVEL;};
        }
        if(mountain){
            if(fine>.48)return Blocks.GRAVEL;
            double family=WorldHydrology.edgeNoise(worldX,worldZ,seed^0x524F434B46414D49L,1800);
            if(family>.62)return Blocks.GRANITE;
            if(family<-.62)return Blocks.DIORITE;
            return family>.18?Blocks.ANDESITE:Blocks.STONE;
        }
        boolean dry=anchor==1||anchor==2,coastal=Math.abs(ecology.coastDistanceKm())<4;
        WorldHydrology prepared=WorldHydrology.preview();
        boolean major=prepared==null?depth>42:prepared.isMajorRiverAt(worldX,worldZ,WorldScaleManager.getCurrentScale());
        if(major&&(dry||coastal)&&broad>-.42)return dry&&broad>.48?Blocks.RED_SAND:Blocks.SAND;
        if(fine<-.58&&slope<.12f)return Blocks.CLAY;
        if(broad<-.20)return Blocks.DIRT;
        if(fine>.36)return Blocks.STONE;
        return Blocks.GRAVEL;
    }
    private static boolean natural(BlockState s){
        return s.isOf(Blocks.STONE)||s.isIn(BlockTags.DIRT)||s.isIn(BlockTags.BASE_STONE_OVERWORLD)||s.isIn(BlockTags.TERRACOTTA)||
                s.isOf(Blocks.GRAVEL)||s.isOf(Blocks.SAND)||s.isOf(Blocks.RED_SAND)||s.isOf(Blocks.CLAY)||
                s.isOf(Blocks.SNOW_BLOCK)||s.isOf(Blocks.ICE)||s.isOf(Blocks.SANDSTONE)||s.isOf(Blocks.RED_SANDSTONE);
    }
}
