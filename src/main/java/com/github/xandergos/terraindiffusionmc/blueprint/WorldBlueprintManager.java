package com.github.xandergos.terraindiffusionmc.blueprint;

import com.github.xandergos.terraindiffusionmc.pipeline.ConditioningMapProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipelineModelConfig;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Owns the active world's immutable, save-local blueprint. */
public final class WorldBlueprintManager {
    private static volatile BlueprintTileStore store;
    private static volatile String fingerprint="stock";
    private static volatile BlueprintDetailMap detail;
    private static volatile List<BiomeReservation> finalReservations;
    private WorldBlueprintManager(){}
    public static void initializeForWorld(ServerWorld world){
        com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology.clearPreview();
        com.github.xandergos.terraindiffusionmc.world.BiomeTileCache.activate(null);
        finalReservations=null;
        WorldBlueprintSettingsState state=world.getPersistentStateManager().getOrCreate(WorldBlueprintSettingsState.TYPE);
        Path destination=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("terrain-diffusion-blueprint");
        try{
            if(!state.explicit()){
                CompiledBlueprint pending=BlueprintSelectionState.consume();
                if(pending==null)state.disable();else{installBlueprint(pending,destination);state.configure(pending.manifest());}
            }
            if(state.enabled()){
                BlueprintTileStore loaded=new BlueprintTileStore(destination);
                if(!loaded.manifest().generationFingerprint().equals(state.hash()))throw new IOException("Saved blueprint/settings fingerprint does not match world settings");
                BlueprintDetailMap loadedDetail=loaded.manifest().effectiveBiomeClassifierVersion()>=3?BlueprintDetailMap.read(destination.resolve("ecology.bin.gz")):null;
                // Include guidance, edge rules, and reservations, not just raster bytes.
                fingerprint=loaded.manifest().generationFingerprint();
                detail=loadedDetail;
                store=loaded;
            }else{store=null;detail=null;fingerprint="stock";}
            int tile=com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig.tileSize();
            if(store!=null&&tile>=4){
                // v2 excludes biome samples possibly produced by the pre-initialization race.
                String key=fingerprint+"-"+Long.toUnsignedString(world.getSeed())+"-s"+WorldScaleManager.getCurrentScale()+"-p"+com.github.xandergos.terraindiffusionmc.world.VerticalProfile.identity()+"-t"+tile+"-v5";
                com.github.xandergos.terraindiffusionmc.world.BiomeTileCache.activate(new com.github.xandergos.terraindiffusionmc.world.BiomeTileCache(destination.resolve("biome-search-cache").resolve(key),tile/4));
            }
        }catch(IOException e){store=null;fingerprint="error";throw new IllegalStateException("Terrain blueprint could not be loaded; refusing to generate inconsistent chunks",e);}
    }
    public static ConditioningMapProvider provider(long seed){BlueprintTileStore s=store;return s==null?null:new BlueprintConditioningProvider(s,seed);}
    public static String fingerprint(){return fingerprint;}
    public static boolean enabled(){return store!=null;}
    public static int generationVersion(){return store==null?2:store.manifest().effectiveBiomeClassifierVersion();}
    public static BlueprintTileStore activeStore(){return store;}
    public static BlueprintDetailMap detail(){return detail;}
    public static float[][][] conditioningPreview(long seed,int x1,int y1,int x2,int y2){
        BlueprintTileStore s=store;return s==null?null:new BlueprintConditioningProvider(s,seed).sample(x1,y1,x2,y2);
    }
    /**
     * Samples auxiliary authored ecology at block coordinates. The categorical anchor is
     * intentionally nearest-neighbour; the classifier warps and feathers it before use.
     */
    public static EcologySample ecologyAt(double blockX,double blockZ){
        BlueprintTileStore s=store;
        if(s==null)return EcologySample.NONE;
        BlueprintManifest m=s.manifest();
        double divisor=256.0*WorldScaleManager.getCurrentScale();
        int x=(int)Math.floor(blockX/divisor+m.width()/2.0);
        int y=(int)Math.floor(blockZ/divisor+m.height()/2.0);
        if(x<0||x>=m.width()||y<0||y>=m.height())return EcologySample.NONE;
        try{
            double u=blockX/(divisor*m.width())+.5,v=blockZ/(divisor*m.height())+.5;
            int anchor=Math.round(detail==null?s.valueOrDefault(7,x,y,-1):detail.nearest(1,u,v));
            float coast=s.valueOrDefault(9,x,y,Float.POSITIVE_INFINITY),strength=1;
            if(detail!=null&&m.effectiveBiomeClassifierVersion()>=4){
                double kmPerPixel=m.physicalWidthKm()/detail.width;
                float sourceElevation=detail.sample(0,u,v);
                coast=(sourceElevation>=0?1:-1)*detail.coastDistance(u,v)*(float)kmPerPixel;
                double worldBlocksPerPixel=m.width()*256.0*WorldScaleManager.getCurrentScale()/detail.width;
                double transition=m.effectiveBiomeClassifierVersion()>=6?110.0*WorldScaleManager.getCurrentScale():240;
                if(m.effectiveBiomeClassifierVersion()>=13)transition=com.github.xandergos.terraindiffusionmc.world.SurfaceTransitions.nativeWidth()*WorldScaleManager.getCurrentScale();
                strength=detail.categoryAgreement(1,u,v,transition/Math.max(1,worldBlocksPerPixel));
            }
            return new EcologySample(anchor,
                    s.valueOrDefault(5,x,y,Float.NaN),
                    s.valueOrDefault(6,x,y,Float.NaN),
                    detail==null?s.valueOrDefault(8,x,y,0):detail.sample(2,u,v),
                    coast,strength,true);
        }catch(IOException e){throw new IllegalStateException("Failed to read blueprint ecology tile",e);}
    }
    public record EcologySample(int anchor,float sourceTemperature,float sourcePrecipitation,
                                float riverInfluence,float coastDistanceKm,float anchorStrength,boolean authored){
        public static final EcologySample NONE=new EcologySample(-1,Float.NaN,Float.NaN,0,
                Float.POSITIVE_INFINITY,0,false);
    }
    public static List<BiomeReservation> biomeReservations(){
        if(finalReservations!=null)return finalReservations;
        BlueprintTileStore s=store;
        return s==null?List.of():s.manifest().effectiveBiomeReservations();
    }
    public static void setPreparedReservations(List<BiomeReservation> reservations){finalReservations=List.copyOf(reservations);}
    public static BlockLocation reservationCenter(BiomeReservation r){
        BlueprintTileStore s=store;
        if(s==null)return new BlockLocation(0,0,0);
        BlueprintManifest m=s.manifest();
        double mapW=m.width()*256.0*WorldScaleManager.getCurrentScale();
        double mapH=m.height()*256.0*WorldScaleManager.getCurrentScale();
        return new BlockLocation((r.normalizedX()-.5)*mapW,(r.normalizedY()-.5)*mapH,
                r.radiusNativeBlocks()*WorldScaleManager.getCurrentScale());
    }
    public record BlockLocation(double x,double z,double radius){}
    public static Coordinates coordinates(double blockX,double blockZ){
        BlueprintTileStore s=store;if(s==null)return new Coordinates(Double.NaN,Double.NaN,false);
        return coordinatesFor(blockX,blockZ,s.manifest().width(),s.manifest().height(),WorldScaleManager.getCurrentScale());
    }
    public static Coordinates coordinatesFor(double blockX,double blockZ,int coarseWidth,int coarseHeight,int scale){double mapW=coarseWidth*256.0*scale,mapH=coarseHeight*256.0*scale;double lon=blockX/mapW*360.0,lat=Math.max(-90,Math.min(90,-blockZ/mapH*180.0));boolean authored=Math.abs(blockX)<=mapW/2&&Math.abs(blockZ)<=mapH/2;return new Coordinates(lat,lon,authored);}
    public record Coordinates(double latitude,double longitude,boolean authored){}
    public static double mapWidthBlocks(){BlueprintTileStore s=store;return s==null?0:s.manifest().width()*256.0*WorldScaleManager.getCurrentScale();}
    public static double mapHeightBlocks(){BlueprintTileStore s=store;return s==null?0:s.manifest().height()*256.0*WorldScaleManager.getCurrentScale();}
    private static void copyTree(Path from,Path to)throws IOException{
        if(Files.exists(to))throw new IOException("World already contains a terrain-diffusion-blueprint directory");
        try(var paths=Files.walk(from)){for(Path p:paths.toList()){Path q=to.resolve(from.relativize(p).toString());if(Files.isDirectory(p))Files.createDirectories(q);else Files.copy(p,q,StandardCopyOption.COPY_ATTRIBUTES);}}
    }
    private static void installBlueprint(CompiledBlueprint pending,Path destination)throws IOException{
        if(Files.exists(destination))throw new IOException("World already contains a terrain-diffusion-blueprint directory");
        Path staging=destination.resolveSibling("terrain-diffusion-blueprint.installing");
        if(Files.exists(staging))throw new IOException("A previous blueprint installation is incomplete: "+staging);
        try{
            copyTree(pending.directory(),staging);
            BlueprintTileStore check=new BlueprintTileStore(staging);
            if(!check.manifest().dataSha256().equals(pending.manifest().dataSha256()))
                throw new IOException("Copied blueprint hash does not match compiled blueprint");
            try{Files.move(staging,destination,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(staging,destination);}
        }catch(IOException e){
            if(Files.exists(staging))try(var paths=Files.walk(staging)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
            throw e;
        }
    }
}
