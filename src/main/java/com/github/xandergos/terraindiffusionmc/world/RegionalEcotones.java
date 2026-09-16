package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;

/** Bounded, immutable regional family fields. <= 7 MiB, independent of world block area. */
public final class RegionalEcotones {
    private RegionalEcotones(){}
    private record Cache(BlueprintDetailMap source,int nw,int w,int h,float[][] fields){}
    private static volatile Cache cached;
    public static final int WOOD=0,COLD_WOOD=1,DRY=2,SNOW=3,WET=4,OPEN=5;
    public static float weight(int family,double x,double z){
        var store=WorldBlueprintManager.activeStore();var detail=WorldBlueprintManager.detail();
        if(store==null||detail==null)return 0;
        int nw=store.manifest().width()*256,nh=store.manifest().height()*256,scale=WorldScaleManager.getCurrentScale();
        double u=x/(nw*(double)scale)+.5,v=z/(nh*(double)scale)+.5;
        if(u<=0||v<=0||u>=1||v>=1)return 0;
        // Use the authored detail grid directly: small regions must not disappear
        // into the former 512-column, twice-blurred regional masks.
        return detailWeight(detail,family,u,v);
    }
    private static float detailWeight(BlueprintDetailMap d,int family,double u,double v) {
        double gx=u*d.width-.5,gz=v*d.height-.5;int ix=(int)Math.floor(gx),iz=(int)Math.floor(gz);
        double fx=gx-ix,fz=gz-iz,result=0;
        for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++){
            double pu=(Math.clamp(ix+dx,0,d.width-1)+.5)/d.width,pv=(Math.clamp(iz+dz,0,d.height-1)+.5)/d.height;
            int a=Math.round(d.nearest(1,pu,pv));
            boolean belongs=switch(family){
                case WOOD->EcotoneDecorator.woodland(a);
                case COLD_WOOD->a==9||a==1000+BiomeIds.TAIGA||a==1000+BiomeIds.SNOWY_TAIGA;
                case DRY->a==1||a==2||a==1000+BiomeIds.DESERT||a==1000+BiomeIds.BADLANDS;
                case SNOW->a==10||a==11||a==1000+BiomeIds.SNOWY_PLAINS||a==1000+BiomeIds.SNOWY_TAIGA;
                case WET->a==12||a==1000+BiomeIds.SWAMP||a==1000+BiomeIds.MANGROVE_SWAMP;
                default->a==3||a==4||a==1000+BiomeIds.PLAINS||a==1000+BiomeIds.MEADOW;
            };
            if(belongs&&d.nearest(0,pu,pv)>0&&d.nearest(3,pu,pv)<0)result+=(dx==0?1-fx:fx)*(dz==0?1-fz:fz);
        }
        return (float)result;
    }
    private static float legacyWeight(BlueprintDetailMap detail,int nw,double u,double v,int family) {
        Cache c=cached;
        if(c==null||c.source!=detail||c.nw!=nw){synchronized(RegionalEcotones.class){c=cached;if(c==null||c.source!=detail||c.nw!=nw)cached=c=build(detail,nw);}}
        double gx=u*c.w-.5,gz=v*c.h-.5;int ix=(int)Math.floor(gx),iz=(int)Math.floor(gz);
        double fx=gx-ix,fz=gz-iz;float[] a=c.fields[family];double result=0;
        for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++)result+=a[Math.clamp(iz+dz,0,c.h-1)*c.w+Math.clamp(ix+dx,0,c.w-1)]*(dx==0?1-fx:fx)*(dz==0?1-fz:fz);
        return (float)result;
    }
    private static Cache build(BlueprintDetailMap d,int nw){
        int w=Math.min(512,d.width),h=Math.max(1,(int)Math.round(w*d.height/(double)d.width));float[][] f=new float[6][w*h];
        for(int z=0;z<h;z++)for(int x=0;x<w;x++)for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++){
            double u=(x+(dx+.5)/2)/w,v=(z+(dz+.5)/2)/h;
            if(d.sample(0,u,v)<=0||d.nearest(3,u,v)>=0)continue;
            int a=Math.round(d.nearest(1,u,v)),i=z*w+x;
            if(EcotoneDecorator.woodland(a))f[WOOD][i]+=.25f;
            if(a==9||a==1000+BiomeIds.TAIGA||a==1000+BiomeIds.SNOWY_TAIGA)f[COLD_WOOD][i]+=.25f;
            if(a==1||a==2||a==1000+BiomeIds.DESERT||a==1000+BiomeIds.BADLANDS)f[DRY][i]+=.25f;
            if(a==10||a==11||a==1000+BiomeIds.SNOWY_PLAINS||a==1000+BiomeIds.SNOWY_TAIGA)f[SNOW][i]+=.25f;
            if(a==12||a==1000+BiomeIds.SWAMP||a==1000+BiomeIds.MANGROVE_SWAMP)f[WET][i]+=.25f;
            if(a==3||a==4||a==1000+BiomeIds.PLAINS||a==1000+BiomeIds.MEADOW)f[OPEN][i]+=.25f;
        }
        int radius=Math.max(1,(int)Math.round(SurfaceTransitions.nativeWidth()*w/nw));
        for(int i=0;i<f.length;i++)f[i]=blur(blur(f[i],w,h,radius),w,h,radius);
        return new Cache(d,nw,w,h,f);
    }
    private static float[] blur(float[] a,int w,int h,int r){
        float[] b=new float[a.length],out=new float[a.length];
        for(int z=0;z<h;z++){double sum=0;for(int x=0;x<=r&&x<w;x++)sum+=a[z*w+x];for(int x=0;x<w;x++){
            b[z*w+x]=(float)(sum/(2*r+1));if(x-r>=0)sum-=a[z*w+x-r];if(x+r+1<w)sum+=a[z*w+x+r+1];}}
        for(int x=0;x<w;x++){double sum=0;for(int z=0;z<=r&&z<h;z++)sum+=b[z*w+x];for(int z=0;z<h;z++){
            out[z*w+x]=(float)(sum/(2*r+1));if(z-r>=0)sum-=b[(z-r)*w+x];if(z+r+1<h)sum+=b[(z+r+1)*w+x];}}
        return out;
    }
}
