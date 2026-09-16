package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

/** Cheap surface-only geology and snow exposure pass for blueprint v4 worlds. */
public final class TerrainDiffusionSurfaceDecorator {
    private TerrainDiffusionSurfaceDecorator(){}
    public static void decorate(Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        if(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=6){
            decorateMountainZones(chunk,data,tx,tz,false);return;
        }
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        BlockPos.Mutable pos=new BlockPos.Mutable();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int r=sz+z-tz,c=sx+x-tx,id=data.biomeIds[r][c];
            float dx=data.heightmap[r][Math.min(data.width-1,c+1)]-data.heightmap[r][Math.max(0,c-1)];
            float dz=data.heightmap[Math.min(data.height-1,r+1)][c]-data.heightmap[Math.max(0,r-1)][c];
            float slope=(float)Math.hypot(dx,dz)/Math.max(1,60f/WorldScaleManager.getCurrentScale());
            boolean snowy=id==BiomeIds.SNOWY_SLOPES||id==BiomeIds.FROZEN_PEAKS||id==BiomeIds.JAGGED_PEAKS||id==BiomeIds.ICE_SPIKES;
            boolean windswept=id==BiomeIds.WINDSWEPT_HILLS||id==BiomeIds.WINDSWEPT_FOREST||id==BiomeIds.WINDSWEPT_SAVANNA;
            int wx=sx+x,wz=sz+z,top=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x,z);
            if(top<chunk.getBottomY()||top>chunk.getTopYInclusive())continue;
            double exposure=WorldHydrology.edgeNoise(wx,wz,LocalTerrainProvider.getSeed()^0x534E4F574558504FL,95);
            if(snowy&&(slope>.34f||slope>.22f&&exposure>.24)){
                Block rock=rock(wx,wz);
                replaceNatural(chunk,pos,wx,wz,top,rock,3);
            }else if(windswept&&slope<.30f){
                // Windswept is an ecology/shape label, not permission to pave flat ground.
                replaceNatural(chunk,pos,wx,wz,top,Blocks.GRASS_BLOCK,1);
                replaceNatural(chunk,pos,wx,wz,top-1,Blocks.DIRT,2);
            }
        }
        Heightmap.populateHeightmaps(chunk,java.util.Set.of(Heightmap.Type.WORLD_SURFACE_WG,Heightmap.Type.OCEAN_FLOOR_WG));
    }
    /** The final pass removes the uniform snow film that vanilla freeze features can restore.
     * Trees, structures, fluids and non-natural blocks are left alone. */
    public static void decorateMountainZones(Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz,boolean afterFeatures){
        // Feature placement can use different heightmaps; read the actual final snow surface.
        if(afterFeatures)Heightmap.populateHeightmaps(chunk,java.util.Set.of(Heightmap.Type.WORLD_SURFACE_WG));
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ(),scale=WorldScaleManager.getCurrentScale();
        long seed=LocalTerrainProvider.getSeed();BlockPos.Mutable pos=new BlockPos.Mutable();
        boolean v18=com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=10;
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int r=sz+z-tz,c=sx+x-tx;short id=data.biomeIds[r][c];float elevation=data.heightmap[r][c];
            if(data.waterSurface!=null&&data.waterSurface[r][c]!=Short.MIN_VALUE)continue;
            int wx=sx+x,wz=sz+z,y=chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG,x,z);
            if(y<chunk.getBottomY()||y>=chunk.getTopYInclusive())continue;
            boolean mountain=elevation>=1800&&MountainZones.mountainBiome(id);
            if(!mountain){
                if(!afterFeatures){
                    if(data.contexts==null)EcotoneDecorator.surface(chunk,data,tx,tz,wx,wz,y,id);
                    // Preserve Blueprint.12's correction of flat windswept stone carpets.
                    if(id==BiomeIds.WINDSWEPT_HILLS||id==BiomeIds.WINDSWEPT_FOREST||id==BiomeIds.WINDSWEPT_SAVANNA){
                        float dx0=data.heightmap[r][Math.min(data.width-1,c+1)]-data.heightmap[r][Math.max(0,c-1)];
                        float dz0=data.heightmap[Math.min(data.height-1,r+1)][c]-data.heightmap[Math.max(0,r-1)][c];
                        if(Math.hypot(dx0,dz0)/(60f/scale)<.30){
                            replaceNatural(chunk,pos,wx,wz,y,Blocks.GRASS_BLOCK,1);
                            replaceNatural(chunk,pos,wx,wz,y-1,Blocks.DIRT,2);
                        }
                    }
                }
                continue;
            }
            float dx=data.heightmap[r][Math.min(data.width-1,c+1)]-data.heightmap[r][Math.max(0,c-1)];
            float dz=data.heightmap[Math.min(data.height-1,r+1)][c]-data.heightmap[Math.max(0,r-1)][c];
            float slope=(float)Math.hypot(dx,dz)/(60f/scale);
            double nx=wx/(double)scale,nz=wz/(double)scale;
            double exposure=WorldHydrology.edgeNoise(nx,nz,seed^0x534E4F5750415443L,45);
            boolean snow=id==BiomeIds.SNOWY_SLOPES||id==BiomeIds.FROZEN_PEAKS||id==BiomeIds.JAGGED_PEAKS;
            boolean newFoothills=com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=9;
            boolean steepFace=steepSnowFace(data,r,c,scale);
            boolean meadow=!steepFace&&(id==BiomeIds.MEADOW||id==BiomeIds.GROVE)&&slope<(newFoothills?.44:.32);
            boolean exceptionalFace=v18&&elevation>4800&&slope>.40;
            boolean exposed=!meadow&&(!snow||exceptionalFace||slope>.65||exposure>(elevation>5000?.62:.24));
            if(SurfaceTransitions.enabled()&&snow&&!meadow){
                double snowCover=(1-SurfaceTransitions.smooth((slope-.25)/.4))*(.55+.35*SurfaceTransitions.smooth((elevation-1800)/4000));
                snowCover*=.8-.2*exposure;
                exposed=exceptionalFace||!SurfaceTransitions.pick(snowCover,wx,wz,seed^0x534E4F5745444745L);
            }
            // Cliff exposure is mandatory, including where opposite gradients cancel on a ridge.
            exposed|=steepFace;
            pos.set(wx,y,wz);
            boolean layer=chunk.getBlockState(pos).isOf(Blocks.SNOW);
            if(layer){
                // Do not remove snow resting on trees or non-terrain feature blocks.
                if(!natural(chunk.getBlockState(pos.down())))continue;
                if(exposed||meadow&&id==BiomeIds.MEADOW)chunk.setBlockState(pos,Blocks.AIR.getDefaultState());
                y--;pos.set(wx,y,wz);
            }
            if(!natural(chunk.getBlockState(pos)))continue;
            if(meadow){
                double grain=WorldHydrology.edgeNoise(nx,nz,seed^0x464F4F5447524149L,7);
                Block soil=newFoothills&&slope>.23&&grain>.38?Blocks.COARSE_DIRT:Blocks.GRASS_BLOCK;
                if(newFoothills&&slope>.34&&grain<-.38)soil=Blocks.GRAVEL;
                replaceNatural(chunk,pos,wx,wz,y,soil,1);
                replaceNatural(chunk,pos,wx,wz,y-1,Blocks.DIRT,2);
            }else if(exposed){
                Block material=v18?SurfaceGeology.rock(wx,y,wz,id,seed):rock((int)nx,(int)nz);
                double scree=WorldHydrology.edgeNoise(nx,nz,seed^0x54414C5553L,65);
                if(!steepFace&&slope>.18&&slope<.5&&scree>.28)material=Blocks.GRAVEL;
                replaceNatural(chunk,pos,wx,wz,y,material,3);
                if(v18&&!afterFeatures&&slope>.4f){
                    int lowest=HeightConverter.convertToMinecraftHeight((short)Math.min(
                            Math.min(data.heightmap[r][Math.max(0,c-1)],data.heightmap[r][Math.min(data.width-1,c+1)]),
                            Math.min(data.heightmap[Math.max(0,r-1)][c],data.heightmap[Math.min(data.height-1,r+1)][c])))-1;
                    // Exposed column sides only. Bound work and stay above neighbouring ground;
                    // this avoids scanning caves or the entire tall-world column.
                    int bottom=Math.max(chunk.getBottomY(),Math.max(lowest,y-128));
                    for(int faceY=y-3;faceY>bottom;faceY--){
                        pos.set(wx,faceY,wz);
                        if(natural(chunk.getBlockState(pos)))chunk.setBlockState(pos,
                                SurfaceGeology.rock(wx,faceY,wz,id,seed).getDefaultState());
                    }
                }
            }else if(snow){
                // Sheltered ice patches are visual glacial remnants, not simulated ice flow.
                double hollow=LocalTerrainProvider.regionalConcavity(wx,wz);
                Block material=id==BiomeIds.FROZEN_PEAKS&&elevation>4000&&slope<.28&&hollow>25&&exposure<-.28
                        ?Blocks.PACKED_ICE:Blocks.SNOW_BLOCK;
                replaceNatural(chunk,pos,wx,wz,y,material,1);
            }
        }
        Heightmap.populateHeightmaps(chunk,java.util.Set.of(Heightmap.Type.WORLD_SURFACE_WG,Heightmap.Type.OCEAN_FLOOR_WG));
    }
    /** Fixed 16 cached samples: detect cliff lips/ridges without loading adjacent chunks or tiles.
     * Use converted block heights so the rule follows rendered geometry at every world scale.
     * Ordinary one-block snow stairs remain eligible for snow. */
    static boolean steepSnowFace(LocalTerrainProvider.HeightmapData data,int r,int c,int scale){
        int center=HeightConverter.convertToMinecraftHeight(data.heightmap[r][c],scale);
        for(int distance=1;distance<=2;distance++){
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                if(dx==0&&dz==0)continue;
                int rr=r+dz*distance,cc=c+dx*distance;
                if(rr<0||rr>=data.height||cc<0||cc>=data.width)continue;
                int neighbor=HeightConverter.convertToMinecraftHeight(data.heightmap[rr][cc],scale);
                if(Math.abs(center-neighbor)>1.25*Math.hypot(dx*distance,dz*distance))return true;
            }
        }
        return false;
    }
    private static Block rock(int x,int z){
        double family=WorldHydrology.edgeNoise(x,z,LocalTerrainProvider.getSeed()^0x47454F4C4F47594CL,2100);
        double patch=WorldHydrology.edgeNoise(x,z,LocalTerrainProvider.getSeed()^0x4558504F53555245L,85);
        if(family>.66&&patch>.05)return Blocks.GRANITE;
        if(family<-.70&&patch>.18)return Blocks.DIORITE;
        if(family>.16&&patch>-.25)return Blocks.ANDESITE;
        if(family<-.35&&patch>.58)return Blocks.CALCITE;
        return Blocks.STONE;
    }
    private static void replaceNatural(Chunk chunk,BlockPos.Mutable pos,int x,int z,int top,Block replacement,int depth){
        for(int d=0;d<depth;d++){
            int y=top-d;if(y<chunk.getBottomY())return;pos.set(x,y,z);BlockState old=chunk.getBlockState(pos);
            if(!natural(old))return;chunk.setBlockState(pos,replacement.getDefaultState());
        }
    }
    private static boolean natural(BlockState s){
        return s.isOf(Blocks.STONE)||s.isIn(BlockTags.BASE_STONE_OVERWORLD)||s.isIn(BlockTags.DIRT)||s.isIn(BlockTags.TERRACOTTA)||
                s.isOf(Blocks.SANDSTONE)||s.isOf(Blocks.RED_SANDSTONE)||
                s.isOf(Blocks.SNOW_BLOCK)||s.isOf(Blocks.GRAVEL)||s.isOf(Blocks.SAND)||s.isOf(Blocks.TERRACOTTA)||s.isOf(Blocks.CALCITE);
    }
}
