package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BiomeCatalogTest {
    @Test void everyEmittedBiomeHasUniqueDisplayColor() {
        Set<Integer> colors = new HashSet<>();
        for (BiomeCatalog.Entry entry : BiomeCatalog.entries()) {
            assertTrue(colors.add(entry.color()), "duplicate color for " + entry.name());
            assertTrue(entry.hexColor().matches("#[0-9A-F]{6}"));
        }
        assertEquals(54, colors.size());
    }

    @Test void everyClassifierIdHasMetadata() {
        for (short id : BiomeIds.VANILLA_SURFACE) {
            assertTrue(BiomeCatalog.byId(id) != null, "missing metadata for vanilla surface ID " + id);
        }
        for (short id : new short[]{
                BiomeClassifier.PLAINS, BiomeClassifier.SNOWY_PLAINS, BiomeClassifier.DESERT,
                BiomeClassifier.SWAMP, BiomeClassifier.MANGROVE_SWAMP, BiomeClassifier.FOREST,
                BiomeClassifier.TAIGA, BiomeClassifier.SNOWY_TAIGA, BiomeClassifier.SAVANNA,
                BiomeClassifier.WINDSWEPT_HILLS, BiomeClassifier.JUNGLE, BiomeClassifier.BADLANDS,
                BiomeClassifier.MEADOW, BiomeClassifier.GROVE, BiomeClassifier.SNOWY_SLOPES,
                BiomeClassifier.FROZEN_PEAKS, BiomeClassifier.STONY_PEAKS, BiomeClassifier.WARM_OCEAN,
                BiomeClassifier.OCEAN, BiomeClassifier.COLD_OCEAN, BiomeClassifier.FROZEN_OCEAN,
                BiomeClassifier.FOREST_SPARSE, BiomeClassifier.TAIGA_SPARSE,
                BiomeClassifier.SNOWY_TAIGA_SPARSE}) {
            assertTrue(BiomeCatalog.byId(id) != null, "missing metadata for ID " + id);
        }
    }
}
