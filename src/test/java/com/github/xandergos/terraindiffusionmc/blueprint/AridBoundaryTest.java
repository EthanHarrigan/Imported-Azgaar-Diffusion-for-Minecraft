package com.github.xandergos.terraindiffusionmc.blueprint;

import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class AridBoundaryTest {
    @Test void coldAridIsNotPermissionForTerracottaButTerrainMaskStaysCompatible(){
        float[][] a=new float[4][128*64];Arrays.fill(a[0],800);Arrays.fill(a[1],2);Arrays.fill(a[3],-1);
        var d=new BlueprintDetailMap(128,64,a);
        assertEquals(1,d.desertWeight(.5,.5,25088));assertEquals(0,d.warmDesertWeight(.5,.5,25088));
        for(int id:new int[]{1,1000+BiomeIds.DESERT,1000+BiomeIds.BADLANDS,1000+BiomeIds.ERODED_BADLANDS,1000+BiomeIds.WOODED_BADLANDS}){
            Arrays.fill(a[1],id);d=new BlueprintDetailMap(128,64,a);
            assertEquals(1,d.warmDesertWeight(.5,.5,25088));
        }
    }
    @Test void rockContactHasNoScatteredTilesInTheOuterFeather(){
        for(long seed:new long[]{0,37,-9876543})for(int z=-1000;z<1000;z+=17)for(int x=-1000;x<1000;x+=17){
            assertFalse(DesertTerrain.aridRock(.53,x,z,seed));
            assertTrue(DesertTerrain.aridRock(.67,x,z,seed));
        }
    }
    @Test void aMonotoneCrossingMakesOneContactInsteadOfRepeatedTiles(){
        for(int z=-2000;z<2000;z+=23){
            int changes=0;boolean previous=false;
            for(int x=0;x<=500;x++){
                boolean rock=DesertTerrain.aridRock(x/500.0,x,z,37);
                if(rock!=previous)changes++;previous=rock;
            }
            assertEquals(1,changes);
        }
    }
}
