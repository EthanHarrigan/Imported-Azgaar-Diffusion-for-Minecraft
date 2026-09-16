package com.github.xandergos.terraindiffusionmc.hydrology;

import java.util.*;

/** Global D8 priority-flood drainage. Parent ranks strictly decrease: no cycles or tile-edge outlets. */
public final class DrainageGrid {
    public final int width,height,step;
    public final float[] terrain,surface,flow;
    public final int[] parent;
    public final boolean[] river;
    private record Node(int index,float level) implements Comparable<Node>{
        public int compareTo(Node b){
            int c=Float.compare(level,b.level);
            if(c!=0)return c;
            c=Long.compareUnsigned(tie(index),tie(b.index));
            return c!=0?c:Integer.compare(index,b.index);
        }
        private static long tie(int i){
            long x=i*0x9E3779B97F4A7C15L+0xD1B54A32D192ED03L;
            x=(x^(x>>>30))*0xBF58476D1CE4E5B9L;
            x=(x^(x>>>27))*0x94D049BB133111EBL;
            return x^(x>>>31);
        }
    }
    public DrainageGrid(int width,int height,int step,float[] terrain,float[] rainfall,float[] sourceRiver){
        if(width<2||height<2||step<1||(long)width*height!=terrain.length||rainfall.length!=terrain.length||sourceRiver.length!=terrain.length)
            throw new IllegalArgumentException("Drainage dimensions do not match");
        this.width=width;this.height=height;this.step=step;this.terrain=terrain.clone();
        int n=terrain.length;surface=terrain.clone();flow=new float[n];parent=new int[n];river=new boolean[n];
        Arrays.fill(parent,-1);boolean[] seen=new boolean[n];int[] order=new int[n];int count=0;
        PriorityQueue<Node> queue=new PriorityQueue<>();
        for(int i=0;i<n;i++){
            if(!Float.isFinite(terrain[i]))throw new IllegalArgumentException("Non-finite drainage elevation");
            if(!Float.isFinite(rainfall[i])||!Float.isFinite(sourceRiver[i]))throw new IllegalArgumentException("Non-finite drainage climate/source hint");
            int x=i%width,z=i/width;
            flow[i]=Math.max(.15f,Math.min(2.5f,rainfall[i]/900));
            if(terrain[i]<=0||x==0||z==0||x==width-1||z==height-1){seen[i]=true;surface[i]=Math.max(0,terrain[i]);queue.add(new Node(i,surface[i]));}
        }
        while(!queue.isEmpty()){
            Node a=queue.remove();int i=a.index;order[count++]=i;int x=i%width,z=i/width;
            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                if(dx==0&&dz==0)continue;int xx=x+dx,zz=z+dz;
                if(xx<0||xx>=width||zz<0||zz>=height)continue;int j=zz*width+xx;
                if(seen[j])continue;seen[j]=true;parent[j]=i;
                surface[j]=Math.max(terrain[j],surface[i]);queue.add(new Node(j,surface[j]));
            }
        }
        for(int k=count-1;k>=0;k--){int i=order[k];if(parent[i]>=0)flow[parent[i]]+=flow[i];}
        // Authored corridors favor headwaters; flow routing always follows final terrain.
        for(int i=0;i<n;i++)river[i]=parent[i]>=0&&terrain[i]>0&&
                (flow[i]>=80||(sourceRiver[i]>.2f&&flow[i]>=24));
        // Keep every tributary connected even when its authored preference ends.
        for(int k=count-1;k>=0;k--){int i=order[k],p=parent[i];if(river[i]&&p>=0&&terrain[p]>0&&parent[p]>=0)river[p]=true;}
    }
    /** Restore the validated routing solution without invoking priority flood. */
    DrainageGrid(int width,int height,int step,float[] terrain,float[] surface,float[] flow,int[] parent,boolean[] river){
        this.width=width;this.height=height;this.step=step;
        this.terrain=terrain;this.surface=surface;this.flow=flow;this.parent=parent;this.river=river;
        validate();
    }
    public float radiusNative(int i){return Math.min(13.3f,1.2f+1.9f*(float)Math.log1p(flow[i]/80));}
    public float lakeDepth(int i){return terrain[i]>0?surface[i]-terrain[i]:0;}
    /** Shared tangent at a junction makes all incident curves meet without a positional seam. */
    public double[] tangent(int i){
        int x=i%width,z=i/width,child=i;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            int xx=x+dx,zz=z+dz;if(xx<0||xx>=width||zz<0||zz>=height)continue;
            int p=zz*width+xx;if(parent[p]==i&&(child==i||flow[p]>flow[child]))child=p;
        }
        int downstream=parent[i]>=0?parent[i]:i;
        double dx=downstream%width-child%width,dz=downstream/width-child/width,len=Math.hypot(dx,dz);
        return len==0?new double[]{0,0}:new double[]{dx/len,dz/len};
    }
    public int riverCount(){int n=0;for(boolean r:river)if(r)n++;return n;}
    public void validate(){
        for(int i=0;i<parent.length;i++){
            int p=parent[i];
            if(p< -1||p>=parent.length||!Float.isFinite(terrain[i])||!Float.isFinite(surface[i])||!Float.isFinite(flow[i])||flow[i]<0)
                throw new IllegalStateException("Invalid saved drainage node");
            if(p>=0&&surface[p]>surface[i])throw new IllegalStateException("Uphill drainage edge");
            if(river[i]&&p>=0&&terrain[p]>0&&parent[p]>=0&&!river[p])throw new IllegalStateException("Disconnected tributary");
        }
        // Linear-time cycle test; protects serialization/reconstruction changes as well.
        byte[] state=new byte[parent.length];
        for(int start=0;start<parent.length;start++){
            int p=start;while(p>=0&&state[p]==0){state[p]=1;p=parent[p];}
            if(p>=0&&state[p]==1)throw new IllegalStateException("Drainage cycle");
            p=start;while(p>=0&&state[p]==1){state[p]=2;p=parent[p];}
        }
    }
}
