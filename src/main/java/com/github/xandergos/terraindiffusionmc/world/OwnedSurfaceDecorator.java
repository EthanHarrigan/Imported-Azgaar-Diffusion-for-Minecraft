package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.*;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

/** Single ordered consumer: water wins, then owned landform, then owned surface, then accents. */
public final class OwnedSurfaceDecorator {
    private OwnedSurfaceDecorator(){}
    public static Block desertMaterial(int x,int y,int z,int baseY,DesertTerrain.Field f,DesertLandforms.Sample land){
        if(land.rockHeight()<=1)return f.red()>.82?Blocks.RED_SAND:Blocks.SAND;
        return DesertProvinces.strata(x,y-baseY,z,land.siteSeed());
    }
    public static Block material(ColumnWorldContext c,int y,long seed){
        short owner=c.biome();
        if(c.transition()>0&&compatible(owner)&&compatible(c.neighbor())){
            // One curved dividing line between the actual two owners. No per-block lottery.
            double signed=c.signedBoundaryDistance()+c.boundaryWidth()*.85*DesertTerrain.noise(c.x(),c.z(),seed^0x424F554E44415259L,96);
            owner=signed<0?(short)Math.min(c.biome(),c.neighbor()):(short)Math.max(c.biome(),c.neighbor());
        }
        if(owner==BiomeIds.BADLANDS||owner==BiomeIds.ERODED_BADLANDS||owner==BiomeIds.WOODED_BADLANDS)
            return DesertProvinces.strata(c.x()/(double)WorldScaleManager.getCurrentScale(),y,c.z()/(double)WorldScaleManager.getCurrentScale(),seed);
        if(owner==BiomeIds.DESERT)return desertMaterial(c.x(),y,c.z(),c.baseY(),c.desert(),c.landform());
        if(ForestDistricts.woodland(owner)){
            var d=ForestDistricts.at(c.x(),c.z(),seed);
            return d.path()>.6?Blocks.DIRT_PATH:Blocks.GRASS_BLOCK;
        }
        return compatible(owner)?Blocks.GRASS_BLOCK:null;
    }
    private static boolean compatible(short id){return id==BiomeIds.DESERT||ForestDistricts.woodland(id)||id==BiomeIds.PLAINS||id==BiomeIds.SUNFLOWER_PLAINS||id==BiomeIds.SAVANNA;}
    public static void decorate(Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();long seed=LocalTerrainProvider.getSeed();
        var pos=new BlockPos.Mutable();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            var c=data.contexts[sz+z-tz][sx+x-tx];if(c.wet()||c.waterDistance()<4)continue;
            int y=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,x,z);
            if(Math.abs(y-c.surfaceY())>3||y<chunk.getBottomY()+4||y>=chunk.getTopYInclusive())continue;
            Block material=material(c,y,seed);if(material==null)continue;
            pos.set(c.x(),y,c.z());if(!natural(chunk.getBlockState(pos))||!chunk.getBlockState(pos.up()).isAir())continue;
            // Change only existing solid natural mass. Never fill caves or structure interiors.
            boolean badlands=c.biome()==BiomeIds.BADLANDS||c.biome()==BiomeIds.ERODED_BADLANDS||c.biome()==BiomeIds.WOODED_BADLANDS;
            int depth=badlands?Math.min(112,Math.max(3,(int)Math.ceil(c.slope()*3)+3)):c.rock()?Math.min(112,(int)Math.ceil(c.landform().rockHeight())+4):3;
            for(int d=depth-1;d>=0;d--){
                pos.set(c.x(),y-d,c.z());BlockState old=chunk.getBlockState(pos);
                if(!natural(old))continue;
                Block b=d==0?material:(c.rock()||badlands)?material(c,y-d,seed):c.biome()==BiomeIds.DESERT?
                        (c.desert().red()>.82?Blocks.RED_SANDSTONE:Blocks.SANDSTONE):Blocks.DIRT;
                chunk.setBlockState(pos,b.getDefaultState());
            }
        }
    }
    private static boolean natural(BlockState b){return b.isIn(BlockTags.TERRACOTTA)||b.isIn(BlockTags.DIRT)||b.isIn(BlockTags.BASE_STONE_OVERWORLD)||b.isIn(BlockTags.SAND)
            ||b.isOf(Blocks.TERRACOTTA)||b.isOf(Blocks.ORANGE_TERRACOTTA)||b.isOf(Blocks.WHITE_TERRACOTTA)||b.isOf(Blocks.BROWN_TERRACOTTA)||b.isOf(Blocks.RED_TERRACOTTA)||b.isOf(Blocks.STONE)||b.isOf(Blocks.SAND)||b.isOf(Blocks.RED_SAND)||b.isOf(Blocks.SANDSTONE)||b.isOf(Blocks.RED_SANDSTONE)||b.isOf(Blocks.GRAVEL);}
}
