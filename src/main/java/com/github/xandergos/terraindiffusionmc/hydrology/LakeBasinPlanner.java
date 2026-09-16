package com.github.xandergos.terraindiffusionmc.hydrology;

import java.util.*;

/** Fits authored lake regions to a common natural rim without excavating mountains. */
final class LakeBasinPlanner {
    private LakeBasinPlanner(){}
    private static final int[][] NEIGHBORS={{-1,0},{1,0},{0,-1},{0,1}};
    static float[] cuts(float[] elevation,boolean[] mask,int w,int h){
        float[] cuts=new float[elevation.length];boolean[] seen=new boolean[elevation.length];
        for(int start=0;start<mask.length;start++){
            if(!mask[start]||seen[start])continue;
            ArrayList<Integer> basin=new ArrayList<>();ArrayDeque<Integer> queue=new ArrayDeque<>();
            queue.add(start);seen[start]=true;float rim=Float.POSITIVE_INFINITY;
            while(!queue.isEmpty()){
                int i=queue.remove();basin.add(i);int x=i%w,z=i/w;
                for(int[] d:NEIGHBORS){
                    int xx=x+d[0],zz=z+d[1];if(xx<0||xx>=w||zz<0||zz>=h){rim=0;continue;}
                    int p=zz*w+xx;
                    if(!mask[p])rim=Math.min(rim,elevation[p]);
                    else if(!seen[p]){seen[p]=true;queue.add(p);}
                }
            }
            if(!Float.isFinite(rim)||rim<=0)continue;
            for(int i:basin){
                // Upper bound is 60 metres (six blocks at scale 3), not an arbitrary deep trench.
                float desired=rim-30;
                if(elevation[i]>desired&&elevation[i]-desired<=60)cuts[i]=elevation[i]-desired;
            }
        }
        return cuts;
    }
}
