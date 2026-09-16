package com.github.xandergos.terraindiffusionmc.hydrology;

/** Continuous replacement for the categorical lake exemption. Distances are native pixels. */
public final class LakeEdgeProfile {
    private LakeEdgeProfile(){}
    public static float height(float model,float coast,double lakeDistance){
        if(!Double.isFinite(lakeDistance))return coast;
        // High interiors need broad shoulders, not a fixed-width wall. No extra model queries.
        double width=Math.max(48,Math.min(320,Math.abs(model-coast)/10.0));
        double t=Math.max(0,Math.min(1,.5+lakeDistance/(2*width)));
        t=t*t*(3-2*t);
        return (float)(coast+(model-coast)*t);
    }
}
