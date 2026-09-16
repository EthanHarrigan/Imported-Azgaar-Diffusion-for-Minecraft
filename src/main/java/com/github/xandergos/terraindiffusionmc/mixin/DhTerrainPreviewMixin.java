package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeCatalog;
import com.github.xandergos.terraindiffusionmc.world.*;
import com.seibel.distanthorizons.api.enums.worldGeneration.*;
import com.seibel.distanthorizons.api.objects.data.*;
import com.seibel.distanthorizons.common.wrappers.block.*;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.minecraft.block.Blocks;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
import java.util.function.Consumer;

/** Optional DH rough-pass replacement. SURFACE priority permits normal detailed LOD replacement. */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.common.wrappers.worldGeneration.DhRoughSurfaceGenerator",remap=false)
public abstract class DhTerrainPreviewMixin {
    @Shadow @Final private IServerLevelWrapper serverLevelWrapper;
    @Unique private boolean terrainDiffusion$announced;
    /** DH 3.2.1's wrapper reports a height span from getMaxHeight(), but the rough
     * generator subtracts minY again. API columns already use bottom-relative Y. */
    @ModifyArg(method="generateSurface",at=@At(value="INVOKE",target="Lcom/seibel/distanthorizons/common/wrappers/worldGeneration/DhRoughSurfaceGenerator;populateApiDataPoints(Lcom/seibel/distanthorizons/api/objects/data/IDhApiFullDataSource;Ljava/util/ArrayList;IIZLcom/seibel/distanthorizons/core/wrapperInterfaces/block/IBlockStateWrapper;Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IBiomeWrapper;III)V"),index=7,require=0,remap=false)
    private int terrainDiffusion$roughColumnHeight(int original){
        if(serverLevelWrapper.getWrappedMcObject() instanceof net.minecraft.world.HeightLimitView world)
            return world.getHeight();
        return original;
    }

    @Inject(method="generateSurface",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private void terrainDiffusion$preview(int chunkX,int chunkZ,int posX,int posZ,byte detailLevel,
            IDhApiFullDataSource data,EDhApiDistantGeneratorMode mode,Consumer<IDhApiFullDataSource> consumer,CallbackInfo ci){
        // Use the prepared terrain preview by default, as in Blueprint.21.
        if(Boolean.getBoolean("terrainDiffusion.disableDhPreview")||!WorldBlueprintManager.enabled())return;
        if(!(serverLevelWrapper.getWrappedMcObject() instanceof ServerWorld world)
                ||!(world.getChunkManager().getChunkGenerator().getBiomeSource() instanceof TerrainDiffusionBiomeSource))return;
        try{
            TerrainPreview preview=new TerrainPreview(WorldBlueprintManager.activeStore(),WorldBlueprintManager.detail(),WorldHydrology.preview(),WorldScaleManager.getCurrentScale());
            int width=data.getWidthInDataColumns();
            TerrainPreview.Tile tile=preview.sample(chunkX*16,chunkZ*16,width,1<<detailLevel);
            int min=world.getBottomY(),height=world.getHeight();
            var registry=world.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
            var columns=new ArrayList<DhApiTerrainDataPoint>(3);
            for(int z=0;z<width;z++)for(int x=0;x<width;x++){
                int i=z*width+x;var entry=BiomeCatalog.byId(tile.biomes()[i]);
                var biome=registry.getOrThrow(RegistryKey.of(RegistryKeys.BIOME,Identifier.of(entry==null?"minecraft:plains":entry.key())));
                var bw=BiomeWrapper.getBiomeWrapper(biome,serverLevelWrapper);
                String name=entry==null?"":entry.key();
                int xm=Math.max(0,x-1),xp=Math.min(width-1,x+1),zm=Math.max(0,z-1),zp=Math.min(width-1,z+1);
                float slope=(float)Math.hypot(tile.elevation()[z*width+xp]-tile.elevation()[z*width+xm],tile.elevation()[zp*width+x]-tile.elevation()[zm*width+x]);
                boolean cold=name.contains("snow")||name.contains("frozen")||name.contains("ice");
                var block=cold&&slope<180?Blocks.SNOW_BLOCK:name.contains("badlands")?Blocks.TERRACOTTA:
                    name.contains("desert")||name.contains("beach")?Blocks.SAND:
                    name.contains("stony")||name.contains("jagged")||name.contains("gravelly")||cold?Blocks.STONE:Blocks.GRASS_BLOCK;
                int ground=Math.max(1,Math.min(height-1,HeightConverter.convertToMinecraftHeight((short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(tile.elevation()[i]))))-min));
                if(WorldBlueprintManager.generationVersion()>=12&&!cold&&DesertSurface.dryCompatible(tile.biomes()[i])){
                    int scale=WorldScaleManager.getCurrentScale();
                    double nx=(chunkX*16.0+x*(1<<detailLevel))/scale,nz=(chunkZ*16.0+z*(1<<detailLevel))/scale;
                    long seed=com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.getSeed();
                    var f=com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.at(nx,nz,seed);
                    if(SurfaceTransitions.enabled()?com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.aridRockAt(f.dry(),nx,nz,seed):f.dry()>.5){
                        double gradient=slope/Math.max(1,2*(1<<detailLevel)*30.0/scale);
                        block=name.contains("stony")||gradient>.4?DesertSurface.rock(nx,ground+min-1,nz,f,seed):DesertSurface.sediment(nx,nz,f,gradient,seed,false);
                    }
                }
                if(tile.surfaces()[i]!=null)block=tile.surfaces()[i];
                int wet=Float.isFinite(tile.water()[i])?Math.max(ground,Math.min(height-1,HeightConverter.convertToMinecraftHeight((short)Math.round(tile.water()[i]))-min)):ground;
                columns.clear();
                columns.add(DhApiTerrainDataPoint.create((byte)0,0,15,0,ground,BlockStateWrapper.fromBlockState(block.getDefaultState(),serverLevelWrapper),bw));
                if(wet>ground)columns.add(DhApiTerrainDataPoint.create((byte)0,0,15,ground,wet,BlockStateWrapper.getWaterBlockStateWrapper(serverLevelWrapper),bw));
                columns.add(DhApiTerrainDataPoint.create((byte)0,0,15,wet,height,BlockStateWrapper.AIR,bw));
                data.setApiDataPointColumn(x,z,EDhApiWorldGenerationStep.SURFACE,columns);
            }
            if(!terrainDiffusion$announced){org.slf4j.LoggerFactory.getLogger("TerrainDiffusion-DH").info("Fast DH preview active: {}, no neural inference in rough pass",tile.refined()?"prepared diffusion relief":"imported elevation approximation");terrainDiffusion$announced=true;}
            consumer.accept(data);ci.cancel();
        }catch(java.io.IOException e){throw new IllegalStateException("Could not read Terrain Diffusion preview",e);}
    }
}
