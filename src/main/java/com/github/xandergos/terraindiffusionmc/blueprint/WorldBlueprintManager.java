package com.github.xandergos.terraindiffusionmc.blueprint;

import com.github.xandergos.terraindiffusionmc.pipeline.ConditioningMapProvider;
import com.github.xandergos.terraindiffusionmc.pipeline.WorldPipelineModelConfig;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import java.io.IOException;
import java.nio.file.*;

/** Owns the active world's immutable, save-local blueprint. */
public final class WorldBlueprintManager {
    private static volatile BlueprintTileStore store;
    private static volatile String fingerprint="stock";
    private WorldBlueprintManager(){}
    public static void initializeForWorld(ServerWorld world){
        WorldBlueprintSettingsState state=world.getPersistentStateManager().getOrCreate(WorldBlueprintSettingsState.TYPE);
        Path destination=world.getServer().getSavePath(WorldSavePath.ROOT).resolve("terrain-diffusion-blueprint");
        try{
            if(!state.explicit()){
                CompiledBlueprint pending=BlueprintSelectionState.consume();
                if(pending==null)state.disable();else{copyTree(pending.directory(),destination);state.configure(pending.manifest());}
            }
            if(state.enabled()){
                store=new BlueprintTileStore(destination);
                if(!store.manifest().dataSha256().equals(state.hash()))throw new IOException("Saved blueprint hash does not match world settings");
                fingerprint=state.hash();
            }else{store=null;fingerprint="stock";}
        }catch(IOException e){store=null;fingerprint="error";throw new IllegalStateException("Terrain blueprint could not be loaded; refusing to generate inconsistent chunks",e);}
    }
    public static ConditioningMapProvider provider(long seed){BlueprintTileStore s=store;return s==null?null:new BlueprintConditioningProvider(s,seed);}
    public static String fingerprint(){return fingerprint;}
    public static boolean enabled(){return store!=null;}
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
}
