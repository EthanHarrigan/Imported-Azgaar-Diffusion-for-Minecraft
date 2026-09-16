package com.github.xandergos.terraindiffusionmc.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
class DesertLandformsTest {
    @Test void regionalBudgetQuietSandAndAllArchetypes(){
        for(long seed:new long[]{42,-4702486086583876136L}){
            int total=0,rocks=0,quiet=0;java.util.Set<Integer> kinds=new java.util.HashSet<>();
            for(int z=-1024;z<1024;z+=8)for(int x=-1024;x<1024;x+=8){
                var a=DesertLandforms.sample(x,z,seed);total++;if(a.rockHeight()>1)rocks++;if(a.duneHeight()<10)quiet++;kinds.add(a.archetype());
                assertTrue(a.offset()>=0&&a.offset()<160);
                assertEquals(a,DesertLandforms.sample(x,z,seed));
            }
            double coverage=rocks/(double)total;
            System.out.println("seed="+seed+" rock="+coverage+" quiet="+quiet/(double)total+" archetypes="+kinds);
            assertTrue(coverage>=.003&&coverage<=.15);assertTrue(quiet>total*.6);assertEquals(4,kinds.size());
        }
    }
    @Test void denseWindowsRemainMostlyOpen(){
        for(int z0=-600;z0<600;z0+=139)for(int x0=-600;x0<600;x0+=151){
            int rock=0,n=0;for(int z=z0;z<z0+512;z+=8)for(int x=x0;x<x0+512;x+=8){n++;if(DesertLandforms.sample(x,z,42).rockHeight()>1)rock++;}
            assertTrue(rock/(double)n<=.50);
        }
    }
    @Test void reverseChunkOrderAndSiteBordersAreStable(){
        double[] forward=new double[1024];for(int x=0;x<1024;x++)forward[x]=DesertLandforms.sample(x-512,256,73).offset();
        for(int x=1023;x>=0;x--)assertEquals(forward[x],DesertLandforms.sample(x-512,256,73).offset());
        for(int x=-1024;x<=1024;x+=512)assertEquals(DesertLandforms.sample(x-.001,100,73).offset(),DesertLandforms.sample(x+.001,100,73).offset(),.01);
    }
    @Test void protectedWaterBiomeAndCoastCannotBeRaised(){
        assertEquals(0,DesertLandforms.protectedGate(BiomeIds.FOREST,false,64,64,0,1,-1700));
        assertEquals(0,DesertLandforms.protectedGate(BiomeIds.DESERT,true,64,64,0,1,-1700));
        assertEquals(0,DesertLandforms.protectedGate(BiomeIds.DESERT,false,64,10,0,1,-1700));
        assertEquals(0,DesertLandforms.protectedGate(BiomeIds.DESERT,false,0,64,0,1,-1700));
        assertEquals(0,DesertLandforms.protectedGate(BiomeIds.DESERT,false,64,64,0,1,HeightConverter.seaLevel()));
    }
    @Test void fieldsHaveClearingsClustersAndNoMandatoryGridSites() throws Exception {
        long seed=330971835197786486L;int empty=0,dense=0;
        var csv=new StringBuilder("x,z,height\n");
        for(int z=19000;z<23096;z+=16)for(int x=52000;x<56096;x+=16){
            var a=DesertLandforms.sample(x,z,seed);
            csv.append(x).append(',').append(z).append(',').append(a.rockHeight()).append('\n');
            double d=DesertLandforms.density(x,z,seed);if(d==0)empty++;if(d>.65)dense++;
        }
        assertTrue(empty>100,"Broad fully empty density regions");assertTrue(dense>100,"Dense rock districts");
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/desert-review"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/desert-review/rocks.csv"),csv);
        assertEquals(0,DesertProvinces.contactWeight(100,3000));
        assertTrue(DesertProvinces.contactWeight(1000,2600)>.9);
    }
    @Test void completedLandformsKeepBlockScaleAndRespectWaterAtEveryWorldScale(){
        var field=new com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.Field(1,.6,0,0,0,1);
        for(int scale=1;scale<=6;scale++)for(int z=-400;z<400;z+=47)for(int x=-400;x<400;x+=43){
            var dry=DesertLandforms.complete(1800,x,z,BiomeIds.DESERT,false,64,64,field,41,scale);
            assertEquals(dry.land().offset(),dry.topY()-dry.baseY(),1+scale/30.0+.00001,"Metre rounding plus block rounding bounds the error");
            var wet=DesertLandforms.complete(1800,x,z,BiomeIds.DESERT,true,64,64,field,41,scale);
            assertEquals(1800,wet.metres());assertEquals(wet.baseY(),wet.topY());
        }
    }
}
