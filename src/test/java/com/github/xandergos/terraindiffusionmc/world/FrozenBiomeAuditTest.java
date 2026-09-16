package com.github.xandergos.terraindiffusionmc.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import java.nio.file.*;
import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
class FrozenBiomeAuditTest {
    @Test void frozenReferenceCoordinatesAndPreviewPartitions()throws Exception{
        Path path=Path.of("build/biome23-audit/blueprint");assumeTrue(Files.exists(path.resolve("manifest.json")));
        var store=new BlueprintTileStore(path);var detail=BlueprintDetailMap.read(path.resolve("ecology.bin.gz"));
        var sf=WorldBlueprintManager.class.getDeclaredField("store");sf.setAccessible(true);
        var df=WorldBlueprintManager.class.getDeclaredField("detail");df.setAccessible(true);
        var sc=WorldScaleManager.class.getDeclaredField("currentScale");sc.setAccessible(true);
        var sd=LocalTerrainProvider.class.getDeclaredField("instanceSeed");sd.setAccessible(true);
        Object oldS=sf.get(null),oldD=df.get(null),oldC=sc.get(null),oldSeed=sd.get(null);
        long seed=-4702486086583876136L;
        try{
            sf.set(null,store);df.set(null,detail);sc.set(null,5);sd.set(null,seed);BiomeClassifier.configure(seed,store.manifest().generationFingerprint());
            var preview=new TerrainPreview(store,detail,null,5);StringBuilder report=new StringBuilder("Authored rough preview; no ONNX, no running game, no hydrology regenerated.\n");
            for(int[] p:new int[][]{{66000,8000},{71800,34760}}){
                var whole=preview.sample(p[0]-512,p[1]-512,64,16);var part=preview.sample(p[0],p[1],32,16);
                for(int z=0;z<32;z++)for(int x=0;x<32;x++){
                    int a=(z+32)*64+x+32,b=z*32+x;
                    assertEquals(whole.elevation()[a],part.elevation()[b]);assertEquals(whole.biomes()[a],part.biomes()[b]);assertEquals(whole.surfaces()[a],part.surfaces()[b]);
                }
                int desert=0,sand=0,rock=0;float lo=Float.MAX_VALUE,hi=-Float.MAX_VALUE;
                for(int i=0;i<whole.biomes().length;i++){
                    int y=HeightConverter.convertToMinecraftHeight((short)whole.elevation()[i]);assertTrue(y>=VerticalProfile.BOTTOM_Y+32&&y<=VerticalProfile.TOP_Y-48);
                    lo=Math.min(lo,y);hi=Math.max(hi,y);
                    if(whole.biomes()[i]==BiomeIds.DESERT){desert++;var b=whole.surfaces()[i];if(b==net.minecraft.block.Blocks.SAND||b==net.minecraft.block.Blocks.RED_SAND)sand++;else rock++;}
                }
                if(p[0]==71800)assertTrue(desert>whole.biomes().length*.95,"Authored Sand Sea core must retain desert ownership");
                report.append("x=").append(p[0]).append(" z=").append(p[1]).append(" sandSea=").append(DesertTerrain.sandSea(p[0]/5.0,p[1]/5.0))
                    .append(" desertColumns=").append(desert).append(" sand=").append(sand).append(" rock=").append(rock).append(" Y=").append(lo).append("..").append(hi).append('\n');
            }
            Files.writeString(Path.of("build/biome23-audit/reference-samples.txt"),report);System.out.println(report);
        }finally{sf.set(null,oldS);df.set(null,oldD);sc.set(null,oldC);sd.set(null,oldSeed);BiomeClassifier.configure((long)oldSeed,WorldBlueprintManager.fingerprint());}
    }
    @Test void strataAreHeightRelativeAndDesertInteriorsStayPure(){
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var f=new DesertTerrain.Field(1,1,0,.2,0,1);
        var rock=new DesertLandforms.Sample(0,40,0,1,42);
        var sand=new DesertLandforms.Sample(4,0,0,1,42);
        java.util.Set<net.minecraft.block.Block> strata=new java.util.HashSet<>();
        for(int y=0;y<1024;y++){
            var a=OwnedSurfaceDecorator.desertMaterial(120,y-1700,260,-1700,f,rock);strata.add(a);
            assertEquals(a,OwnedSurfaceDecorator.desertMaterial(120,y+200,260,200,f,rock));
            assertEquals(net.minecraft.block.Blocks.SAND,OwnedSurfaceDecorator.desertMaterial(120,y-1700,260,-1700,f,sand));
        }
        assertTrue(strata.size()>=5);
    }
}

