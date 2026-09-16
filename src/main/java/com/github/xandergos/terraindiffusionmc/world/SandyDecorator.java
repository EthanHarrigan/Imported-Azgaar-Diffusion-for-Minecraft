package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.block.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.TreeConfiguredFeatures;

/** Rare dry snags and small freshwater bank oases. Candidate/soil patches stay in their owning chunk;
 * living trees use vanilla's bounded feature placement. */
public final class SandyDecorator {
    private SandyDecorator(){}
    public static void decorate(StructureWorldAccess world,Chunk chunk,ChunkGenerator generator,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        if(data.contexts!=null)return; // Keep unbudgeted soil/oasis patches out of the owner pass.
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ(),cx=chunk.getPos().x,cz=chunk.getPos().z,scale=WorldScaleManager.getCurrentScale();
        long seed=LocalTerrainProvider.getSeed();Random random=Random.create(Double.doubleToLongBits(SurfaceAccentRules.unit(seed,cx,cz,0x53414E44L)));
        int x=sx+5+random.nextInt(6),z=sz+5+random.nextInt(6),r=z-tz,c=x-tx;
        if(SurfaceTransitions.enabled()&&com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.sandSea(
                x/(double)scale,z/(double)scale)>.90)return;
        short id=data.biomeIds[r][c];boolean beach=id==BiomeIds.BEACH;
        if(id!=BiomeIds.DESERT&&!beach&&id!=BiomeIds.BADLANDS&&id!=BiomeIds.ERODED_BADLANDS)return;
        int y=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x-sx,z-sz);
        BlockPos ground=new BlockPos(x,y,z);
        if(!sand(world.getBlockState(ground))||!world.getBlockState(ground.up()).isAir())return;
        if(data.waterSurface!=null&&data.waterSurface[r][c]!=Short.MIN_VALUE)return;
        // No cliff-top oasis: nearby water must also be at approximately ground level.
        var prepared=com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology.preview();
        boolean fresh=random.nextFloat()<.12f&&prepared!=null&&prepared.freshwaterNear(x/(double)scale,z/(double)scale,data.heightmap[r][c]);
        if(fresh&&flat(chunk,x,z,y)){
            // Preflight compact oak/acacia canopy clearance; normal configured features do final validation.
            if(!clear(world,x,y+1,z,3,9))return;
            for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++){
                if(dx*dx+dz*dz>5||random.nextFloat()<.15f)continue;
                int yy=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x+dx-sx,z+dz-sz);
                BlockPos p=new BlockPos(x+dx,yy,z+dz);
                if(Math.abs(yy-y)<=1&&sand(world.getBlockState(p))&&world.getBlockState(p.up()).isAir()){
                    world.setBlockState(p,Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);
                    if(sand(world.getBlockState(p.down())))world.setBlockState(p.down(),Blocks.DIRT.getDefaultState(),Block.NOTIFY_LISTENERS);
                }
            }
            world.setBlockState(ground,Blocks.GRASS_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS);
            var key=random.nextBoolean()?TreeConfiguredFeatures.ACACIA:TreeConfiguredFeatures.OAK;
            world.getRegistryManager().getOrThrow(RegistryKeys.CONFIGURED_FEATURE).getOrThrow(key).value().generate(world,generator,random,ground.up());
        }else if(SurfaceAccentRules.deadWood(seed,cx,cz,beach)&&flat(chunk,x,z,y)){
            int height=beach?1:3+random.nextInt(4);if(!clear(world,x,y+1,z,2,height+1))return;
            Block wood=beach?Blocks.STRIPPED_OAK_LOG:Blocks.STRIPPED_ACACIA_LOG;
            if(beach){
                for(int dx=-1;dx<=2;dx++){
                    int yy=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x+dx-sx,z-sz);
                    if(yy!=y||!sand(world.getBlockState(new BlockPos(x+dx,yy,z))))return;
                }
                for(int dx=-1;dx<=2;dx++)world.setBlockState(new BlockPos(x+dx,y+1,z),wood.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X),Block.NOTIFY_LISTENERS);
            }else{
                for(int dy=1;dy<=height;dy++)world.setBlockState(ground.up(dy),wood.getDefaultState(),Block.NOTIFY_LISTENERS);
                int sign=random.nextBoolean()?1:-1;
                world.setBlockState(ground.add(sign,height-1,0),wood.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X),Block.NOTIFY_LISTENERS);
                if(height>4)world.setBlockState(ground.add(0,height-2,-sign),wood.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.Z),Block.NOTIFY_LISTENERS);
            }
        }
    }
    private static boolean sand(BlockState s){return s.isOf(Blocks.SAND)||s.isOf(Blocks.RED_SAND);}
    private static boolean flat(Chunk chunk,int x,int z,int y){
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        for(int dz=-2;dz<=2;dz+=2)for(int dx=-2;dx<=2;dx+=2)
            if(Math.abs(chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x+dx-sx,z+dz-sz)-y)>1)return false;
        return true;
    }
    private static boolean clear(StructureWorldAccess world,int x,int y,int z,int radius,int height){
        for(int dy=0;dy<height;dy++)for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++)
            if(!world.getBlockState(new BlockPos(x+dx,y+dy,z+dz)).isAir())return false;
        return true;
    }
}
