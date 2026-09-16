package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;

/** Small water-surrounded coral outcrops within the two planned reef provinces. */
public final class MarineDecorator {
    private MarineDecorator(){}
    private static final Block[] CORAL={Blocks.TUBE_CORAL_BLOCK,Blocks.BRAIN_CORAL_BLOCK,Blocks.BUBBLE_CORAL_BLOCK,Blocks.FIRE_CORAL_BLOCK,Blocks.HORN_CORAL_BLOCK};
    private static final Block[] FANS={Blocks.TUBE_CORAL_FAN,Blocks.BRAIN_CORAL_FAN,Blocks.BUBBLE_CORAL_FAN,Blocks.FIRE_CORAL_FAN,Blocks.HORN_CORAL_FAN};
    public static void decorate(StructureWorldAccess world,Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ(),scale=WorldScaleManager.getCurrentScale();long seed=LocalTerrainProvider.getSeed();
        short center=data.biomeIds[sz+8-tz][sx+8-tx];
        if(!BiomeIds.isOcean(center))return;
        if(center!=BiomeIds.FROZEN_OCEAN&&center!=BiomeIds.DEEP_FROZEN_OCEAN)
            seaPlants(world,chunk,seed,center,data,tx,tz);
        var sites=ReefRegions.sites(WorldBlueprintManager.activeStore(),seed);
        double weight=ReefRegions.weight(sites,(sx+8)/(double)scale,(sz+8)/(double)scale,seed);
        if(weight<=0)return;
        Random random=Random.create(Double.doubleToLongBits(SurfaceAccentRules.unit(seed,chunk.getPos().x,chunk.getPos().z,0x434F52414CL)));
        if(random.nextDouble()>weight*.7)return;
        for(int attempt=0;attempt<2+random.nextInt(3);attempt++){
            int x=sx+3+random.nextInt(10),z=sz+3+random.nextInt(10),r=z-tz,c=x-tx;
            if(!BiomeIds.isOcean(data.biomeIds[r][c]))continue;
            // Water-column depth in actual Minecraft blocks, not physical map metres.
            int y=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,x-sx,z-sz);
            if(!reefDepthAllowed(y))continue;
            BlockPos floor=new BlockPos(x,y,z);BlockState ground=world.getBlockState(floor);
            if(!ground.isOf(Blocks.SAND)&&!ground.isOf(Blocks.GRAVEL)&&!ground.isIn(BlockTags.BASE_STONE_OVERWORLD))continue;
            // Validate local water and biome; no coral inside a structure or in a cold water pocket.
            if(world.getBiome(floor.up()).value().getTemperature()<.5f)continue;
            int type=random.nextInt(CORAL.length),height=1+random.nextInt(3);
            placeColumn(world,floor,type,height);
            // Low side lobes vary the footprint instead of identical vertical coral posts.
            for(int k=0;k<3;k++){
                int dx=random.nextBetween(-1,1),dz=random.nextBetween(-1,1);if(dx==0&&dz==0)continue;
                BlockPos p=floor.add(dx,1,dz);
                if(surrounded(world,p)&&world.getBlockState(p.down()).isSolidBlock(world,p.down()))
                    world.setBlockState(p,CORAL[type].getDefaultState(),Block.NOTIFY_LISTENERS);
            }
        }
    }
    private static void seaPlants(StructureWorldAccess world,Chunk chunk,long seed,short biome,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        int cx=chunk.getPos().x,cz=chunk.getPos().z;
        if(SurfaceAccentRules.unit(seed,cx,cz,0x5345414752415353L)>.14)return;
        Random random=Random.create(Double.doubleToLongBits(SurfaceAccentRules.unit(seed,cx,cz,0x4B454C5041544348L)));
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ(),ox=5+random.nextInt(6),oz=5+random.nextInt(6);
        for(int n=0;n<7;n++){
            int lx=ox+random.nextBetween(-3,3),lz=oz+random.nextBetween(-3,3),y=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,lx,lz);
            short localBiome=WorldBlueprintManager.generationVersion()>=11?data.biomeIds[sz+lz-tz][sx+lx-tx]:biome;
            if(WorldBlueprintManager.generationVersion()>=11&&(!BiomeIds.isOcean(localBiome)||localBiome==BiomeIds.FROZEN_OCEAN||localBiome==BiomeIds.DEEP_FROZEN_OCEAN))continue;
            if(!plantDepthAllowed(y))continue;
            BlockPos floor=new BlockPos(sx+lx,y,sz+lz),p=floor.up();var ground=world.getBlockState(floor);
            if(!ground.isOf(Blocks.SAND)&&!ground.isOf(Blocks.GRAVEL)&&!ground.isIn(BlockTags.DIRT))continue;
            if(!world.getBlockState(p).isOf(Blocks.WATER)||!world.getBlockState(p.up()).isOf(Blocks.WATER))continue;
            var grass=Blocks.SEAGRASS.getDefaultState();
            if(localBiome!=BiomeIds.WARM_OCEAN&&random.nextFloat()<.2f){
                var kelp=Blocks.KELP.getDefaultState().with(net.minecraft.state.property.Properties.AGE_25,25);
                if(!kelp.canPlaceAt(world,p))continue;
                int height=1+random.nextInt(4);
                for(int dy=0;dy<height;dy++){
                    BlockPos q=p.up(dy);if(!world.getBlockState(q).isOf(Blocks.WATER))break;
                    boolean top=dy==height-1||!world.getBlockState(q.up()).isOf(Blocks.WATER);
                    world.setBlockState(q,top?kelp:Blocks.KELP_PLANT.getDefaultState(),Block.NOTIFY_LISTENERS);
                    if(top)break;
                }
            }else if(grass.canPlaceAt(world,p))world.setBlockState(p,grass,Block.NOTIFY_LISTENERS);
        }
    }
    static void placeColumn(StructureWorldAccess world,BlockPos floor,int type,int height){
            for(int dy=1;dy<=height;dy++){
                BlockPos p=floor.up(dy);if(!surrounded(world,p))break;
                world.setBlockState(p,CORAL[type].getDefaultState(),Block.NOTIFY_LISTENERS);
                if(dy==height){
                    var fan=FANS[type].getDefaultState();BlockPos above=p.up();
                    if(world.getBlockState(above).isOf(Blocks.WATER)&&fan.canPlaceAt(world,above))world.setBlockState(above,fan,Block.NOTIFY_LISTENERS);
                }
            }
    }
    private static boolean surrounded(StructureWorldAccess world,BlockPos p){
        if(!world.getBlockState(p).isOf(Blocks.WATER))return false;
        return world.getBlockState(p.north()).isOf(Blocks.WATER)||world.getBlockState(p.south()).isOf(Blocks.WATER)||world.getBlockState(p.east()).isOf(Blocks.WATER)||world.getBlockState(p.west()).isOf(Blocks.WATER);
    }
    /** Preserve the former vanilla-ocean depth bands after moving sea level. */
    static boolean reefDepthAllowed(int floorY){
        int depth=HeightConverter.seaLevel()-floorY;
        return depth>=5&&depth<=34;
    }
    static boolean plantDepthAllowed(int floorY){
        int depth=HeightConverter.seaLevel()-floorY;
        return depth>=4&&depth<=43;
    }
}
