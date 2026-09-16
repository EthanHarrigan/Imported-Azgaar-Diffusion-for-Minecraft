package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldInitializationTest {
    @Test void earlyRequestCannotCreateSeedZeroProvider(){
        LocalTerrainProvider.beginWorldLoad();
        var error=assertThrows(IllegalStateException.class,LocalTerrainProvider::getInstance);
        assertTrue(error.getMessage().contains("seed-zero fallback"));
    }
}
