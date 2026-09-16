package com.github.xandergos.terraindiffusionmc.hydrology;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HydrologyCacheNoticeTest {
    @TempDir Path directory;
    private int older(String prefix,long seed,int scale,int width,int version) {
        return SharedHydrologyCache.olderVersion(directory.resolve("world"),directory.resolve("shared"),
                prefix,"-s"+scale,"map-profile",seed,scale,width,128,version);
    }
    private void checkpoint(Path path)throws Exception {
        Files.createDirectories(path.getParent());Files.write(path,new byte[]{1});
    }
    @Test void matchingPreviousWorldCacheIsDetectedButEmptyAndCurrentCachesAreNot()throws Exception {
        assertEquals(0,older("map-seed-profile",42,5,256,4));
        Files.createDirectories(directory.resolve("world/map-seed-profile-v3-s5"));
        assertEquals(0,older("map-seed-profile",42,5,256,4));
        checkpoint(directory.resolve("world/map-seed-profile-v4-s5/prepared-routing-v1.bin.gz"));
        assertEquals(0,older("map-seed-profile",42,5,256,4));
        checkpoint(directory.resolve("world/map-seed-profile-v3-s5/0_0.bin.gz"));
        assertEquals(3,older("map-seed-profile",42,5,256,4));
        assertEquals(0,older("other-map-seed-profile",42,5,256,4));
        assertEquals(0,older("map-seed-profile",42,6,256,4));
    }
    @Test void newSaveCanRecognizeMatchingSharedCacheWithoutReusingOtherWorldSettings()throws Exception {
        String key=SharedHydrologyCache.cacheKey("map-profile",42,5,256,128,3);
        checkpoint(directory.resolve("shared").resolve(key).resolve("prepared-routing-v1.bin.gz"));
        assertEquals(3,older("new-world",42,5,256,4));
        assertEquals(0,older("new-world",43,5,256,4));
        assertEquals(0,older("new-world",42,6,256,4));
        assertEquals(0,older("new-world",42,5,512,4));
        assertEquals(0,older("new-world",42,5,256,3));
        assertFalse(Files.exists(directory.resolve("world")),"Detection must not create any cache directories");
    }
    @Test void incompleteCopiesAndEmptyCheckpointsDoNotTriggerNotice()throws Exception {
        var old=directory.resolve("world/map-seed-profile-v3-s5");Files.createDirectories(old);
        Files.write(old.resolve("0_0.bin.gz.shared.tmp"),new byte[]{1});
        Files.write(old.resolve("preview_0_0.bin.gz"),new byte[0]);
        assertEquals(0,older("map-seed-profile",42,5,256,4));
    }
}
