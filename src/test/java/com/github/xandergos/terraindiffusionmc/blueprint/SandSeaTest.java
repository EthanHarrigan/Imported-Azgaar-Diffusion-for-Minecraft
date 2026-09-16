package com.github.xandergos.terraindiffusionmc.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SandSeaTest {
    @TempDir Path temp;
    @Test void profileFeathersOnlyInwardAndSurvivesReload()throws Exception{
        int w=128,h=64;float[][] f=new float[5][w*h];Arrays.fill(f[0],600);Arrays.fill(f[3],-1);
        for(int z=0;z<h;z++)for(int x=0;x<w/2;x++){f[1][z*w+x]=1;f[4][z*w+x]=1;}
        // A protected non-desert enclave and a lake within the authored region.
        for(int z=25;z<35;z++)for(int x=15;x<25;x++)f[4][z*w+x]=0;
        f[3][40*w+30]=700;
        var d=new BlueprintDetailMap(w,h,f);d.write(temp.resolve("ecology.gz"));var copy=BlueprintDetailMap.read(temp.resolve("ecology.gz"));
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            double u=(x+.5)/w,v=(z+.5)/h;float a=d.sandSeaWeight(u,v,25088);
            assertEquals(a,copy.sandSeaWeight(u,v,25088));assertTrue(a>=0&&a<=1);
            if(f[4][z*w+x]==0||f[3][z*w+x]>=0)assertEquals(0,a);
        }
        assertEquals(1,d.sandSeaWeight(.2,.2,25088),1e-5);
        assertEquals(0,d.sandSeaWeight(.8,.2,25088));
        float[][] old=Arrays.copyOf(f,4);var legacy=new BlueprintDetailMap(w,h,old);legacy.write(temp.resolve("old.gz"));
        assertEquals(0,BlueprintDetailMap.read(temp.resolve("old.gz")).sandSeaWeight(.2,.2,25088));
    }
    @Test void finalHeightCapIncludesTheBaseAtEveryScale(){
        for(int scale=1;scale<=6;scale++){
            double last=0;for(int height=0;height<60000;height+=11){
                double capped=ExceptionalSummit.ceilingCurve(height,scale);
                assertTrue(capped>=last);assertTrue(com.github.xandergos.terraindiffusionmc.world.VerticalProfile.SEA_LEVEL+capped*scale/30<=com.github.xandergos.terraindiffusionmc.world.VerticalProfile.TOP_Y);last=capped;
            }
            var s=List.of(new ExceptionalSummit(.5,.5,2500,10000));
            float prev=ExceptionalSummit.shapedHeight(s,8000,0,0,25088,12544,scale,3);
            assertTrue(com.github.xandergos.terraindiffusionmc.world.VerticalProfile.SEA_LEVEL+prev*scale/30<=com.github.xandergos.terraindiffusionmc.world.VerticalProfile.TOP_Y);
            assertEquals(ExceptionalSummit.ceilingCurve(8000,scale),ExceptionalSummit.shapedHeight(s,8000,3000,0,25088,12544,scale,3),.002);
        }
    }
    @Test void customSandSeaImportsAsSoftDesertNotAnUnknownBiome()throws Exception{
        Path json=temp.resolve("map.json");
        Files.writeString(json,"""
          {"info":{"width":200,"height":100},"settings":{"heightExponent":2},"mapCoordinates":{},
           "grid":{"vertices":{"p":[[0,0],[200,0],[200,100],[0,100]]},"cells":{"v":[[0,1,2,3]],"h":[30],"biome":[13]}},
           "biomesData":{"name":["Marine","Desert","ColdArid","Savanna","Plains","a","b","c","d","e","f","g","h","Sand Sea"]},
           "terrainDiffusion":{"biomeProfiles":[{"sourceName":"Sand Sea","minecraftBiome":"minecraft:desert","archetype":"sand_sea"}]}}
          """);
        var c=AzgaarBlueprintCompiler.compile(json,temp.resolve("out"),new BlueprintCompileOptions(100,.35f,.2f,10,-20,2),10);
        assertEquals(1,new BlueprintTileStore(c.directory()).value(7,8,4));
        var d=BlueprintDetailMap.read(c.directory().resolve("ecology.bin.gz"));assertEquals(1,d.nearest(1,.5,.5));assertEquals(1,d.sandSeaWeight(.5,.5,4096));
        var root=com.google.gson.JsonParser.parseString(Files.readString(json)).getAsJsonObject();
        root.remove("biomesData");root.add("pack",com.google.gson.JsonParser.parseString("{\"biomes\":[{\"i\":13,\"name\":\"Sand Sea\"}]}"));
        Files.writeString(json,root.toString());
        var newer=AzgaarBlueprintCompiler.compile(json,temp.resolve("newer"),new BlueprintCompileOptions(100,.35f,.2f,10,-20,2),10);
        assertEquals(1,new BlueprintTileStore(newer.directory()).value(7,8,4));
    }
}
