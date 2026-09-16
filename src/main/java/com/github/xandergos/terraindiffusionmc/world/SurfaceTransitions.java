package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;

/** Shared, absolute-coordinate material probabilities. No model calls or chunk neighbours. */
public final class SurfaceTransitions {
    private SurfaceTransitions(){}
    public static boolean enabled(){return WorldBlueprintManager.generationVersion()>=13;}
    public static double mapFactor(){var s=WorldBlueprintManager.activeStore();return s==null?1:Math.sqrt(Math.max(.25,s.manifest().physicalWidthKm()/750));}
    public static double nativeWidth(){return 320*mapFactor();}
    public static double smooth(double t){return DesertTerrain.smooth(t);}
    /** Deterministic irregular surface variation without fixed block-grid tiling. */
    public static double grain(double x,double z,long seed){
        return SurfaceAccentRules.correlatedUnit(seed,x,z,0x475241494E33L,12);
    }
    public static int choose(double random,double... weights){
        double sum=0;for(double w:weights)if(Double.isFinite(w)&&w>0)sum+=w;
        if(sum<=0)return 0;
        double target=Math.max(0,Math.min(Math.nextDown(1.0),random))*sum;
        int last=0;for(int i=0;i<weights.length;i++)if(Double.isFinite(weights[i])&&weights[i]>0){last=i;target-=weights[i];if(target<0)return i;}return last;
    }
    public static boolean pick(double probability,double x,double z,long seed){return grain(x,z,seed)<Math.max(0,Math.min(1,probability));}
}
