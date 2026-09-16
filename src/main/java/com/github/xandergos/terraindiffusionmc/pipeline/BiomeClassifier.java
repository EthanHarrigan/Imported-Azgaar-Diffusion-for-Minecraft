package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.blueprint.BiomeReservation;
import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import com.github.xandergos.terraindiffusionmc.world.VegetationDensity;

/**
 * Rule-based biome classifier port of _classify_biome in minecraft_api.py.
 *
 * <p>Uses fixed-seed FastNoiseLite instances for climate and elevation noise perturbations.
 * Biome IDs match the Python server's _BIOME_ID mapping.
 */
public final class BiomeClassifier {

    // Fixed-seed noise instances (matching Python's module-level _TEMP_NOISE etc.)
    private static final FastNoiseLite TEMP_NOISE, TEMP_NOISE_FINE;
    private static final FastNoiseLite PRECIP_NOISE;
    private static final FastNoiseLite SNOW_NOISE, SNOW_NOISE_FINE;
    private static final FastNoiseLite MANGROVE_BORDER_NOISE;

    static {
        TEMP_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        TEMP_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
        PRECIP_NOISE = makeFnl(12345, 1f/500f, 5, 2f, 0.5f);
        SNOW_NOISE = makeFnl(12345, 1f/500f, 3, 2f, 0.5f);
        SNOW_NOISE_FINE = makeFnl(54321, 1f/128f, 2, 2f, 0.5f);
        MANGROVE_BORDER_NOISE = makeFnl(24681357, 1f/24f, 2, 2f, 0.5f);
    }

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

    // Compatibility aliases retained for tests and explorer callers.
    public static final short PLAINS=BiomeIds.PLAINS,SUNFLOWER_PLAINS=BiomeIds.SUNFLOWER_PLAINS,
            SNOWY_PLAINS=BiomeIds.SNOWY_PLAINS,ICE_SPIKES=BiomeIds.ICE_SPIKES,DESERT=BiomeIds.DESERT,
            SWAMP=BiomeIds.SWAMP,MANGROVE_SWAMP=BiomeIds.MANGROVE_SWAMP,FOREST=BiomeIds.FOREST,
            FLOWER_FOREST=BiomeIds.FLOWER_FOREST,BIRCH_FOREST=BiomeIds.BIRCH_FOREST,
            OLD_GROWTH_BIRCH_FOREST=BiomeIds.OLD_GROWTH_BIRCH_FOREST,DARK_FOREST=BiomeIds.DARK_FOREST,
            PALE_GARDEN=BiomeIds.PALE_GARDEN,TAIGA=BiomeIds.TAIGA,SNOWY_TAIGA=BiomeIds.SNOWY_TAIGA,
            OLD_GROWTH_PINE_TAIGA=BiomeIds.OLD_GROWTH_PINE_TAIGA,
            OLD_GROWTH_SPRUCE_TAIGA=BiomeIds.OLD_GROWTH_SPRUCE_TAIGA,SAVANNA=BiomeIds.SAVANNA,
            SAVANNA_PLATEAU=BiomeIds.SAVANNA_PLATEAU,WINDSWEPT_HILLS=BiomeIds.WINDSWEPT_HILLS,
            WINDSWEPT_GRAVELLY_HILLS=BiomeIds.WINDSWEPT_GRAVELLY_HILLS,
            WINDSWEPT_FOREST=BiomeIds.WINDSWEPT_FOREST,WINDSWEPT_SAVANNA=BiomeIds.WINDSWEPT_SAVANNA,
            JUNGLE=BiomeIds.JUNGLE,SPARSE_JUNGLE=BiomeIds.SPARSE_JUNGLE,BAMBOO_JUNGLE=BiomeIds.BAMBOO_JUNGLE,
            BADLANDS=BiomeIds.BADLANDS,ERODED_BADLANDS=BiomeIds.ERODED_BADLANDS,
            WOODED_BADLANDS=BiomeIds.WOODED_BADLANDS,MEADOW=BiomeIds.MEADOW,CHERRY_GROVE=BiomeIds.CHERRY_GROVE,
            GROVE=BiomeIds.GROVE,SNOWY_SLOPES=BiomeIds.SNOWY_SLOPES,FROZEN_PEAKS=BiomeIds.FROZEN_PEAKS,
            JAGGED_PEAKS=BiomeIds.JAGGED_PEAKS,STONY_PEAKS=BiomeIds.STONY_PEAKS,RIVER=BiomeIds.RIVER,
            FROZEN_RIVER=BiomeIds.FROZEN_RIVER,BEACH=BiomeIds.BEACH,SNOWY_BEACH=BiomeIds.SNOWY_BEACH,
            STONY_SHORE=BiomeIds.STONY_SHORE,WARM_OCEAN=BiomeIds.WARM_OCEAN,
            LUKEWARM_OCEAN=BiomeIds.LUKEWARM_OCEAN,DEEP_LUKEWARM_OCEAN=BiomeIds.DEEP_LUKEWARM_OCEAN,
            OCEAN=BiomeIds.OCEAN,DEEP_OCEAN=BiomeIds.DEEP_OCEAN,COLD_OCEAN=BiomeIds.COLD_OCEAN,
            DEEP_COLD_OCEAN=BiomeIds.DEEP_COLD_OCEAN,FROZEN_OCEAN=BiomeIds.FROZEN_OCEAN,
            DEEP_FROZEN_OCEAN=BiomeIds.DEEP_FROZEN_OCEAN,MUSHROOM_FIELDS=BiomeIds.MUSHROOM_FIELDS,
            FOREST_SPARSE=BiomeIds.FOREST_SPARSE,TAIGA_SPARSE=BiomeIds.TAIGA_SPARSE,
            SNOWY_TAIGA_SPARSE=BiomeIds.SNOWY_TAIGA_SPARSE;

