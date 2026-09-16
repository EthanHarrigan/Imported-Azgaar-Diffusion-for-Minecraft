package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TerrainDiffusionMicroDecoratorTest {
    @Test void plansAreDeterministicAndBiomeAware(){
        var a=TerrainDiffusionMicroDecorator.plan(42,17,-9,BiomeIds.PLAINS);
        assertEquals(a,TerrainDiffusionMicroDecorator.plan(42,17,-9,BiomeIds.PLAINS));
        var ocean=TerrainDiffusionMicroDecorator.plan(42,17,-9,BiomeIds.DEEP_OCEAN);
        assertEquals(0,ocean.coverClusters());assertFalse(ocean.flowers());assertFalse(ocean.tree());assertFalse(ocean.rock());assertFalse(ocean.hummock());
    }
    @Test void additionsRemainSparseAcrossManyChunks(){
        int trees=0,rocks=0,hummocks=0,flowers=0;
        for(int z=0;z<100;z++)for(int x=0;x<100;x++){
            var p=TerrainDiffusionMicroDecorator.plan(987654321L,x,z,BiomeIds.PLAINS);
            trees+=p.tree()?1:0;rocks+=p.rock()?1:0;hummocks+=p.hummock()?1:0;flowers+=p.flowers()?1:0;
            assertTrue(p.coverClusters()>=1&&p.coverClusters()<=3);
        }
        assertTrue(trees>500&&trees<1000);assertTrue(rocks>500&&rocks<1000);
        assertTrue(hummocks>250&&hummocks<650);assertTrue(flowers>1200&&flowers<2000);
    }
}
