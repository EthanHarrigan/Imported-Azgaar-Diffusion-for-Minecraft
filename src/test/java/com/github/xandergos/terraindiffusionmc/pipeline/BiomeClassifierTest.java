package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class BiomeClassifierTest {
    @Test void mangroveBorderIsFuzzyDeterministicAndDoesNotSpreadIntoForest() {
        int w = 96, h = 8;
        short[] first = new short[w * h];
        java.util.Arrays.fill(first, BiomeClassifier.SWAMP);
        for (int x = 0; x < w; x++) first[x] = BiomeClassifier.FOREST;
        short[] second = first.clone();

        BiomeClassifier.applyMangroveBorder(first, 120, -45, h, w);
        BiomeClassifier.applyMangroveBorder(second, 120, -45, h, w);

        assertArrayEquals(first, second, "the same world coordinates must produce the same border");
        for (int x = 0; x < w; x++) assertEquals(BiomeClassifier.FOREST, first[x]);
        int mangroves = 0;
        for (short biome : first) if (biome == BiomeClassifier.MANGROVE_SWAMP) mangroves++;
        assertTrue(mangroves > 0 && mangroves < w * 2,
                "the transition should affect some, but not all, nearby swamp cells");
    }

    @Test void inlandSwampIsUnchanged() {
        int w = 12, h = 12;
        short[] biomes = new short[w * h];
        java.util.Arrays.fill(biomes, BiomeClassifier.SWAMP);
        BiomeClassifier.applyMangroveBorder(biomes, 0, 0, h, w);
        for (short biome : biomes) assertNotEquals(BiomeClassifier.MANGROVE_SWAMP, biome);
    }
}
