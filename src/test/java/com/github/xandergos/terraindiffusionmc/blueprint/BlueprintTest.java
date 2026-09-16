package com.github.xandergos.terraindiffusionmc.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

final class BlueprintTest {
    @TempDir Path temp;
    @Test void regionalCoordinatesDoNotCompressMapHorizontally()throws Exception{
        Path json=temp.resolve("regional.json");
        Files.writeString(json,"""
            {"info":{"width":2560,"height":1317},"settings":{"heightExponent":2},
             "mapCoordinates":{"lonT":213,"latT":109.6},
             "grid":{"vertices":{"p":[[0,0],[2560,0],[2560,1317],[0,1317]]},
                     "cells":{"v":[[0,1,2,3]],"h":[30]}}}
            """);
        var m=AzgaarBlueprintCompiler.compile(json,temp.resolve("proportional"),BlueprintCompileOptions.defaults()).manifest();
        int content=m.contentMaxX()-m.contentMinX()+1;
        assertEquals(95,content);assertEquals(98,m.width());assertEquals(49,m.height());
        assertEquals(2560.0/1317,content/(double)m.height(),.015);
    }

    @Test void azgaarElevationCurveMatchesResearchConverter(){
        assertEquals(4f,AzgaarBlueprintCompiler.convertAzgaarHeight(20,2),1e-5);
        assertEquals(144f,AzgaarBlueprintCompiler.convertAzgaarHeight(30,2),1e-5);
        assertEquals(-4000f,AzgaarBlueprintCompiler.convertAzgaarHeight(0,2),1e-4);
    }

    @Test void climateIsDeterministicLatitudinalAndUsesLapseRate(){
        int w=64,h=32;float[] low=new float[w*h];java.util.Arrays.fill(low,100);float[][] a=PlanetaryClimate.generate(low,w,h,42),b=PlanetaryClimate.generate(low,w,h,42);
        assertArrayEquals(a[0],b[0]);
        assertTrue(a[0][(h/2)*w+w/2]>a[0][w/2]);
        float[] high=low.clone();high[(h/2)*w+w/2]=2100;float[][] c=PlanetaryClimate.generate(high,w,h,42);
        assertTrue(c[0][(h/2)*w+w/2]<a[0][(h/2)*w+w/2]-10);
    }

    @Test void warmSouthernClimateOverrideLeavesTerrainIndependent(){
        int w=32,h=16;float[] elevation=new float[w*h];java.util.Arrays.fill(elevation,100);
        float[][] normal=PlanetaryClimate.generate(elevation,w,h,7);
        float[][] warm=PlanetaryClimate.generate(elevation,w,h,7,-20,2f);
        int south=(h-1)*w+w/2;
        assertTrue(warm[0][south]>normal[0][south]+20,"southern override should warm the edge climate");
        assertTrue(warm[2][south]>=normal[2][south]*1.9f,"southern override should increase precipitation");
        assertEquals(normal[2][w/2],warm[2][w/2],1e-4,"southern precipitation must not change the north edge");
        assertEquals(-20,PlanetaryClimate.effectiveLatitude(-90,-20),1e-6);
        assertEquals(-90,PlanetaryClimate.effectiveLatitude(-90,-90),1e-6);
    }

    @Test void orographicUpliftCreatesARecognizableRainShadow(){
        int w=96,h=48;float[] e=new float[w*h];java.util.Arrays.fill(e,100);
        for(int y=0;y<h;y++){for(int x=44;x<=48;x++)e[y*w+x]=2500;for(int x=72;x<w;x++)e[y*w+x]=-100;}
        float[][] c=PlanetaryClimate.generate(e,w,h,9);
        int y=h/2;assertTrue(c[2][y*w+48]>c[2][y*w+40],"mountain windward precipitation should exceed leeward lowland");
        float driest=Float.MAX_VALUE;
        for(int i=0;i<e.length;i++) if(e[i]>=0) driest=Math.min(driest,c[2][i]);
        assertTrue(driest<350,"corrected advection should produce genuinely dry land somewhere in the shadow");
    }

