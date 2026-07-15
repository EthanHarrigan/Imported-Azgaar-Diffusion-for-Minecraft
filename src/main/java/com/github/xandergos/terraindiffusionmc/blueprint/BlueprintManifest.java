package com.github.xandergos.terraindiffusionmc.blueprint;

public record BlueprintManifest(
        int version, int width, int height, int tileSize, double physicalWidthKm,
        double coarseKmPerPixel, String sourceName, String sourceSha256, String dataSha256,
        float elevationNoiseRatio, float climateNoiseRatio, double edgeBlendKm,
        double southClimateLatitudeDeg, float southPrecipitationMultiplier,
        int climateAlgorithmVersion) {
    public static final int FORMAT_VERSION = 1;
    public static final int CLIMATE_VERSION = 3;

    /** Defaults used when loading a pre-v2 manifest that has no warm-south settings. */
    public double effectiveSouthClimateLatitudeDeg() {
        return climateAlgorithmVersion >= 2 ? southClimateLatitudeDeg : -90;
    }

    public float effectiveSouthPrecipitationMultiplier() {
        return climateAlgorithmVersion >= 2 ? southPrecipitationMultiplier : 1f;
    }
}
