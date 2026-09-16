package com.github.xandergos.terraindiffusionmc.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BiomeOwnershipTest {
    private static short owner(int x,int z){return (short)(Math.hypot(x-40,z-40)<7?2:x<80+12*Math.sin(z/27.0)?1:3);}
    private static BiomeBoundaryField field(int x0,int z0,int n){short[][] ids=new short[n][n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)ids[z][x]=owner(x0+x,z0+z);return new BiomeBoundaryField(ids,null);}
    @Test void smallAndLargePatchesKeepPureCenters(){
        var f=field(-64,-64,256);
        assertEquals(0,f.transition(104,104));assertEquals(0,f.transition(64,104));assertEquals(0,f.transition(210,104));
        assertTrue(f.width(104,104)<f.width(64,104));
    }
    @Test void finalOwnerDistancesAndWidthDoNotDependOnTilePartition(){
        var whole=field(-64,-64,320);var part=field(0,0,192);
        for(int z=64;z<128;z++)for(int x=64;x<128;x++){
            assertEquals(whole.distance(x+64,z+64),part.distance(x,z),1e-5);
            assertEquals(whole.width(x+64,z+64),part.width(x,z),1e-5);
            assertEquals(whole.transition(x+64,z+64),part.transition(x,z),1e-5);
            assertEquals(whole.neighbor(x+64,z+64),part.neighbor(x,z));
        }
    }
    @Test void singleBlockPatchIsNeverLostToTransition(){short[][] ids=new short[129][129];ids[64][64]=1;var f=new BiomeBoundaryField(ids,null);assertEquals(0,f.transition(64,64));assertEquals(0,f.neighbor(64,64));}
    @Test void convertedHeightIsMonotoneAndWithinProfileAtAllScales(){
        for(int scale=1;scale<=6;scale++){int previous=Integer.MIN_VALUE;
            for(int e=Short.MIN_VALUE;e<=Short.MAX_VALUE;e++){
                int y=HeightConverter.convertToMinecraftHeight((short)e,scale);
                assertTrue(y>=previous);assertTrue(y>=VerticalProfile.BOTTOM_Y+32&&y<=VerticalProfile.TOP_Y-48);previous=y;
            }
        }
    }
}