    @Test void equirectangularCoordinatesUseNegativeZAsNorthAndExtendLongitude(){
        var north=WorldBlueprintManager.coordinatesFor(0,-1280,10,5,2);
        assertEquals(90,north.latitude(),1e-6);assertTrue(north.authored());
        var east=WorldBlueprintManager.coordinatesFor(5120,0,10,5,2);
        assertEquals(360,east.longitude(),1e-6);assertFalse(east.authored());
    }

    @Test void fullJsonCompilesToReloadableTiles() throws Exception {
        Path json=temp.resolve("map.json");
        Files.writeString(json,"""
          {"info":{"width":200,"height":100},"settings":{"heightExponent":2},"mapCoordinates":{},
           "grid":{"vertices":{"p":[[0,0],[200,0],[200,100],[0,100]]},
                   "cells":{"v":[[0,1,2,3]],"h":[30]}}}
          """);
        CompiledBlueprint c=AzgaarBlueprintCompiler.compile(json,temp.resolve("compiled"),new BlueprintCompileOptions(100,.5f,.2f,10,-20,2f),10);
        assertEquals(13,c.manifest().effectiveBiomeClassifierVersion());
        assertEquals(16,c.manifest().width());assertEquals(8,c.manifest().height());
        BlueprintTileStore store=new BlueprintTileStore(c.directory());
        assertEquals(12f,store.value(0,4,4),1e-5); // signed sqrt of 144 m
        assertEquals(c.manifest().dataSha256(),store.manifest().dataSha256());
    }

    @Test void newerAzgaarArrayRecordSchemaCompiles() throws Exception {
        Path json=temp.resolve("array-record-map.json");
        Files.writeString(json,"""
          {"info":{"width":200,"height":100},"settings":{"heightExponent":2},"mapCoordinates":{},
           "grid":{"vertices":[{"i":0,"p":[0,0]},{"i":1,"p":[200,0]},{"i":2,"p":[200,100]},{"i":3,"p":[0,100]}],
                   "cells":[{"i":0,"v":[0,1,2,3],"h":30}]}}
          """);
        CompiledBlueprint c=AzgaarBlueprintCompiler.compile(json,temp.resolve("array-compiled"),new BlueprintCompileOptions(100,.5f,.2f,10,-20,2f),10);
        assertEquals(12f,new BlueprintTileStore(c.directory()).value(0,4,4),1e-5);
    }

    @Test void longitudeSpanAddsOceanPaddingAndOnlyPlansFeasibleSurfaceBiomes() throws Exception {
        Path json=temp.resolve("partial-longitude.json");
        Files.writeString(json,"""
          {"info":{"width":194,"height":100},"settings":{"heightExponent":2},
           "mapCoordinates":{"lonT":349.9},
           "grid":{"vertices":[{"i":0,"p":[0,0]},{"i":1,"p":[194,0]},{"i":2,"p":[194,100]},{"i":3,"p":[0,100]}],
                   "cells":[{"i":0,"v":[0,1,2,3],"h":30,"temp":15,"prec":10,"biome":4}]}}
          """);
        CompiledBlueprint c=AzgaarBlueprintCompiler.compile(json,temp.resolve("partial"),
                new BlueprintCompileOptions(768,.5f,.2f,10,-20,2),7.68);
        assertEquals(100,c.manifest().width());
        assertTrue(c.manifest().effectiveContentMinX()>0);
        assertTrue(c.manifest().effectiveBiomeReservations().size()>30);
        assertTrue(c.manifest().effectiveBiomeReservations().stream().noneMatch(r->r.biomeId()==com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.JAGGED_PEAKS),"a flat 144 m map must not invent a fake mountain reservation");
        BlueprintTileStore store=new BlueprintTileStore(c.directory());
        assertTrue(store.value(0,0,25)<0,"longitude padding must be ocean");
        assertEquals(4,Math.round(store.value(7,50,25)),"authored biome anchor must survive compilation");
    }

