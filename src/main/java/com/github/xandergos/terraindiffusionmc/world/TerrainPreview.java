package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeClassifier;
import java.io.IOException;

/** Cheap sampled fields exclusively for LOD display. Never uses LocalTerrainProvider or writes chunks. */
public final class TerrainPreview {
    private final BlueprintTileStore store;
    private final BlueprintDetailMap detail;
    private final WorldHydrology prepared;
    private final int scale;
    public TerrainPreview(BlueprintTileStore store,BlueprintDetailMap detail,WorldHydrology prepared,int scale){
        this.store=store;this.detail=detail;this.prepared=prepared;this.scale=scale;
    }
    public boolean refined(){return prepared!=null;}
    private double u(double x){return x/(store.manifest().width()*256.0*scale)+.5;}
    private double v(double z){return z/(store.manifest().height()*256.0*scale)+.5;}
    private float coarse(int channel,double u,double v)throws IOException{
        int w=store.manifest().width(),h=store.manifest().height();
        double x=Math.max(0,Math.min(w-1,u*w-.5)),z=Math.max(0,Math.min(h-1,v*h-.5));
        int ix=(int)x,iz=(int)z,jx=Math.min(w-1,ix+1),jz=Math.min(h-1,iz+1);double a=x-ix,b=z-iz;
        return (float)((store.value(channel,ix,iz)*(1-a)+store.value(channel,jx,iz)*a)*(1-b)+(store.value(channel,ix,jz)*(1-a)+store.value(channel,jx,jz)*a)*b);
    }
    private float elevation(double x,double z)throws IOException{
        double u=u(x),v=v(z),cu=Math.max(0,Math.min(1,u)),cv=Math.max(0,Math.min(1,v));
        float raw=detail==null?coarse(0,cu,cv):detail.sample(0,cu,cv);
        if(detail==null)raw=Math.copySign(raw*raw,raw);
        double nx=(cu-.5)*store.manifest().width()*256,nz=(cv-.5)*store.manifest().height()*256;
        float e=prepared==null?raw:prepared.previewElevation(nx,nz);
        if(prepared==null&&store.manifest().effectiveBiomeClassifierVersion()>=12){
            e=WorldHydrology.enhanceMountainRelief(e,nx,nz,com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.getSeed());
            e=WorldHydrology.refineCoast(e,nx,nz,store.manifest().width()*256,store.manifest().height()*256);
        }
        // The authored preview has no exterior diffusion samples. Fade to a clearly approximate ocean apron.
        double outside=Math.hypot((u-cu)*store.manifest().width()*256*scale,(v-cv)*store.manifest().height()*256*scale);
        double t=Math.min(1,outside/(256*scale));t=t*t*(3-2*t);
        return (float)(e*(1-t)-1500*t);
    }
    public record Tile(float[] elevation,float[] water,short[] biomes,boolean refined,net.minecraft.block.Block[] surfaces){}
    public Tile sample(int x0,int z0,int width,int step)throws IOException{
        if(width<1||width>256||step<1||step>1<<20)throw new IllegalArgumentException("Invalid LOD preview extent");
        if(store.manifest().effectiveBiomeClassifierVersion()<13)return sampleRaw(x0,z0,width,step);
        int halo=(int)Math.ceil(BiomeBoundaryField.HALO/(double)step)+1,pw=width+2*halo;
        var padded=sampleRaw(x0-halo*step,z0-halo*step,pw,step);
        float[] e=new float[width*width],water=new float[e.length];short[] ids=new short[e.length];
        net.minecraft.block.Block[] surface=new net.minecraft.block.Block[e.length];
        for(int z=0;z<width;z++){
            int start=(z+halo)*pw+halo,target=z*width;
            System.arraycopy(padded.elevation(),start,e,target,width);System.arraycopy(padded.water(),start,water,target,width);
            System.arraycopy(padded.biomes(),start,ids,target,width);System.arraycopy(padded.surfaces(),start,surface,target,width);
        }
        return new Tile(e,water,ids,refined(),surface);
    }
    private Tile sampleRaw(int x0,int z0,int width,int step)throws IOException{
        if(width<1||width>512||step<1||step>1<<20)throw new IllegalArgumentException("Invalid LOD preview extent");
        int n=width*width,pw=width+2;float[] e=new float[n],water=new float[n],climate=new float[n*4],halo=new float[pw*pw];
        for(int z=-1;z<=width;z++)for(int x=-1;x<=width;x++)halo[(z+1)*pw+x+1]=elevation(x0+(double)x*step,z0+(double)z*step);
        for(int z=0;z<width;z++)for(int x=0;x<width;x++){
            int i=z*width+x;double bx=x0+(double)x*step,bz=z0+(double)z*step,u=u(bx),v=v(bz);
            e[i]=halo[(z+1)*pw+x+1];water[i]=e[i]<0?0:Float.NaN;
            if(u>=0&&u<=1&&v>=0&&v<=1){
                float lake=prepared!=null?prepared.previewWater(bx/scale,bz/scale):detail==null?-1:detail.sample(3,u,v);
                if(Float.isFinite(lake)&&lake>=0&&lake>e[i]+8)water[i]=lake;
            }
            for(int ch=0;ch<4;ch++)climate[ch*n+i]=coarse(ch+1,u,v);
            if(store.manifest().effectiveBiomeClassifierVersion()>=6){
                float source=coarse(0,u,v);source=source>0?source*source:0;
                climate[i]-=.0065f*(Math.max(0,e[i])-source);
            }
        }
        short[] biomes=BiomeClassifier.classify(e,climate,z0,x0,halo,width,width,30f/scale*step,step);
        net.minecraft.block.Block[] surfaces=new net.minecraft.block.Block[n];
        if(store.manifest().effectiveBiomeClassifierVersion()>=13){
            long seed=com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.getSeed();
            // Preview halo distances are coarse approximations; detailed DH chunks replace this rough pass.
            short[][] ids=new short[width][width],waters=new short[width][width];
            for(int z=0;z<width;z++)for(int x=0;x<width;x++){int i=z*width+x;ids[z][x]=biomes[i];waters[z][x]=Float.isFinite(water[i])?(short)water[i]:Short.MIN_VALUE;}
            var boundary=new BiomeBoundaryField(ids,waters);
            for(int z=0;z<width;z++)for(int x=0;x<width;x++){
                int i=z*width+x,bx=x0+x*step,bz=z0+z*step;
                var f=com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.at(bx/(double)scale,bz/(double)scale,seed);
                var completed=DesertLandforms.complete(e[i],bx,bz,biomes[i],Float.isFinite(water[i]),boundary.distance(x,z)*step,boundary.waterDistance(x,z)*step,f,seed,scale);
                e[i]=completed.metres();
                if(biomes[i]==com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.BADLANDS||biomes[i]==com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.ERODED_BADLANDS||biomes[i]==com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.WOODED_BADLANDS)
                    surfaces[i]=DesertProvinces.strata(bx/(double)scale,completed.topY()-1,bz/(double)scale,seed);
                if(biomes[i]==com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.DESERT)
                    surfaces[i]=OwnedSurfaceDecorator.desertMaterial(bx,completed.topY()-1,bz,completed.baseY(),f,completed.land());
            }
        }
        return new Tile(e,water,biomes,refined(),surfaces);
    }
}
