package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerWorld.class)
public abstract class WorldInitializationMixin {
    // The chunk manager and persistent-state manager exist here, but structure workers have not started.
    @Inject(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/world/gen/chunk/placement/StructurePlacementCalculator;tryCalculate()V"))
    private void terrainDiffusion$initializeBeforeStructures(CallbackInfo ci){
        ServerWorld world=(ServerWorld)(Object)this;
        if(!world.getRegistryKey().equals(World.OVERWORLD))return;
        LocalTerrainProvider.beginWorldLoad(); // Drain old workers before replacing any world globals.
        WorldScaleManager.initializeForWorld(world);
        com.github.xandergos.terraindiffusionmc.world.VerticalProfileState.initialize(world);
        WorldBlueprintManager.initializeForWorld(world);
        world.getPersistentStateManager().save(); // Persist blueprint/scale before preparation can fail.
        LocalTerrainProvider.init(world.getSeed());
        LocalTerrainProvider.restorePreparedPreview();
    }
}
