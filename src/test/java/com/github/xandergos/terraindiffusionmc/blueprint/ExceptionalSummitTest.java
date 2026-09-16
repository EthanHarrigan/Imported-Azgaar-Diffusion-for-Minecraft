package com.github.xandergos.terraindiffusionmc.blueprint;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ExceptionalSummitTest {
    @Test void newMapAnchorsRespectSourcePaddingAndHighlandNeighbourhoods(){
        float[] e=new float[400*200];Arrays.fill(e,6500);
        var p=ExceptionalSummit.plan(ExceptionalSummit.DESERT_CETEVILES_SOURCE,e,400,200,25088,4,90,98);
        assertEquals(2,p.size());
        assertEquals(4.0/98+90.0/98*650.08/2560,p.get(0).u(),.012);
        assertEquals(768.2/1317,p.get(1).v(),.015);
        assertTrue(p.get(0).boostMetres()>p.get(1).boostMetres());
        Arrays.fill(e,100);
        assertTrue(ExceptionalSummit.plan(ExceptionalSummit.DESERT_CETEVILES_SOURCE,e,400,200,25088,4,90,98).isEmpty());
    }
    @Test void sourceBindingSeparationAndDeterminism(){
        float[] e=new float[400*200];Arrays.fill(e,6000);
        assertTrue(ExceptionalSummit.plan("another-map",e,400,200,25088).isEmpty());
        var p=ExceptionalSummit.plan(ExceptionalSummit.CETEVILES_SOURCE,e,400,200,25088);
        assertEquals(5,p.size());assertEquals(p,ExceptionalSummit.plan(ExceptionalSummit.CETEVILES_SOURCE,e,400,200,25088));
        assertTrue(p.getFirst().u()<.20&&p.getFirst().v()<.23);
        for(int i=0;i<p.size();i++)for(int j=0;j<i;j++)assertTrue(Math.hypot((p.get(i).u()-p.get(j).u())*25088,(p.get(i).v()-p.get(j).v())*12544)>=650);
    }
    @Test void taperedBoostPreservesLowlandsAndHeightHeadroom(){
        var p=List.of(new ExceptionalSummit(.5,.5,600,8500));
        assertEquals(8500,ExceptionalSummit.boost(p,6000,0,0,25088,12544,3));
        assertEquals(0,ExceptionalSummit.boost(p,2000,0,0,25088,12544,3));
        assertEquals(0,ExceptionalSummit.boost(p,6000,600,0,25088,12544,3));
        assertTrue(ExceptionalSummit.boost(p,6000,599,0,25088,12544,3)<.1);
        for(int scale=1;scale<=6;scale++)for(int e=3000;e<=11000;e+=100){
            double top=com.github.xandergos.terraindiffusionmc.world.VerticalProfile.SEA_LEVEL+(e+ExceptionalSummit.boost(p,e,0,0,25088,12544,scale))*scale/30;
            if(scale<=4)assertTrue(top<1967,"Summit must fit supported dimension");
        }
        float last=0;
        for(int e=3000;e<20000;e+=10){
            float raised=e+ExceptionalSummit.boost(p,e,0,0,25088,12544,3);
            assertTrue(raised>last,"Increasing source elevation must not invert a summit");last=raised;
        }
    }
    @Test void broadBoostHasRangeShouldersAndAContinuousEdge(){
        var p=List.of(new ExceptionalSummit(.5,.5,1800,10000));
        float core=ExceptionalSummit.broadBoost(p,6000,0,0,25088,12544,3,42);
        float shoulder=ExceptionalSummit.broadBoost(p,6000,900,0,25088,12544,3,42);
        assertTrue(core>shoulder&&shoulder>0);
        assertEquals(0,ExceptionalSummit.broadBoost(p,6000,1800,0,25088,12544,3,42));
        assertTrue(ExceptionalSummit.broadBoost(p,6000,1799,0,25088,12544,3,42)<1);
        assertEquals(0,ExceptionalSummit.broadBoost(p,1000,0,0,25088,12544,3,42));
    }
}
