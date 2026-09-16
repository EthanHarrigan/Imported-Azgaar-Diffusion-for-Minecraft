package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;

/** Small post-feature accents. Every footprint is preflighted and stays in its owning chunk. */
public final class LocalLandscapeDecorator {
    private LocalLandscapeDecorator(){}
    public static void decorate(StructureWorldAccess world,Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();long seed=LocalTerrainProvider.getSeed();
        Random random=Random.create(Double.doubleToLongBits(SurfaceAccentRules.unit(seed,chunk.getPos().x,chunk.getPos().z,0x4C414E4453434150L)));
        for(int attempt=0;attempt<2;attempt++){
            int x=sx+5+random.nextInt(6),z=sz+5+random.nextInt(6),r=z-tz,c=x-tx;
            short biome=data.biomeIds[r][c];if(BiomeIds.isOcean(biome))continue;
            if(data.waterSurface!=null&&data.waterSurface[r][c]!=Short.MIN_VALUE)continue;
            int y=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,x-sx,z-sz);
            BlockPos floor=new BlockPos(x,y,z);BlockState ground=world.getBlockState(floor);
            if(y+3>=world.getTopYInclusive()||!world.getBlockState(floor.up()).isAir())continue;
            boolean soil=ground.isIn(BlockTags.DIRT),stone=ground.isIn(BlockTags.BASE_STONE_OVERWORLD)||ground.isOf(Blocks.GRAVEL);
            if(woodland(biome)&&soil&&random.nextFloat()<.022f){
                fallenLog(world,chunk,floor,biome,random);continue;
            }
            if(data.heightmap[r][c]>1400&&stone&&random.nextFloat()<.10f){
                rocks(world,chunk,floor,biome,seed,random);continue;
            }
            if(soil&&random.nextFloat()<.20f&&freshBank(data,r,c,WorldScaleManager.getCurrentScale())){
                // Fewer plants in colder/drier banks; avoid importing tropical plants into snow.
                Block plant=switch(biome){
                    case BiomeIds.SWAMP,BiomeIds.MANGROVE_SWAMP -> Blocks.FIREFLY_BUSH;
                    case BiomeIds.TAIGA,BiomeIds.TAIGA_SPARSE,BiomeIds.FOREST,BiomeIds.FOREST_SPARSE -> Blocks.FERN;
                    case BiomeIds.PLAINS,BiomeIds.MEADOW,BiomeIds.BIRCH_FOREST,BiomeIds.SAVANNA -> Blocks.SHORT_GRASS;
                    default -> null;
                };
                if(plant!=null)for(int n=0;n<5;n++){
                    int px=x+random.nextBetween(-2,2),pz=z+random.nextBetween(-2,2);
                    int yy=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,px-sx,pz-sz);BlockPos p=new BlockPos(px,yy+1,pz);
                    var state=plant.getDefaultState();
                    if(Math.abs(yy-y)<=2&&world.getBlockState(p).isAir()&&world.getBlockState(p.down()).isIn(BlockTags.DIRT)&&state.canPlaceAt(world,p))
                        world.setBlockState(p,state,Block.NOTIFY_LISTENERS);
                }
            }
        }
        Heightmap.populateHeightmaps(chunk,java.util.Set.of(Heightmap.Type.WORLD_SURFACE_WG,Heightmap.Type.OCEAN_FLOOR_WG));
    }
    private static boolean freshBank(LocalTerrainProvider.HeightmapData d,int r,int c,int scale){
        if(d.waterSurface==null)return false;
        for(int dz=-6;dz<=6;dz+=3)for(int dx=-6;dx<=6;dx+=3){
            int rr=r+dz,cc=c+dx;if(rr<0||cc<0||rr>=d.height||cc>=d.width)continue;
            short water=d.waterSurface[rr][cc];
            if(water!=Short.MIN_VALUE&&water>0&&Math.abs(d.heightmap[r][c]-water)<=90f/scale)return true;
        }
        return false;
    }
    private static boolean woodland(short b){return b==BiomeIds.FOREST||b==BiomeIds.FOREST_SPARSE||b==BiomeIds.BIRCH_FOREST||b==BiomeIds.TAIGA||b==BiomeIds.OLD_GROWTH_PINE_TAIGA||b==BiomeIds.OLD_GROWTH_SPRUCE_TAIGA;}
    private static void fallenLog(StructureWorldAccess world,Chunk chunk,BlockPos floor,short biome,Random random){
        boolean alongX=random.nextBoolean();int length=3+random.nextInt(3);
        Block wood=biome==BiomeIds.BIRCH_FOREST?Blocks.BIRCH_LOG:
                biome==BiomeIds.TAIGA||biome==BiomeIds.OLD_GROWTH_PINE_TAIGA||biome==BiomeIds.OLD_GROWTH_SPRUCE_TAIGA?Blocks.SPRUCE_LOG:Blocks.OAK_LOG;
        for(int i=0;i<length;i++){
            BlockPos p=floor.add(alongX?i-2:0,1,alongX?0:i-2);
            if(!world.getBlockState(p).isAir()||!world.getBlockState(p.down()).isIn(BlockTags.DIRT))return;
        }
        for(int i=0;i<length;i++)world.setBlockState(floor.add(alongX?i-2:0,1,alongX?0:i-2),
                wood.getDefaultState().with(PillarBlock.AXIS,alongX?Direction.Axis.X:Direction.Axis.Z),Block.NOTIFY_LISTENERS);
    }
    private static void rocks(StructureWorldAccess world,Chunk chunk,BlockPos floor,short biome,long seed,Random random){
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        BlockPos[] places=new BlockPos[2+random.nextInt(2)];
        int signX=random.nextBoolean()?1:-1,signZ=random.nextBoolean()?1:-1;
        boolean swap=random.nextBoolean();
        for(int i=0;i<places.length;i++){
            int dx=i==1?signX:0,dz=i==2?signZ:0;
            int x=floor.getX()+(swap?dz:dx),z=floor.getZ()+(swap?dx:dz);
            int y=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,x-sx,z-sz);BlockPos p=new BlockPos(x,y+1,z);
            var ground=world.getBlockState(p.down());
            if(Math.abs(y-floor.getY())>1||!world.getBlockState(p).isAir()||!(ground.isIn(BlockTags.BASE_STONE_OVERWORLD)||ground.isOf(Blocks.GRAVEL)))return;
            places[i]=p;
        }
        for(var p:places)world.setBlockState(p,SurfaceGeology.rock(p.getX(),p.getY(),p.getZ(),biome,seed).getDefaultState(),Block.NOTIFY_LISTENERS);
    }
}
