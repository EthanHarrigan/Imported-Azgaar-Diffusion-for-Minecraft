package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.TerrainDiffusionDensityFunction;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's absolute -54 lava plane must not flood a lowered overworld. */
@Mixin(NoiseChunkGenerator.class)
public abstract class OceanFluidMixin {
    @Inject(method="createFluidLevelSampler",at=@At("HEAD"),cancellable=true)
    private static void terrainDiffusion$relativeFluids(ChunkGeneratorSettings settings,
            CallbackInfoReturnable<AquiferSampler.FluidLevelSampler> cir) {
        if (!(settings.noiseRouter().finalDensity() instanceof TerrainDiffusionDensityFunction)) return;
        int lavaY=Math.min(settings.generationShapeConfig().minimumY()+10,settings.seaLevel());
        var lava=new AquiferSampler.FluidLevel(lavaY,Blocks.LAVA.getDefaultState());
        var water=new AquiferSampler.FluidLevel(settings.seaLevel(),settings.defaultFluid());
        var empty=new AquiferSampler.FluidLevel(Integer.MIN_VALUE,Blocks.AIR.getDefaultState());
        cir.setReturnValue((x,y,z)->SharedConstants.DISABLE_FLUID_GENERATION?empty:y<lavaY?lava:water);
    }
}
