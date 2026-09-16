package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.*;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject whole candidates before placement; never truncate trees at biome boundaries. */
@Mixin(TreeFeature.class)
public class TreeEcotoneMixin {
    @Inject(method="generate(Lnet/minecraft/world/gen/feature/util/FeatureContext;)Z",at=@At("HEAD"),cancellable=true)
    private void td$ecotone(FeatureContext<TreeFeatureConfig> context,CallbackInfoReturnable<Boolean> cir){
        if(!SurfaceTransitions.enabled()||!(context.getGenerator().getBiomeSource() instanceof TerrainDiffusionBiomeSource))return;
        int x=context.getOrigin().getX(),z=context.getOrigin().getZ(),scale=WorldScaleManager.getCurrentScale();
        int tile=com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig.tileSize();
        int tx=Math.floorDiv(x,tile)*tile,tz=Math.floorDiv(z,tile)*tile;
        var data=LocalTerrainProvider.getInstance().fetchHeightmap(tz,tx,tz+tile,tx+tile);
        short biome=data.biomeIds[z-tz][x-tx];
        if(data.contexts!=null){
            if(biome==com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.DESERT||ForestDistricts.woodland(biome))cir.setReturnValue(false);
            return;
        }
    }
}
