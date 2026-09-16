package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.infinitetensor.FloatTensor;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides terrain heightmap and biome data from the local WorldPipeline.
 *
 * <p>When scale=1 the pipeline is sampled at native model resolution directly.
 * When scale>1 the pipeline is sampled at native resolution and the result is
 * bilinearly upsampled, giving 1 block = nativeResolution/scale.
 */
public final class LocalTerrainProvider {
    private static final Logger LOG = LoggerFactory.getLogger(LocalTerrainProvider.class);

    private static final float NATIVE_RESOLUTION = WorldPipelineModelConfig.nativeResolution();

    private static final FastNoiseLite ELEV_NOISE_COARSE = makeFnl(99999, 1f/24f, 3, 2f, 0.5f);
    private static final FastNoiseLite ELEV_NOISE_FINE   = makeFnl(88888, 1f/6f,  2, 2f, 0.6f);

    private static FastNoiseLite makeFnl(int seed, float freq, int oct, float lac, float gain) {
        FastNoiseLite fnl = new FastNoiseLite(seed);
        fnl.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        fnl.SetFrequency(freq);
        fnl.SetFractalType(FastNoiseLite.FractalType.FBm);
        fnl.SetFractalOctaves(oct);
        fnl.SetFractalLacunarity(lac);
        fnl.SetFractalGain(gain);
        return fnl;
    }

    public static final class HeightmapData {
        public final short[][] heightmap;
        public final short[][] biomeIds;
        /** Preconverted once per tile; avoids conversion for every density sample. */
        public final short[][] blockHeights;
        public final com.github.xandergos.terraindiffusionmc.world.ColumnWorldContext[][] contexts;
        /** Water surface in pipeline metres; Short.MIN_VALUE means no inland water. */
        public final short[][] waterSurface;
        public final int width;
        public final int height;

        public HeightmapData(short[][] heightmap, short[][] biomeIds, int width, int height) {
            this(heightmap,biomeIds,null,width,height);
        }
        public HeightmapData(short[][] heightmap, short[][] biomeIds, short[][] waterSurface,int width,int height){
            this(heightmap,biomeIds,waterSurface,width,height,null);
        }
        public HeightmapData(short[][] heightmap, short[][] biomeIds, short[][] waterSurface,int width,int height,
                com.github.xandergos.terraindiffusionmc.world.ColumnWorldContext[][] contexts){
            this.contexts=contexts;
            this.heightmap = heightmap;
            this.blockHeights = new short[height][width];
            for(int z=0;z<height;z++)for(int x=0;x<width;x++)
                blockHeights[z][x]=(short)com.github.xandergos.terraindiffusionmc.world.HeightConverter.convertToMinecraftHeight(heightmap[z][x]);
            this.biomeIds  = biomeIds;
            this.width     = width;
            this.height    = height;
            this.waterSurface=waterSurface;
        }
    }

    private static record CacheKey(int i1, int j1, int i2, int j2) {}
    private static record CacheEntry(HeightmapData data, AtomicLong lastAccessed) {}

