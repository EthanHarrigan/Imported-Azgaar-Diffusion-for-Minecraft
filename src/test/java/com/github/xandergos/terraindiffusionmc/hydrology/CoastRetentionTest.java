package com.github.xandergos.terraindiffusionmc.hydrology;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CoastRetentionTest {
    @Test void retainsIslandInteriorWithoutLoweringMountainsOrCreatingAHardShoreStep()throws Exception{
        float[][] channels=new float[4][16*8];Arrays.fill(channels[3],-1);
        for(int z=0;z<8;z++)for(int x=0;x<16;x++)channels[0][z*16+x]=x<8?-4000:500;
        Field field=WorldBlueprintManager.class.getDeclaredField("detail");field.setAccessible(true);Object previous=field.get(null);
        try{
            field.set(null,new BlueprintDetailMap(16,8,channels));
            assertTrue(WorldHydrology.refineCoast(-1200,72,8,256,128)>0,"An island interior must not remain submerged");
            assertTrue(WorldHydrology.refineCoast(800,-72,8,256,128)<0,"Distant authored ocean must not become accidental land");
            assertEquals(2200,WorldHydrology.refineCoast(2200,72,8,256,128));
            assertEquals(-1200,WorldHydrology.refineCoast(-1200,130,8,256,128),"Exterior generation is not clamped");
            float last=WorldHydrology.refineCoast(-1200,-24,8,256,128);
            for(double x=-23.75;x<=72;x+=.25){
                float next=WorldHydrology.refineCoast(-1200,x,8,256,128);
                assertTrue(Float.isFinite(next));assertTrue(Math.abs(next-last)<30,"Hard shoreline step at "+x);last=next;
            }
        }finally{field.set(null,previous);}
    }
}
