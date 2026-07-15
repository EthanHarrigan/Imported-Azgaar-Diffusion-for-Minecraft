package com.github.xandergos.terraindiffusionmc.pipeline;

/** Supplies the five coarse conditioning channels used by Terrain Diffusion. */
public interface ConditioningMapProvider {
    /** Channels: signed sqrt elevation, mean temperature, temperature seasonality, precipitation, precipitation seasonality. */
    float[][][] sample(int x1, int y1, int x2, int y2);

    /** Per-channel conditioning noise ratios. Lower values adhere more strongly to the source map. */
    default float[] conditioningNoiseRatios() {
        return WorldPipelineModelConfig.conditioningSnr();
    }

    /** Returns an equivalent provider for a different Minecraft world seed. */
    ConditioningMapProvider withSeed(long seed);
}
