package com.github.xandergos.terraindiffusionmc.blueprint;

import com.github.xandergos.terraindiffusionmc.pipeline.ConditioningMapProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.SyntheticMapFactory;
import java.io.IOException;

/** Maps a finite authored globe into coarse coordinates and blends to procedural/polar exteriors. */
public final class BlueprintConditioningProvider implements ConditioningMapProvider {
    private final BlueprintTileStore store;
    private final long seed;
    private final SyntheticMapFactory exterior;

    public BlueprintConditioningProvider(BlueprintTileStore store, long seed) {
        this.store = store;
        this.seed = seed;
        this.exterior = new SyntheticMapFactory(seed);
    }

    @Override
    public float[][][] sample(int x1, int y1, int x2, int y2) {
        int w = x2 - x1, h = y2 - y1;
        float[][][] proc = exterior.sample(x1, y1, x2, y2);
        float[][][] out = new float[5][h][w];
        BlueprintManifest m = store.manifest();
        double bx = m.width() / 2.0, by = m.height() / 2.0;
        double blend = m.edgeBlendKm() / m.coarseKmPerPixel();
        double southLat = m.effectiveSouthClimateLatitudeDeg();
        float southRain = m.effectiveSouthPrecipitationMultiplier();
        boolean warmSouth = southLat > -89.999;
        try {
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    double gx = x1 + c, gy = y1 + r;
                    double px = gx + bx, py = gy + by;
                    boolean vert = py < 0 || py >= m.height();
                    if (vert) {
                        if (gy < 0 || !warmSouth) polar(out, r, c);
                        else warmSouthBoundary(out, r, c, southLat, southRain);
                        continue;
                    }

                    int ix = Math.max(0, Math.min(m.width() - 1, (int) Math.floor(px)));
                    int iy = Math.max(0, Math.min(m.height() - 1, (int) Math.floor(py)));
                    double edge = Math.min(py, m.height() - 1 - py);
                    double polarWeight = smooth(edge / Math.max(1, blend));
                    boolean southEdgeOverride = warmSouth && py >= m.height() - 1 - blend;
                    if (px >= 0 && px < m.width()) {
                        for (int ch = 0; ch < 5; ch++) out[ch][r][c] = store.value(ch, ix, iy);
                        // The warm southern mode intentionally keeps authored terrain/climate at
                        // the south edge instead of blending it into the stock cold polar ocean.
                        if (polarWeight < 1 && !southEdgeOverride) {
                            float[][][] p = new float[5][1][1];
                            polar(p, 0, 0);
                            for (int ch = 0; ch < 5; ch++)
                                out[ch][r][c] = (float) (p[ch][0][0] * (1 - polarWeight)
                                        + out[ch][r][c] * polarWeight);
                        }
                    } else {
                        double dx = px < 0 ? -px : px - (m.width() - 1);
                        double auth = smooth(1 - dx / Math.max(1, blend));
                        for (int ch = 0; ch < 5; ch++) out[ch][r][c] = proc[ch][r][c];
                        if (auth > 0) {
                            int ex = px < 0 ? 0 : m.width() - 1;
                            for (int ch = 0; ch < 5; ch++)
                                out[ch][r][c] = (float) (proc[ch][r][c] * (1 - auth)
                                        + store.value(ch, ex, iy) * auth);
                        }
                        applyLatitudeClimate(out, r, c,
                                PlanetaryClimate.rowLatitude((int) Math.floor(py), m.height()),
                                southLat, southRain);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read compiled terrain blueprint", e);
        }
        return out;
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static void polar(float[][][] o, int r, int c) {
        o[0][r][c] = (float) -Math.sqrt(4000);
        o[1][r][c] = -18;
        o[2][r][c] = 22;
        o[3][r][c] = 220;
        o[4][r][c] = 35;
    }

    private static void warmSouthBoundary(float[][][] o, int r, int c,
                                          double southLat, float rainMultiplier) {
        double lat = PlanetaryClimate.effectiveLatitude(-90, southLat);
        double sl = Math.abs(Math.sin(Math.toRadians(lat)));
        o[0][r][c] = (float) -Math.sqrt(4000);
        o[1][r][c] = (float) (27 - 42 * sl * sl);
        o[2][r][c] = (float) (3 + 18 * sl);
        o[3][r][c] = Math.min(4500, 900 * rainMultiplier);
        o[4][r][c] = (float) (22 + 45 * sl);
    }

    private static void applyLatitudeClimate(float[][][] o, int r, int c, double baseLat,
                                              double southLat, float rainMultiplier) {
        double lat = PlanetaryClimate.effectiveLatitude(baseLat, southLat);
        double s = Math.abs(Math.sin(Math.toRadians(Math.max(-90, Math.min(90, lat)))));
        o[1][r][c] = (float) (o[1][r][c] * .35 + (27 - 42 * s * s) * .65);
        o[2][r][c] = (float) (o[2][r][c] * .4 + (3 + 18 * s) * .6);
        if (southLat > -89.999 && baseLat < southLat)
            o[3][r][c] = Math.min(4500, o[3][r][c] * rainMultiplier);
    }

    @Override
    public float[] conditioningNoiseRatios() {
        BlueprintManifest m = store.manifest();
        return new float[]{m.elevationNoiseRatio(), m.climateNoiseRatio(), m.climateNoiseRatio(),
                m.climateNoiseRatio(), m.climateNoiseRatio()};
    }

    @Override
    public ConditioningMapProvider withSeed(long s) {
        return s == seed ? this : new BlueprintConditioningProvider(store, s);
    }
}
