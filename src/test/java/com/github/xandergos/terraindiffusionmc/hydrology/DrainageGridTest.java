package com.github.xandergos.terraindiffusionmc.hydrology;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class DrainageGridTest {
    private DrainageGrid valley(){
        int w=65,h=97;float[] e=new float[w*h],rain=new float[e.length],source=new float[e.length];
        Arrays.fill(rain,900);
        for(int z=0;z<h;z++)for(int x=0;x<w;x++)e[z*w+x]=z==h-1?-20:40+(h-1-z)*4+Math.abs(x-w/2)*12;
        return new DrainageGrid(w,h,16,e,rain,source);
    }
    @Test void riversAreConnectedDownhillAndDeterministic(){
        var a=valley();var b=valley();a.validate();
        assertTrue(a.riverCount()>50);assertArrayEquals(a.parent,b.parent);assertArrayEquals(a.flow,b.flow);
        for(int i=0;i<a.parent.length;i++)if(a.river[i]){
            int p=i,steps=0;while(a.parent[p]>=0){assertTrue(a.surface[a.parent[p]]<=a.surface[p]);p=a.parent[p];assertTrue(++steps<a.parent.length);}
            assertTrue(a.terrain[p]<=0||p%a.width==0||p%a.width==a.width-1||p/a.width==0||p/a.width==a.height-1);
        }
    }
    @Test void closedBasinHasLevelLakeAndAnOutlet(){
        int w=33;float[] e=new float[w*w],r=new float[e.length],s=new float[e.length];Arrays.fill(e,200);Arrays.fill(r,900);
        for(int z=10;z<23;z++)for(int x=10;x<23;x++)e[z*w+x]=80;
        for(int x=16;x<w;x++)e[16*w+x]=150;e[16*w+w-1]=-10;
        var g=new DrainageGrid(w,w,16,e,r,s);g.validate();
        assertEquals(150,g.surface[15*w+15]);assertEquals(70,g.lakeDepth(15*w+15));
    }
    @Test void badElevationsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new DrainageGrid(2,2,16,new float[]{1,Float.NaN,2,3},new float[4],new float[4]));
    }
    @Test void mountainEnhancementIsBoundedDeterministicAndLeavesLowlandsAlone(){
        assertEquals(900,WorldHydrology.enhanceMountainRelief(900,10,20,42));
        float a=WorldHydrology.enhanceMountainRelief(5000,140,270,42);
        assertEquals(a,WorldHydrology.enhanceMountainRelief(5000,140,270,42));
        assertTrue(a>4880&&a<5200,"ridge relief must not replace the broad range envelope");
    }
    @Test void authoredLakesCreateShallowBasinsButDoNotRemoveMountains(){
        int w=17;float[] e=new float[w*w];boolean[] mask=new boolean[e.length];Arrays.fill(e,200);
        for(int z=6;z<11;z++)for(int x=6;x<11;x++)mask[z*w+x]=true;
        e[8*w+8]=1800;float[] cuts=LakeBasinPlanner.cuts(e,mask,w,w);
        assertEquals(30,cuts[7*w+7]);assertEquals(0,cuts[8*w+8]);
        for(float cut:cuts)assertTrue(cut>=0&&cut<=60);
    }
    @Test void carvingIsIdenticalAcrossChunkPartitions(){
        var g=valley();var hydrology=new WorldHydrology(g,(g.width-1)*16,(g.height-1)*16);
        int n=192;float[] terrain=new float[n*n];Arrays.fill(terrain,100);
        var full=hydrology.carve(terrain,200,-96,n,n,3);
        int wet=0;
        for(int z=0;z<n;z+=16)for(int x=0;x<n;x+=16){
            float[] input=new float[256];Arrays.fill(input,100);
            var part=hydrology.carve(input,200+z,-96+x,16,16,3);
            for(int r=0;r<16;r++)for(int c=0;c<16;c++){
                int a=(z+r)*n+x+c,b=r*16+c;
                assertEquals(full.bed()[a],part.bed()[b],1e-5);
                assertEquals(full.water()[a],part.water()[b],1e-5);
                if(Float.isFinite(part.water()[b])){wet++;assertTrue(part.bed()[b]<part.water()[b]);}
            }
        }
        assertTrue(wet>0);
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @Test void texturedBanksArePartitionIndependentInGenerationEight()throws Exception{
        var json=temp.resolve("map.json");
        java.nio.file.Files.writeString(json,"""
            {"info":{"width":200,"height":100},"settings":{"heightExponent":2},"mapCoordinates":{},
             "grid":{"vertices":{"p":[[0,0],[200,0],[200,100],[0,100]]},"cells":{"v":[[0,1,2,3]],"h":[30]}}}
            """);
        var compiled=com.github.xandergos.terraindiffusionmc.blueprint.AzgaarBlueprintCompiler.compile(json,temp.resolve("compiled"),
                new com.github.xandergos.terraindiffusionmc.blueprint.BlueprintCompileOptions(100,.35f,.2f,10,-20,2));
        var f=com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.class.getDeclaredField("store");f.setAccessible(true);Object old=f.get(null);
        try{
            f.set(null,new com.github.xandergos.terraindiffusionmc.blueprint.BlueprintTileStore(compiled.directory()));
            assertEquals(13,com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion());
            carvingIsIdenticalAcrossChunkPartitions();
        }finally{f.set(null,old);}
    }
}
