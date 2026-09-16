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

    @Test void easyRegisteredBiomesAreReachableFromSimpleClimateBands() {
        assertEquals(BiomeClassifier.BADLANDS, classifyFlat(500, 24, 100));
        assertEquals(BiomeClassifier.MEADOW, classifyFlat(800, 10, 700));
        assertEquals(BiomeClassifier.SAVANNA, classifyFlat(100, 22, 600));
        assertEquals(BiomeClassifier.SNOWY_TAIGA, classifyFlat(100, -1, 4500, 1000));
        assertEquals(BiomeClassifier.FROZEN_PEAKS, classifyPeak(-10));
        assertEquals(BiomeClassifier.STONY_PEAKS, classifyPeak(10));
    }

    @Test void oceanTemperatureAndDepthBandsAreDistinct() {
        assertEquals(BiomeClassifier.WARM_OCEAN,classifyFlat(-100,27,900));
        assertEquals(BiomeClassifier.LUKEWARM_OCEAN,classifyFlat(-100,20,900));
        assertEquals(BiomeClassifier.DEEP_LUKEWARM_OCEAN,classifyFlat(-1400,20,900));
        assertEquals(BiomeClassifier.DEEP_OCEAN,classifyFlat(-1400,11,900));
        assertEquals(BiomeClassifier.DEEP_COLD_OCEAN,classifyFlat(-1400,2,900));
        assertEquals(BiomeClassifier.DEEP_FROZEN_OCEAN,classifyFlat(-1400,-10,900));
    }

    private static short classifyFlat(float elevation, float temp, float precipitation) {
        return classifyFlat(elevation, temp, precipitation, 10);
    }

    private static short classifyFlat(float elevation, float temp, float precipitation, float seasonality) {
        float[] elev = {elevation};
        float[] climate = {temp, seasonality, precipitation, 22};
        float[] padded = new float[9];
        java.util.Arrays.fill(padded, elevation);
        return BiomeClassifier.classify(elev, climate, 0, 0, padded, 1, 1, 1)[0];
    }

    private static short classifyPeak(float temp) {
        int w = 3, h = 3;
        float[] elev = {4000, 4000, 4000, 4000, 4500, 3500, 4000, 3500, 3500};
        float[] climate = new float[4 * w * h];
        for (int i = 0; i < w * h; i++) {
            climate[i] = temp;
            climate[w * h + i] = 10;
            climate[2 * w * h + i] = 1000;
            climate[3 * w * h + i] = 22;
        }
        float[] padded = new float[(w + 2) * (h + 2)];
        for (int y = 0; y < h + 2; y++) for (int x = 0; x < w + 2; x++) {
            int sy = Math.max(0, Math.min(h - 1, y - 1));
            int sx = Math.max(0, Math.min(w - 1, x - 1));
            padded[y * (w + 2) + x] = elev[sy * w + sx];
        }
        return BiomeClassifier.classify(elev, climate, 0, 0, padded, h, w, 500)[4];
    }
}
