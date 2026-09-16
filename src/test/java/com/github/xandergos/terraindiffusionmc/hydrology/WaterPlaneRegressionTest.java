package com.github.xandergos.terraindiffusionmc.hydrology;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class WaterPlaneRegressionTest {
    private static WorldHydrology lake(float[] ground,float[] spill){
        int[] parent={-1,-1,-1,-1};
        return new WorldHydrology(new DrainageGrid(2,2,16,ground,spill,new float[4],parent,new boolean[4]),16,16);
    }
    @Test void dryMountainCannotPullTheLakePlaneUp(){
        var h=lake(new float[]{40,1000,40,1700},new float[]{100,1000,100,1700});
        for(double z=0;z<1;z+=.05)for(double x=0;x<.99;x+=.01){
            var sample=h.naturalLake(x,z);
            assertEquals(100,sample.level());assertTrue(sample.depth()>0);
            assertEquals(45*(1-x),sample.depth(),.0001);
        }
    }
    @Test void adjacentSpillPlanesNeverCreateIntermediateWaterHeights(){
        var h=lake(new float[]{40,240,40,240},new float[]{100,300,100,300});
        for(double x=0;x<1;x+=.01){
            var sample=h.naturalLake(x,.5);
            assertEquals(100,sample.level());assertEquals(45*(1-x),sample.depth(),.0001);
        }
        var dry=lake(new float[]{100,1000,100,1700},new float[]{100,1000,100,1700});
        assertEquals(0,dry.naturalLake(.3,.4).depth());
    }
    @Test void channelEnteringLakeCannotFlowUphill(){
        float[] e={104,40,100,130,-10,-10},s={104,100,100,130,0,0};
        var grid=new DrainageGrid(2,3,16,e,s,new float[6],new int[]{1,2,4,0,-1,-1},new boolean[6]);
        var h=new WorldHydrology(grid,16,32);
        assertEquals(100,h.channelLevel(1));assertEquals(100,h.channelLevel(0));
        for(int i=0;i<6;i++)if(grid.parent[i]>=0)assertTrue(h.channelLevel(i)>=h.channelLevel(grid.parent[i]));
    }
    @Test void everySegmentOnRuggedDrainageGraphsIsFlatOrDownhill(){
        for(int seed=0;seed<8;seed++){
            int w=40,n=w*w;float[] e=new float[n],rain=new float[n],hint=new float[n];
            Arrays.fill(rain,900);var random=new java.util.Random(seed);
            for(int i=0;i<n;i++)e[i]=i%w==0||i/w==0||i%w==w-1||i/w==w-1?-10:30+random.nextInt(900);
            var grid=new DrainageGrid(w,w,16,e,rain,hint);var h=new WorldHydrology(grid,624,624);
            for(int i=0;i<n;i++){
                int p=grid.parent[i];if(p>=0)assertTrue(h.channelLevel(i)>=h.channelLevel(p));
                if(grid.lakeDepth(i)>15)assertEquals(grid.surface[i],h.channelLevel(i));
            }
        }
    }
}
