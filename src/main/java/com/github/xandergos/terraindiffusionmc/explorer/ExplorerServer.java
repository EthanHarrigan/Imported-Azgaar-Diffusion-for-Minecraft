package com.github.xandergos.terraindiffusionmc.explorer;

import com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig;
import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeCatalog;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeClassifier;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipelineModelConfig;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Embedded terrain explorer HTTP server. Java port of
 * terrain_diffusion/inference/explorer/server.py.
 *
 * <p>Bound to 127.0.0.1 only. All pipeline calls are routed through
 * LocalTerrainProvider's inference thread for thread safety.
 */
public final class ExplorerServer {

    private static final Logger LOG = LoggerFactory.getLogger(ExplorerServer.class);
    private static final Gson GSON = new Gson();

    private static final String[] CHANNEL_NAMES = {"Elev", "p5", "Temp", "T std", "Precip", "Precip CV"};
    private static final float NATIVE_RESOLUTION = WorldPipelineModelConfig.nativeResolution();

    private static volatile HttpServer SERVER;
    private static volatile int SERVER_PORT = -1;
    private static final int MAX_BIOME_GRID_CACHE = 32;
    private static final Map<String, short[]> BIOME_GRID_CACHE = new LinkedHashMap<>(MAX_BIOME_GRID_CACHE, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, short[]> eldest) {
            return size() > MAX_BIOME_GRID_CACHE;
        }
    };

    private ExplorerServer() {}

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Start the server if not already running. Returns the port.
     */
    public static synchronized int startIfNotRunning() throws IOException {
        if (SERVER != null) return SERVER_PORT;
        int port = TerrainDiffusionConfig.explorerPort();
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);
        HttpServer server = HttpServer.create(addr, 0);
        server.createContext("/", ExplorerServer::handleRoot);
        server.createContext("/api/status", ExplorerServer::handleStatus);
        server.createContext("/api/seed", ExplorerServer::handleSeed);
        server.createContext("/api/new_seed", ExplorerServer::handleNewSeed);
        server.createContext("/api/coarse.png", ExplorerServer::handleCoarsePng);
        server.createContext("/api/coarse_biomes.png", ExplorerServer::handleCoarseBiomesPng);
        server.createContext("/api/coarse_data.json", ExplorerServer::handleCoarseData);
        server.createContext("/api/biome_data.json", ExplorerServer::handleBiomeData);
        server.createContext("/api/coarse_stats", ExplorerServer::handleCoarseStats);
        server.createContext("/api/detail.png", ExplorerServer::handleDetailPng);
        server.createContext("/api/detail_raw", ExplorerServer::handleDetailRaw);
        // Single-thread executor matches Python's threaded=False
        server.setExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "terrain-explorer-http");
            t.setDaemon(true);
            return t;
        }));
        server.start();
        SERVER = server;
        SERVER_PORT = port;
        LOG.info("Terrain explorer started at http://127.0.0.1:{}", port);
        return port;
    }

    public static synchronized void stop() {
        if (SERVER != null) {
            SERVER.stop(0);
            SERVER = null;
            SERVER_PORT = -1;
            LOG.info("Terrain explorer stopped.");
        }
    }

    public static boolean isRunning() {
        return SERVER != null;
    }

    public static int getPort() {
        return SERVER_PORT;
    }

    // =========================================================================
    // Handlers — direct port of server.py routes
    // =========================================================================

    /** GET / → serve index.html */
    private static void handleRoot(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try (InputStream in = ExplorerServer.class.getResourceAsStream(
                "/assets/terrain-diffusion-mc/explorer/index.html")) {
            if (in == null) {
                sendError(ex, 404, "index.html not found");
                return;
            }
            byte[] body = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
        } finally {
            ex.close();
        }
    }

    /** GET /api/status → {seed, channels, native_resolution, scale} */
    private static void handleStatus(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("seed", Long.toUnsignedString(LocalTerrainProvider.getSeed()));
            resp.put("channels", Arrays.asList(CHANNEL_NAMES));
            resp.put("native_resolution", NATIVE_RESOLUTION);
            resp.put("scale", WorldScaleManager.getCurrentScale());
            resp.put("blueprint_enabled", WorldBlueprintManager.enabled());
            resp.put("map_width_blocks", WorldBlueprintManager.mapWidthBlocks());
            resp.put("map_height_blocks", WorldBlueprintManager.mapHeightBlocks());
            resp.put("biome_resolution_blocks", 4);
            resp.put("coarse_biome_resolution_blocks", 256 * WorldScaleManager.getCurrentScale());
            resp.put("biomes", BiomeCatalog.metadata());
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 500, e.getMessage());
        }
    }

    /** POST /api/seed body={seed:int} → {seed} */
    private static void handleSeed(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { send405(ex); return; }
        try {
            String body = readBody(ex, 1024);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = GSON.fromJson(body, Map.class);
            if (!data.containsKey("seed")) { sendError(ex, 400, "seed required"); return; }
            long newSeed = ((Number) data.get("seed")).longValue();
            LocalTerrainProvider.changeSeedFromExplorer(newSeed);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("seed", Long.toUnsignedString(LocalTerrainProvider.getSeed()));
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    /** POST /api/new_seed → {seed} */
    private static void handleNewSeed(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { send405(ex); return; }
        try {
            long newSeed = LocalTerrainProvider.generateRandomSeedFromExplorer();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("seed", Long.toUnsignedString(newSeed));
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    /**
     * GET /api/coarse.png — port of coarse_png() + _coarse_channel().
     * Query params: channel, ci0, ci1, cj0, cj1, ch{0,2,3,4,5}_min/max
     * Response headers: X-Vmin, X-Vmax
     */
    private static void handleCoarsePng(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int channel = getInt(q, "channel", 0);
            int ci0 = getInt(q, "ci0", -50), ci1 = getInt(q, "ci1", 50);
            int cj0 = getInt(q, "cj0", -50), cj1 = getInt(q, "cj1", 50);
            int rawH = Math.max(1, ci1 - ci0), rawW = Math.max(1, cj1 - cj0);
            int step = resolutionStep(q);

            float[] data = sampleGrid(coarseChannel(ci0, ci1, cj0, cj1, channel), rawH, rawW, step);
            int H = sampledSize(rawH, step), W = sampledSize(rawW, step);

            // Precipitation: log1p(max(v,0)) before normalizing (matches Python)
            float[] display = data.clone();
            if (channel == 4) {
                for (int i = 0; i < display.length; i++)
                    display[i] = (float) Math.log1p(Math.max(0f, display[i]));
            }
            float vmin = nanMin(display), vmax = nanMax(display);
            if (vmax == vmin) vmax = vmin + 1f;

            // Viridis colormap
            float[][] rgba = new float[4][H * W];
            for (int i = 0; i < H * W; i++) {
                float t = (display[i] - vmin) / (vmax - vmin);
                float[] rgb = Colormaps.viridis(clamp01(t));
                rgba[0][i] = rgb[0]; rgba[1][i] = rgb[1]; rgba[2][i] = rgb[2]; rgba[3][i] = 1f;
            }

            // Optional filter: dim non-matching pixels to 30% (matches Python rgba[~mask, :3] *= 0.3)
            int[] filterChs = {0, 2, 3, 4, 5};
            boolean filterActive = false;
            for (int ch : filterChs) {
                if (q.containsKey("ch" + ch + "_min") || q.containsKey("ch" + ch + "_max")) {
                    filterActive = true; break;
                }
            }
            if (filterActive) {
                boolean[] mask = new boolean[H * W];
                Arrays.fill(mask, true);
                for (int ch : filterChs) {
                    Float lo = getFloat(q, "ch" + ch + "_min");
                    Float hi = getFloat(q, "ch" + ch + "_max");
                    if (lo == null && hi == null) continue;
                    float[] chData = sampleGrid(coarseChannel(ci0, ci1, cj0, cj1, ch), rawH, rawW, step);
                    for (int i = 0; i < H * W; i++) {
                        if (lo != null && chData[i] < lo) mask[i] = false;
                        if (hi != null && chData[i] > hi) mask[i] = false;
                    }
                }
                for (int i = 0; i < H * W; i++) {
                    if (!mask[i]) {
                        rgba[0][i] *= 0.3f; rgba[1][i] *= 0.3f; rgba[2][i] *= 0.3f;
                    }
                }
            }

            byte[] png = toPng(rgba, H, W);
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.getResponseHeaders().set("X-Vmin", String.format("%.3f", vmin));
            ex.getResponseHeaders().set("X-Vmax", String.format("%.3f", vmax));
            ex.getResponseHeaders().set("X-Resolution-Step", String.valueOf(step));
            ex.getResponseHeaders().set("Cache-Control", "private, max-age=60");
            ex.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Vmin, X-Vmax");
            ex.sendResponseHeaders(200, png.length);
            ex.getResponseBody().write(png);
        } catch (Exception e) {
            LOG.error("coarse.png error", e);
            sendError(ex, 400, e.getMessage());
        } finally {
            ex.close();
        }
    }

    /**
     * GET /api/coarse_data.json — port of coarse_data().
     * Returns all 6 channel values as 2D arrays for client-side hover.
     */
    private static void handleCoarseData(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci0 = getInt(q, "ci0", -50), ci1 = getInt(q, "ci1", 50);
            int cj0 = getInt(q, "cj0", -50), cj1 = getInt(q, "cj1", 50);
            int rawH = Math.max(1, ci1 - ci0), rawW = Math.max(1, cj1 - cj0);
            int step = resolutionStep(q);
            int H = sampledSize(rawH, step), W = sampledSize(rawW, step);

            Map<String, Object> channels = new LinkedHashMap<>();
            for (int ch = 0; ch < CHANNEL_NAMES.length; ch++) {
                float[] flat = sampleGrid(coarseChannel(ci0, ci1, cj0, cj1, ch), rawH, rawW, step);
                channels.put(CHANNEL_NAMES[ch], roundedGrid(flat, H, W, 2));
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ci0", ci0); resp.put("ci1", ci1);
            resp.put("cj0", cj0); resp.put("cj1", cj1);
            resp.put("resolution_step", step);
            resp.put("channels", channels);
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    /**
     * GET /api/coarse_biomes.png — categorical biome preview at model/coarse
     * resolution. This deliberately does not pretend to be block resolution;
     * the status endpoint reports the actual Minecraft biome resolution (4 blocks).
     */
    private static void handleCoarseBiomesPng(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci0 = getInt(q, "ci0", -50), ci1 = getInt(q, "ci1", 50);
            int cj0 = getInt(q, "cj0", -50), cj1 = getInt(q, "cj1", 50);
            int rawH = Math.max(1, Math.min(512, ci1 - ci0));
            int rawW = Math.max(1, Math.min(512, cj1 - cj0));
            int step = resolutionStep(q);
            ci1 = ci0 + rawH;
            cj1 = cj0 + rawW;

            short[] biomes = sampleGrid(coarseBiomes(ci0, cj0, ci1, cj1), rawH, rawW, step);
            int H = sampledSize(rawH, step), W = sampledSize(rawW, step);
            float[][] rgba = new float[4][H * W];
            for (int i = 0; i < biomes.length; i++) {
                int color = BiomeCatalog.color(biomes[i]);
                rgba[0][i] = ((color >>> 16) & 0xFF) / 255f;
                rgba[1][i] = ((color >>> 8) & 0xFF) / 255f;
                rgba[2][i] = (color & 0xFF) / 255f;
                rgba[3][i] = 1f;
            }

            byte[] png = toPng(rgba, H, W);
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.getResponseHeaders().set("Cache-Control", "private, max-age=31536000");
            ex.getResponseHeaders().set("X-Biome-Resolution-Blocks", String.valueOf(
                    256 * WorldScaleManager.getCurrentScale()));
            ex.getResponseHeaders().set("X-Resolution-Step", String.valueOf(step));
            ex.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Biome-Resolution-Blocks");
            ex.sendResponseHeaders(200, png.length);
            ex.getResponseBody().write(png);
        } catch (Exception e) {
            LOG.error("coarse_biomes.png error", e);
            sendError(ex, 400, e.getMessage());
        } finally {
            ex.close();
        }
    }

    /** GET /api/biome_data.json — IDs matching /api/coarse_biomes.png for hover labels. */
    private static void handleBiomeData(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci0 = getInt(q, "ci0", -50), ci1 = getInt(q, "ci1", 50);
            int cj0 = getInt(q, "cj0", -50), cj1 = getInt(q, "cj1", 50);
            int rawH = Math.max(1, Math.min(512, ci1 - ci0));
            int rawW = Math.max(1, Math.min(512, cj1 - cj0));
            int step = resolutionStep(q);
            ci1 = ci0 + rawH;
            cj1 = cj0 + rawW;
            short[] flat = sampleGrid(coarseBiomes(ci0, cj0, ci1, cj1), rawH, rawW, step);
            int H = sampledSize(rawH, step), W = sampledSize(rawW, step);

            List<List<Integer>> ids = new ArrayList<>(H);
            for (int r = 0; r < H; r++) {
                List<Integer> row = new ArrayList<>(W);
                for (int c = 0; c < W; c++) row.add((int) flat[r * W + c]);
                ids.add(row);
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ci0", ci0); resp.put("ci1", ci1);
            resp.put("cj0", cj0); resp.put("cj1", cj1);
            resp.put("resolution_step", step);
            resp.put("ids", ids);
            resp.put("resolution_blocks", 256 * WorldScaleManager.getCurrentScale());
            sendJson(ex, 200, resp);
        } catch (Exception e) {
            LOG.error("biome_data error", e);
            sendError(ex, 400, e.getMessage());
        }
    }

    /** GET /api/coarse_stats — port of coarse_stats(). */
    private static void handleCoarseStats(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci0 = getInt(q, "ci0", -50), ci1 = getInt(q, "ci1", 50);
            int cj0 = getInt(q, "cj0", -50), cj1 = getInt(q, "cj1", 50);

            Map<String, Object> stats = new LinkedHashMap<>();
            for (int ch = 0; ch < CHANNEL_NAMES.length; ch++) {
                float[] data = coarseChannel(ci0, ci1, cj0, cj1, ch);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", CHANNEL_NAMES[ch]);
                entry.put("min", round3(nanMin(data)));
                entry.put("max", round3(nanMax(data)));
                stats.put(String.valueOf(ch), entry);
            }
            sendJson(ex, 200, stats);
        } catch (Exception e) {
            sendError(ex, 400, e.getMessage());
        }
    }

    /**
     * GET /api/detail.png — port of detail_png().
     * Query params: ci, cj, detail_size, pan_i, pan_j, mode
     */
    private static void handleDetailPng(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci         = getInt(q, "ci", 0);
            int cj         = getInt(q, "cj", 0);
            int detailSize = detailSize(q);
            int panI       = getInt(q, "pan_i", 0);
            int panJ       = getInt(q, "pan_j", 0);
            String mode    = q.getOrDefault("mode", "relief");

            int centerI = ci * 256 + panI;
            int centerJ = cj * 256 + panJ;
            int half    = detailSize / 2;

            boolean biomeMode = mode.equals("biomes");
            int pad = biomeMode ? 1 : 0;
            float[][] out = LocalTerrainProvider.getPipelineData(
                    centerI - half - pad, centerJ - half - pad,
                    centerI + half + pad, centerJ + half + pad,
                    mode.equals("temperature") || biomeMode);
            float[] elevFlat  = out[0];
            float[] climate   = out[1];
            int H = detailSize, W = detailSize;

            float[][] rgba;
            if (mode.equals("elevation")) {
                float vmin = nanMin(elevFlat), vmax = nanMax(elevFlat);
                if (vmax == vmin) vmax = vmin + 1f;
                rgba = applyColormap1D(elevFlat, H, W, vmin, vmax, "terrain");
            } else if (mode.equals("temperature") && climate != null) {
                // climate[0] = temperature channel (H*W floats)
                float[] temp = Arrays.copyOfRange(climate, 0, H * W);
                float vmin = nanMin(temp), vmax = nanMax(temp);
                if (vmax == vmin) vmax = vmin + 1f;
                rgba = applyColormap1D(temp, H, W, vmin, vmax, "rdbu_r");
            } else if (biomeMode && climate != null) {
                int paddedW = W + 2;
                float[] elevCore = new float[H * W];
                float[] elevPadded = elevFlat;
                float[] biomeClimate = new float[4 * H * W];
                for (int r = 0; r < H; r++) {
                    for (int c = 0; c < W; c++) {
                        int core = r * W + c;
                        int padded = (r + 1) * paddedW + (c + 1);
                        elevCore[core] = elevFlat[padded];
                        biomeClimate[core] = climate[padded];
                        biomeClimate[H * W + core] = climate[(H + 2) * (W + 2) + padded];
                        biomeClimate[2 * H * W + core] = climate[2 * (H + 2) * (W + 2) + padded];
                        biomeClimate[3 * H * W + core] = climate[3 * (H + 2) * (W + 2) + padded];
                    }
                }
                short[] biomes = BiomeClassifier.classify(
                        elevCore, biomeClimate, (centerI - half)*WorldScaleManager.getCurrentScale(), (centerJ - half)*WorldScaleManager.getCurrentScale(),
                        elevPadded, H, W, NATIVE_RESOLUTION,WorldScaleManager.getCurrentScale());
                if(out.length>2)for(int r=0;r<H;r++)for(int c=0;c<W;c++){
                    float water=out[2][(r+1)*paddedW+c+1];
                    if(Float.isFinite(water)&&water>0)biomes[r*W+c]=biomeClimate[r*W+c]<0?BiomeClassifier.FROZEN_RIVER:BiomeClassifier.RIVER;
                }
                rgba = new float[4][H * W];
                // Minecraft's horizontal biome grid is quart-based (4 blocks).
                // Keep the browser preview from inventing sub-biome detail when
                // the model's native pixels are smaller than one biome cell.
                int pixelsPerBiomeCell = Math.max(1, (int) Math.ceil(
                        4.0 / Math.max(1, WorldScaleManager.getCurrentScale())));
                for (int r = 0; r < H; r++) {
                    for (int c = 0; c < W; c++) {
                        int sr = Math.min(H - 1, (r / pixelsPerBiomeCell) * pixelsPerBiomeCell);
                        int sc = Math.min(W - 1, (c / pixelsPerBiomeCell) * pixelsPerBiomeCell);
                        int color = BiomeCatalog.color(biomes[sr * W + sc]);
                        int i = r * W + c;
                        rgba[0][i] = ((color >>> 16) & 0xFF) / 255f;
                        rgba[1][i] = ((color >>> 8) & 0xFF) / 255f;
                        rgba[2][i] = (color & 0xFF) / 255f;
                        rgba[3][i] = 1f;
                    }
                }
            } else {
                // relief mode (default)
                float[][] reliefRgb = ReliefMap.getReliefMap(elevFlat, H, W, 90.0);
                rgba = new float[4][H * W];
                for (int i = 0; i < H * W; i++) {
                    rgba[0][i] = reliefRgb[0][i];
                    rgba[1][i] = reliefRgb[1][i];
                    rgba[2][i] = reliefRgb[2][i];
                    rgba[3][i] = 1f;
                }
            }

            byte[] png = toPng(rgba, H, W);
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.getResponseHeaders().set("Cache-Control", "private, max-age=60");
            ex.sendResponseHeaders(200, png.length);
            ex.getResponseBody().write(png);
        } catch (Exception e) {
            LOG.error("detail.png error", e);
            sendError(ex, 400, e.getMessage());
        } finally {
            ex.close();
        }
    }

    /**
     * GET /api/detail_raw — port of detail_raw().
     * Binary: int16-LE elevation (H*W*2 bytes) + float32-LE temperature (H*W*4 bytes).
     * Headers: X-Height, X-Width, X-Has-Temp.
     */
    private static void handleDetailRaw(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { send405(ex); return; }
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI());
            int ci         = getInt(q, "ci", 0);
            int cj         = getInt(q, "cj", 0);
            int detailSize = detailSize(q);
            int panI       = getInt(q, "pan_i", 0);
            int panJ       = getInt(q, "pan_j", 0);

            int centerI = ci * 256 + panI;
            int centerJ = cj * 256 + panJ;
            int half    = detailSize / 2;
            int H = detailSize, W = detailSize;

            float[][] out = LocalTerrainProvider.getPipelineData(
                    centerI - half, centerJ - half, centerI + half, centerJ + half, true);
            float[] elevFlat = out[0];
            float[] climate  = out[1];

            // Elevation → int16 LE (matching Python: clip(floor(elev), -32768, 32767).astype('<i2'))
            ByteBuffer elevBuf = ByteBuffer.allocate(H * W * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (float e : elevFlat) {
                short s = (short) Math.max(-32768, Math.min(32767, (int) Math.floor(e)));
                elevBuf.putShort(s);
            }

            boolean hasTemp = climate != null;
            byte[] payload;
            if (hasTemp) {
                // Temperature = climate[0..H*W] as float32 LE
                ByteBuffer tempBuf = ByteBuffer.allocate(H * W * 4).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < H * W; i++) tempBuf.putFloat(climate[i]);
                payload = new byte[elevBuf.capacity() + tempBuf.capacity()];
                System.arraycopy(elevBuf.array(), 0, payload, 0, elevBuf.capacity());
                System.arraycopy(tempBuf.array(), 0, payload, elevBuf.capacity(), tempBuf.capacity());
            } else {
                payload = elevBuf.array();
            }

            ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
            ex.getResponseHeaders().set("X-Height", String.valueOf(H));
            ex.getResponseHeaders().set("X-Width", String.valueOf(W));
            ex.getResponseHeaders().set("X-Has-Temp", hasTemp ? "1" : "0");
            ex.getResponseHeaders().set("Access-Control-Expose-Headers", "X-Height, X-Width, X-Has-Temp");
            ex.sendResponseHeaders(200, payload.length);
            ex.getResponseBody().write(payload);
        } catch (Exception e) {
            LOG.error("detail_raw error", e);
            sendError(ex, 400, e.getMessage());
        } finally {
            ex.close();
        }
    }

    // =========================================================================
    // Coarse channel helper — port of _coarse_channel() in server.py
    // =========================================================================

    /**
     * Return the given channel of the coarse map in real units.
     * Channels 0 and 1: undo signed-sqrt (sign(v) * v^2).
     */
    private static float[] coarseChannel(int ci0, int ci1, int cj0, int cj1, int channel) throws Exception {
        FloatTensor slice = LocalTerrainProvider.getPipelineCoarse(ci0, cj0, ci1, cj1);
        int H = ci1 - ci0, W = cj1 - cj0;
        float[] result = new float[H * W];
        for (int i = 0; i < H * W; i++) {
            float w   = slice.data[6 * H * W + i];
            float raw = (w > 1e-8f) ? slice.data[channel * H * W + i] / w : 0f;
            // Channels 0 (elev) and 1 (p5): signed-sqrt → real units via sign(v)*v^2
            result[i] = (channel <= 1) ? (float) (Math.signum(raw) * raw * raw) : raw;
        }
        return result;
    }

    /**
     * Classify a coarse slice using the same rule-based classifier as Minecraft.
     * A one-cell elevation halo is fetched so slope decisions are stable at the
     * edge of the displayed window.
     */
    private static short[] coarseBiomes(int ci0, int cj0, int ci1, int cj1) throws Exception {
        String cacheKey = ci0 + ":" + cj0 + ":" + ci1 + ":" + cj1 + ":"
                + Long.toUnsignedString(LocalTerrainProvider.getSeed()) + ":"
                + WorldBlueprintManager.fingerprint();
        synchronized (BIOME_GRID_CACHE) {
            short[] cached = BIOME_GRID_CACHE.get(cacheKey);
            if (cached != null) return cached;
        }

        int H = ci1 - ci0, W = cj1 - cj0;
        int pH = H + 2, pW = W + 2;
        if(WorldBlueprintManager.enabled()){
            float[][][] authored=WorldBlueprintManager.conditioningPreview(LocalTerrainProvider.getSeed(),
                    cj0-1,ci0-1,cj1+1,ci1+1);
            float[] elev=new float[H*W],elevPadded=new float[pH*pW],climate=new float[4*H*W];
            for(int r=0;r<pH;r++)for(int c=0;c<pW;c++){
                float q=authored[0][r][c];float meters=(float)Math.copySign(q*q,q);elevPadded[r*pW+c]=meters;
                if(r==0||c==0||r==pH-1||c==pW-1)continue;int core=(r-1)*W+c-1;elev[core]=meters;
                for(int ch=0;ch<4;ch++)climate[ch*H*W+core]=authored[ch+1][r][c];
            }
            int coordinateStep=256*WorldScaleManager.getCurrentScale();
            short[] result=BiomeClassifier.classify(elev,climate,ci0*coordinateStep+coordinateStep/2,
                    cj0*coordinateStep+coordinateStep/2,
                    elevPadded,H,W,WorldPipelineModelConfig.nativeResolution()*256f,coordinateStep);
            synchronized(BIOME_GRID_CACHE){BIOME_GRID_CACHE.put(cacheKey,result);}return result;
        }
        FloatTensor slice = LocalTerrainProvider.getPipelineCoarse(ci0 - 1, cj0 - 1, ci1 + 1, cj1 + 1);
        int total = pH * pW;

        float[] elev = new float[H * W];
        float[] elevPadded = new float[total];
        float[] climate = new float[4 * H * W];
        for (int r = 0; r < pH; r++) {
            for (int c = 0; c < pW; c++) {
                int p = r * pW + c;
                elevPadded[p] = coarseReal(slice, 0, p, total);
                if (r == 0 || r == pH - 1 || c == 0 || c == pW - 1) continue;
                int core = (r - 1) * W + (c - 1);
                elev[core] = elevPadded[p];
                climate[core] = coarseReal(slice, 2, p, total);
                climate[H * W + core] = coarseReal(slice, 3, p, total);
                climate[2 * H * W + core] = coarseReal(slice, 4, p, total);
                climate[3 * H * W + core] = coarseReal(slice, 5, p, total);
            }
        }

        // One coarse index is 256 native pixels; use that physical spacing for slope.
        float pixelSizeM = WorldPipelineModelConfig.nativeResolution() * 256f;
        int coordinateStep=256*WorldScaleManager.getCurrentScale();
        short[] result = BiomeClassifier.classify(elev, climate, ci0*coordinateStep, cj0*coordinateStep,
                elevPadded, H, W, pixelSizeM, coordinateStep);
        synchronized (BIOME_GRID_CACHE) {
            BIOME_GRID_CACHE.put(cacheKey, result);
        }
        return result;
    }

    private static float coarseReal(FloatTensor tensor, int channel, int index, int planeSize) {
        float weight = tensor.data[6 * planeSize + index];
        float raw = weight > 1e-8f ? tensor.data[channel * planeSize + index] / weight : 0f;
        return channel <= 1 ? (float) (Math.signum(raw) * raw * raw) : raw;
    }

    // =========================================================================
    // PNG rendering
    // =========================================================================

    /** Encode RGBA channels (float[4][H*W]) to a PNG byte array. */
    private static byte[] toPng(float[][] rgba, int H, int W) throws IOException {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                int ri = (int) (clamp01(rgba[0][idx]) * 255f + 0.5f);
                int gi = (int) (clamp01(rgba[1][idx]) * 255f + 0.5f);
                int bi = (int) (clamp01(rgba[2][idx]) * 255f + 0.5f);
                int ai = (int) (clamp01(rgba[3][idx]) * 255f + 0.5f);
                img.setRGB(c, r, (ai << 24) | (ri << 16) | (gi << 8) | bi);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static float[][] applyColormap1D(float[] data, int H, int W, float vmin, float vmax, String cmap) {
        float[][] rgba = new float[4][H * W];
        for (int i = 0; i < H * W; i++) {
            float t = (data[i] - vmin) / (vmax - vmin);
            float[] rgb;
            switch (cmap) {
                case "terrain": rgb = Colormaps.terrain(clamp01(t)); break;
                case "rdbu_r":  rgb = Colormaps.rdBuR(clamp01(t));   break;
                default:        rgb = Colormaps.viridis(clamp01(t)); break;
            }
            rgba[0][i] = rgb[0]; rgba[1][i] = rgb[1]; rgba[2][i] = rgb[2]; rgba[3][i] = 1f;
        }
        return rgba;
    }

    // =========================================================================
    // HTTP utilities
    // =========================================================================

    private static void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] body = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        ex.close();
    }

    private static void sendError(HttpExchange ex, int status, String msg) throws IOException {
        Map<String, String> err = new HashMap<>();
        err.put("error", msg != null ? msg : "unknown error");
        sendJson(ex, status, err);
    }

    private static void send405(HttpExchange ex) throws IOException {
        sendError(ex, 405, "Method Not Allowed");
    }

    private static String readBody(HttpExchange ex, int maxBytes) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] buf = in.readNBytes(maxBytes);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> map = new HashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isEmpty()) return map;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                map.put(pair.substring(0, eq), pair.substring(eq + 1));
            } else {
                map.put(pair, "");
            }
        }
        return map;
    }

    // =========================================================================
    // Math utilities
    // =========================================================================

    private static float nanMin(float[] arr) {
        float min = Float.MAX_VALUE;
        for (float v : arr) if (!Float.isNaN(v) && v < min) min = v;
        return min == Float.MAX_VALUE ? 0f : min;
    }

    private static float nanMax(float[] arr) {
        float max = -Float.MAX_VALUE;
        for (float v : arr) if (!Float.isNaN(v) && v > max) max = v;
        return max == -Float.MAX_VALUE ? 0f : max;
    }

    private static float clamp01(float v) {
        return Math.min(1f, Math.max(0f, v));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** Rounded 2-D list for coarse_data JSON (np.round equivalent). */
    private static List<List<Double>> roundedGrid(float[] flat, int H, int W, int decimals) {
        double factor = Math.pow(10, decimals);
        List<List<Double>> grid = new ArrayList<>(H);
        for (int r = 0; r < H; r++) {
            List<Double> row = new ArrayList<>(W);
            for (int c = 0; c < W; c++) {
                row.add(Math.round(flat[r * W + c] * factor) / factor);
            }
            grid.add(row);
        }
        return grid;
    }

    /** Downsample a row-major grid by taking a deterministic nearest sample. */
    private static float[] sampleGrid(float[] source, int H, int W, int step) {
        int outH = sampledSize(H, step), outW = sampledSize(W, step);
        float[] result = new float[outH * outW];
        for (int r = 0; r < outH; r++) {
            int sr = Math.min(H - 1, r * step);
            for (int c = 0; c < outW; c++) {
                int sc = Math.min(W - 1, c * step);
                result[r * outW + c] = source[sr * W + sc];
            }
        }
        return result;
    }

    /** Downsample categorical biome IDs using nearest-neighbour sampling. */
    private static short[] sampleGrid(short[] source, int H, int W, int step) {
        int outH = sampledSize(H, step), outW = sampledSize(W, step);
        short[] result = new short[outH * outW];
        for (int r = 0; r < outH; r++) {
            int sr = Math.min(H - 1, r * step);
            for (int c = 0; c < outW; c++) {
                int sc = Math.min(W - 1, c * step);
                result[r * outW + c] = source[sr * W + sc];
            }
        }
        return result;
    }

    private static int sampledSize(int size, int step) {
        return Math.max(1, (size + step - 1) / step);
    }

    private static int resolutionStep(Map<String, String> q) {
        return Math.max(1, Math.min(8, getInt(q, "step", 1)));
    }

    /** Keep explorer requests bounded so an accidental URL cannot request a huge tensor. */
    private static int detailSize(Map<String, String> q) {
        return Math.max(128, Math.min(1024, getInt(q, "detail_size", 512)));
    }

    private static int getInt(Map<String, String> q, String key, int def) {
        String v = q.get(key);
        if (v == null) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    private static Float getFloat(Map<String, String> q, String key) {
        String v = q.get(key);
        if (v == null) return null;
        try { return Float.parseFloat(v); } catch (NumberFormatException e) { return null; }
    }
}
