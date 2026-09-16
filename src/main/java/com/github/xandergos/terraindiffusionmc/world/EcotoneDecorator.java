package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

/** Continuous source-family weights drive decoration across regional edges.
 * Samples use world coordinates, never neighboring chunk generation or tile edges. */
public final class EcotoneDecorator {
    private EcotoneDecorator(){}
    public record Blend(float woodland,float coldWoodland,float dry){}
    public static Blend at(int x,int z){
        if(SurfaceTransitions.enabled())return new Blend(RegionalEcotones.weight(RegionalEcotones.WOOD,x,z),RegionalEcotones.weight(RegionalEcotones.COLD_WOOD,x,z),RegionalEcotones.weight(RegionalEcotones.DRY,x,z));
        var store=WorldBlueprintManager.activeStore();var detail=WorldBlueprintManager.detail();
        if(store==null||detail==null)return new Blend(0,0,0);
        int scale=WorldScaleManager.getCurrentScale();double mw=store.manifest().width()*256.0*scale,mh=store.manifest().height()*256.0*scale;
        double radius=110.0*scale,trees=0,cold=0,dry=0,total=0;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            double u=(x+dx*radius)/mw+.5,v=(z+dz*radius)/mh+.5;
            if(u<0||u>1||v<0||v>1)continue;
            double weight=(dx==0?2:1)*(dz==0?2:1);total+=weight;
            int anchor=Math.round(detail.nearest(1,u,v));
            if(woodland(anchor))trees+=weight;
            if(anchor==9||anchor==1000+BiomeIds.TAIGA||anchor==1000+BiomeIds.SNOWY_TAIGA)cold+=weight;
            if(anchor==1||anchor==2||anchor==1000+BiomeIds.DESERT)dry+=weight;
        }
        return total==0?new Blend(0,0,0):new Blend((float)(trees/total),(float)(cold/total),(float)(dry/total));
    }
    static boolean woodland(int anchor){
        if(anchor>=5&&anchor<=9)return true;
        if(anchor<1000)return false;
        if(SurfaceTransitions.enabled())switch(anchor-1000){
            case BiomeIds.PALE_GARDEN,BiomeIds.WINDSWEPT_FOREST,BiomeIds.CHERRY_GROVE,BiomeIds.GROVE,
                 BiomeIds.OLD_GROWTH_PINE_TAIGA,BiomeIds.OLD_GROWTH_SPRUCE_TAIGA,BiomeIds.BAMBOO_JUNGLE,
                 BiomeIds.SNOWY_TAIGA_SPARSE,BiomeIds.WOODED_BADLANDS,BiomeIds.MANGROVE_SWAMP -> {return true;}
        }
        return switch(anchor-1000){
            case BiomeIds.FOREST,BiomeIds.FOREST_SPARSE,BiomeIds.BIRCH_FOREST,BiomeIds.OLD_GROWTH_BIRCH_FOREST,
                 BiomeIds.DARK_FOREST,BiomeIds.FLOWER_FOREST,BiomeIds.TAIGA,BiomeIds.TAIGA_SPARSE,
                 BiomeIds.SNOWY_TAIGA,BiomeIds.JUNGLE,BiomeIds.SPARSE_JUNGLE -> true;
            default -> false;
        };
    }
    public static void surface(Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz,int x,int z,int y,short biome){
        if(biome!=BiomeIds.DESERT)return;
        int r=z-tz,c=x-tx;if(data.heightmap[r][c]>1800)return;
        Blend blend=at(x,z);if(blend.woodland()<=0||blend.woodland()>=1)return;
        double n=(WorldHydrology.edgeNoise(x,z,LocalTerrainProvider.getSeed()^0x45434F544F4E45L,18*WorldScaleManager.getCurrentScale())+1)*.5;
        if(n>blend.woodland()*.85)return;
        var pos=new BlockPos(x,y,z);var ground=chunk.getBlockState(pos);
        if(!ground.isOf(Blocks.SAND)&&!ground.isIn(BlockTags.DIRT))return;
        if(!chunk.getBlockState(pos.up()).isAir())return;
        chunk.setBlockState(pos,Blocks.GRASS_BLOCK.getDefaultState());
        var below=chunk.getBlockState(pos.down());
        if(below.isOf(Blocks.SAND)||below.isIn(BlockTags.DIRT))chunk.setBlockState(pos.down(),Blocks.DIRT.getDefaultState());
    }
}
