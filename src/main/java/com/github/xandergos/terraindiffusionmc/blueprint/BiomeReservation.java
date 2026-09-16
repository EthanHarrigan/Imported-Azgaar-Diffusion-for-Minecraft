package com.github.xandergos.terraindiffusionmc.blueprint;

/** A deterministic, compile-time biome coverage site in normalized blueprint coordinates. */
public record BiomeReservation(short biomeId, double normalizedX, double normalizedY,
                               int radiusNativeBlocks) {
}
