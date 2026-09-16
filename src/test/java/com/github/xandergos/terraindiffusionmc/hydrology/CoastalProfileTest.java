package com.github.xandergos.terraindiffusionmc.hydrology;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalProfileTest {
    @Test void signedCoastSmoothingRetainsTinyIslands(){
        float[][] channels=new float[4][81];java.util.Arrays.fill(channels[0],-100);channels[0][40]=100;
        var map=new com.github.xandergos.terraindiffusionmc.blueprint.BlueprintDetailMap(9,9,channels);
        assertTrue(map.signedCoastDistance(.5,.5)>0);
        assertTrue(map.signedCoastDistance(5.5/9,.5)<0);
        assertEquals(100,map.nearest(0,.5,.5));
    }
    @Test void coastHasNoHighRimAndPreservesInterior(){
        for(float model:new float[]{100,1200,4000})for(double cliff:new double[]{-1,0,.9}){
            float previous=CoastalProfile.height(model,-350,-40,cliff,0);
            for(int d=-39;d<=300;d++){
                float e=CoastalProfile.height(model,d<0?-350:1500,d,cliff,0);
                assertTrue(e>=previous-.001,"No trench or detached outer ridge on a uniform coastal transect");previous=e;
            }
            assertEquals(model,CoastalProfile.height(model,1000,300,cliff,0),.001);
            assertTrue(CoastalProfile.height(model,1000,0,cliff,0)<=338);
        }
    }
    @Test void shoreIsContinuousAndIslandLandSurvives(){
        float a=CoastalProfile.height(1200,-20,-.001,.8,.4),b=CoastalProfile.height(1200,20,.001,.8,.4);
        assertEquals(a,b,.01);
        for(int d=0;d<100;d++)assertTrue(CoastalProfile.height(-300,20,d,0,-1)>=18,"Land must survive block-height quantization at scale 3");
        assertTrue(CoastalProfile.height(3000,-350,-30,1,0)<0);
    }
}