    private static final int MAX_CACHE_SIZE = 64;
    private static final int MAX_CACHE_SIZE_HEADROOM = 8;
    private static final Map<RequestKey, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong CACHE_CLOCK = new AtomicLong();
    private static final TerrainWorkQueue TERRAIN_WORK = new TerrainWorkQueue(2, 16);
    // Provider identity also guards the fast cache-hit path against a world-switch race.
    private record RequestKey(LocalTerrainProvider owner, CacheKey region) {}
    /** Single thread for pipeline.get() so MemoryTileStore is not accessed concurrently. */
    private static final ExecutorService INFERENCE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "terrain-diffusion-inference");
        t.setDaemon(true);
        return t;
    });

    private static volatile LocalTerrainProvider INSTANCE;
    private static volatile long instanceSeed;

    @FunctionalInterface interface NativeFieldReader {
        float[][] read(int i1,int j1,int i2,int j2,boolean climate);
    }
    private final WorldPipeline pipeline;
    private final NativeFieldReader nativeReader;
    private volatile WorldHydrology hydrology;
    private boolean hydrologyPrepared;
    private PreparationGate<WorldHydrology> preparation=new PreparationGate<>();

    private static volatile boolean worldReady;
    public static synchronized void beginWorldLoad(){
        worldReady=false;
        TERRAIN_WORK.pauseAndDrain();
        CACHE.clear();
    }

    private LocalTerrainProvider(long seed, PipelineModels models) {
        ConditioningMapProvider provider = WorldBlueprintManager.provider(seed);
        this.pipeline = provider == null ? new WorldPipeline(seed, models) : new WorldPipeline(seed, models, provider);
        this.nativeReader = this::readPipeline;
    }

    // Allows the CPU stage to be replayed against captured/synthetic model output without ONNX.
    LocalTerrainProvider(WorldHydrology hydrology, NativeFieldReader reader) {
        this.pipeline=null;
        this.hydrology=hydrology;
        this.nativeReader=reader;
    }

    /** Bind shared models to a fresh save-local provider after draining the previous world. */
    public static synchronized void init(long seed) {
        worldReady=false;
        TERRAIN_WORK.pauseAndDrain();
        PipelineModels.awaitLoad();
        PipelineModels models = PipelineModels.getInstance();
        if (models == null) throw new IllegalStateException("PipelineModels failed to load");
        String blueprint = WorldBlueprintManager.fingerprint();
        BiomeClassifier.configure(seed, blueprint);
        // Always bind a fresh provider to the save-local store, even for the same seed/map.
        // beginWorldLoad drains old readers before world-scoped globals are replaced.
        if (INSTANCE != null) INSTANCE.pipeline.close();
        INSTANCE = new LocalTerrainProvider(seed, models);
        instanceSeed = seed;
        CACHE.clear();
        TERRAIN_WORK.resume();
        worldReady=true;
        LOG.info("Terrain world ready: seed={}, blueprint={}, scale={}",seed,blueprint,WorldScaleManager.getCurrentScale());
    }

    public static void restorePreparedPreview(){
        if(INSTANCE!=null)WorldHydrology.publishPreview(INSTANCE.hydrology);
    }

    public static synchronized LocalTerrainProvider getInstance() {
        if(!worldReady||INSTANCE==null)throw new IllegalStateException("Terrain requested before world seed and blueprint initialization; refusing seed-zero fallback");
        return INSTANCE;
    }

    public static synchronized void clearCache() {
        TERRAIN_WORK.pauseAndDrain();
        CACHE.clear();
        if(worldReady) TERRAIN_WORK.resume();
    }

    // =========================================================================
    // Explorer API — all pipeline calls routed through INFERENCE_EXECUTOR
    // =========================================================================

    /** Returns the current world seed used by the pipeline. */
    public static long getSeed() {
        return instanceSeed;
    }
    public static float regionalConcavity(int x,int z){
        LocalTerrainProvider p=INSTANCE;if(p==null||p.hydrology==null)return Float.NaN;
        int scale=WorldScaleManager.getCurrentScale();return p.hydrology.concavityAt(x/(double)scale,z/(double)scale);
    }

    /**
     * Run elevation and climate inference on the inference thread.
     *
     * @return float[2]: [0] = elev (H*W), [1] = climate (5*H*W, or null)
     */
    public static float[][] getPipelineData(int i1, int j1, int i2, int j2, boolean withClimate) throws Exception {
        var provider=getInstance();
        return TERRAIN_WORK.awaitBackground(new Object(), () -> {
            provider.requireActive();
            float[][] out=provider.readPipeline(i1,j1,i2,j2,withClimate);
            if(WorldBlueprintManager.generationVersion()>=3&&provider.hydrology!=null){
                int nw=WorldBlueprintManager.activeStore().manifest().width()*256,nh=WorldBlueprintManager.activeStore().manifest().height()*256;
                for(int z=0;z<i2-i1;z++)for(int x=0;x<j2-j1;x++){
                    int p=z*(j2-j1)+x;
                    float e=out[0][p];
                    if(WorldBlueprintManager.generationVersion()>=12)e=WorldHydrology.enhanceMountainRelief(e,j1+x,i1+z,instanceSeed);
                    out[0][p]=WorldHydrology.refineCoast(WorldHydrology.correctCoast(e,j1+x,i1+z,nw,nh),j1+x,i1+z,nw,nh);
                }
                var result=provider.hydrology.carve(out[0],i1,j1,i2-i1,j2-j1,1);
                // Third channel is optional inland water; legacy callers still use [0]/[1].
                return new float[][]{result.bed(),out[1],result.water()};
            }
            return out;
        });
    }

    /**
     * Fetch a coarse tensor slice on the inference thread.
     * Coordinates are in coarse index units (1 unit = 256 native pixels).
     *
     * @return FloatTensor with shape [7, ci1-ci0, cj1-cj0]
     */
    public static FloatTensor getPipelineCoarse(int ci0, int cj0, int ci1, int cj1) throws Exception {
        var provider=getInstance();
        return TERRAIN_WORK.awaitBackground(new Object(), () -> {
            provider.requireActive();
            return submitToInferenceThread(() -> provider.pipeline.getCoarseSlice(ci0,cj0,ci1,cj1));
        });
    }

    /**
     * Reject live-world seed changes; selecting the existing seed is a no-op.
     */
    public static void changeSeedFromExplorer(long newSeed) throws Exception {
        if(newSeed!=instanceSeed)throw new IllegalStateException("Live-world seed changes are disabled to protect your save. Create a new world to preview a different seed.");
        // An unchanged seed is a no-op. Do not clear work shared with chunk workers.
    }

    /** Change to a random new seed; returns the new seed value. */
    public static long generateRandomSeedFromExplorer() throws Exception {
        long newSeed = new Random().nextLong();
        changeSeedFromExplorer(newSeed);
        return newSeed;
    }

    private static <T> T submitToInferenceThread(Callable<T> task) throws Exception {
        return INFERENCE_EXECUTOR.submit(task).get();
    }

    /**
     * Fetch heightmap for a block-coordinate region (i=Z, j=X).
     * Coordinates are in block space; scale from config determines blocks per native pixel.
     * Blocks the calling thread until the tile is ready (one tile can take 10–30+ seconds).
     * If the caller is the server or a chunk worker, the game will stall until this returns.
     */
    public HeightmapData fetchHeightmap(int i1, int j1, int i2, int j2) {
        requireActive();
        CacheKey key = new CacheKey(i1, j1, i2, j2);
        CacheEntry cached = CACHE.get(new RequestKey(this,key));
        if (cached != null) {
            cached.lastAccessed.set(CACHE_CLOCK.incrementAndGet());
            return cached.data;
        }

        return this.genHeightmap(key, i1, j1, i2, j2);
    }

    private void requireActive() {
        if(!worldReady || INSTANCE!=this)
            throw new java.util.concurrent.CancellationException("Terrain provider belongs to an inactive world");
    }

    /** Only this stage may touch the model or its mutable tensor cache. get() returns
     * newly allocated arrays, whose ownership transfers to the requesting CPU worker. */
    private float[][] readPipeline(int i1,int j1,int i2,int j2,boolean climate) {
        try {
            return submitToInferenceThread(() -> {
                if(WorldBlueprintManager.generationVersion()>=3&&!hydrologyPrepared){
                    hydrology=preparation.get(()->WorldHydrology.prepare(pipeline,instanceSeed));
                    hydrologyPrepared=true;
                }
                return pipeline.get(i1,j1,i2,j2,climate);
            });
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted terrain model request",e);
        } catch(Exception e) {
            throw new IllegalStateException("Terrain model request failed",e);
        }
    }

    private HeightmapData genHeightmap(CacheKey key, int i1, int j1, int i2, int j2) {
        long requested=System.nanoTime();
        try {
            return TERRAIN_WORK.await(new RequestKey(this,key), () -> {
                requireActive();
                CacheEntry cached=CACHE.get(new RequestKey(this,key));
                if(cached!=null){cached.lastAccessed.set(CACHE_CLOCK.incrementAndGet());return cached.data;}
                long started=System.nanoTime();
                int scale=WorldScaleManager.getCurrentScale();
                HeightmapData data = prepareTile(i1,j1,i2,j2,scale);
                CACHE.put(new RequestKey(this,key),new CacheEntry(data,new AtomicLong(CACHE_CLOCK.incrementAndGet())));
                evictLruTo(MAX_CACHE_SIZE);
                LOG.info("Terrain Diffusion ({}) finished generating region {}x{} at ({}, {}): queue {} ms, work {} ms",
                        OnnxModel.getResolvedInferenceProvider(),j2-j1,i2-i1,j1,i1,
                        (started-requested)/1_000_000,(System.nanoTime()-started)/1_000_000);
                return data;
            });
        } catch(InterruptedException e) {
            // This caller stops waiting. Other callers still share the original work.
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for terrain tile: "+key,e);
        } catch(Exception e) {
            throw new RuntimeException("Terrain tile failed: "+key,e);
        }
    }

    HeightmapData prepareTile(int i1,int j1,int i2,int j2,int scale) {
        return WorldBlueprintManager.generationVersion()>=3 ? handleModern(i1,j1,i2,j2,scale) : scale<=1
                ? handle1x(i1,j1,i2,j2) : handleUpsampled(i1,j1,i2,j2,scale);
    }

    private static void evictLruTo(int maxSize) {
        int headroomHalf = MAX_CACHE_SIZE_HEADROOM / 2;
        if (CACHE.size() > maxSize + headroomHalf) {
            CACHE.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastAccessed.get()))
                .limit(MAX_CACHE_SIZE_HEADROOM)
                .map(Map.Entry::getKey)
                .forEach(CACHE::remove);
        }
    }

    // =========================================================================
    // Scale == 1: block coords == native pixel coords
    // =========================================================================

    private HeightmapData handle1x(int i1, int j1, int i2, int j2) {
        int H = i2 - i1, W = j2 - j1;

        float[] elevPadded = nativeReader.read(i1 - 1, j1 - 1, i2 + 1, j2 + 1, false)[0];
        float[][] out = nativeReader.read(i1, j1, i2, j2, true);
        float[] elevFlat = out[0];
        float[] climate  = out[1];

        short[] biomeFlat = BiomeClassifier.classify(elevFlat, climate, i1, j1, elevPadded, H, W, NATIVE_RESOLUTION);
        return buildHeightmapData(elevFlat, biomeFlat, H, W);
    }

    // =========================================================================
    // Scale > 1: pipeline at native res → bilinear upsample to block res
    // =========================================================================

    private HeightmapData handleUpsampled(int i1, int j1, int i2, int j2, int scale) {
        int H = i2 - i1, W = j2 - j1;
        float pixelSizeM = NATIVE_RESOLUTION / scale;

        // Convert block coords to native pixel coords
        int i1n = Math.floorDiv(i1, scale);
        int j1n = Math.floorDiv(j1, scale);
        int i2n = -Math.floorDiv(-i2, scale);
        int j2n = -Math.floorDiv(-j2, scale);

        // 2-pixel native padding (1 for bilinear + 1 for slope)
        int i1p = i1n - 2, j1p = j1n - 2;
        int i2p = i2n + 2, j2p = j2n + 2;
        int nH = i2p - i1p, nW = j2p - j1p;

        float[][] out = nativeReader.read(i1p, j1p, i2p, j2p, true);
        float[] elevNativeFlat    = out[0];
        float[] climateNativeFlat = out[1];

        // Bilinear upsample elevation: (nH, nW) → (nH*scale, nW*scale)
        float[][] elevNative2D = to2D(elevNativeFlat, nH, nW);
        float[][] elevUp = LaplacianUtils.bilinearResize(elevNative2D, nH * scale, nW * scale);

        // Crop offsets in the upsampled array
        int padUp   = 2 * scale;
        int offsetI = i1 - i1n * scale;
        int offsetJ = j1 - j1n * scale;
        int cropI1  = padUp + offsetI;
        int cropJ1  = padUp + offsetJ;

        float[] elevSmooth = cropFlat(elevUp, cropI1,     cropJ1,     H,   W,   nH * scale, nW * scale);
        float[] elevPadded = cropFlat(elevUp, cropI1 - 1, cropJ1 - 1, H+2, W+2, nH * scale, nW * scale);

        // Upsample climate (4, nH, nW) → (4, H, W)
        float[] climate = upsampleClimate(climateNativeFlat, nH, nW, cropI1, cropJ1, H, W, scale, nH * scale, nW * scale);

        float[] elevOut = addElevationNoise(elevSmooth, elevPadded, i1, j1, H, W, pixelSizeM);

        // Land/ocean and immediate topology must follow the same final elevation field used
        // by the density function. Broad ecology still comes from the smoothed climate/model.
        short[] biomeFlat = BiomeClassifier.classify(elevOut, climate, i1, j1, elevPadded, H, W, pixelSizeM);
        return buildHeightmapData(elevOut, biomeFlat, H, W);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private HeightmapData handleModern(int i1,int j1,int i2,int j2,int scale){
        if(WorldBlueprintManager.generationVersion()<13)return handleModernRaw(i1,j1,i2,j2,scale);
        int halo=com.github.xandergos.terraindiffusionmc.world.BiomeBoundaryField.HALO;
        var raw=handleModernRaw(i1-halo,j1-halo,i2+halo,j2+halo,scale);
        var boundaries=new com.github.xandergos.terraindiffusionmc.world.BiomeBoundaryField(raw.biomeIds,raw.waterSurface);
        int h=i2-i1,w=j2-j1;short[][] heights=new short[h][w],ids=new short[h][w],waters=new short[h][w];
        var contexts=new com.github.xandergos.terraindiffusionmc.world.ColumnWorldContext[h][w];
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int r=z+halo,c=x+halo,wx=j1+x,wz=i1+z;short id=raw.biomeIds[r][c];
            float e=raw.heightmap[r][c];short water=raw.waterSurface[r][c];
            var f=com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.at(wx/(double)scale,wz/(double)scale,instanceSeed);
            double slope=Math.hypot(raw.heightmap[r][c+1]-raw.heightmap[r][c-1],raw.heightmap[r+1][c]-raw.heightmap[r-1][c])/(60.0/scale);
            var completed=com.github.xandergos.terraindiffusionmc.world.DesertLandforms.complete(e,wx,wz,id,water!=Short.MIN_VALUE,
                    boundaries.distance(c,r),boundaries.waterDistance(c,r),f,instanceSeed,scale);
            heights[z][x]=completed.metres();ids[z][x]=id;waters[z][x]=water;
            contexts[z][x]=new com.github.xandergos.terraindiffusionmc.world.ColumnWorldContext(wx,wz,id,boundaries.neighbor(c,r),
                    completed.baseY(),completed.topY(),water,boundaries.distance(c,r),boundaries.width(c,r),boundaries.transition(c,r),
                    boundaries.waterDistance(c,r),slope,f,completed.land());
        }
        return new HeightmapData(heights,ids,waters,w,h,contexts);
    }

    private HeightmapData handleModernRaw(int i1,int j1,int i2,int j2,int scale){
        int h=i2-i1,w=j2-j1,ph=h+2,pw=w+2;
        int ni=Math.floorDiv(i1-3,scale)-2,nj=Math.floorDiv(j1-3,scale)-2;
        int endI=-Math.floorDiv(-(i2+3),scale)+2,endJ=-Math.floorDiv(-(j2+3),scale)+2;
        int nh=endI-ni,nw=endJ-nj;float[][] field=nativeReader.read(ni,nj,endI,endJ,true);
        int mapW=WorldBlueprintManager.activeStore().manifest().width()*256;
        int mapH=WorldBlueprintManager.activeStore().manifest().height()*256;
        for(int r=0;r<nh;r++)for(int c=0;c<nw;c++){
            float e=field[0][r*nw+c],originalElevation=e;
            if(WorldBlueprintManager.generationVersion()>=4)e=WorldHydrology.enhanceMountainRelief(e,nj+c,ni+r,instanceSeed);
            field[0][r*nw+c]=WorldHydrology.refineCoast(WorldHydrology.correctCoast(e,nj+c,ni+r,mapW,mapH),nj+c,ni+r,mapW,mapH);
            if(WorldBlueprintManager.generationVersion()>=9){
                // Pipeline climate precedes our terrain edits. Keep alpine bands tied to final height.
                field[1][r*nw+c]-=.0065f*(Math.max(0,field[0][r*nw+c])-Math.max(0,originalElevation));
            }
        }
        // Absolute-coordinate interpolation (not resize-dependent centre offsets).
        float[] smooth=sampleNative(field[0],nh,nw,ni,nj,i1-2,j1-2,h+4,w+4,scale,1);
        float[] inner=new float[ph*pw];
        for(int r=0;r<ph;r++)System.arraycopy(smooth,(r+1)*(w+4)+1,inner,r*pw,pw);
        float[] noisy=addElevationNoise(inner,smooth,i1-1,j1-1,ph,pw,NATIVE_RESOLUTION/scale);
        var carved=hydrology.carve(noisy,i1-1,j1-1,ph,pw,scale);
        float[] elev=new float[h*w],water=new float[h*w];
        for(int r=0;r<h;r++){System.arraycopy(carved.bed(),(r+1)*pw+1,elev,r*w,w);System.arraycopy(carved.water(),(r+1)*pw+1,water,r*w,w);}
        float[] climate=sampleNative(field[1],nh,nw,ni,nj,i1,j1,h,w,scale,4);
        if(WorldBlueprintManager.generationVersion()>=9)
            for(int r=0;r<h;r++)for(int c=0;c<w;c++)
                climate[r*w+c]-=.0065f*(Math.max(0,elev[r*w+c])-Math.max(0,inner[(r+1)*pw+c+1]));
        short[] biomes=BiomeClassifier.classify(elev,climate,i1,j1,carved.bed(),h,w,NATIVE_RESOLUTION/scale);
        short[][] waters=new short[h][w];
        for(int r=0;r<h;r++)for(int c=0;c<w;c++){
            int p=r*w+c;waters[r][c]=Short.MIN_VALUE;
            if(Float.isFinite(water[p])&&water[p]>0){
                waters[r][c]=(short)Math.max(0,Math.min(32767,Math.floor(water[p])));
                biomes[p]=climate[p]<0?BiomeIds.FROZEN_RIVER:BiomeIds.RIVER;
            }
        }
        return buildHeightmapData(elev,biomes,waters,h,w);
    }

    private static float[] sampleNative(float[] field,int nh,int nw,int ni,int nj,int z0,int x0,int h,int w,int scale,int channels){
        float[] out=new float[h*w*channels];
        for(int r=0;r<h;r++)for(int c=0;c<w;c++){
            double x=(x0+c)/(double)scale-nj,z=(z0+r)/(double)scale-ni;
            int ix=(int)Math.floor(x),iz=(int)Math.floor(z);double fx=x-ix,fz=z-iz;
            for(int ch=0;ch<channels;ch++){
                int p=ch*nh*nw+iz*nw+ix;
                out[ch*h*w+r*w+c]=(float)((field[p]*(1-fx)+field[p+1]*fx)*(1-fz)+(field[p+nw]*(1-fx)+field[p+nw+1]*fx)*fz);
            }
        }
        return out;
    }

    private float[] addElevationNoise(float[] elevSmooth, float[] elevPadded,
                                       int i1, int j1, int H, int W, float pixelSizeM) {
        float[] slopeGradient = sobelGradient(elevPadded, H + 2, W + 2, H, W);
        float[] elevOut = elevSmooth.clone();
        float normFactor = 40f * pixelSizeM / NATIVE_RESOLUTION;
        float ampC = 100f * pixelSizeM / NATIVE_RESOLUTION;
        float ampF = 70f  * pixelSizeM / NATIVE_RESOLUTION;

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float e = elevSmooth[idx];
                if (e < 0f) continue;

                float grad = slopeGradient[idx];
                float sf = Math.min(1f, grad / normFactor);
                sf = sf * sf * (float) Math.sqrt(sf);

                float nx = j1 + c, ny = i1 + r;
                elevOut[idx] = e
                        + ELEV_NOISE_COARSE.GetNoise(nx, ny) * ampC * sf
                        + ELEV_NOISE_FINE.GetNoise(nx, ny)   * ampF * sf;
            }
        }
        return elevOut;
    }

    private static float[] sobelGradient(float[] padded, int pH, int pW, int H, int W) {
        final float[] SOBEL_X = {-1,0,1, -2,0,2, -1,0,1};
        final float[] SOBEL_Y = {-1,-2,-1, 0,0,0, 1,2,1};
        float[] result = new float[H * W];
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float dx = 0, dy = 0;
                for (int k = 0; k < 9; k++) {
                    float v = padded[(r + k/3) * pW + (c + k%3)];
                    dx += v * SOBEL_X[k];
                    dy += v * SOBEL_Y[k];
                }
                dx /= 8f; dy /= 8f;
                result[r * W + c] = (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
        return result;
    }

    private static float[] upsampleClimate(float[] climNative, int nH, int nW,
                                            int cropI1, int cropJ1, int H, int W,
                                            int scale, int upH, int upW) {
        if (climNative == null) return null;
        float[] result = new float[4 * H * W];
        for (int ch = 0; ch < 4; ch++) {
            float[][] chNative = new float[nH][nW];
            for (int r = 0; r < nH; r++)
                System.arraycopy(climNative, ch * nH * nW + r * nW, chNative[r], 0, nW);
            float[][] chUp = LaplacianUtils.bilinearResize(chNative, upH, upW);
            for (int r = 0; r < H; r++)
                for (int c = 0; c < W; c++)
                    result[ch * H * W + r * W + c] = chUp[cropI1 + r][cropJ1 + c];
        }
        return result;
    }

    private static float[] cropFlat(float[][] src, int r0, int c0, int H, int W, int srcH, int srcW) {
        float[] out = new float[H * W];
        for (int r = 0; r < H; r++) {
            int sr = Math.max(0, Math.min(srcH - 1, r0 + r));
            for (int c = 0; c < W; c++)
                out[r * W + c] = src[sr][Math.max(0, Math.min(srcW - 1, c0 + c))];
        }
        return out;
    }

    private static float[][] to2D(float[] flat, int H, int W) {
        float[][] a = new float[H][W];
        for (int r = 0; r < H; r++) System.arraycopy(flat, r * W, a[r], 0, W);
        return a;
    }

    private static HeightmapData buildHeightmapData(float[] elevFlat, short[] biomeFlat, int H, int W) {
        return buildHeightmapData(elevFlat,biomeFlat,null,H,W);
    }

    private static HeightmapData buildHeightmapData(float[] elevFlat, short[] biomeFlat, short[][] water, int H, int W) {
        short[][] heightmap = new short[H][W];
        short[][] biomeIds  = new short[H][W];
        for (int r = 0; r < H; r++)
            for (int c = 0; c < W; c++) {
                float e = elevFlat[r * W + c];
                heightmap[r][c] = (short) Math.max(-32768, Math.min(32767, (int) Math.floor(e)));
                biomeIds[r][c]  = biomeFlat[r * W + c];
            }
        return new HeightmapData(heightmap, biomeIds, water, W, H);
    }
}
