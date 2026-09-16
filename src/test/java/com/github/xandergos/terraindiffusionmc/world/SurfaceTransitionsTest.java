package com.github.xandergos.terraindiffusionmc.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceTransitionsTest {
    @Test void ordinaryDesertHasOnlyTwoCoherentSandProvinces()throws Exception{
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var path=java.nio.file.Path.of(System.getProperty("terrainDiffusion.testBlueprint","build/desert-audit-v21"));
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path.resolve("manifest.json")));
        var manager=com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.class;
        var sf=manager.getDeclaredField("store");sf.setAccessible(true);Object old=sf.get(null);
        try{
            sf.set(null,new com.github.xandergos.terraindiffusionmc.blueprint.BlueprintTileStore(path));
            var image=new java.awt.image.BufferedImage(512,192,java.awt.image.BufferedImage.TYPE_INT_RGB);
            int[] red=new int[8];long seed=37;
            for(int z=0;z<192;z++)for(int x=0;x<512;x++){
                var f=new com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.Field(1,.9,.02,x/511.0,.03,1);
                var b=DesertSurface.sediment(x/3.0,z/3.0,f,.03,seed,true);
                assertTrue(b==net.minecraft.block.Blocks.SAND||b==net.minecraft.block.Blocks.RED_SAND,"No soil/powder/rock confetti in ordinary sand");
                assertEquals(x/511.0>.58?net.minecraft.block.Blocks.RED_SAND:net.minecraft.block.Blocks.SAND,b);
                if(b==net.minecraft.block.Blocks.RED_SAND)red[x/64]++;
                int color=b.getDefaultState().getMapColor(null,net.minecraft.util.math.BlockPos.ORIGIN).color;
                image.setRGB(x,z,color);
            }
            for(int i=1;i<red.length;i++)assertTrue(red[i]>=red[i-1],"Coherent provinces must not alternate back to pale sand");
            var output=java.nio.file.Path.of("build/transition-palette-check.png");
            javax.imageio.ImageIO.write(image,"png",output.toFile());
        }finally{sf.set(null,old);}
    }
    @Test void weightedPaletteIsNormalizedAndContinuousInDensity(){
        for(double p:new double[]{0,.1,.25,.5,.75,.9,1}){
            int count=0,n=40000;for(int i=0;i<n;i++)if(SurfaceTransitions.choose(SurfaceAccentRules.unit(431,i%200-100,i/200-100,0),1-p,p)==1)count++;
            assertEquals(p,count/(double)n,.015);
        }
        assertEquals(0,SurfaceTransitions.choose(.5,0,Double.NaN));
        assertEquals(1,SurfaceTransitions.choose(.5,0,2));
    }
    @Test void grainUsesWorldCoordinatesAndIsSeedStable(){
        for(int z=-40;z<40;z++)for(int x=-40;x<40;x++){
            double a=SurfaceTransitions.grain(x,z,17);assertTrue(a>=0&&a<1);assertEquals(a,SurfaceTransitions.grain(x,z,17));
        }
        assertNotEquals(SurfaceTransitions.grain(12,42,17),SurfaceTransitions.grain(12,42,18));
    }
    @Test void vegetationMixinsAreApplied()throws Exception{
        for(String name:new String[]{"TreeFeature","RandomPatchFeature"}){
            Class<?> type=Class.forName("net.minecraft.world.gen.feature."+name);
            assertTrue(java.util.Arrays.stream(type.getDeclaredMethods()).anyMatch(m->m.getName().contains("td$")),name+" injection missing");
        }
    }
    @Test void sandSeaCoreRejectsGenericSediment()throws Exception{
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var path=java.nio.file.Path.of(System.getProperty("terrainDiffusion.testBlueprint","build/desert-audit-v21"));
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path.resolve("manifest.json")));
        var sf=com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.class.getDeclaredField("store");sf.setAccessible(true);Object old=sf.get(null);
        try{
            sf.set(null,new com.github.xandergos.terraindiffusionmc.blueprint.BlueprintTileStore(path));
            var f=new com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.Field(1,.98,.0,.0,.03,1);
            var b=DesertSurface.sediment(0,0,f,.03,19,true);
            assertTrue(b==net.minecraft.block.Blocks.SAND||b==net.minecraft.block.Blocks.RED_SAND);
        }finally{sf.set(null,old);}
    }
}
