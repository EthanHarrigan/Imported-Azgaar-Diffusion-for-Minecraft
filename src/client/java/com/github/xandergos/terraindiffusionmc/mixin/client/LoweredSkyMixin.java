package com.github.xandergos.terraindiffusionmc.mixin.client;

import com.github.xandergos.terraindiffusionmc.world.VerticalProfile;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.HeightLimitView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.Properties.class)
public abstract class LoweredSkyMixin {
    @Inject(method="getSkyDarknessHeight",at=@At("HEAD"),cancellable=true)
    private void terrainDiffusion$horizon(HeightLimitView world, CallbackInfoReturnable<Double> cir) {
        if(world.getBottomY()==VerticalProfile.BOTTOM_Y && world.getHeight()==VerticalProfile.HEIGHT)
            cir.setReturnValue((double)VerticalProfile.SEA_LEVEL);
    }
}
