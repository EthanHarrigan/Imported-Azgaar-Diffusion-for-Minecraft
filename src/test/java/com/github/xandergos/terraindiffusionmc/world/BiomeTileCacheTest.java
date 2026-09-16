package com.github.xandergos.terraindiffusionmc.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BiomeTileCacheTest {
    @TempDir Path temp;
    @Test void reloadUsesExactSamplesWithoutRunningGenerator(){
        short[] expected={1,2,3,4};
        new BiomeTileCache(temp,2).get(-3,4,()->expected);
        assertArrayEquals(expected,new BiomeTileCache(temp,2).get(-3,4,()->{throw new AssertionError("Inference must not run on a disk hit");}));
    }
    @Test void separateWorldSettingsAndTileCoordinatesDoNotShareSamples(){
        new BiomeTileCache(temp.resolve("seed-one-scale-three"),1).get(-1,2,()->new short[]{8});
        BiomeTileCache other=new BiomeTileCache(temp.resolve("seed-two-scale-three"),1);
        assertArrayEquals(new short[]{9},other.get(-1,2,()->new short[]{9}));
        assertArrayEquals(new short[]{10},other.get(2,-1,()->new short[]{10}));
    }
    @Test void corruptCacheRecomputesAndWritesUsableReplacement()throws Exception{
        new BiomeTileCache(temp,1).get(1,2,()->new short[]{8});
        Files.write(temp.resolve("1_2.bin.gz"),new byte[]{0,1,2});
        assertArrayEquals(new short[]{9},new BiomeTileCache(temp,1).get(1,2,()->new short[]{9}));
        assertArrayEquals(new short[]{9},new BiomeTileCache(temp,1).get(1,2,()->{throw new AssertionError();}));
    }
    @Test void concurrentRequestsComputeOnceAndProgressClearsOnFailure()throws Exception{
        BiomeTileCache cache=new BiomeTileCache(temp,1);AtomicInteger calls=new AtomicInteger();
        try(ExecutorService pool=Executors.newFixedThreadPool(8)){
            var tasks=new java.util.ArrayList<Future<short[]>>();
            for(int i=0;i<16;i++)tasks.add(pool.submit(()->cache.get(0,0,()->{calls.incrementAndGet();return new short[]{7};})));
            for(var task:tasks)assertArrayEquals(new short[]{7},task.get());
        }
        assertEquals(1,calls.get());BiomeTileCache.activate(cache);
        try{
            assertThrows(IllegalStateException.class,()->cache.get(9,9,()->{
                assertFalse(BiomeTileCache.progress().isEmpty());throw new IllegalStateException("test");
            }));
            assertTrue(BiomeTileCache.progress().isEmpty());
        }finally{BiomeTileCache.activate(null);}
    }
}
