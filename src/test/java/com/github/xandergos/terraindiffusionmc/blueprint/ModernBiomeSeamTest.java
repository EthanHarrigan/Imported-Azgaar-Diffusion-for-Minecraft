package com.github.xandergos.terraindiffusionmc.blueprint;

import com.github.xandergos.terraindiffusionmc.pipeline.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the new coordinate-based mangrove/ecotone path, not only the legacy array-border pass. */
class ModernBiomeSeamTest {
    @TempDir Path temp;
    @Test void forestSwampEcotoneIsIndependentOfRequestedTileBounds()throws Exception{
        Path json=temp.resolve("forest-swamp.json");
        Files.writeString(json,"""
          {"info":{"width":200,"height":100},"settings":{"heightExponent":2},"mapCoordinates":{},
           "grid":{"vertices":{"p":[[0,0],[100,0],[100,100],[0,100],[200,0],[200,100]]},
                   "cells":{"v":[[0,1,2,3],[1,4,5,2]],"h":[30,30],"biome":[6,12]}}}
          """);
        var compiled=AzgaarBlueprintCompiler.compile(json,temp.resolve("compiled"),new BlueprintCompileOptions(122.88,.35f,.2f,10,-20,2));
        Map<Field,Object> previous=new LinkedHashMap<>();
        try{
            replace(previous,WorldBlueprintManager.class,"store",new BlueprintTileStore(compiled.directory()));
            replace(previous,WorldBlueprintManager.class,"detail",BlueprintDetailMap.read(compiled.directory().resolve("ecology.bin.gz")));
            replace(previous,WorldBlueprintManager.class,"finalReservations",List.of());
            Field seed=BiomeClassifier.class.getDeclaredField("contextSeed");seed.setAccessible(true);previous.put(seed,seed.get(null));
            BiomeClassifier.configure(42,compiled.manifest().generationFingerprint());
            // The manifest version participates in the noise seed; cover a region, not
            // one narrow strip that can legitimately miss every patch in a new version.
            int w=2048,h=256,x0=-1024,z0=-128;
            short[] full=flat(z0,x0,h,w);
            Set<Short> present=new HashSet<>();for(short id:full)present.add(id);
            assertTrue(present.contains(BiomeIds.MANGROVE_SWAMP),"Irregular warm swamp/forest border must contain mangroves");
            assertTrue(present.contains(BiomeIds.SWAMP),"The whole swamp must not become mangrove");
            for(int z=h-32;z>=0;z-=32)for(int x=w-32;x>=0;x-=32){
                short[] tile=flat(z0+z,x0+x,32,32);
                for(int r=0;r<32;r++)for(int c=0;c<32;c++)assertEquals(full[(z+r)*w+x+c],tile[r*32+c],"Biome seam at "+(x0+x+c)+","+(z0+z+r));
            }
        }finally{for(var entry:previous.entrySet())entry.getKey().set(null,entry.getValue());}
    }
    private static void replace(Map<Field,Object> previous,Class<?> owner,String name,Object value)throws Exception{
        Field field=owner.getDeclaredField(name);field.setAccessible(true);previous.put(field,field.get(null));field.set(null,value);
    }
    private static short[] flat(int z,int x,int h,int w){
        int n=w*h;float[] e=new float[n],climate=new float[4*n],padded=new float[(w+2)*(h+2)];Arrays.fill(e,144);Arrays.fill(padded,144);
        Arrays.fill(climate,0,n,17);Arrays.fill(climate,n,2*n,1000);Arrays.fill(climate,2*n,3*n,1700);Arrays.fill(climate,3*n,4*n,22);
        return BiomeClassifier.classify(e,climate,z,x,padded,h,w,10);
    }
}
