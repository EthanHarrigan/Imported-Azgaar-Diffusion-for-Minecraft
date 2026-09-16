package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

/** V12 surface-only arid geology, supported sediment and terrain-aware dry transitions. */
public final class DesertSurface {
    private DesertSurface(){}
    public static Block rock(double nx,int y,double nz,DesertTerrain.Field f,long seed){
        // The same warm clay geology wraps peaks and hollows. There is no global
        // dark-bottom/light-top ramp: erosion exposes the three-dimensional beds.
        Block bed=DesertProvinces.strata(nx,y,nz,seed);
        return bed==Blocks.WHITE_TERRACOTTA?Blocks.LIGHT_GRAY_TERRACOTTA:bed;
    }

    public static Block sediment(double nx,double nz,DesertTerrain.Field f,double slope,long seed,boolean powders){
        if(SurfaceTransitions.enabled())return blendedSediment(nx,nz,f,slope,seed,powders);
        double patch=DesertTerrain.unit(nx,nz,seed^0x534544494D454EL,23);
        if(slope>.42||f.sand()<.2&&patch>.72)return rock(nx,0,nz,f,seed);
        if(slope>.22&&patch>.6)return Blocks.GRAVEL;
        if(f.scrub()>.35&&patch<.3)return patch<.1?Blocks.PACKED_MUD:Blocks.COARSE_DIRT;
        if(powders&&patch>.83&&patch<.93){
            if(f.red()>.70)return Blocks.ORANGE_CONCRETE_POWDER;
            if(f.red()>.55)return Blocks.BROWN_CONCRETE_POWDER;
            if(f.red()<.20)return Blocks.WHITE_CONCRETE_POWDER;
            if(f.red()<.35)return Blocks.LIGHT_GRAY_CONCRETE_POWDER;
            return Blocks.YELLOW_CONCRETE_POWDER;
        }
        return f.red()>.58?Blocks.RED_SAND:Blocks.SAND;
    }
    private static Block blendedSediment(double nx,double nz,DesertTerrain.Field f,double slope,long seed,boolean powders){
        // One low-frequency province boundary. Ordinary deserts remain sand-only too.
        return f.red()>.58?Blocks.RED_SAND:Blocks.SAND;
    }
    public static boolean dryCompatible(short id){
        return switch(id){
            case BiomeIds.DESERT,BiomeIds.BEACH,BiomeIds.PLAINS,BiomeIds.SUNFLOWER_PLAINS,
                 BiomeIds.SAVANNA,BiomeIds.SAVANNA_PLATEAU,BiomeIds.WINDSWEPT_SAVANNA,
                 BiomeIds.BADLANDS,BiomeIds.ERODED_BADLANDS,BiomeIds.WOODED_BADLANDS,
                 BiomeIds.FOREST,BiomeIds.FOREST_SPARSE,BiomeIds.BIRCH_FOREST,
                 BiomeIds.STONY_PEAKS,BiomeIds.WINDSWEPT_HILLS -> true;
            default -> false;
        };
    }
    public static void decorate(Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        if(data.contexts!=null)return;
        int scale=WorldScaleManager.getCurrentScale(),sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        long seed=LocalTerrainProvider.getSeed();var pos=new BlockPos.Mutable();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int wx=sx+x,wz=sz+z,r=wz-tz,c=wx-tx;short id=data.biomeIds[r][c];
            if(!dryCompatible(id)||data.waterSurface!=null&&data.waterSurface[r][c]!=Short.MIN_VALUE)continue;
            var f=DesertTerrain.at(wx/(double)scale,wz/(double)scale,seed);if(f.dry()<.025)continue;
            double nx=wx/(double)scale,nz=wz/(double)scale;
            double patch=DesertTerrain.unit(nx,nz,seed^0x45434F544F4E4553L,33);
            if(SurfaceTransitions.enabled()? !DesertTerrain.aridRockAt(f.dry(),nx,nz,seed):patch>DesertTerrain.smooth(f.dry()))continue;
            int y=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x,z);
            if(y<=chunk.getBottomY()+3||y>=chunk.getTopYInclusive())continue;
            // Skip structures, existing features and wet banks instead of recolouring their roofs.
            if(Math.abs(y-HeightConverter.convertToMinecraftHeight(data.heightmap[r][c]))>3)continue;
            pos.set(wx,y,wz);if(!natural(chunk.getBlockState(pos))||!chunk.getBlockState(pos.up()).isAir())continue;
            double dx=data.heightmap[r][Math.min(c+1,data.width-1)]-data.heightmap[r][Math.max(0,c-1)];
            double dz=data.heightmap[Math.min(r+1,data.height-1)][c]-data.heightmap[Math.max(0,r-1)][c];
            double slope=Math.hypot(dx,dz)/(60.0/scale);
            boolean dryBuffer=f.waterProtection()>.995&&y>HeightConverter.seaLevel()+5;
            if(dryBuffer&&data.waterSurface!=null){
                for(int dz0=-3;dz0<=3&&dryBuffer;dz0++)for(int dx0=-3;dx0<=3;dx0++){
                    int rr=r+dz0,cc=c+dx0;
                    if(rr<0||cc<0||rr>=data.heightmap.length||cc>=data.width||data.waterSurface[rr][cc]!=Short.MIN_VALUE){dryBuffer=false;break;}
                }
            }
            Block material=slope>.4||MountainZones.mountainBiome(id)?rock(nx,y,nz,f,seed):sediment(nx,nz,f,slope,seed,dryBuffer);
            // At moist edges keep soil/grass pockets, not concrete powder or sand in the water.
            if(f.waterProtection()<.35)continue;
            if(SurfaceTransitions.enabled()){
                if(SurfaceTransitions.pick((1-f.dry())*(1-f.scrub()*.25),wx,wz,seed^0x534F494C45444745L))
                    material=SurfaceTransitions.pick(f.dry(),wx,wz,seed^0x524F4F544544L)?Blocks.COARSE_DIRT:Blocks.GRASS_BLOCK;
            }else if(f.dry()<.7&&patch>.12)material=patch>.35?Blocks.COARSE_DIRT:Blocks.ROOTED_DIRT;
            // All loose sediment is backed by solid material; no gravity-triggered collapse over caves.
            for(int d=1;d<=2;d++){
                pos.set(wx,y-d,wz);if(!natural(chunk.getBlockState(pos)))break;
                chunk.setBlockState(pos,(slope>.4?rock(nx,y-d,nz,f,seed):f.red()>.5?Blocks.RED_SANDSTONE:Blocks.SANDSTONE).getDefaultState());
            }
            pos.set(wx,y-1,wz);if(!chunk.getBlockState(pos).isSolidBlock(chunk,pos))continue;
            pos.set(wx,y,wz);chunk.setBlockState(pos,material.getDefaultState());
            // Only exposed face columns, bounded independently of the world's tall build limit.
            if(slope>.4){
                int lowest=HeightConverter.convertToMinecraftHeight((short)Math.min(Math.min(data.heightmap[r][Math.max(0,c-1)],data.heightmap[r][Math.min(data.width-1,c+1)]),Math.min(data.heightmap[Math.max(0,r-1)][c],data.heightmap[Math.min(data.height-1,r+1)][c])));
                for(int yy=y-1;yy>=Math.max(lowest,y-96);yy--){
                    pos.set(wx,yy,wz);if(natural(chunk.getBlockState(pos)))chunk.setBlockState(pos,rock(nx,yy,nz,f,seed).getDefaultState());
                }
            }
        }
    }
    private static boolean natural(BlockState s){return s.isIn(BlockTags.DIRT)||s.isIn(BlockTags.BASE_STONE_OVERWORLD)||s.isOf(Blocks.SAND)||s.isOf(Blocks.RED_SAND)||s.isOf(Blocks.SANDSTONE)||s.isOf(Blocks.RED_SANDSTONE)||s.isOf(Blocks.GRAVEL)||s.isOf(Blocks.TERRACOTTA);}
}
