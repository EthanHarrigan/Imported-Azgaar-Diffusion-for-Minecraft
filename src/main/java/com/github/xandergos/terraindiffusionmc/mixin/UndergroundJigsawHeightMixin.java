package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import java.util.Optional;

@Mixin(JigsawStructure.class)
public abstract class UndergroundJigsawHeightMixin {
    @Shadow @Final private RegistryEntry<StructurePool> startPool;
    @Shadow @Final private Optional<Heightmap.Type> projectStartToHeightmap;

    @Redirect(method="getStructurePosition",at=@At(value="INVOKE",target="Lnet/minecraft/world/gen/heightprovider/HeightProvider;get(Lnet/minecraft/util/math/random/Random;Lnet/minecraft/world/gen/HeightContext;)I"))
    private int terrainDiffusion$undergroundHeight(HeightProvider provider,Random random,
            HeightContext heightContext,Structure.Context context){
        return UndergroundStructureHeight.adjust(provider,
                context.biomeSource() instanceof TerrainDiffusionBiomeSource,
                projectStartToHeightmap.isPresent(),
                startPool.getKey().map(k->k.getValue()).orElse(null),
                context.chunkGenerator().getSeaLevel()).get(random,heightContext);
    }
}
