package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.blueprint.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import java.nio.file.*;
import java.util.Arrays;

class TerrainPreviewTest {
    @Test void interpolationPreservesSlopesAndSharedCoordinates(){
        float[] plane={0,10,20,20,30,40,40,50,60};
        assertEquals(30,WorldHydrology.interpolate(plane,3,3,1,1));
        assertEquals(17.5,WorldHydrology.interpolate(plane,3,3,.75,.5));
        assertEquals(0,WorldHydrology.interpolate(plane,3,3,-10,-5));
        assertEquals(60,WorldHydrology.interpolate(plane,3,3,10,5));
    }
    @Test void preparedPreviewUsesTwiceLinearDrainageResolution(){
        float[] terrain={0,0,0,0};float[] rain={900,900,900,900},hint=new float[4];
        var graph=new com.github.xandergos.terraindiffusionmc.hydrology.DrainageGrid(2,2,16,terrain,rain,hint);
        float[] dense={0,10,20,20,30,40,40,50,60};
        var hydrology=new WorldHydrology(graph,16,16,dense,3,3);
        // Native (4,4) lies halfway between the new 8-pixel preview samples.
        assertEquals(15,hydrology.previewElevation(-4,-4),.001);
        assertEquals(8,WorldHydrology.PREVIEW_STEP);
    }
    @Test void optionalMixinActuallyAppliesToInstalledNightly()throws Exception{
        Class<?> dh;
        try{dh=Class.forName("com.seibel.distanthorizons.common.wrappers.worldGeneration.DhRoughSurfaceGenerator",false,getClass().getClassLoader());}
        catch(ClassNotFoundException e){assumeTrue(false,"DH integration test requires -PtestDhPreview");return;}
        assertTrue(Arrays.stream(dh.getDeclaredMethods()).anyMatch(m->m.getName().contains("terrainDiffusion$preview")),"DH preview injection missing");
    }
    @Test void roughPreviewHeightFitsDhEncodingInLoweredAndVanillaWorlds() throws Exception {
        Class<?> dh;
        try{dh=Class.forName("com.seibel.distanthorizons.common.wrappers.worldGeneration.DhRoughSurfaceGenerator",false,getClass().getClassLoader());}
        catch(ClassNotFoundException e){assumeTrue(false,"DH integration test requires -PtestDhPreview");return;}
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        // Isolate the transformed height hook without launching a server or a DH worker pool.
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        Object generator=unsafe.allocateInstance(dh);
        var wrapperField=dh.getDeclaredField("serverLevelWrapper");wrapperField.setAccessible(true);
        var hook=Arrays.stream(dh.getDeclaredMethods()).filter(m->m.getName().contains("terrainDiffusion$roughColumnHeight")).findFirst().orElseThrow();
        hook.setAccessible(true);
        Class<?> packing=Class.forName("com.seibel.distanthorizons.core.util.FullDataPointUtil");
        var encode=packing.getMethod("encode",int.class,int.class,int.class,byte.class,byte.class);
        var readHeight=packing.getMethod("getHeight",long.class);
        var readBottom=packing.getMethod("getBottomY",long.class);
        for(int[] bounds:new int[][]{{-2000,3968},{-64,384},{0,256},{64,256}}){
            var world=net.minecraft.world.HeightLimitView.create(bounds[0],bounds[1]);
            Object wrapper=java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{wrapperField.getType()},
                    (proxy,method,args)->method.getName().equals("getWrappedMcObject")?world:null);
            wrapperField.set(generator,wrapper);
            int corrected=(int)hook.invoke(generator,bounds[1]-bounds[0]);
            assertEquals(bounds[1],corrected);
            for(int ground:new int[]{1,Math.min(240,corrected-1),corrected-1}){
                long packed=(long)encode.invoke(null,0,corrected-ground,ground,(byte)0,(byte)15);
                assertEquals(corrected-ground,readHeight.invoke(null,packed));
                assertEquals(ground,readBottom.invoke(null,packed));
            }
        }
        // Exact failing height from the user's log must overflow without the correction.
        try{
            long invalid=(long)encode.invoke(null,0,5728,240,(byte)0,(byte)15);
            assertNotEquals(5728,readHeight.invoke(null,invalid));
        }catch(java.lang.reflect.InvocationTargetException expected){
            assertTrue(expected.getCause().getMessage().contains("5728"));
        }
    }
    @Test void realBlueprintPreviewIsFiniteAndPartitionIndependentWithoutModels()throws Exception{
        Path path=Path.of(System.getProperty("terrainDiffusion.testBlueprint","build/hydrology-final-scale3"));assumeTrue(Files.exists(path.resolve("manifest.json")));
        var store=new BlueprintTileStore(path);var detail=BlueprintDetailMap.read(path.resolve("ecology.bin.gz"));
        var sf=WorldBlueprintManager.class.getDeclaredField("store");sf.setAccessible(true);
        var df=WorldBlueprintManager.class.getDeclaredField("detail");df.setAccessible(true);
        Object oldStore=sf.get(null),oldDetail=df.get(null);
        try{
        sf.set(null,store);df.set(null,detail);
        var preview=new TerrainPreview(store,detail,null,3);
        long start=System.nanoTime();var whole=preview.sample(-2048,-1024,64,32);
        var part=preview.sample(-1024,0,32,32);
        for(int z=0;z<32;z++)for(int x=0;x<32;x++)assertEquals(whole.elevation()[(z+32)*64+x+32],part.elevation()[z*32+x]);
        for(float h:whole.elevation())assertTrue(Float.isFinite(h));
        for(int i=0;i<whole.elevation().length;i++)if(whole.elevation()[i]<0)assertEquals(0,whole.water()[i]);
        assertFalse(whole.refined());
        System.out.println("Preview 4096 columns + overlap, no ONNX: "+(System.nanoTime()-start)/1_000_000+" ms");
        }finally{sf.set(null,oldStore);df.set(null,oldDetail);}
    }
}
