package com.github.xandergos.terraindiffusionmc.hydrology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LakeEdgeProfileTest {
    @Test void highIslandRetainsInteriorWithContinuousShoulders(){
        float last=LakeEdgeProfile.height(1700,100,-200);
        for(int d=-199;d<=200;d++){
            float value=LakeEdgeProfile.height(1700,100,d);
            assertTrue(value>=last);assertTrue(value-last<8,"No categorical wall");last=value;
        }
        assertEquals(1700,last);assertEquals(100,LakeEdgeProfile.height(1700,100,-200));
        assertEquals(900,LakeEdgeProfile.height(1700,100,0));
    }
    @Test void noLakeDoesNotChangeSeaCoast(){assertEquals(50,LakeEdgeProfile.height(2000,50,Double.NEGATIVE_INFINITY));}
    @Test void signedMaskInterpolatesThroughBoundary(){
        float[][] a=new float[4][200];java.util.Arrays.fill(a[3],-1);
        for(int z=0;z<10;z++)for(int x=10;x<20;x++)a[3][z*20+x]=300;
        var map=new com.github.xandergos.terraindiffusionmc.blueprint.BlueprintDetailMap(20,10,a);
        assertEquals(0,map.signedLakeDistance(.5,.5),.0001);
        assertTrue(map.signedLakeDistance(.55,.5)>0);assertTrue(map.signedLakeDistance(.45,.5)<0);
    }
}
