package com.github.xandergos.terraindiffusionmc.blueprint;

import java.util.Random;

/** Deterministic Earth-inspired latitude, continentality, lapse-rate, and orographic climate approximation. */
public final class PlanetaryClimate {
    private PlanetaryClimate() { }

    /** Preserves the original full-globe climate behavior for stock callers and old tests. */
    public static float[][] generate(float[] elev, int w, int h, long seed) {
        return generate(elev, w, h, seed, -90, 1f);
    }

    /**
     * Generates climate with an optional warm southern boundary. The terrain and geographic
     * latitude labels remain unchanged; only the climate latitude is eased toward the selected
     * southern latitude. A value of -90 disables the override.
     */
    public static float[][] generate(float[] elev, int w, int h, long seed,
                                     double southClimateLatitudeDeg,
                                     float southPrecipitationMultiplier) {
        int n = w * h;
        float[][] out = new float[4][n];
        float[] dist = new float[n];
        for (int i = 0; i < n; i++) dist[i] = elev[i] < 0 ? 0 : Math.max(4, w / 8);
        int max = Math.max(4, w / 8);
        for (int pass = 0; pass < max; pass++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (dist[i] == 0) continue;
                    float v = dist[i];
                    if (x > 0) v = Math.min(v, dist[i - 1] + 1);
                    if (x + 1 < w) v = Math.min(v, dist[i + 1] + 1);
                    if (y > 0) v = Math.min(v, dist[i - w] + 1);
                    if (y + 1 < h) v = Math.min(v, dist[i + w] + 1);
                    dist[i] = v;
                }
            }
        }

        double south = Math.max(-90, Math.min(0, southClimateLatitudeDeg));
        float rainMultiplier = Math.max(.01f, Math.min(10f, southPrecipitationMultiplier));
        Random r = new Random(seed);
        float phase = r.nextFloat() * 1000;

        for (int y = 0; y < h; y++) {
            double baseLat = rowLatitude(y, h);
            double lat = effectiveLatitude(baseLat, south);
            double sl = Math.abs(Math.sin(Math.toRadians(lat)));
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float cont = Math.min(1, dist[i] / Math.max(1f, w / 16f));
                float noise = (float) (Math.sin(x * .071 + phase) + Math.sin(y * .113 - phase)) * .8f;
                out[0][i] = (float) (27 - 42 * sl * sl) - Math.max(0, elev[i]) * .0065f + noise;
                out[1][i] = (float) (3 + 18 * sl + 8 * cont);
            }
        }

        // Advect moisture in latitude-dependent prevailing wind directions; uplift removes moisture as rain.
        float[] moist = new float[n], rain = new float[n];
        for (int i = 0; i < n; i++) moist[i] = elev[i] < 0 ? 1.2f : .08f;
        for (int step = 0; step < 96; step++) {
            // Rebuild the moisture field each step. Carrying the old array forward
            // prevents land from drying and erases rain shadows. Oceans are the
            // moisture source; land retains only the current downwind remainder.
            float[] next = new float[n];
            for (int i = 0; i < n; i++) if (elev[i] < 0) next[i] = 1.2f;
            for (int y = 0; y < h; y++) {
                double baseLat = rowLatitude(y, h);
                double lat = effectiveLatitude(baseLat, south);
                int dx = Math.abs(lat) < 30 ? -1 : Math.abs(lat) < 60 ? 1 : -1;
                int sy = lat > 0 ? 1 : -1;
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (elev[i] < 0) {
                        next[i] = Math.max(next[i], 1.2f);
                        continue;
                    }
                    int ux = Math.floorMod(x - dx, w);
                    // Use a real meridional step. Integer sy/4 was always zero.
                    int uy = Math.max(0, Math.min(h - 1, y - sy));
                    int u = uy * w + ux;
                    float incoming = moist[u] * .985f;
                    float uplift = Math.max(0, elev[i] - elev[u]) / 1400f;
                    float fall = Math.min(incoming, .008f + uplift * .22f);
                    rain[i] += fall;
                    next[i] = Math.max(0, incoming - fall);
                }
            }
            moist = next;
        }

        for (int y = 0; y < h; y++) {
            double baseLat = rowLatitude(y, h);
            double lat = effectiveLatitude(baseLat, south);
            double sl = Math.abs(Math.sin(Math.toRadians(lat)));
            float localRainMultiplier = south > -89.999 && baseLat < south ? rainMultiplier : 1f;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float p = elev[i] < 0 ? 900 : (80 + rain[i] * 1800 + moist[i] * 420);
                out[2][i] = Math.max(30, Math.min(4500, p * localRainMultiplier));
                out[3][i] = (float) Math.max(10, Math.min(100, 22 + 45 * sl
                        + 20 * Math.min(1, dist[i] / Math.max(1f, w / 16f))));
            }
        }
        return out;
    }

    public static double rowLatitude(int y, int h) {
        return 90 - 180 * (y + .5) / h;
    }

    /** Smoothly eases the coldest southern rows toward the requested warm-edge latitude. */
    public static double effectiveLatitude(double baseLatitude, double southClimateLatitudeDeg) {
        double south = Math.max(-90, Math.min(0, southClimateLatitudeDeg));
        if (south <= -89.999 || baseLatitude >= south) return baseLatitude;
        double t = (south - baseLatitude) / (south + 90);
        t = t * t * (3 - 2 * t);
        return baseLatitude + (south - baseLatitude) * t;
    }
}
