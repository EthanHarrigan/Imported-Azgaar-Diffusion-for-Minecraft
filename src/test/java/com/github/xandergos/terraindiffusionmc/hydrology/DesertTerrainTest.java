package com.github.xandergos.terraindiffusionmc.hydrology;

import com.github.xandergos.terraindiffusionmc.blueprint.BlueprintDetailMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;

class DesertTerrainTest {
    @Test void ridgeIsBoundedContinuousAndAsymmetric(){
        for(double p=-3;p<3;p+=.0007){assertTrue(DesertTerrain.ridge(p)>=0);assertTrue(DesertTerrain.ridge(p)<=1);}
        assertEquals(0,DesertTerrain.ridge(0));assertEquals(1,DesertTerrain.ridge(.76));
        assertEquals(.5,DesertTerrain.ridge(.38),1e-8);assertEquals(.5,DesertTerrain.ridge(.88),1e-8);
        assertTrue(Math.abs(DesertTerrain.ridge(1-1e-6)-DesertTerrain.ridge(1+1e-6))<1e-8);
    }
    @Test void dunesHaveBoundedReliefAndStableWorldCoordinateSampling(){
        var f=new DesertTerrain.Field(1,1,0,1,0,1);
        double lo=Double.POSITIVE_INFINITY,hi=0;
        for(int x=-2000;x<2000;x++){
            double h=DesertTerrain.duneOffset(x,300,12,f);lo=Math.min(lo,h);hi=Math.max(hi,h);
            assertTrue(h>=0&&h<=354);assertEquals(h,DesertTerrain.duneOffset(x,300,12,f));
            assertTrue(Math.abs(h-DesertTerrain.duneOffset(x+.001,300,12,f))<.1);
        }
        assertTrue(hi-lo>180,"A sand sea must have visible dunes, not just texture");
        var rock=new DesertTerrain.Field(1,1,0,1,.9,1);
        assertEquals(0,DesertTerrain.duneOffset(100,80,12,rock));
    }
    @Test void regionalMaskHasWideContinuousEdgesAndKeepsDryCore(){
        int w=128,h=64,n=w*h;float[][] a=new float[4][n];
        Arrays.fill(a[0],500);Arrays.fill(a[3],-1);
        for(int z=0;z<h;z++)for(int x=0;x<w;x++)a[1][z*w+x]=x<w/2?1:6;
        var map=new BlueprintDetailMap(w,h,a);
        assertTrue(map.desertWeight(.25,.5,2048)>.99);
        assertTrue(map.desertWeight(.75,.5,2048)<.01);
        assertEquals(.5,map.desertWeight(.5,.5,2048),.02);
        float previous=1;
        for(double u=.3;u<.7;u+=.001){float value=map.desertWeight(u,.5,2048);assertTrue(value<=previous+1e-6);assertTrue(Math.abs(previous-value)<.03);previous=value;}
        assertEquals(0,map.desertWeight(-.1,.5,2048));
        var field=DesertTerrain.at(map,2048,1024,-500,0,42);
        assertTrue(field.dry()>.9);assertTrue(field.waterProtection()>.99);
    }
    @Test void lakesSuppressSandWithoutSharpMaskBoundary(){
        int w=128,h=64,n=w*h;float[][] a=new float[4][n];Arrays.fill(a[0],500);Arrays.fill(a[1],1);Arrays.fill(a[3],-1);
        for(int z=20;z<44;z++)for(int x=50;x<78;x++)a[3][z*w+x]=600;
        var map=new BlueprintDetailMap(w,h,a);
        assertEquals(0,DesertTerrain.at(map,2048,1024,0,0,42).waterProtection());
    }
}
