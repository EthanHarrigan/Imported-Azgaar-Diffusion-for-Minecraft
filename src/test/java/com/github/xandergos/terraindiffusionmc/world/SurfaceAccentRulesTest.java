package com.github.xandergos.terraindiffusionmc.world;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceAccentRulesTest {
    @Test void snagsAreRareAndMonumentsAreIrregular(){
        int dead=0,monuments=0;boolean previous=false;int changes=0;
        for(int i=0;i<20000;i++){
            if(SurfaceAccentRules.deadWood(42,i%200,i/200,false))dead++;
            boolean m=SurfaceAccentRules.monument(42,(i%200)*32,(i/200)*32,3);if(m)monuments++;
            if(previous!=m)changes++;previous=m;
            assertEquals(m,SurfaceAccentRules.monument(42,(i%200)*32,(i/200)*32,3));
        }
        assertTrue(dead>60&&dead<160);assertTrue(monuments>3000&&monuments<7000);assertTrue(changes>3000);
    }
    @Test void oasisRequiresFreshwaterAtReachableHeight(){
        assertTrue(SurfaceAccentRules.freshwater(150,120,20,3));
        assertFalse(SurfaceAccentRules.freshwater(150,0,10,3));
        assertFalse(SurfaceAccentRules.freshwater(1400,120,10,3));
        assertFalse(SurfaceAccentRules.freshwater(150,120,40,3));
        assertFalse(SurfaceAccentRules.freshwater(150,Float.NaN,10,3));
    }
    @Test void correlatedFieldIsSmoothAcrossLatticeBoundaries(){
        double before=SurfaceAccentRules.correlatedUnit(7,23.99,11.5,91,24);
        double after=SurfaceAccentRules.correlatedUnit(7,24.01,11.5,91,24);
        assertTrue(Math.abs(after-before)<.02);
        assertEquals(before,SurfaceAccentRules.correlatedUnit(7,23.99,11.5,91,24));
    }
    @Test void reefRequiresWarmShallowSeaAndHasFiniteExtent(){
        assertTrue(ReefRegions.suitable(-200,25));assertFalse(ReefRegions.suitable(-2000,25));
        assertFalse(ReefRegions.suitable(-200,5));assertFalse(ReefRegions.suitable(100,25));
        var sites=List.of(new ReefRegions.Site(0,0,450,1));
        assertEquals(1,ReefRegions.weight(sites,0,0,12));assertEquals(0,ReefRegions.weight(sites,1000,0,12));
    }
}
