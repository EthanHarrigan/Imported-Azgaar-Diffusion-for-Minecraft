package com.github.xandergos.terraindiffusionmc.hydrology;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PreparedRoutingTest {
    @TempDir Path dir;
    @Test void solvedGraphRestoresByteIdenticallyWithoutRoutingAgain()throws Exception{
        int n=81;float[] e=new float[n],rain=new float[n],hint=new float[n];
        for(int z=0;z<9;z++)for(int x=0;x<9;x++){int i=z*9+x;e[i]=x==0||z==0||x==8||z==8?-10:100+x*17+z*11;rain[i]=900;hint[i]=x==4?1:0;}
        var original=new WorldHydrology(new DrainageGrid(9,9,16,e,rain,hint),128,128);
        Path a=dir.resolve("a.gz"),b=dir.resolve("b.gz");String identity="seed42|jsonSHA|scale5|profile";
        original.writePrepared(a,identity);
        var restored=WorldHydrology.readPrepared(a,identity,128,128);assertNotNull(restored);
        restored.writePrepared(b,identity);assertArrayEquals(Files.readAllBytes(a),Files.readAllBytes(b));
        for(int z=-60;z<60;z+=3)for(int x=-60;x<60;x+=3){
            assertEquals(original.previewElevation(x,z),restored.previewElevation(x,z));
            assertEquals(original.previewWater(x,z),restored.previewWater(x,z));
            assertEquals(original.isMajorRiverAt(x,z,1),restored.isMajorRiverAt(x,z,1));
        }
        float[] plane=new float[32*32];java.util.Arrays.fill(plane,150);
        assertArrayEquals(original.carve(plane,-16,-16,32,32,1).bed(),restored.carve(plane,-16,-16,32,32,1).bed());
        assertArrayEquals(original.carve(plane,-16,-16,32,32,1).water(),restored.carve(plane,-16,-16,32,32,1).water());
    }
    @Test void identityCorruptionAndTruncationFailClosed()throws Exception{
        float[] e={0,0,0,0},rain={900,900,900,900};
        var h=new WorldHydrology(new DrainageGrid(2,2,16,e,rain,e),16,16);
        Path p=dir.resolve("saved.gz");h.writePrepared(p,"first");
        assertThrows(java.io.IOException.class,()->WorldHydrology.readPrepared(p,"second",16,16));
        byte[] bytes=Files.readAllBytes(p);Files.write(p,java.util.Arrays.copyOf(bytes,bytes.length-5));
        assertThrows(java.io.IOException.class,()->WorldHydrology.readPrepared(p,"first",16,16));
    }
}
