package com.github.xandergos.terraindiffusionmc.blueprint;

import java.util.*;

/** Saved, source-bound summit anchors. Boosts are physical metres (10 metres/block at scale 3). */
public record ExceptionalSummit(double u,double v,double radiusNative,float boostMetres) {
    public static final String CETEVILES_SOURCE="b2cc2579e67ce18c457265147e596563055fc6068dbd23a107ba0a7f44713d53";
    public static final String DESERT_CETEVILES_SOURCE="3908d731f734b0ad413e1f171ab86891eba7b1644271e36ccbfcb123a6650f51";
    /** Source-cell neighbourhoods transformed through the compiler's longitude padding. */
    public static List<ExceptionalSummit> plan(String hash,float[] height,int w,int h,int nativeWidth,int contentMin,int contentWidth,int coarseWidth){
        if(!DESERT_CETEVILES_SOURCE.equals(hash))return plan(hash,height,w,h,nativeWidth);
        List<ExceptionalSummit> out=new ArrayList<>();
        double left=contentMin/(double)coarseWidth,span=contentWidth/(double)coarseWidth;
        // Reviewed cells 901 and 4003; score the surrounding high terrain, not raster scan order.
        near(out,height,w,h,nativeWidth,left+span*(650.08/2560),328.01/1317,10000,1800);
        near(out,height,w,h,nativeWidth,left+span*(1715.74/2560),768.20/1317,6500,1550);
        return List.copyOf(out);
    }
    static void near(List<ExceptionalSummit> out,float[] e,int w,int h,int nw,double u,double v,float boost,double radius){
        double best=-Double.MAX_VALUE,bu=u,bv=v;
        int nh=nw*h/w,cx=(int)(u*w),cz=(int)(v*h),r=Math.max(2,(int)Math.ceil(250.0*w/nw));
        for(int z=Math.max(1,cz-r);z<Math.min(h-1,cz+r+1);z++)for(int x=Math.max(1,cx-r);x<Math.min(w-1,cx+r+1);x++){
            double pu=(x+.5)/w,pv=(z+.5)/h,distance=Math.hypot((pu-u)*nw,(pv-v)*nh);
            if(distance>250||e[z*w+x]<3000)continue;
            double neighbours=(e[z*w+x-1]+e[z*w+x+1]+e[(z-1)*w+x]+e[(z+1)*w+x])*.25;
            double score=e[z*w+x]*.65+neighbours*.35-distance*1.5;
            if(score>best){best=score;bu=pu;bv=pv;}
        }
        if(best>-Double.MAX_VALUE)out.add(new ExceptionalSummit(bu,bv,radius,boost));
    }
    public static List<ExceptionalSummit> plan(String hash,float[] height,int w,int h,int nativeWidth){
        if(!CETEVILES_SOURCE.equals(hash))return List.of();
        List<ExceptionalSummit> out=new ArrayList<>();
        select(out,height,w,h,nativeWidth,.17,.265,.205,.325,new float[]{10000,8500,6500},true,1800);
        select(out,height,w,h,nativeWidth,.62,.69,.52,.62,new float[]{6500,5000},false,1550);
        return List.copyOf(out);
    }
    private static void select(List<ExceptionalSummit> out,float[] heights,int w,int h,int nw,
                               double x0,double x1,double z0,double z1,float[] boosts,boolean northwest,double radius){
        for(float boost:boosts){
            double best=-Double.MAX_VALUE,bu=0,bv=0;
            for(int z=(int)(z0*h);z<z1*h;z++)for(int x=(int)(x0*w);x<x1*w;x++){
                float e=heights[z*w+x];if(e<3000)continue;
                double u=(x+.5)/w,v=(z+.5)/h;
                boolean close=false;
                for(var s:out)if(Math.hypot((u-s.u)*nw,(v-s.v)*nw*h/w)<650){close=true;break;}
                if(close)continue;
                double score=e+(northwest?2500*((x1-u)/(x1-x0)+(z1-v)/(z1-z0)):0);
                if(score>best){best=score;bu=u;bv=v;}
            }
            if(best>-Double.MAX_VALUE)out.add(new ExceptionalSummit(bu,bv,radius,boost));
        }
    }
    public double weight(double x,double z,int nw,int nh){
        double dx=x-(u-.5)*nw,dz=z-(v-.5)*nh;
        if(Math.abs(dx)>=radiusNative||Math.abs(dz)>=radiusNative)return 0;
        double r=Math.hypot(dx,dz)/radiusNative;
        return r>=1?0:(1-r)*(1-r)*(1+2*r);
    }
    public static float boost(List<ExceptionalSummit> summits,float elevation,double x,double z,int nw,int nh,int scale){
        if(elevation<=3000||summits.isEmpty())return 0;
        double high=Math.min(1,(elevation-3000)/1500.0);high=high*high*(3-2*high);
        double extra=0;
        for(var s:summits)extra=Math.max(extra,s.boostMetres*s.weight(x,z,nw,nh));
        // Smoothly spend only available headroom. Unlike a hard cap or amplitude fade,
        // this remains monotonic in model elevation and never flattens or inverts a summit.
        return (float)(ceilingCurve(elevation+extra*high,scale)-ceilingCurve(elevation,scale));
    }
    /** V10 spreads the uplift through a range-sized shoulder while retaining a handful of sharp cores. */
    public static float broadBoost(List<ExceptionalSummit> summits,float elevation,double x,double z,int nw,int nh,int scale,long seed){
        if(elevation<=1100||summits.isEmpty())return 0;
        double altitude=smooth((elevation-1100)/2400.0),extra=0;
        for(var s:summits){
            double dx=x-(s.u-.5)*nw,dz=z-(s.v-.5)*nh,r=Math.hypot(dx,dz)/s.radiusNative;
            if(r>=1)continue;
            double shoulder=smooth(1-r);
            double core=Math.pow(Math.max(0,1-r),4);
            // Long ridges and shallow gullies keep the enlarged envelope from reading as one bulb.
            double ridge=com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology.edgeNoise(
                    (x+z*.31), (z-x*.19), seed^Double.doubleToLongBits(s.u+s.v), 105);
            double texture=.90+.10*Math.max(-.7,ridge);
            double weight=(.50*shoulder+.50*core)*texture;
            extra=Math.max(extra,s.boostMetres*weight);
        }
        return (float)(ceilingCurve(elevation+extra*altitude,scale)-ceilingCurve(elevation,scale));
    }
    private static double smooth(double x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    /** V13: support the surrounding range, carve connected ridge-side gullies, cap the final height. */
    public static float shapedHeight(List<ExceptionalSummit> summits,float elevation,double x,double z,int nw,int nh,int scale,long seed){
        double extra=0,altitude=smooth((elevation-700)/2800.0);
        for(var s:summits){
            double r=Math.hypot(x-(s.u-.5)*nw,z-(s.v-.5)*nh)/s.radiusNative;
            if(r>=1)continue;
            double shoulder=smooth(1-r),core=Math.pow(1-r,4);
            double a=x+z*.31,b=z-x*.19;
            double coarse=com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology.edgeNoise(a,b,seed^Double.doubleToLongBits(s.u+s.v),160);
            double fine=com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology.edgeNoise(a,b,seed^0x47554C4C593133L,48);
            double ridged=Math.pow(1-Math.abs(coarse+.16*fine),3);
            double texture=.74+.26*Math.max(0,ridged);
            var map=WorldBlueprintManager.detail();
            if(map!=null){
                double u=x/nw+.5,v=z/nh+.5;
                double source=map.sample(0,u,v),east=map.sample(0,u+64.0/nw,v),west=map.sample(0,u-64.0/nw,v);
                double south=map.sample(0,u,v+64.0/nh),north=map.sample(0,u,v-64.0/nh);
                // Preserve source valley corridors and favour existing spines rather than filling basins.
                double hollow=smooth(((east+west+south+north)*.25-source)/350);
                texture*=1-.22*hollow;
            }
            extra=Math.max(extra,s.boostMetres*(.65*shoulder+.35*core)*texture);
        }
        return (float)ceilingCurve(elevation+extra*altitude,scale);
    }
    public static double ceilingCurve(double e,int scale){
        double metresPerBlock=30.0/Math.max(1,scale),start=(com.github.xandergos.terraindiffusionmc.world.VerticalProfile.TOP_Y-com.github.xandergos.terraindiffusionmc.world.VerticalProfile.SEA_LEVEL-80)*metresPerBlock,range=80*metresPerBlock;
        return e<=start?e:start+range*(e-start)/(range+e-start);
    }
}