    private static volatile long contextSeed=0x544442494f4d454cL;
    private static boolean modern(){return WorldBlueprintManager.generationVersion()>=3;}
    private static boolean blended(){return WorldBlueprintManager.generationVersion()>=4;}
    public static void configure(long worldSeed,String blueprintHash){
        long h=blueprintHash==null?0:blueprintHash.hashCode();contextSeed=mix64(worldSeed^(h<<32)^2L);
    }

    /**
     * Classify biomes for a grid of pixels.
     *
     * @param elev       elevation in meters, (H, W) row-major
     * @param climate    climate data (5, H, W) row-major or null
     * @param i0         top-left row in world space (for noise sampling)
     * @param j0         top-left col in world space
     * @param elevPadded elevation with 1-pixel padding, (H+2, W+2) row-major
     * @param H          height
     * @param W          width
     * @param pixelSizeM physical size of one pixel in meters
     * @return short array (H, W) with biome IDs
     */
    public static short[] classify(float[] elev, float[] climate, int i0, int j0,
                                    float[] elevPadded, int H, int W, float pixelSizeM) {
        return classify(elev,climate,i0,j0,elevPadded,H,W,pixelSizeM,1);
    }

    /** Variant for explorer/coarse atlases whose adjacent samples are many blocks apart. */
    public static short[] classify(float[] elev,float[] climate,int i0,int j0,float[] elevPadded,
                                   int H,int W,float pixelSizeM,int coordinateStep) {
        short[] out = new short[H * W];
        for (int i = 0; i < H * W; i++) out[i] = PLAINS;

        if (climate == null || climate.length < 4 * H * W) {
            return out;
        }

        // Generate Perlin noise perturbations
        float[] tempNoise = new float[H * W];
        float[] precipNoiseFact = new float[H * W];
        float[] snowNoise = new float[H * W];

        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float nx = j0 + c*coordinateStep, ny = i0 + r*coordinateStep;
                float tnc = TEMP_NOISE.GetNoise(nx, ny);
                float tnf = TEMP_NOISE_FINE.GetNoise(nx, ny);
                tempNoise[idx] = 0.4f * tnc + 0.2f * tnf;

                float pn = PRECIP_NOISE.GetNoise(nx, ny);
                precipNoiseFact[idx] = 1.0f + 0.2f * pn;

                float snc = SNOW_NOISE.GetNoise(nx, ny);
                float snf = SNOW_NOISE_FINE.GetNoise(nx, ny);
                snowNoise[idx] = 3.0f * snc + 2.0f * snf;
            }
        }

        // Compute slope from padded elevation using Sobel (divide by pixelSizeM for ratio)
        float[] slopeRatio = computeSlopeRatio(elevPadded, H, W, pixelSizeM);

        // Process per-pixel
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                float elevVal   = elev[idx];
                float altM      = Math.max(0f, elevVal);
                float slope     = slopeRatio[idx];
                int blockX=j0+c*coordinateStep,blockZ=i0+r*coordinateStep;
                double warpScale=Math.max(1,WorldScaleManager.getCurrentScale());
                double warpX=regionNoise(blockX,blockZ,811,2600)*190*warpScale;
                double warpZ=regionNoise(blockX,blockZ,977,2600)*190*warpScale;
                if(modern()){
                    // 300–600 block regional ecotones at scale 3; coherent patches, not per-block speckles.
                    warpX=regionNoise(blockX,blockZ,811,900)*65*warpScale+regionNoise(blockX,blockZ,819,70*warpScale)*100*warpScale;
                    warpZ=regionNoise(blockX,blockZ,977,900)*65*warpScale+regionNoise(blockX,blockZ,983,70*warpScale)*100*warpScale;
                }
                if(WorldBlueprintManager.generationVersion()>=13){
                    // Preserve authored geography. Boundary material fields supply the local feather.
                    warpX=0;
                    warpZ=0;
                }
                WorldBlueprintManager.EcologySample ecology=WorldBlueprintManager.ecologyAt(blockX+warpX,blockZ+warpZ);
                int anchor=ecology.anchor();
                int pw=W+2,pc=(r+1)*pw+c+1;
                float neighborMean=(elevPadded[pc-1]+elevPadded[pc+1]+elevPadded[pc-pw]+elevPadded[pc+pw])*.25f;
                float concavity=neighborMean-(modern()?elevPadded[pc]:elevVal);
                if(modern()){
                    float regional=LocalTerrainProvider.regionalConcavity(blockX,blockZ);
                    if(Float.isFinite(regional))concavity=regional;
                }

                // Climate channels: [0]=temp, [1]=t_season, [2]=precip, [3]=p_cv
                float temp     = climate[idx] + tempNoise[idx];
                float tSeason  = climate[H * W + idx];
                float precip   = Math.max(0f, climate[2 * H * W + idx]) * precipNoiseFact[idx];
                float pCV      = climate[3 * H * W + idx];

                // Derived climate variables
                float tStd     = tSeason / 100f;
                float tEff     = Math.max(0f, temp + 0.5f * tStd);
                float pet      = Math.max(250f, 250f + 25f * tEff + 0.7f * tEff * tEff);
                float aridity  = precip / Math.max(1f, pet);
                float seasonPenalty = 1f - 0.35f * Math.min(1f, pCV / 100f);
                float treeMoisture = aridity * seasonPenalty;

                // Growing season
                float amplitude = tStd * 1.414f;
                float growingSeason;
                if (amplitude < 0.1f) {
                    growingSeason = temp > 5f ? 365f : 0f;
                } else {
                    float x = (5f - temp) / amplitude;
                    if (x <= -1f) growingSeason = 365f;
                    else if (x >= 1f) growingSeason = 0f;
                    else growingSeason = 365f * (0.5f - (float) Math.asin(Math.max(-1f, Math.min(1f, x))) / (float) Math.PI);
                }

                float gsFactor = Math.max(0f, Math.min(1f, (growingSeason - 60f) / (150f - 60f)));
                float effTreeMoisture = treeMoisture * gsFactor;

                // Slope-dependent bare threshold
                float moistureFactor = Math.max(0f, Math.min(1f, (treeMoisture - 0.35f) / 0.45f));
                float bareThreshold = 0.7f + (1.19f - 0.7f) * moistureFactor;

                // Tree coverage classification
                boolean treesNone = effTreeMoisture < 0.2f;
                boolean tooArid   = treeMoisture < 0.05f;
                boolean tooCold   = growingSeason < 60f;
                boolean barren    = tooArid || tooCold;
                boolean treesSparse    = !treesNone && effTreeMoisture < 0.5f;
                boolean treesForest    = !treesNone && effTreeMoisture >= 0.5f && effTreeMoisture < 0.8f;
                boolean treesDense     = !treesNone && effTreeMoisture >= 0.8f && effTreeMoisture < 1.3f;
                boolean treesRainforest = !treesNone && effTreeMoisture >= 1.3f;

                // Slope overrides
                boolean slopeMedium = slope >= 0.62f && slope < bareThreshold;
                boolean slopeBare   = slope >= bareThreshold;
                if (slopeMedium) {
                    if (treesForest || treesDense || treesRainforest) { treesSparse = true; }
                    treesForest = treesForest && false; treesDense = false; treesRainforest = false;
                }
                if (slopeBare) {
                    treesNone = true; treesSparse = false; treesForest = false;
                    treesDense = false; treesRainforest = false;
                }

                // Snow classification
                float snowTemp = temp + snowNoise[idx];
                boolean isSteep = slope > 0.78f;
                boolean hasSnow = (snowTemp < 0f || (temp < -6f && precip > 300f)) && precip > 150f && !isSteep;

                // Elevation/temp bands
                boolean isOcean   = elevVal < 0f;
                boolean mountains = altM > 2500f;
                boolean lowland   = altM < 200f;
                boolean frozen    = temp < -5f;
                boolean cold      = temp >= -5f && temp < 5f;
                boolean cool      = temp >= 5f  && temp < 12f;
                boolean temperate = temp >= 12f && temp < 20f;
                boolean warm      = temp >= 20f && temp < 26f;
                boolean hot       = temp >= 26f;

                short biome = PLAINS;

                boolean badlandsCandidate = !isOcean && !lowland && altM >= 200f && altM < 1800f
                        && (warm || hot) && precip < 500f && treesNone;
                boolean meadowCandidate = !isOcean && altM >= 600f && altM < 2500f
                        && temp >= 5f && temp < 20f && precip >= 500f && precip < 2500f
                        && !slopeMedium && !slopeBare && treeMoisture >= .35f && treeMoisture < 1.15f;

                if (isOcean) {
                    boolean deep= -elevVal > 850f || (-elevVal>550f && ecology.coastDistanceKm()<-15);
                    if (frozen) biome = deep ? DEEP_FROZEN_OCEAN : FROZEN_OCEAN;
                    else if (cold) biome = deep ? DEEP_COLD_OCEAN : COLD_OCEAN;
                    else if (temp >= 18f) biome = deep ? DEEP_LUKEWARM_OCEAN : (temp>=24f?WARM_OCEAN:LUKEWARM_OCEAN);
                    else biome = deep ? DEEP_OCEAN : OCEAN;
                } else if (mountains) {
                    // A lower-slope high-elevation fallback makes the registered peak
                    // biomes reachable even when the learned terrain is broad rather
                    // than cliff-like.
                    if (slopeBare || (altM > 3500f && slope >= .35f)) {
                        biome = hasSnow ? FROZEN_PEAKS : STONY_PEAKS;
                    } else if (hasSnow) {
                        if (treesNone) biome = SNOWY_SLOPES;
                        else if (treesSparse || treesForest) biome = SNOWY_TAIGA_SPARSE;
                        else biome = SNOWY_TAIGA;
                    } else if (treesNone) {
                        if (barren) biome = WINDSWEPT_HILLS;
                        else if (treeMoisture < 0.35f || precip < 350f) biome = modern()?WINDSWEPT_HILLS:GROVE;
                        else biome = PLAINS;
                    } else if (treesSparse || treesForest) {
                        biome = TAIGA_SPARSE;
                    } else {
                        biome = TAIGA;
                    }
                } else {
                    // Lowland/midland
                    if (hasSnow && treesNone) {
                        biome = SNOWY_PLAINS;
                    } else if (hasSnow) {
                        biome = (treesSparse || treesForest) ? SNOWY_TAIGA_SPARSE : SNOWY_TAIGA;
                    } else if (meadowCandidate) {
                        biome = MEADOW;
                    } else if (badlandsCandidate) {
                        biome = BADLANDS;
                    } else if (treesNone) {
                        if (warm || hot) biome = DESERT;
                        else if (barren && !lowland && (cold || cool || temperate)) biome = modern()?WINDSWEPT_HILLS:GROVE;
                        else if (treeMoisture < 0.35f || precip < 350f) biome = modern()?PLAINS:GROVE;
                        else biome = PLAINS;
                    } else if (treesSparse || treesForest) {
                        if (hot) biome = JUNGLE;
                        else if (warm && (treesSparse || (treesForest && treeMoisture < .65f)) && !slopeMedium) biome = SAVANNA;
                        else if (warm && treesForest) biome = FOREST_SPARSE;
                        else if (temperate) biome = FOREST_SPARSE;
                        else biome = TAIGA_SPARSE;
                    } else if (treesDense) {
                        if (hot) biome = JUNGLE;
                        else if (warm && lowland) biome = SWAMP;
                        else if (cool || cold) biome = TAIGA;
                        else biome = FOREST;
                    } else { // rainforest
                        if (hot || (warm && temp >= 18f && tStd < 5f)) biome = JUNGLE;
                        else if (lowland) biome = SWAMP;
                        else if (cool || cold) biome = TAIGA;
                        else biome = FOREST;
                    }
                }

                // Bare slope override for lowland/non-mountain cliffs
                if (slopeBare && !isOcean && !mountains) {
                    biome = hasSnow ? FROZEN_PEAKS : STONY_PEAKS;
                }

                if(!isOcean){
                    biome=applySoftAnchor(biome,anchor,ecology.anchorStrength(),temp,precip,altM,slope,treeMoisture,blockX,blockZ);
                    biome=deriveRegionalVariant(biome,temp,precip,altM,slope,concavity,treeMoisture,
                            ecology,blockX,blockZ);
                    biome=applyCoastAndRiver(biome,temp,elevVal,slope,concavity,ecology,blockX,blockZ);
                    // Keep village, mansion, hut, and temple biome tags off steep footprints;
                    // vanilla structures can then use their normal terrain adaptation on broad ground.
                    if(!blended()&&slope>.42f&&isFlatStructureBiome(biome))
                        biome=temp>18&&precip<900?WINDSWEPT_SAVANNA:
                                (treeMoisture>.55f?WINDSWEPT_FOREST:WINDSWEPT_HILLS);
                }
                if(anchor>=1000){
                    short explicit=(short)(anchor-1000);
                    if((BiomeIds.isOcean(explicit)==isOcean&&!BiomeIds.isShore(explicit))||
                            (BiomeIds.isShore(explicit)&&!isOcean&&altM<220))biome=explicit;
                }
                if(WorldBlueprintManager.generationVersion()>=6&&anchor<1000&&!isOcean)
                    biome=com.github.xandergos.terraindiffusionmc.world.MountainZones.biome(biome,altM,temp,precip,slope,concavity,
                            blockX,blockZ,WorldScaleManager.getCurrentScale(),LocalTerrainProvider.getSeed());
                if(WorldBlueprintManager.generationVersion()>=10&&WorldBlueprintManager.generationVersion()<13&&anchor<1000&&!isOcean&&slope<.36f){
                    float density=VegetationDensity.at(blockX,blockZ,biome,LocalTerrainProvider.getSeed());
                    if(biome==FOREST&&density<.58f)biome=FOREST_SPARSE;
                    else if(biome==FOREST_SPARSE&&density>.70f&&precip>620)biome=FOREST;
                    else if(biome==TAIGA&&density<.56f)biome=TAIGA_SPARSE;
                    else if(biome==TAIGA_SPARSE&&density>.72f&&precip>520)biome=TAIGA;
                    else if(biome==JUNGLE&&density<.70f)biome=SPARSE_JUNGLE;
                }
                if(modern()&&biome==SWAMP&&slope<.18f&&temp>9&&sourceForestNearby(blockX,blockZ)
                        &&regionNoise(blockX,blockZ,617,90*warpScale)>-.2)biome=MANGROVE_SWAMP;
                // Let a few coherent woodland fingers cross an authored dry boundary. Warm
                // fringes use savanna; cooler ones use the deliberately sparse forest biome.
                if(blended()&&anchor==1&&ecology.anchorStrength()<.68f&&ecology.anchorStrength()>.40f
                        &&sourceForestNearby(blockX,blockZ)&&regionNoise(blockX,blockZ,641,150*warpScale)>.18f)
                    biome=temp>19?SAVANNA:FOREST_SPARSE;
                // Reserve a surviving swamp core after the natural mangrove border pass.
                if(WorldBlueprintManager.generationVersion()>=12&&anchor<1000&&!isOcean&&altM<1800&&temp>10&&slope<.36){
                    var desert=com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.at(blockX/(double)WorldScaleManager.getCurrentScale(),blockZ/(double)WorldScaleManager.getCurrentScale(),LocalTerrainProvider.getSeed());
                    if(desert.dry()>.04&&desert.dry()<.90&&desert.waterProtection()>.7){
                        // Broad low-density ecotone; leave wet, rare, mountainous and explicit biomes alone.
                        if(WorldBlueprintManager.generationVersion()>=13){
                            double pick=.5; // One continuous desert boundary, no block-scale identity lottery.
                            double dry=desert.dry();
                            if(biome==DESERT&&pick>dry)biome=temp>18?SAVANNA:PLAINS;
                            else if(biome==FOREST&&pick<dry)biome=FOREST_SPARSE;
                            else if(biome==JUNGLE&&pick<dry)biome=SPARSE_JUNGLE;
                        }else{
                            if(biome==DESERT&&desert.dry()<.62)biome=temp>18?SAVANNA:PLAINS;
                            else if(biome==FOREST&&desert.dry()>.20)biome=FOREST_SPARSE;
                            else if(biome==JUNGLE&&desert.dry()>.20)biome=SPARSE_JUNGLE;
                        }
                    }
                }
                // Authored Sand Sea is a vast desert province, not a climate-derived patchwork.
                // This remains inside the sole final classifier; water ownership is applied later.
                if(WorldBlueprintManager.generationVersion()>=13&&!isOcean&&
                        com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.sandSea(
                                blockX/(double)WorldScaleManager.getCurrentScale(),blockZ/(double)WorldScaleManager.getCurrentScale())>.90)
                    biome=DESERT;
                if(WorldBlueprintManager.generationVersion()>=13&&!isOcean&&biome==DESERT&&anchor<1000&&
                        com.github.xandergos.terraindiffusionmc.world.DesertProvinces.mountainContact(
                            blockX/(double)WorldScaleManager.getCurrentScale(),blockZ/(double)WorldScaleManager.getCurrentScale(),LocalTerrainProvider.getSeed()))
                    biome=BADLANDS;

                biome=applyCoverageReservation(biome,isOcean,elevVal,slope,blockX,blockZ);
                // Climate and coverage reservations cannot invent a warm desert outside
                // authored dry provinces. Apply last so reservations cannot bypass the map.
                if(WorldBlueprintManager.generationVersion()>=13&&aridBiome(biome)&&
                        !com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.authoredArid(
                                blockX/(double)WorldScaleManager.getCurrentScale(),blockZ/(double)WorldScaleManager.getCurrentScale()))
                    biome=nonAridFallback(anchor,temp,slope);

                out[idx] = biome;
            }
        }
        // Apply this after the normal classification so it only changes a narrow,
        // irregular border. It intentionally does not alter the terrain or climate
        // model: mangrove is a single-player biome presentation rule.
        if(!modern())applyMangroveBorder(out, i0, j0, H, W);
        return out;
    }

    private static short applySoftAnchor(short biome,int anchor,float anchorStrength,float temp,float precip,float alt,float slope,
                                         float moisture,int x,int z){
        if(anchor<0||alt>2300||slope>.68f)return biome;
        // A broad, warped gate preserves most of the authored family while leaving natural
        // ecotones in which final climate and relief win.
        if(regionNoise(x,z,101,1800)<-.58)return biome;
        if(blended()&&anchorStrength<.43f)return biome;
        boolean fringe=blended()&&anchorStrength<.72f;
        return switch(anchor){
            case 1 -> temp>10?(fringe?(temp>18?SAVANNA:PLAINS):(WorldBlueprintManager.generationVersion()<13&&alt>280&&regionNoise(x,z,102,1500)>.35?BADLANDS:DESERT)):biome;
            case 2 -> temp<1?SNOWY_PLAINS:(alt>350?WINDSWEPT_HILLS:PLAINS); // never warm badlands
            case 3 -> temp>12?(slope>.42?WINDSWEPT_SAVANNA:SAVANNA):biome;
            case 4 -> temp<0?SNOWY_PLAINS:PLAINS;
            case 5 -> temp>18?(fringe?SAVANNA:(moisture>.62?SPARSE_JUNGLE:SAVANNA)):(fringe?FOREST_SPARSE:FOREST);
            case 6 -> temp<5?(fringe?TAIGA_SPARSE:TAIGA):(fringe?FOREST_SPARSE:(moisture>1.05&&(!modern()||regionNoise(x,z,109,1300)>.73)?DARK_FOREST:FOREST));
            case 7 -> temp>17?(fringe?SPARSE_JUNGLE:JUNGLE):(fringe?FOREST_SPARSE:FOREST);
            case 8 -> temp<6?(fringe?TAIGA_SPARSE:TAIGA):(fringe?FOREST_SPARSE:(alt<180&&precip>1300?SWAMP:(!modern()||regionNoise(x,z,109,1300)>.45?DARK_FOREST:FOREST)));
            case 9 -> temp<0?(fringe?SNOWY_TAIGA_SPARSE:SNOWY_TAIGA):(fringe?TAIGA_SPARSE:TAIGA);
            case 10 -> temp<2?SNOWY_PLAINS:(moisture<.3?PLAINS:TAIGA);
            case 11 -> alt>1100?SNOWY_SLOPES:SNOWY_PLAINS;
            case 12 -> alt<260?(temp>18?MANGROVE_SWAMP:SWAMP):FOREST;
            default -> biome;
        };
    }

    private static short deriveRegionalVariant(short biome,float temp,float precip,float alt,float slope,
                                                float concavity,float moisture,
                                                WorldBlueprintManager.EcologySample ecology,int x,int z){
        float broad=regionNoise(x,z,203,1350),medium=regionNoise(x,z,211,520),fine=regionNoise(x,z,223,210);
        if(WorldBlueprintManager.generationVersion()>=13){
            double k=WorldScaleManager.getCurrentScale()/3.0*com.github.xandergos.terraindiffusionmc.world.SurfaceTransitions.mapFactor();
            float grain=0; // Categorical region ownership must not contain material grain.
            // Fractional variant boundaries: retain the broad cores and break up their margins.
            broad=regionNoise(x,z,203,(float)(1350*k))+.16f*grain;
            medium=regionNoise(x,z,211,(float)(520*k))+.20f*grain;
            fine=regionNoise(x,z,223,(float)(210*k))+.16f*grain;
        }
        // Relative mountain morphology. Peak cores use roughness; shoulders remain groves/meadows.
        if(alt>2500){
            if(temp<-3&&slope>.43f&&medium>.05)return alt>4800&&(slope>.72f||fine>.48f)?JAGGED_PEAKS:FROZEN_PEAKS;
            if(temp>=-3&&slope>.48f)return STONY_PEAKS;
            if(temp<2){
                if(blended()&&alt<3400&&slope<.26f&&moisture>.42f&&medium<.38f)return moisture>.68f?GROVE:SNOWY_TAIGA_SPARSE;
                return SNOWY_SLOPES;
            }
        }
        if((biome==WINDSWEPT_HILLS||alt>700)&&slope>.34f){
            if(temp>17&&precip<900&&broad>.15)return WINDSWEPT_SAVANNA;
            if(moisture>.55&&temp>4&&broad>-.1)return WINDSWEPT_FOREST;
            if(moisture<.5&&medium>.05)return WINDSWEPT_GRAVELLY_HILLS;
        }
        if(biome==SAVANNA&&alt>320&&slope<.23f&&concavity<(modern()?-15:45)&&broad>.02)return SAVANNA_PLATEAU;
        if(modern()&&(biome==SNOWY_TAIGA||biome==SNOWY_TAIGA_SPARSE)&&alt>800&&alt<2600&&temp>-7&&slope<.4)return GROVE;
        if(biome==PLAINS){
            if(temp>9&&temp<23&&precip>450&&medium>.68&&fine>-.2)return SUNFLOWER_PLAINS;
        }
        if(biome==SNOWY_PLAINS&&temp<-7&&medium>.47)return ICE_SPIKES;
        if(biome==FOREST||biome==FOREST_SPARSE){
            if(temp>7&&temp<19&&precip>850&&broad>.56)return DARK_FOREST;
            if(temp>6&&temp<20&&precip>600&&medium>.34){
                if(broad>.12)return medium>(modern()?.43:.58)?OLD_GROWTH_BIRCH_FOREST:BIRCH_FOREST;
                if(fine>.36)return FLOWER_FOREST;
            }
            if(alt>650&&temp>3&&temp<16&&precip>650&&slope<.38&&medium>.48)return CHERRY_GROVE;
            return biome==FOREST_SPARSE&&!modern()?FOREST:biome;
        }
        if(biome==DARK_FOREST){
            if(precip>1050&&((concavity>8&&medium>.42)||(slope>.2&&slope<.48&&fine>.62)))return PALE_GARDEN;
            if(precip>1700&&concavity>15&&alt<500&&broad>.7)return MUSHROOM_FIELDS;
        }
        if(biome==TAIGA||biome==TAIGA_SPARSE){
            if(precip>850&&broad>.24)return OLD_GROWTH_SPRUCE_TAIGA;
            if(precip>450&&medium>.27)return OLD_GROWTH_PINE_TAIGA;
            return biome==TAIGA_SPARSE&&!modern()?TAIGA:biome;
        }
        if(biome==JUNGLE){
            if(temp>20&&precip>1800&&slope<.25&&medium>.58)return BAMBOO_JUNGLE;
            if(moisture<1.15||broad<-.42)return SPARSE_JUNGLE;
        }
        if(biome==BADLANDS){
            if(slope>.28&&medium>.05)return ERODED_BADLANDS;
            if(precip>260&&slope<.3&&broad>.18)return WOODED_BADLANDS;
        }
        if((biome==MEADOW||biome==GROVE)&&alt>900&&temp>4&&temp<16&&precip>650&&slope<.4&&medium>.38)
            return CHERRY_GROVE;
        return biome;
    }

    private static short applyCoastAndRiver(short biome,float temp,float elev,float slope,float concavity,
                                             WorldBlueprintManager.EcologySample ecology,int x,int z){
        float coast=Math.abs(ecology.coastDistanceKm());
        float coastLimit=blended()?2.4f:18f;
        float maxElevation=blended()?110f:180f;
        if(coast<coastLimit&&elev>=0&&elev<maxElevation&&slope<(blended()?.34f:.42f)){
            float edge=regionNoise(x,z,307,170);
            if(slope>(blended()?.27f:.20f)||edge<(blended()?-.72f:-.48f))return STONY_SHORE;
            if(slope<(blended()?.11f:.13f)&&edge>(blended()?-.08f:.02f))return temp<0?SNOWY_BEACH:BEACH;
        }
        // Initial conservative river pass: source river corridor + an already low, concave TD
        // surface. It labels water ecology but deliberately does not carve a hillside channel.
        if(!modern()&&ecology.riverInfluence()>.7f&&elev>=0&&elev<75&&slope<.16f&&concavity>-.5f
                &&Math.abs(regionNoise(x,z,331,95))<.18f)return temp<0?FROZEN_RIVER:RIVER;
        return biome;
    }

    private static volatile ReservationIndex reservationIndex;
    private static ReservationIndex reservations(){
        var list=WorldBlueprintManager.biomeReservations();var store=WorldBlueprintManager.activeStore();
        int scale=WorldScaleManager.getCurrentScale();var index=reservationIndex;
        if(index!=null&&index.source==list&&index.store==store&&index.scale==scale&&index.seed==contextSeed)return index;
        synchronized(BiomeClassifier.class){
            index=reservationIndex;
            if(index==null||index.source!=list||index.store!=store||index.scale!=scale||index.seed!=contextSeed)
                reservationIndex=index=new ReservationIndex(list,store,scale,contextSeed);
        }
        return index;
    }
    static boolean aridBiome(short id){return id==DESERT||id==BADLANDS||id==ERODED_BADLANDS||id==WOODED_BADLANDS;}
    static short nonAridFallback(int anchor,float temp,float slope){
        if(slope>.42f)return temp<0?SNOWY_SLOPES:WINDSWEPT_HILLS;
        return switch(anchor){
            case 2,10 -> temp<0?SNOWY_PLAINS:PLAINS;
            case 3,5 -> SAVANNA;
            case 6 -> FOREST;
            case 7 -> JUNGLE;
            case 8 -> DARK_FOREST;
            case 9 -> temp<0?SNOWY_TAIGA:TAIGA;
            case 11 -> SNOWY_PLAINS;
            case 12 -> SWAMP;
            default -> temp<0?SNOWY_PLAINS:PLAINS;
        };
    }
    private static short applyCoverageReservation(short current,boolean ocean,float elev,float slope,int x,int z){
        short selected=current;double best=0;
        Double coast=null;var index=reservations();
        for(var entry:modern()?index.at(x,z):index.all){
            BiomeReservation reservation=entry.reservation();
            WorldBlueprintManager.BlockLocation p=entry.center();
            double radius=Math.max(1,p.radius());
            if(modern()&&(Math.abs(x-p.x())>radius*1.7||Math.abs(z-p.z())>radius*1.7))continue;
            short target=reservation.biomeId();
            if(BiomeIds.isOcean(target)!=ocean&&!BiomeIds.isShore(target))continue;
            if(BiomeIds.isShore(target)&&(ocean||elev>220))continue;
            if(blended()&&BiomeIds.isShore(target)){
                if(coast==null)coast=(double)WorldBlueprintManager.ecologyAt(x,z).coastDistanceKm();
                if(Math.abs(coast)>3.2)continue;
            }
            if(!ocean&&!BiomeIds.isShore(target)&&isFlatStructureBiome(target)&&slope>.40f)continue;
            double dx=x-p.x()+regionNoise(x,z,701+target,Math.max(180,radius*.9))*radius*.24;
            double dz=z-p.z()+regionNoise(x,z,809+target,Math.max(180,radius*.9))*radius*.24;
            double aspect=entry.aspect();
            double d=Math.hypot(dx/(radius*aspect),dz/(radius/aspect));
            double strength=1-d+regionNoise(x,z,401+target,Math.max(90,p.radius()*.42))*.20;
            if(modern())strength-=(regionNoise(x,z,431+target,70*WorldScaleManager.getCurrentScale())+1)*.5*Math.min(.4,80*WorldScaleManager.getCurrentScale()/radius);
            if(strength>best&&compatibleTerrain(target,elev,slope)){best=strength;selected=target;}
        }
        if(blended()&&selected!=current&&best<.20){
            if(selected==WINDSWEPT_FOREST||selected==FOREST||selected==BIRCH_FOREST||selected==DARK_FOREST||selected==FLOWER_FOREST)return FOREST_SPARSE;
            if(selected==WINDSWEPT_SAVANNA)return SAVANNA;
            if(selected==OLD_GROWTH_PINE_TAIGA||selected==OLD_GROWTH_SPRUCE_TAIGA)return TAIGA_SPARSE;
            if(selected==STONY_SHORE)return current;
        }
        return selected;
    }

    private static boolean compatibleTerrain(short id,float elev,float slope){
        if(BiomeIds.isOcean(id))return elev<0;
        if(BiomeIds.isShore(id))return elev>=0&&elev<220;
        if(id==RIVER||id==FROZEN_RIVER)return !modern()&&elev>=0&&elev<100&&slope<.2f;
        if(id==FROZEN_PEAKS||id==JAGGED_PEAKS||id==STONY_PEAKS)return elev>500;
        if(blended()&&(id==WINDSWEPT_HILLS||id==WINDSWEPT_FOREST||id==WINDSWEPT_SAVANNA))return elev>150&&slope>.15f;
        if(blended()&&id==WINDSWEPT_GRAVELLY_HILLS)return elev>200&&slope>.22f;
        return elev>=0;
    }
    private static boolean isFlatStructureBiome(short id){
        return id==PLAINS||id==DESERT||id==SAVANNA||id==TAIGA||id==SNOWY_PLAINS||
                id==SWAMP||id==JUNGLE||id==DARK_FOREST;
    }

    private static boolean sourceForestNearby(int x,int z){
        int radius=70*WorldScaleManager.getCurrentScale();
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            if(dx==0&&dz==0)continue;
            int anchor=WorldBlueprintManager.ecologyAt(x+dx*radius,z+dz*radius).anchor();
            if(anchor==6||anchor==8||anchor==7)return true;
            if(anchor>=1000){
                int id=anchor-1000;
                if(id==FOREST||id==FLOWER_FOREST||id==BIRCH_FOREST||id==OLD_GROWTH_BIRCH_FOREST||id==DARK_FOREST||id==PALE_GARDEN||id==JUNGLE||id==SPARSE_JUNGLE||id==BAMBOO_JUNGLE)return true;
            }
        }
        return false;
    }

    /** Smooth deterministic value noise; independent of query order and tile boundaries. */
    static float regionNoise(double x,double z,long salt,double period){
        double gx=x/period,gz=z/period;long x0=(long)Math.floor(gx),z0=(long)Math.floor(gz);
        double fx=smooth01(gx-x0),fz=smooth01(gz-z0);
        double a=hashUnit(x0,z0,salt),b=hashUnit(x0+1,z0,salt),c=hashUnit(x0,z0+1,salt),d=hashUnit(x0+1,z0+1,salt);
        return(float)((a+(b-a)*fx)*(1-fz)+(c+(d-c)*fx)*fz);
    }
    private static double smooth01(double x){return x*x*(3-2*x);}
    private static double hashUnit(long x,long z,long salt){long h=mix64(contextSeed^mix64(x*0x9E3779B97F4A7C15L)^mix64(z*0xC2B2AE3D27D4EB4FL)^salt);return ((h>>>11)*(1.0/(1L<<53)))*2-1;}
    private static long mix64(long z){z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;z=(z^(z>>>27))*0x94d049bb133111ebL;return z^(z>>>31);}

    /** Converts a deterministic, fuzzy subset of swamp cells beside forest into mangrove swamp. */
    static void applyMangroveBorder(short[] biomes, int i0, int j0, int H, int W) {
        if (biomes == null || biomes.length < H * W) return;
        short[] original = biomes.clone();
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int idx = r * W + c;
                if (original[idx] != SWAMP) continue;
                int forestDistance = nearestForestDistance(original, r, c, H, W, 2);
                if (forestDistance < 0) continue;
                float chance = forestDistance == 1 ? .68f : .32f;
                float noise = .5f * (MANGROVE_BORDER_NOISE.GetNoise(j0 + c, i0 + r) + 1f);
                if (noise < chance) biomes[idx] = MANGROVE_SWAMP;
            }
        }
    }

    private static int nearestForestDistance(short[] biomes, int r, int c, int H, int W, int radius) {
        int best = Integer.MAX_VALUE;
        for (int dr = -radius; dr <= radius; dr++) {
            for (int dc = -radius; dc <= radius; dc++) {
                if (dr == 0 && dc == 0) continue;
                int distance = Math.max(Math.abs(dr), Math.abs(dc));
                if (distance >= best) continue;
                int rr = r + dr, cc = c + dc;
                if (rr < 0 || rr >= H || cc < 0 || cc >= W) continue;
                short neighbor = biomes[rr * W + cc];
                if (neighbor == FOREST || neighbor == FOREST_SPARSE) best = distance;
            }
        }
        return best == Integer.MAX_VALUE ? -1 : best;
    }

    private static float[] computeSlopeRatio(float[] elevPadded, int H, int W, float pixelSizeM) {
        // Sobel kernels / 8 applied to (H+2, W+2) padded array → (H, W) output
        float[] slope = new float[H * W];
        int PW = W + 2;
        float[] sx = {-1,0,1, -2,0,2, -1,0,1};
        float[] sy = {-1,-2,-1, 0,0,0, 1,2,1};
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                float dx = 0, dy = 0;
                for (int kr = 0; kr < 3; kr++)
                    for (int kc = 0; kc < 3; kc++) {
                        float v = elevPadded[(r + kr) * PW + (c + kc)];
                        dx += v * sx[kr * 3 + kc];
                        dy += v * sy[kr * 3 + kc];
                    }
                dx /= 8f; dy /= 8f;
                slope[r * W + c] = (float) Math.sqrt(dx * dx + dy * dy) / pixelSizeM;
            }
        }
        return slope;
    }
}
