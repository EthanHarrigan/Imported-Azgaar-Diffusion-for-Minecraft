package com.github.xandergos.terraindiffusionmc.blueprint;

public record BlueprintCompileOptions(double physicalWidthKm, float elevationNoiseRatio,
                                      float climateNoiseRatio, double edgeBlendKm,
                                      double southClimateLatitudeDeg,
                                      float southPrecipitationMultiplier) {
    public static final double DEFAULT_WIDTH_KM = 40_075.0;
    public static final double COARSE_KM_PER_PIXEL = 7.68;

    public BlueprintCompileOptions {
        if (!(physicalWidthKm >= 100 && physicalWidthKm <= 200_000)) throw new IllegalArgumentException("Physical width must be 100-200,000 km");
        if (!(elevationNoiseRatio > 0 && elevationNoiseRatio <= 8)) throw new IllegalArgumentException("Elevation noise ratio must be >0 and <=8");
        if (!(climateNoiseRatio > 0 && climateNoiseRatio <= 8)) throw new IllegalArgumentException("Climate noise ratio must be >0 and <=8");
        if (!(edgeBlendKm >= 0 && edgeBlendKm <= physicalWidthKm / 2)) throw new IllegalArgumentException("Edge blend distance is invalid");
        if (!(southClimateLatitudeDeg >= -90 && southClimateLatitudeDeg <= 0)) throw new IllegalArgumentException("Southern climate latitude must be between -90 and 0 degrees");
        if (!(southPrecipitationMultiplier > 0 && southPrecipitationMultiplier <= 10)) throw new IllegalArgumentException("Southern precipitation multiplier must be >0 and <=10");
    }

    public static BlueprintCompileOptions defaults() { return new BlueprintCompileOptions(DEFAULT_WIDTH_KM, .5f, .2f, 250, -20, 2f); }
}