    @Test void explicitlyNamedCustomMinecraftBiomeBecomesStrongAnchor() throws Exception {
        Path json=temp.resolve("custom-biome.json");
        Files.writeString(json,"""
          {"info":{"width":200,"height":100},"settings":{"heightExponent":2},"mapCoordinates":{},
           "biomesData":{"i":[13],"name":["Mushroom Fields"]},
           "grid":{"vertices":[{"i":0,"p":[0,0]},{"i":1,"p":[200,0]},{"i":2,"p":[200,100]},{"i":3,"p":[0,100]}],
                   "cells":[{"i":0,"v":[0,1,2,3],"h":30,"temp":15,"prec":20,"biome":13}]}}
          """);
        CompiledBlueprint c=AzgaarBlueprintCompiler.compile(json,temp.resolve("custom"),
                new BlueprintCompileOptions(100,.5f,.2f,10,-20,2),10);
        assertEquals(1000+com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.MUSHROOM_FIELDS,
                Math.round(new BlueprintTileStore(c.directory()).value(7,8,4)));
    }

    @Test void missingFieldsAreRejected() throws Exception {
        Path json=temp.resolve("bad.json");Files.writeString(json,"{}");
        IOExceptionLike e=assertThrows(IOExceptionLike.class,()->{try{AzgaarBlueprintCompiler.compile(json,temp.resolve("bad"),BlueprintCompileOptions.defaults(),1000);}catch(java.io.IOException x){throw new IOExceptionLike(x);}});
        assertTrue(e.getCause().getMessage().contains("Missing required Azgaar object"));
    }
    @Test void detailReloadAndIntegrityCheck() throws Exception {
        Path json=temp.resolve("detail.json");
        Files.writeString(json,"""
          {"info":{"width":200,"height":100},"settings":{"heightExponent":2},"mapCoordinates":{},
           "grid":{"vertices":{"p":[[0,0],[200,0],[200,100],[0,100]]},"cells":{"v":[[0,1,2,3]],"h":[30],"biome":[4]}}}
          """);
        var compiled=AzgaarBlueprintCompiler.compile(json,temp.resolve("detail"),BlueprintCompileOptions.defaults(),1000);
        var detail=BlueprintDetailMap.read(compiled.directory().resolve("ecology.bin.gz"));
        assertEquals(144,detail.sample(0,.5,.5));assertEquals(4,detail.nearest(1,.5,.5));
        assertTrue(detail.width>compiled.manifest().width());
        Files.write(compiled.directory().resolve("ecology.bin.gz"),new byte[]{1,2,3});
        assertThrows(java.io.IOException.class,()->new BlueprintTileStore(compiled.directory()));
    }
    @Test void temperatureSeasonalityHasBioclimUnits(){
        float[] e=new float[32*16];java.util.Arrays.fill(e,100);
        float[][] c=PlanetaryClimate.generate(e,32,16,42);
        assertTrue(c[1][0]>1000&&c[1][0]<4000);
    }
    @Test void compilerNeverDeletesExistingOutputData() throws Exception {
        Path output=Files.createDirectory(temp.resolve("existing"));
        Path marker=output.resolve("important.txt");Files.writeString(marker,"preserve");
        var error=assertThrows(java.io.IOException.class,()->AzgaarBlueprintCompiler.compile(temp.resolve("unused.json"),output,BlueprintCompileOptions.defaults()));
        assertTrue(error.getMessage().contains("overwrite"));assertEquals("preserve",Files.readString(marker));
    }
    @Test void nearSeaLevelFreshwaterLakeIsNotAnOceanDepth(){
        assertTrue(AzgaarBlueprintCompiler.convertAzgaarHeight(19.9f,2)<0);
        assertEquals(3.61f,AzgaarBlueprintCompiler.convertAzgaarLakeHeight(19.9f,2),.001f);
        assertEquals(AzgaarBlueprintCompiler.convertAzgaarHeight(52.9f,2),AzgaarBlueprintCompiler.convertAzgaarLakeHeight(52.9f,2));
    }
    @Test void coverageCandidatesDoNotPutGreenForestOnFrozenSummits(){
        assertFalse(BiomeCoveragePlanner.generatedSiteAllowed(com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.WINDSWEPT_FOREST,6600,-15));
        assertFalse(BiomeCoveragePlanner.generatedSiteAllowed(com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.TAIGA,3750,4));
        assertTrue(BiomeCoveragePlanner.generatedSiteAllowed(com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.TAIGA,1500,4));
        assertTrue(BiomeCoveragePlanner.generatedSiteAllowed(com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.FROZEN_PEAKS,6000,-15));
    }
    private static final class IOExceptionLike extends RuntimeException{IOExceptionLike(Throwable t){super(t);}}
}
