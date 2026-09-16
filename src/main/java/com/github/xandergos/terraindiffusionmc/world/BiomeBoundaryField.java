package com.github.xandergos.terraindiffusionmc.world;

import java.util.Arrays;

/** Distances over final categorical IDs, never a biome classifier. Inputs include a 64-block halo.
 * The local chord width bounds transitions in narrow patches, retaining pure centers. */
public final class BiomeBoundaryField {
    public static final int HALO=64;
    private final int w,h;
    private final short[][] ids;
    private final float[] distance,width,waterDistance;
    private final short[] neighbor;
    public BiomeBoundaryField(short[][] ids,short[][] water){
        this.ids=ids;h=ids.length;w=ids[0].length;
        distance=new float[w*h];width=new float[w*h];neighbor=new short[w*h];waterDistance=new float[w*h];
        Arrays.fill(distance,HALO);Arrays.fill(waterDistance,HALO);
        int[] horizontal=new int[w*h];
        for(int z=0;z<h;z++)for(int x=0;x<w;){int end=x+1;while(end<w&&ids[z][end]==ids[z][x])end++;
            for(int a=x;a<end;a++)horizontal[z*w+a]=Math.min(HALO,a-x+1)+Math.min(HALO,end-a)-1;x=end;}
        for(int x=0;x<w;x++)for(int z=0;z<h;){int end=z+1;while(end<h&&ids[end][x]==ids[z][x])end++;
            for(int a=z;a<end;a++)width[a*w+x]=Math.min(16,.20f*Math.min(horizontal[a*w+x],Math.min(HALO,a-z+1)+Math.min(HALO,end-a)-1));z=end;}
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int i=z*w+x;neighbor[i]=ids[z][x];
            if(water!=null&&water[z][x]!=Short.MIN_VALUE)waterDistance[i]=0;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                int a=x+dx,b=z+dz;if(a<0||b<0||a>=w||b>=h||ids[b][a]==ids[z][x])continue;
                float d=dx==0||dz==0?.5f:.7071068f;
                if(d<distance[i]||(d==distance[i]&&ids[b][a]<neighbor[i])){distance[i]=d;neighbor[i]=ids[b][a];}
            }
        }
        for(int pass=0;pass<2;pass++)for(int zz=0;zz<h;zz++)for(int xx=0;xx<w;xx++){
            int x=pass==0?xx:w-1-xx,z=pass==0?zz:h-1-zz,i=z*w+x;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                int a=x+dx,b=z+dz;if(a<0||b<0||a>=w||b>=h)continue;int j=b*w+a;
                float cost=dx==0||dz==0?1:1.4142136f;
                waterDistance[i]=Math.min(waterDistance[i],waterDistance[j]+cost);
                if(ids[b][a]!=ids[z][x])continue;float d=distance[j]+cost;
                if(d<distance[i]||(d==distance[i]&&neighbor[j]<neighbor[i])){distance[i]=d;neighbor[i]=neighbor[j];}
            }
        }
    }
    public float distance(int x,int z){return distance[z*w+x];}
    public float width(int x,int z){return width[z*w+x];}
    public float waterDistance(int x,int z){return waterDistance[z*w+x];}
    public short neighbor(int x,int z){return neighbor[z*w+x];}
    public double transition(int x,int z){return 1-SurfaceTransitions.smooth(distance(x,z)/width(x,z));}
}
