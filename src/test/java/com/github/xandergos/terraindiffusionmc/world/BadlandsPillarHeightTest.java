package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.block.*;
import net.minecraft.registry.*;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BadlandsPillarHeightTest {
    @Test void actualVanillaPillarsCannotGrowToOldSeaLevelInLoweredWorld() throws Exception {
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var lookup=BuiltinRegistries.createWrapperLookup();
        var original=lookup.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.OVERWORLD).value();
        var settings=new ChunkGeneratorSettings(GenerationShapeConfig.create(VerticalProfile.BOTTOM_Y,VerticalProfile.HEIGHT,1,2),
                original.defaultBlock(),original.defaultFluid(),original.noiseRouter(),original.surfaceRule(),original.spawnTarget(),
                VerticalProfile.SEA_LEVEL,false,false,false,false);
        var fixed=NoiseConfig.create(settings,lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS),330971835197786486L).getSurfaceBuilder();
        var vanilla=NoiseConfig.create(original,lookup.getOrThrow(RegistryKeys.NOISE_PARAMETERS),330971835197786486L).getSurfaceBuilder();
        var method=SurfaceBuilder.class.getDeclaredMethod("placeBadlandsPillar",BlockColumn.class,int.class,int.class,int.class,HeightLimitView.class);method.setAccessible(true);
        int[] writes={0};
        BlockColumn column=new BlockColumn(){
            public BlockState getState(int y){return (y<=-400?Blocks.STONE:Blocks.AIR).getDefaultState();}
            public void setState(int y,BlockState state){writes[0]++;}
        };
        var world=HeightLimitView.create(VerticalProfile.BOTTOM_Y,VerticalProfile.HEIGHT);
        for(int z=8918;z<=8950;z+=2)for(int x=64760;x<=64792;x+=2)
            method.invoke(fixed,column,x,z,-399,world);
        assertEquals(0,writes[0],"Existing high inland terrain must not sprout vanilla pillars at Y 64..100");
        for(int z=8918;z<=8950;z+=2)for(int x=64760;x<=64792;x+=2)
            method.invoke(vanilla,column,x,z,-399,world);
        assertTrue(writes[0]>0,"Control must reproduce the original absolute-height pillar generation near the reported coordinates");
    }
}
