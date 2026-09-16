package com.github.xandergos.terraindiffusionmc.world;

import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BadlandsStrataTest {
    @Test void darkerBedsHaveVariedWidthsAndCoherentLateralContinuity() throws Exception {
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        long seed=330971835197786486L;int dark=0,total=0,continuous=0;
        Set<Block> colors=new HashSet<>();Set<Integer> widths=new HashSet<>();
        Block previous=null;int run=0;
        for(int y=-1900;y<1600;y++){
            Block b=DesertProvinces.strata(21593,y,2984,seed);colors.add(b);
            if(b==Blocks.BROWN_TERRACOTTA||b==Blocks.TERRACOTTA||b==Blocks.GRAY_TERRACOTTA)dark++;
            if(b==DesertProvinces.strata(21594,y,2984,seed))continuous++;
            if(b!=previous){if(run>0)widths.add(run);run=0;previous=b;}run++;total++;
        }
        assertTrue(dark>total*.60,"Darker clays must dominate the exposed beds");
        assertTrue(colors.size()>=6);assertTrue(widths.size()>15,"Band spacing must vary");
        assertTrue(continuous>total*.97,"Adjacent columns must share coherent strata");
        Path out=Path.of("build/badlands-review");Files.createDirectories(out);
        try(var writer=Files.newBufferedWriter(out.resolve("strata.csv"))){
            writer.write("x,y,block\n");
            for(int y=-1400;y<-800;y+=2)for(int x=0;x<1800;x+=4)
                writer.write(x+","+y+","+Registries.BLOCK.getId(DesertProvinces.strata(x,y,2984,seed)).getPath()+"\n");
        }
    }
}
