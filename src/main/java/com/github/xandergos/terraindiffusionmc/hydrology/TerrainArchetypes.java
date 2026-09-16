package com.github.xandergos.terraindiffusionmc.hydrology;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;

/** Cheap, continuous macro masks which add characteristic relief without replacing diffusion detail. */
public final class TerrainArchetypes {
    public enum Kind { NONE, DUNES, BADLANDS, SAVANNA_PLATEAU, ROLLING_GRASSLAND, WETLAND }
    public record Sample(Kind kind,double strength) {}
    private TerrainArchetypes() {}

    public static Sample at(double x,double z,float elevation,long seed){
        BlueprintTileStore store=WorldBlueprintManager.activeStore();BlueprintDetailMap detail=WorldBlueprintManager.detail();
        if(store==null||detail==null||elevation<=8)return new Sample(Kind.NONE,0);
        var m=store.manifest();int nw=m.width()*256,nh=m.height()*256;
        double u=x/nw+.5,v=z/nh+.5;if(u<=0||u>=1||v<=0||v>=1)return new Sample(Kind.NONE,0);
        int anchor=Math.round(detail.nearest(1,u,v));
        int id=anchor>=1000?anchor-1000:-1;
        double region=(WorldHydrology.edgeNoise(x,z,seed^0x4152434845545950L,1050)+1)*.5;
        Kind kind;
        if(id==BiomeIds.BADLANDS||id==BiomeIds.ERODED_BADLANDS||id==BiomeIds.WOODED_BADLANDS)kind=Kind.BADLANDS;
        else if(id==BiomeIds.SAVANNA_PLATEAU||anchor==3)kind=Kind.SAVANNA_PLATEAU;
        else if(anchor==1||anchor==2||id==BiomeIds.DESERT)kind=Kind.DUNES;
        else if(anchor==12||id==BiomeIds.SWAMP||id==BiomeIds.MANGROVE_SWAMP)kind=Kind.WETLAND;
        else if(anchor==4||id==BiomeIds.PLAINS||id==BiomeIds.SUNFLOWER_PLAINS)kind=Kind.ROLLING_GRASSLAND;
        else kind=Kind.NONE;
        double gate=smooth((region-.18)/.50);
        if(kind==Kind.DUNES)gate*=1-smooth((elevation-650)/500.0);
        if(kind==Kind.WETLAND)gate*=1-smooth((elevation-280)/300.0);
        if(kind==Kind.SAVANNA_PLATEAU)gate*=smooth((elevation-150)/200.0);
        return new Sample(kind,gate);
    }

    public static float shape(float elevation,double x,double z,long seed){
        boolean desertV12=WorldBlueprintManager.generationVersion()>=12;
        if(desertV12)elevation=DesertTerrain.shape(elevation,x,z,seed);
        var store=WorldBlueprintManager.activeStore();var map=WorldBlueprintManager.detail();
        if(store==null||map==null||elevation<=8)return elevation;
        double nw=store.manifest().width()*256.0,nh=store.manifest().height()*256.0;
        double u=x/nw+.5,v=z/nh+.5;
        if(u<=0||u>=1||v<=0||v>=1)return elevation;
        double gx=u*map.width-.5,gz=v*map.height-.5;
        int ix=(int)Math.floor(gx),iz=(int)Math.floor(gz);
        double fx=smooth(gx-ix),fz=smooth(gz-iz),delta=0;
        // Blend categorical contributions, never interpolate numeric biome IDs.
        for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++){
            double cx=((ix+dx+.5)/map.width-.5)*nw,cz=((iz+dz+.5)/map.height-.5)*nh;
            Sample s=at(cx,cz,elevation,seed);
            if(desertV12&&s.kind()==Kind.DUNES)continue;
            delta+=offset(s,x,z,seed)*(dx==0?1-fx:fx)*(dz==0?1-fz:fz);
        }
        double edge=Math.min(Math.min(u,1-u)*nw,Math.min(v,1-v)*nh);
        if(WorldBlueprintManager.generationVersion()>=13){
            double feather=com.github.xandergos.terraindiffusionmc.world.SurfaceTransitions.mapFactor();
            double slope=Math.hypot(map.sample(0,u+32/nw,v)-map.sample(0,u-32/nw,v),map.sample(0,u,v+32/nh)-map.sample(0,u,v-32/nh))/(64*30);
            double gate=(1-smooth(slope/.25))*(1-smooth(map.sample(2,u,v)/.15))
                    *(1-smooth((map.signedLakeDistance(u,v)*nw/map.width+100)/120))*smooth(map.signedCoastDistance(u,v)*nw/map.width/100);
            double rolling=.65*WorldHydrology.edgeNoise(x,z,seed^0x524F4C4C3133L,220*feather)+.35*WorldHydrology.edgeNoise(x,z,seed^0x524F4C4C3134L,90*feather);
            delta+=gate*rolling*120*(1-map.sandSeaWeight(u,v,(int)nw));
        }
        return (float)(elevation+delta*smooth((elevation-8)/80.0)*smooth(edge/64));
    }
    private static double offset(Sample s,double x,double z,long seed){
        double a=s.strength;return switch(s.kind){
            case DUNES -> (Math.sin((x*.91+z*.42)/18)+.45*WorldHydrology.edgeNoise(x,z,seed^0x44554E4553L,42))*18*a;
            case BADLANDS -> (WorldHydrology.edgeNoise(x,z,seed^0x4D455341L,180)*38-Math.abs(WorldHydrology.edgeNoise(x,z,seed^0x52494C4CL,46))*18)*a;
            case SAVANNA_PLATEAU -> (WorldHydrology.edgeNoise(x,z,seed^0x504C4154454155L,240)*26+10)*a;
            case ROLLING_GRASSLAND -> WorldHydrology.edgeNoise(x,z,seed^0x47524153534C414EL,155)*16*a;
            case WETLAND -> WorldHydrology.edgeNoise(x,z,seed^0x5745544C414E44L,36)*4*a;
            default -> 0;
        };
    }
    private static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
}
