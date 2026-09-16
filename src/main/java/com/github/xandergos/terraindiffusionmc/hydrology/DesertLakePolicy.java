package com.github.xandergos.terraindiffusionmc.hydrology;

import java.util.function.IntPredicate;

/** Whole-basin decisions preserve shore continuity; routing and rivers are untouched. */
public final class DesertLakePolicy {
    private DesertLakePolicy(){}
    public static boolean retain(int nodes,boolean authored,boolean river,boolean dry,double selection){
        return !dry||authored||river||nodes>=64||selection<.10;
    }
    static boolean[] suppressed(DrainageGrid grid,float[] cuts,IntPredicate desert){
        int n=grid.terrain.length;boolean[] seen=new boolean[n],removed=new boolean[n];int[] queue=new int[n];
        for(int start=0;start<n;start++){
            if(seen[start]||grid.lakeDepth(start)<=15)continue;
            int head=0,tail=1;queue[0]=start;seen[start]=true;boolean authored=false,river=false;
            while(head<tail){
                int i=queue[head++],x=i%grid.width,z=i/grid.width;
                authored|=cuts[i]>0;river|=grid.river[i];
                for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
                    int xx=x+dx,zz=z+dz;if(xx<0||xx>=grid.width||zz<0||zz>=grid.height)continue;
                    int q=zz*grid.width+xx;
                    if(!seen[q]&&grid.lakeDepth(q)>15&&Math.abs(grid.surface[q]-grid.surface[start])<1){seen[q]=true;queue[tail++]=q;}
                }
            }
            boolean dry=!authored&&!river&&tail<64&&desert.test(queue[tail/2]);
            double selection=com.github.xandergos.terraindiffusionmc.world.SurfaceAccentRules.unit(0x4F41534953L,start%grid.width,start/grid.width,17);
            if(!retain(tail,authored,river,dry,selection))for(int k=0;k<tail;k++)removed[queue[k]]=true;
        }
        return removed;
    }
}
