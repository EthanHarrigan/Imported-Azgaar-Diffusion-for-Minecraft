package com.github.xandergos.terraindiffusionmc.hydrology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RiverEdgeShapeTest {
    @Test void narrowChannelsStayOpenAndVariationScales(){
        for(int scale=1;scale<=4;scale++)for(double n:new double[]{-1,0,1}){
            assertTrue(RiverEdgeShape.radius(2*scale,scale,n,n)>=2*scale);
            assertEquals(RiverEdgeShape.radius(10,1,n,n)*scale,RiverEdgeShape.radius(10*scale,scale,n,n),1e-6);
            assertTrue(RiverEdgeShape.bankWidth(8*scale,n)>=6*scale);
        }
    }
    @Test void shallowsGradeSmoothlyIntoAnUnchangedDeepCore(){
        assertEquals(60,RiverEdgeShape.depth(60,0,20,1));
        float previous=60;
        for(double d=0;d<=20;d+=.1){float v=RiverEdgeShape.depth(60,d,20,0);assertTrue(v<=previous+.0001);assertTrue(v>=10);previous=v;}
        assertEquals(12,RiverEdgeShape.depth(60,20,20,0));
    }
}
