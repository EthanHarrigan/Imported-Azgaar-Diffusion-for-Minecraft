package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.*;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;

/** A handful of bounded attempts per chunk; no neighbour-chunk scans or extra terrain inference. */
public final class DesertVegetation {
    private DesertVegetation(){}
    public static void decorate(StructureWorldAccess world,Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        if(data.contexts!=null)return; // Landmarks belong to the height stage; vanilla supplies sparse desert plants.
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ(),scale=WorldScaleManager.getCurrentScale();
        long seed=LocalTerrainProvider.getSeed();
        Random random=Random.create(Double.doubleToLongBits(SurfaceAccentRules.unit(seed,sx,sz,0x4445534552545645L)));
        var prepared=WorldHydrology.preview();
        for(int attempt=0;attempt<4;attempt++){
            int x=sx+2+random.nextInt(12),z=sz+2+random.nextInt(12),r=z-tz,c=x-tx;
            if(SurfaceTransitions.enabled()&&DesertTerrain.sandSea(x/(double)scale,z/(double)scale)>.90)continue;
            if(!DesertSurface.dryCompatible(data.biomeIds[r][c])||data.waterSurface!=null&&data.waterSurface[r][c]!=Short.MIN_VALUE)continue;
            var f=DesertTerrain.at(x/(double)scale,z/(double)scale,seed);if(f.dry()<.15)continue;
            int y=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x-sx,z-sz);
            if(y<chunk.getBottomY()||y+3>chunk.getTopYInclusive())continue;
            BlockPos ground=new BlockPos(x,y,z),pos=ground.up();
            if(!world.getBlockState(pos).isAir())continue;
            if(attempt==0&&f.sand()<.45&&f.dry()>.7&&f.waterProtection()>.99&&random.nextInt(45)==0){
                outcrop(world,chunk,ground,f,seed,scale);
                continue;
            }
            boolean fresh=prepared!=null&&prepared.freshwaterNear(x/(double)scale,z/(double)scale,data.heightmap[r][c]);
            if(random.nextDouble()>(fresh?.6:.03+.38*f.scrub()))continue;
            BlockState soil=world.getBlockState(ground);
            boolean earthy=soil.isIn(BlockTags.DIRT)||soil.isOf(Blocks.PACKED_MUD);
            Block plant=earthy&&fresh?Blocks.SHORT_GRASS:Blocks.DEAD_BUSH;
            if(fresh&&earthy&&random.nextInt(100)==0)plant=Blocks.TORCHFLOWER;
            else if(fresh&&soil.isIn(BlockTags.DIRT)&&random.nextInt(180)==0&&world.getBlockState(pos.up()).isAir()){
                if(Blocks.PITCHER_PLANT.getDefaultState().canPlaceAt(world,pos))TallPlantBlock.placeAt(world,Blocks.PITCHER_PLANT.getDefaultState(),pos,Block.NOTIFY_LISTENERS);
                continue;
            }else if(!fresh&&(soil.isOf(Blocks.SAND)||soil.isOf(Blocks.RED_SAND))&&f.scrub()>.25&&random.nextInt(5)==0)plant=Blocks.CACTUS;
            BlockState state=plant.getDefaultState();
            if(state.canPlaceAt(world,pos))world.setBlockState(pos,state,Block.NOTIFY_LISTENERS);
        }
    }
    private static void outcrop(StructureWorldAccess world,Chunk chunk,BlockPos ground,DesertTerrain.Field f,long seed,int scale){
        int x=ground.getX(),y=ground.getY(),z=ground.getZ(),sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        // Preflight the entire little ledge: no partially placed or floating rock clusters.
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            int yy=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x+dx-sx,z+dz-sz);
            var p=new BlockPos(x+dx,yy,z+dz);
            if(Math.abs(yy-y)>1||!world.getBlockState(p).isSolidBlock(world,p))return;
            for(int h=1;h<=3;h++)if(!world.getBlockState(p.up(h)).isAir())return;
        }
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            if(Math.abs(dx)+Math.abs(dz)==2)continue;
            int yy=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x+dx-sx,z+dz-sz),top=y+(dx==0&&dz==0?3:1);
            for(int h=yy+1;h<=top;h++)world.setBlockState(new BlockPos(x+dx,h,z+dz),DesertSurface.rock((x+dx)/(double)scale,h,(z+dz)/(double)scale,f,seed).getDefaultState(),Block.NOTIFY_LISTENERS);
        }
    }
}
