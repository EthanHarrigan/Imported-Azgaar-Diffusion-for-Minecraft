package com.github.xandergos.terraindiffusionmc.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FoothillGrassTest {
    @Test void shallowFoothillsHavePatchyGrassButCliffsAndSummitsDoNot(){
        int covered=0,total=0;
        for(int z=0;z<300;z+=5)for(int x=0;x<300;x+=5){
            boolean grass=MountainZones.foothillGrass(3100,3000,.22f,x,z,42);
            assertEquals(grass,MountainZones.foothillGrass(3100,3000,.22f,x,z,42));
            if(grass)covered++;total++;
            assertFalse(MountainZones.foothillGrass(3100,3000,.7f,x,z,42));
            assertFalse(MountainZones.foothillGrass(6000,3000,.1f,x,z,42));
        }
        assertTrue(covered>total*.1&&covered<total*.9,"Grass should form a mixed transition, not a carpet");
    }
    @Test void existingTallDimensionFitsTheNewSummits(){
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        assertTrue(net.minecraft.world.dimension.DimensionType.MAX_COLUMN_HEIGHT>=1967);
        assertTrue(VerticalProfile.HEIGHT<=net.minecraft.world.dimension.DimensionType.MAX_HEIGHT);
        assertEquals(66,HeightConverter.convertToMinecraftHeight((short)18500,3));
    }
}
