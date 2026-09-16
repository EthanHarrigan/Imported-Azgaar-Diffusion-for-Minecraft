package com.github.xandergos.terraindiffusionmc.mixin;

import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Keep vanilla's sea+1 pillar baseline relative when the sea is lowered. */
@Mixin(SurfaceBuilder.class)
public abstract class BadlandsPillarHeightMixin {
    @Shadow @Final private int seaLevel;
    @ModifyConstant(method="placeBadlandsPillar",constant=@Constant(doubleValue=64.0))
    private double terrainDiffusion$relativePillarBase(double original) {
        return seaLevel==com.github.xandergos.terraindiffusionmc.world.VerticalProfile.SEA_LEVEL?seaLevel+1.0:original;
    }
}
