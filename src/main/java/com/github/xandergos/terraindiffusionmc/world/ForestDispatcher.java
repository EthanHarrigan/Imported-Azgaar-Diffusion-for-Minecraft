package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

/** Original small tree blueprints on fixed world-coordinate sites. All writes stay in the owner
 * chunk, and the entire canopy is preflighted against final biome and existing blocks. */
public final class ForestDispatcher {
    private ForestDispatcher(){}
    public static void decorate(Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        if(data.contexts==null)return;
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();long seed=LocalTerrainProvider.getSeed();
        int x=sx+5+(int)(SurfaceAccentRules.unit(seed,sx,sz,100)*6),z=sz+5+(int)(SurfaceAccentRules.unit(seed,sx,sz,101)*6);
        var c=data.contexts[z-tz][x-tx];if(!ForestDistricts.woodland(c.biome())||c.wet()||c.slope()>.45)return;
        var district=ForestDistricts.at(x,z,seed);
        if(SurfaceAccentRules.unit(seed,sx,sz,102)>.86*district.canopy())return;
        int y=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,x-sx,z-sz),height=6+(int)(SurfaceAccentRules.unit(seed,sx,sz,103)*6);
        if(Math.abs(y-c.surfaceY())>3||y+height+2>=chunk.getTopYInclusive())return;
        var pos=new BlockPos.Mutable();pos.set(x,y,z);
        if(!chunk.getBlockState(pos).isIn(BlockTags.DIRT))return;
        for(int dz=-3;dz<=3;dz++)for(int dx=-3;dx<=3;dx++){
            var other=data.contexts[z+dz-tz][x+dx-tx];
            if(other.biome()!=c.biome()||other.wet()||ForestDistricts.at(x+dx,z+dz,seed).path()>.4)return;
            for(int yy=y+1;yy<=y+height+1;yy++){
                pos.set(x+dx,yy,z+dz);if(!chunk.getBlockState(pos).isAir())return;
            }
        }
        boolean birch=c.biome()==BiomeIds.BIRCH_FOREST||c.biome()==BiomeIds.OLD_GROWTH_BIRCH_FOREST;
        boolean spruce=c.biome()==BiomeIds.TAIGA||c.biome()==BiomeIds.TAIGA_SPARSE||c.biome()==BiomeIds.OLD_GROWTH_PINE_TAIGA||c.biome()==BiomeIds.OLD_GROWTH_SPRUCE_TAIGA;
        boolean jungle=c.biome()==BiomeIds.JUNGLE||c.biome()==BiomeIds.SPARSE_JUNGLE||c.biome()==BiomeIds.BAMBOO_JUNGLE;
        Block log=birch?Blocks.BIRCH_LOG:spruce?Blocks.SPRUCE_LOG:jungle?Blocks.JUNGLE_LOG:Blocks.OAK_LOG;
        Block leaves=birch?Blocks.BIRCH_LEAVES:spruce?Blocks.SPRUCE_LEAVES:jungle?Blocks.JUNGLE_LEAVES:Blocks.OAK_LEAVES;
        for(int yy=y+1;yy<=y+height;yy++){pos.set(x,yy,z);chunk.setBlockState(pos,log.getDefaultState());}
        for(int dy=height-3;dy<=height+1;dy++){
            int radius=spruce?Math.max(1,(height+2-dy)/2):dy==height+1?1:dy==height?2:3;
            for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){
                if(dx*dx+dz*dz>radius*radius+1||dx==0&&dz==0&&dy<=height)continue;
                pos.set(x+dx,y+dy,z+dz);chunk.setBlockState(pos,leaves.getDefaultState().with(LeavesBlock.DISTANCE,1));
            }
        }
    }
}
