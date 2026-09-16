package com.github.xandergos.terraindiffusionmc.blueprint;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** Immutable higher-resolution source ecology, independent of the model's coarse tensor. */
public final class BlueprintDetailMap {
    private static final double[] RING_COS=new double[8],RING_SIN=new double[8];
    static{for(int k=0;k<8;k++){RING_COS[k]=Math.cos(k*Math.PI/4);RING_SIN[k]=Math.sin(k*Math.PI/4);}}
    private static final int MAGIC=0x54444531;
    public final int width,height;
    private final float[][] channels;
    private final float[] coastDistance;
    private final float[] signedCoastDistance;
    private final float[] signedLakeDistance;
    private volatile DesertMask desertMask;
    private volatile DesertMask warmDesertMask;
    private final float[] sandSeaDistance;
    private record DesertMask(int nativeWidth,int generation,float[] values){}

    /** Two box passes form a continuous regional transition, computed once, never per chunk.
     * Padding is zero (ocean), so desert does not leak through the authored rectangle edges. */
    public float desertWeight(double u,double v,int nativeWidth){
        return desertWeight(u,v,nativeWidth,false);
    }
    /** Surface/biome permission, separate from the unchanged pre-drainage shaping mask. */
    public float warmDesertWeight(double u,double v,int nativeWidth){
        return desertWeight(u,v,nativeWidth,true);
    }
    private float desertWeight(double u,double v,int nativeWidth,boolean warmOnly){
        if(u<=0||v<=0||u>=1||v>=1)return 0;
        int generation=WorldBlueprintManager.generationVersion()>=13?13:12;
        DesertMask mask=warmOnly?warmDesertMask:desertMask;
        if(mask==null||mask.nativeWidth!=nativeWidth||mask.generation!=generation){
            synchronized(this){
                mask=warmOnly?warmDesertMask:desertMask;
                if(mask==null||mask.nativeWidth!=nativeWidth||mask.generation!=generation){
                    float[] a=new float[width*height];
                    for(int i=0;i<a.length;i++){
                        int id=Math.round(channels[1][i]);
                        // Cold arid source cells are not warm sand/terracotta provinces.
                        a[i]=channels[0][i]>0&&channels[3][i]<0&&(id==1||(!warmOnly&&id==2)||id==1000+com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.DESERT
                                ||warmOnly&&(id==1000+com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.BADLANDS
                                ||id==1000+com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.ERODED_BADLANDS
                                ||id==1000+com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.WOODED_BADLANDS))?1:0;
                    }
                    double feather=WorldBlueprintManager.generationVersion()>=13?320*Math.sqrt(Math.max(.25,nativeWidth/25088.0)):110;
                    int radius=Math.max(1,(int)Math.round(feather*width/nativeWidth));
                    a=blur(blur(a,width,height,radius),width,height,radius);
                    mask=new DesertMask(nativeWidth,generation,a);
                    if(warmOnly)warmDesertMask=mask;else desertMask=mask;
                }
            }
        }
        return sample(mask.values,u,v);
    }
    private static float[] blur(float[] a,int w,int h,int radius){
        float[] b=new float[a.length],out=new float[a.length];int diameter=2*radius+1;
        for(int z=0;z<h;z++){
            double sum=0;for(int x=0;x<=radius&&x<w;x++)sum+=a[z*w+x];
            for(int x=0;x<w;x++){
                b[z*w+x]=(float)(sum/diameter);
                if(x-radius>=0)sum-=a[z*w+x-radius];
                if(x+radius+1<w)sum+=a[z*w+x+radius+1];
            }
        }
        for(int x=0;x<w;x++){
            double sum=0;for(int z=0;z<=radius&&z<h;z++)sum+=b[z*w+x];
            for(int z=0;z<h;z++){
                out[z*w+x]=(float)(sum/diameter);
                if(z-radius>=0)sum-=b[(z-radius)*w+x];
                if(z+radius+1<h)sum+=b[(z+radius+1)*w+x];
            }
        }
        return out;
    }
    public BlueprintDetailMap(int width,int height,float[][] channels){
        if(width<1||height<1||(long)width*height>8_500_000||(channels.length!=4&&channels.length!=5))throw new IllegalArgumentException("Invalid ecology dimensions");
        for(float[] channel:channels){
            if(channel.length!=width*height)throw new IllegalArgumentException("Invalid ecology channel length");
            for(float value:channel)if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite ecology data");
        }
        this.width=width;this.height=height;this.channels=channels;
        if(channels.length==5){
            float[] mask=new float[width*height];
            for(int i=0;i<mask.length;i++)mask[i]=channels[4][i]>.5f&&channels[0][i]>0&&channels[3][i]<0?1:-1;
            sandSeaDistance=coastDistance(mask,width,height);
            for(int i=0;i<mask.length;i++)sandSeaDistance[i]=mask[i]>0?sandSeaDistance[i]:0;
        }else sandSeaDistance=null;
        this.coastDistance=coastDistance(channels[0],width,height);
        boolean hasLake=false;
        for(float level:channels[3])if(level>=0){hasLake=true;break;}
        if(hasLake){
            float[] mask=new float[width*height];
            for(int i=0;i<mask.length;i++)mask[i]=channels[3][i]>=0?1:-1;
            signedLakeDistance=coastDistance(mask,width,height);
            for(int i=0;i<mask.length;i++)signedLakeDistance[i]=(signedLakeDistance[i]+.5f)*mask[i];
        }else signedLakeDistance=null;
        this.signedCoastDistance=new float[width*height];
        for(int i=0;i<signedCoastDistance.length;i++)signedCoastDistance[i]=(coastDistance[i]+.5f)*(channels[0][i]>=0?1:-1);
        float[] signed=signedCoastDistance.clone();
        for(int z=1;z<height-1;z++)for(int x=1;x<width-1;x++){
            int i=z*width+x;if(Math.abs(signed[i])>4)continue;
            float sum=signed[i]*24;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dz!=0)sum+=signed[i+dz*width+dx]*(dx==0||dz==0?2:1);
            // Round raster corners slightly but never erase a one-pixel source island/strait.
            signedCoastDistance[i]=Math.copySign(Math.max(.05f,Math.abs(sum/36)),signed[i]);
        }
    }
    public void write(Path path)throws IOException{
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))){
            out.writeInt(MAGIC);out.writeInt(width);out.writeInt(height);out.writeInt(channels.length);
            for(float[] channel:channels)for(float value:channel)out.writeFloat(value);
        }
    }
    public static BlueprintDetailMap read(Path path)throws IOException{
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))){
            if(in.readInt()!=MAGIC)throw new IOException("Invalid blueprint ecology sidecar");
            int w=in.readInt(),h=in.readInt(),c=in.readInt();
            if(w<1||h<1||(long)w*h>8_500_000||(c!=4&&c!=5))throw new IOException("Invalid ecology dimensions");
            float[][] a=new float[c][w*h];
            for(float[] channel:a)for(int i=0;i<channel.length;i++){
                channel[i]=in.readFloat();if(!Float.isFinite(channel[i]))throw new IOException("Non-finite ecology data");
            }
            if(in.read()!=-1)throw new IOException("Trailing ecology data");
            return new BlueprintDetailMap(w,h,a);
        }
    }
    public float nearest(int channel,double u,double v){
        return channels[channel][clamp((int)Math.floor(v*height),height)*width+clamp((int)Math.floor(u*width),width)];
    }
    /** Inward-only profile feather: no dune designation leaks into protected source cells. */
    public float sandSeaWeight(double u,double v,int nativeWidth){
        if(sandSeaDistance==null||u<=0||v<=0||u>=1||v>=1||nearest(4,u,v)<.5f||nearest(3,u,v)>=0)return 0;
        double widthNative=180*Math.sqrt(Math.max(1,nativeWidth/25088.0));
        double inward=Math.min(sample(sandSeaDistance,u,v),Math.min(Math.min(u,1-u)*width,Math.min(v,1-v)*height));
        double t=Math.max(0,Math.min(1,inward*nativeWidth/width/widthNative));
        return (float)(t*t*(3-2*t));
    }
    public float sample(int channel,double u,double v){
        return sample(channels[channel],u,v);
    }
    /** Fraction of nearby categorical samples matching the centre, used to feather authored ecology. */
    public float categoryAgreement(int channel,double u,double v,double radiusPixels){
        int centre=Math.round(nearest(channel,u,v));
        double du=radiusPixels/width,dv=radiusPixels/height;
        int same=4,total=4; // Give the selected centre a stable core without making boundaries hard.
        for(int ring=1;ring<=2;ring++)for(int k=0;k<8;k++){
            double rr=ring*.5;
            int value=Math.round(nearest(channel,u+RING_COS[k]*du*rr,v+RING_SIN[k]*dv*rr));
            if(value==centre)same++;total++;
        }
        return same/(float)total;
    }
    /** Distance in ecology pixels from the nearest land/ocean boundary, continuously sampled. */
    public float coastDistance(double u,double v){return sample(coastDistance,u,v);}
    public float signedCoastDistance(double u,double v){return sample(signedCoastDistance,u,v);}
    public float signedLakeDistance(double u,double v){return signedLakeDistance==null?Float.NEGATIVE_INFINITY:sample(signedLakeDistance,u,v);}
    private float sample(float[] a,double u,double v){
        double x=u*width-.5,y=v*height-.5;int x0=(int)Math.floor(x),y0=(int)Math.floor(y);
        double fx=x-x0,fy=y-y0;
        double top=a[clamp(y0,height)*width+clamp(x0,width)]*(1-fx)+a[clamp(y0,height)*width+clamp(x0+1,width)]*fx;
        double bottom=a[clamp(y0+1,height)*width+clamp(x0,width)]*(1-fx)+a[clamp(y0+1,height)*width+clamp(x0+1,width)]*fx;
        return (float)(top*(1-fy)+bottom*fy);
    }
    private static float[] coastDistance(float[] e,int w,int h){
        float[] d=new float[e.length];java.util.Arrays.fill(d,w+h);
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int i=z*w+x;boolean land=e[i]>=0;
            if((x>0&&(e[i-1]>=0)!=land)||(x+1<w&&(e[i+1]>=0)!=land)||(z>0&&(e[i-w]>=0)!=land)||(z+1<h&&(e[i+w]>=0)!=land))d[i]=0;
        }
        // Deterministic 8-neighbour chamfer transform; only a two-pixel coastal band is used.
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int i=z*w+x;
            if(x>0)d[i]=Math.min(d[i],d[i-1]+1);
            if(z>0){d[i]=Math.min(d[i],d[i-w]+1);if(x>0)d[i]=Math.min(d[i],d[i-w-1]+1.414214f);if(x+1<w)d[i]=Math.min(d[i],d[i-w+1]+1.414214f);}
        }
        for(int z=h-1;z>=0;z--)for(int x=w-1;x>=0;x--){
            int i=z*w+x;
            if(x+1<w)d[i]=Math.min(d[i],d[i+1]+1);
            if(z+1<h){d[i]=Math.min(d[i],d[i+w]+1);if(x>0)d[i]=Math.min(d[i],d[i+w-1]+1.414214f);if(x+1<w)d[i]=Math.min(d[i],d[i+w+1]+1.414214f);}
        }
        return d;
    }
    private static int clamp(int x,int n){return Math.max(0,Math.min(n-1,x));}
}
