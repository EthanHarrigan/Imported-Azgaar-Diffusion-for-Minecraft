package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.*;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import net.minecraft.world.gen.feature.RandomPatchFeature;
import net.minecraft.world.gen.feature.RandomPatchFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomPatchFeature.class)
public class SandSeaPatchMixin {
    @Inject(method="generate(Lnet/minecraft/world/gen/feature/util/FeatureContext;)Z",at=@At("HEAD"),cancellable=true)
    private void td$sandSea(FeatureContext<RandomPatchFeatureConfig> context,CallbackInfoReturnable<Boolean> cir){
        if(!SurfaceTransitions.enabled()||!(context.getGenerator().getBiomeSource() instanceof TerrainDiffusionBiomeSource))return;
        int x=context.getOrigin().getX(),z=context.getOrigin().getZ(),scale=WorldScaleManager.getCurrentScale();
        int tile=com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig.tileSize();
        int tx=Math.floorDiv(x,tile)*tile,tz=Math.floorDiv(z,tile)*tile;
        var data=LocalTerrainProvider.getInstance().fetchHeightmap(tz,tx,tz+tile,tx+tile);
        if(data.contexts!=null){
            var c=data.contexts[z-tz][x-tx];
            if(ForestDistricts.woodland(c.biome())){
                var district=ForestDistricts.at(x,z,LocalTerrainProvider.getSeed());
                if(district.path()>.2||district.clearing()>.8)cir.setReturnValue(false);
                return;
            }
            if(c.biome()!=com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.DESERT)return;
        }
        double sea=DesertTerrain.sandSea(x/(double)scale,z/(double)scale);
        // Full Sand-Sea cores have no vanilla random-patch vegetation. The
        // transition band remains eligible and is handled by the normal biome.
        if(sea>.90||!SurfaceTransitions.pick(1-sea,x,z,LocalTerrainProvider.getSeed()^0x53414E4450415443L))
            cir.setReturnValue(false);
    }
}
