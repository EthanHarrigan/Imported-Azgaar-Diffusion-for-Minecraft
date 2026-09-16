package com.github.xandergos.terraindiffusionmc.hydrology;

/** Small local bank variation; never changes river routing or water levels. */
public final class RiverEdgeShape {
    private RiverEdgeShape(){}
    public static double radius(double radius,int scale,double medium,double fine){
        return Math.max(2.0*scale,radius+medium*Math.min(radius*.16,1.1*scale)+fine*Math.min(radius*.06,.45*scale));
    }
    public static float depth(float centerDepth,double distance,double radius,double grain){
        double t=Math.max(0,Math.min(1,(distance/radius-.55)/.45));t=t*t*(3-2*t);
        return (float)(centerDepth*(1-t)+(12+grain*2)*t);
    }
    public static double bankWidth(double base,double medium){return base*(1+medium*.25);}
}
