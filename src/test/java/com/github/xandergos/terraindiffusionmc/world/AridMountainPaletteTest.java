package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class AridMountainPaletteTest {
    @Test void warmMountainBodyRetainsDarkPatchesAtMultipleElevations() throws Exception {
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        long seed=330971835197786486L;
        var f=new DesertTerrain.Field(1,.3,0,.25,.5,1);
        int darkLow=0,darkHigh=0,sand=0,total=0,gray=0,darkTotal=0;
        Path out=Path.of("build/mountain33-review");Files.createDirectories(out);
        try(var writer=Files.newBufferedWriter(out.resolve("section.csv"))){
            writer.write("x,y,block\n");
            for(int x=48878;x<50878;x+=10)for(int y=-1680;y<-580;y+=4){
                Block b=DesertSurface.rock(x/5.0,y,24575/5.0,f,seed);
                writer.write(x+","+y+","+Registries.BLOCK.getId(b).getPath()+"\n");
                boolean dark=b==Blocks.BROWN_TERRACOTTA||b==Blocks.GRAY_TERRACOTTA;
                if(dark)darkTotal++;if(b==Blocks.GRAY_TERRACOTTA)gray++;
                if(y<-1400&&dark)darkLow++;if(y>=-860&&dark)darkHigh++;
                if(b==Blocks.SANDSTONE||b==Blocks.RED_SANDSTONE)sand++;total++;
            }
        }
        System.out.println("Palette dark="+darkTotal/(double)total+" gray="+gray/(double)total);
        assertTrue(darkLow>300&&darkHigh>300,"Dark patches belong at both low and high elevations");
        assertTrue(darkTotal>total*.15&&darkTotal<total*.55,"Warm body with substantial dark patches");
        assertTrue(gray<total*.15,"Grey is the darkest accent, not the dominant surface");
        int reversals=0;double prior=DesertProvinces.darkExposure(9975,-1700,4915,seed),direction=0;
        for(int y=-1690;y<0;y+=10){double value=DesertProvinces.darkExposure(9975,y,4915,seed);double next=Math.signum(value-prior);
            if(direction!=0&&next!=direction)reversals++;direction=next;prior=value;}
        assertTrue(reversals>=5,"Darkness must vary non-monotonically with elevation");
        assertTrue(sand<total*.08,"Sandstone is limited to pale seams, not the mountain body");
        assertNotEquals(Blocks.SANDSTONE,DesertSurface.rock(49878/5.0,-1461,24575/5.0,f,seed));
    }
    @Test void savedWorldMountainCoordinatesReachTheCorrectSurfaceRule() throws Exception {
        String input=System.getProperty("terrainDiffusion.testBlueprint");assumeTrue(input!=null);
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var path=Path.of(input);var store=new BlueprintTileStore(path);var detail=BlueprintDetailMap.read(path.resolve("ecology.bin.gz"));
        var sf=WorldBlueprintManager.class.getDeclaredField("store");sf.setAccessible(true);
        var df=WorldBlueprintManager.class.getDeclaredField("detail");df.setAccessible(true);
        var sc=WorldScaleManager.class.getDeclaredField("currentScale");sc.setAccessible(true);
        Object oldS=sf.get(null),oldD=df.get(null),oldC=sc.get(null);
        try{
            sf.set(null,store);df.set(null,detail);sc.set(null,5);
            var f=DesertTerrain.at(49878/5.0,24575/5.0,330971835197786486L);
            Block mountain=SurfaceGeology.rock(49878,-1461,24575,BiomeIds.STONY_PEAKS,330971835197786486L);
            Block preview=DesertSurface.rock(49878/5.0,-1461,24575/5.0,f,330971835197786486L);
            System.out.println("Reported stony peaks: field="+f+" material="+Registries.BLOCK.getId(mountain));
            assertEquals(preview,mountain,"Actual mountain and DH material must agree at reported coordinates");
            assertTrue(mountain==Blocks.BROWN_TERRACOTTA||mountain==Blocks.GRAY_TERRACOTTA||mountain==Blocks.TERRACOTTA);
        }finally{sf.set(null,oldS);df.set(null,oldD);sc.set(null,oldC);}
    }
}
