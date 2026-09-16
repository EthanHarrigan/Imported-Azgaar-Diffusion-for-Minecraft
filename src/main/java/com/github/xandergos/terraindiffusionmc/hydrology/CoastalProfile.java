package com.github.xandergos.terraindiffusionmc.hydrology;

/** Continuous coast-normal shoulder, not an ocean-only height cut with a high rim. */
public final class CoastalProfile {
    private CoastalProfile(){}
    public static float height(float model,float source,double signedNativeDistance,double cliffField,double detailField){
        double cliff=smooth((cliffField-.12)/.55);
        double coast=18+cliff*Math.min(320,Math.max(0,model)*.30);
        // Keep low coastal plains low. Broad shoulders accommodate high inland relief.
        coast=Math.min(coast,Math.max(18,model));
        if(signedNativeDistance>=0){
            double width=Math.max(48,Math.min(240,Math.max(0,model)/12));
            double t=smooth(signedNativeDistance/width);
            double target=Math.max(model,Math.max(18,Math.min(65,Math.max(0,source)*.55)));
            // Broad, low-amplitude buttresses fade completely at both ends of the shoulder.
            double shoulder=detailField*Math.min(65,Math.max(0,model)*.045)*4*t*(1-t)*cliff;
            return (float)Math.max(18,coast+(target-coast)*t+shoulder);
        }
        double width=12+cliff*16,t=smooth(-signedNativeDistance/width);
        double target=Math.min(model,Math.max(-100,Math.min(-12,source*.1)));
        return (float)(coast+(target-coast)*t);
    }
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
