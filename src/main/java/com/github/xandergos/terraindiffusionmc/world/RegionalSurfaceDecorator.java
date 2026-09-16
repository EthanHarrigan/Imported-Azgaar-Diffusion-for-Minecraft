package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

/** Broad regional surface transitions before features, with narrow physical waterlines. */
public final class RegionalSurfaceDecorator {
    private RegionalSurfaceDecorator(){}
    public static void decorate(Chunk chunk,LocalTerrainProvider.HeightmapData data,int tx,int tz){
        if(data.contexts!=null){OwnedSurfaceDecorator.decorate(chunk,data,tx,tz);return;}
        int scale=WorldScaleManager.getCurrentScale(),sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        var pos=new BlockPos.Mutable();long seed=LocalTerrainProvider.getSeed();
        for(int z=0;z<16;z++)for(int x=0;x<16;x++){
            int wx=sx+x,wz=sz+z,r=wz-tz,c=wx-tx;short id=data.biomeIds[r][c];
            // Protect islands/rare identities, structures, vegetation, and the hydrology water seal.
            if(id==BiomeIds.MUSHROOM_FIELDS||id==BiomeIds.ICE_SPIKES||data.waterSurface!=null&&data.waterSurface[r][c]!=Short.MIN_VALUE)continue;
            int y=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG,x,z);
            if(y<=chunk.getBottomY()+3||y>=chunk.getTopYInclusive()||Math.abs(y-HeightConverter.convertToMinecraftHeight(data.heightmap[r][c]))>3)continue;
            pos.set(wx,y,wz);BlockState old=chunk.getBlockState(pos),above=chunk.getBlockState(pos.up());
            if(!natural(old)||(!above.isAir()&&above.getFluidState().isEmpty()))continue;
            double nx=wx/(double)scale,nz=wz/(double)scale;
            var f=DesertTerrain.at(nx,nz,seed);double sea=DesertTerrain.sandSea(nx,nz);if(sea>.95)continue;
            var map=WorldBlueprintManager.detail();var store=WorldBlueprintManager.activeStore();if(map==null||store==null)continue;
            int nw=store.manifest().width()*256,nh=store.manifest().height()*256;double u=nx/nw+.5,v=nz/nh+.5;
            if(u<=0||u>=1||v<=0||v>=1)continue;
            double distance=map.signedCoastDistance(u,v)*nw/map.width;
            double dx=data.heightmap[r][Math.min(data.width-1,c+1)]-data.heightmap[r][Math.max(0,c-1)];
            double dz=data.heightmap[Math.min(data.height-1,r+1)][c]-data.heightmap[Math.max(0,r-1)][c];
            double slope=Math.hypot(dx,dz)/(60.0/scale),grain=SurfaceTransitions.grain(wx,wz,seed^0x524547535552464CL);
            // Wind exposure and slope vary deposit density, without moving the coastline or water.
            double deposit=(1-SurfaceTransitions.smooth((slope-.10)/.55))*(.75+.25*DesertTerrain.noise(nx,nz,seed,170));
            double shore=(1-SurfaceTransitions.smooth(Math.max(0,distance)/(150*SurfaceTransitions.mapFactor())))*deposit;
            if(data.heightmap[r][c]>450)shore*=1-SurfaceTransitions.smooth((data.heightmap[r][c]-450)/400);
            Block replacement=null;
            if(y<HeightConverter.seaLevel()){
                double sand=SurfaceTransitions.smooth((distance+500)/500)*deposit;
                replacement=switch(SurfaceTransitions.choose(grain,sand,1-sand,.25)){case 0->Blocks.SAND;case 1->Blocks.GRAVEL;default->Blocks.STONE;};
            }else if(!MountainZones.mountainBiome(id)&&slope<.65){
                double wood=RegionalEcotones.weight(RegionalEcotones.WOOD,wx,wz),wet=RegionalEcotones.weight(RegionalEcotones.WET,wx,wz);
                double dry=f.dry(),edge=4*dry*(1-dry);
                if(shore>0&&grain<shore)replacement=Blocks.SAND;
                else if(edge>.01&&SurfaceTransitions.pick(edge*(1-shore)*(1-sea),wx,wz,seed^0x45444745534F494CL)){
                    replacement=switch(SurfaceTransitions.choose(grain,(1-dry)*(.5+wood),dry*.6,(1-dry)*.2,wet*.4)){
                        case 1->DesertSurface.sediment(nx,nz,f,slope,seed,false);case 2->Blocks.COARSE_DIRT;case 3->Blocks.MUD;default->Blocks.GRASS_BLOCK;};
                }else if(wet>.02&&wet<.98&&SurfaceTransitions.pick(4*wet*(1-wet)*.5,wx,wz,seed^0x57455445444745L)){
                    replacement=grain<wet?Blocks.MUD:Blocks.GRASS_BLOCK;
                }
            }
            if(replacement!=null){pos.set(wx,y-1,wz);if(!chunk.getBlockState(pos).isSolidBlock(chunk,pos))continue;
                pos.set(wx,y,wz);chunk.setBlockState(pos,replacement.getDefaultState());}
        }
    }
    private static boolean natural(BlockState s){return s.isIn(BlockTags.DIRT)||s.isIn(BlockTags.BASE_STONE_OVERWORLD)||s.isIn(BlockTags.SAND)||s.isOf(Blocks.GRAVEL)||s.isOf(Blocks.SANDSTONE)||s.isOf(Blocks.RED_SANDSTONE)||s.isOf(Blocks.CLAY);}
}
