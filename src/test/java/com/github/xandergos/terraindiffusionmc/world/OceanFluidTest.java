package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.block.Blocks;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.noise.NoiseRouter;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OceanFluidTest {
    @Test void actualGeneratorUsesWaterThroughoutLoweredOceansAndKeepsVanillaFluids() throws Exception {
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var vanilla=net.minecraft.registry.BuiltinRegistries.createWrapperLookup()
                .getOrThrow(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.OVERWORLD).value();
        var router=vanilla.noiseRouter().apply(new DensityFunction.DensityFunctionVisitor(){
            public DensityFunction apply(DensityFunction function){return new TerrainDiffusionDensityFunction();}
        });
        var settings=new ChunkGeneratorSettings(GenerationShapeConfig.create(VerticalProfile.BOTTOM_Y,VerticalProfile.HEIGHT,1,2),
                Blocks.STONE.getDefaultState(),Blocks.WATER.getDefaultState(),router,vanilla.surfaceRule(),vanilla.spawnTarget(),
                VerticalProfile.SEA_LEVEL,false,false,false,false);
        var method=NoiseChunkGenerator.class.getDeclaredMethod("createFluidLevelSampler",ChunkGeneratorSettings.class);method.setAccessible(true);
        var sampler=(AquiferSampler.FluidLevelSampler)method.invoke(null,settings);
        for(int y=VerticalProfile.BOTTOM_Y+32;y<VerticalProfile.SEA_LEVEL;y++)
            assertTrue(sampler.getFluidLevel(64781,y,8952).getBlockState(y).isOf(Blocks.WATER),"Ocean water at "+y);
        assertTrue(sampler.getFluidLevel(0,VerticalProfile.SEA_LEVEL,0).getBlockState(VerticalProfile.SEA_LEVEL).isAir());
        assertTrue(sampler.getFluidLevel(0,VerticalProfile.BOTTOM_Y+5,0).getBlockState(VerticalProfile.BOTTOM_Y+5).isOf(Blocks.LAVA));
        var original=(AquiferSampler.FluidLevelSampler)method.invoke(null,vanilla);
        assertTrue(original.getFluidLevel(0,-55,0).getBlockState(-55).isOf(Blocks.LAVA));
        assertTrue(original.getFluidLevel(0,62,0).getBlockState(62).isOf(Blocks.WATER));
    }
}
