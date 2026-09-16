package com.github.xandergos.terraindiffusionmc.world;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MountainZonesTest {
    @Test void climateLinesFollowClimateRatherThanWorldScale(){
        var warm=MountainZones.bands(3000,5);var cold=MountainZones.bands(3000,-10);
        assertTrue(warm.snowLine()>cold.snowLine());assertTrue(warm.snowLine()>warm.treeLine()+600);
        assertEquals(MountainZones.biome(BiomeIds.SNOWY_SLOPES,3000,5,1000,.1f,30,900,600,3,42),
                MountainZones.biome(BiomeIds.SNOWY_SLOPES,3000,5,1000,.1f,30,1200,800,4,42));
    }
    @Test void mountainBenchesSupportLifeButSummitsRemainAlpine(){
        short bench=MountainZones.biome(BiomeIds.SNOWY_SLOPES,2300,8,1000,.1f,10,0,0,3,42);
        assertTrue(bench==BiomeIds.TAIGA||bench==BiomeIds.TAIGA_SPARSE||bench==BiomeIds.GROVE||bench==BiomeIds.MEADOW);
        short summit=MountainZones.biome(BiomeIds.SNOWY_SLOPES,7500,-30,1200,.9f,0,0,0,3,42);
        assertEquals(BiomeIds.JAGGED_PEAKS,summit);
        assertEquals(BiomeIds.DESERT,MountainZones.biome(BiomeIds.DESERT,400,25,100,.1f,0,0,0,3,42));
    }
}
