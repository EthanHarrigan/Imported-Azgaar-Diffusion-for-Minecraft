package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;

/** Stable independent random streams: decoration changes cannot perturb terrain or structure seeds. */
public final class SurfaceAccentRules {
    private SurfaceAccentRules(){}
    public static double unit(long seed,int x,int z,long salt){
        long v=seed^salt^(long)x*0x9E3779B97F4A7C15L^(long)z*0xC2B2AE3D27D4EB4FL;
        v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;v=(v^(v>>>27))*0x94D049BB133111EBL;
        return ((v^(v>>>31))>>>11)*0x1.0p-53;
    }
    /** Continuous deterministic value field for irregular surface patches. */
    public static double correlatedUnit(long seed,double x,double z,long salt,double cellSize){
        if(!(cellSize>0)||!Double.isFinite(x)||!Double.isFinite(z))return unit(seed,0,0,salt);
        double gx=x/cellSize,gz=z/cellSize;
        long x0=(long)Math.floor(gx),z0=(long)Math.floor(gz);
        double tx=smooth(gx-x0),tz=smooth(gz-z0);
        double a=unit(seed,safeInt(x0),safeInt(z0),salt);
        double b=unit(seed,safeInt(x0+1),safeInt(z0),salt);
        double c=unit(seed,safeInt(x0),safeInt(z0+1),salt);
        double d=unit(seed,safeInt(x0+1),safeInt(z0+1),salt);
        return lerp(lerp(a,b,tx),lerp(c,d,tx),tz);
    }
    private static double smooth(double t){return t*t*(3-2*t);}
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    private static int safeInt(long value){return (int)Math.max(Integer.MIN_VALUE,Math.min(Integer.MAX_VALUE,value));}
    public static boolean deadWood(long seed,int cx,int cz,boolean beach){return unit(seed,cx,cz,0x44454144574F4FL)<(beach?1.0/300:1.0/180);}
    public static boolean monument(long seed,int cx,int cz,int scale){
        double region=WorldHydrology.edgeNoise(cx*16.0/scale,cz*16.0/scale,seed^0x4D4F4E554D454EL,1600);
        // Retain 12–38% of otherwise valid starts. Vanilla location/locate machinery remains intact.
        return unit(seed,cx,cz,0x4D4F4E554D534BL)<.25+.13*region;
    }
    public static boolean freshwater(float terrain,float water,double distanceBlocks,int scale){
        return Float.isFinite(water)&&water>5&&distanceBlocks<=8.0*scale&&Math.abs(terrain-water)<=90;
    }
}
