package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.hydrology.*;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class ParallelTerrainTest {
    @Test void serialAndParallelCpuStagesMatchIncludingHalosAndNegativeCoordinates() throws Exception {
        String input=System.getProperty("terrainDiffusion.testBlueprint");assumeTrue(input!=null);
        var fields=new java.lang.reflect.Field[]{field(WorldBlueprintManager.class,"store"),field(WorldBlueprintManager.class,"detail"),
                field(WorldBlueprintManager.class,"finalReservations"),field(WorldScaleManager.class,"currentScale"),
                field(LocalTerrainProvider.class,"instanceSeed"),field(BiomeClassifier.class,"contextSeed")};
        Object[] old=new Object[fields.length];for(int i=0;i<old.length;i++)old[i]=fields[i].get(null);
        try {
            fields[0].set(null,new BlueprintTileStore(Path.of(input)));
            fields[1].set(null,BlueprintDetailMap.read(Path.of(input).resolve("ecology.bin.gz")));
            fields[2].set(null,null);fields[3].set(null,5);fields[4].set(null,330971835197786486L);
            BiomeClassifier.configure(330971835197786486L,"parallel-audit");
            int side=65,n=side*side;float[] terrain=new float[n],rain=new float[n],hint=new float[n];
            for(int z=0;z<side;z++)for(int x=0;x<side;x++){
                int i=z*side+x;terrain[i]=100+x*3+z*2;rain[i]=800;hint[i]=x==32?1:0;
            }
            var hydro=new WorldHydrology(new DrainageGrid(side,side,16,terrain,rain,hint),1024,1024,null,0,0);
            var provider=new LocalTerrainProvider(hydro,ParallelTerrainTest::nativeField);
            int[][] origins={{-17,-31},{0,0},{49872,24560},{54016,21040},{64768,8928},{-512,511}};
            var expected=new ArrayList<LocalTerrainProvider.HeightmapData>();
            for(var p:origins)expected.add(provider.prepareTile(p[1],p[0],p[1]+32,p[0]+32,5));
            try(var workers=Executors.newFixedThreadPool(2)) {
                var futures=new ArrayList<Future<LocalTerrainProvider.HeightmapData>>();
                for(var p:origins)futures.add(workers.submit(()->provider.prepareTile(p[1],p[0],p[1]+32,p[0]+32,5)));
                for(int i=0;i<futures.size();i++)same(expected.get(i),futures.get(i).get(),0,0);
            }
            // Repartition a tile: the halo must make the overlapping results independent of tile origin.
            for(int dz:new int[]{0,16})for(int dx:new int[]{0,16}) {
                var p=origins[0];var part=provider.prepareTile(p[1]+dz,p[0]+dx,p[1]+dz+16,p[0]+dx+16,5);
                same(expected.getFirst(),part,dx,dz);
            }
        } finally {for(int i=0;i<old.length;i++)fields[i].set(null,old[i]);}
    }
    private static java.lang.reflect.Field field(Class<?> c,String name)throws Exception {
        var f=c.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static float[][] nativeField(int z0,int x0,int z1,int x1,boolean withClimate) {
        int h=z1-z0,w=x1-x0,n=h*w;float[] e=new float[n],c=withClimate?new float[5*n]:null;
        for(int z=0;z<h;z++)for(int x=0;x<w;x++) {
            int i=z*w+x;e[i]=(float)(700+400*Math.sin((x+x0)*.006)+180*Math.cos((z+z0)*.009));
            if(c!=null){c[i]=23;c[n+i]=350;c[2*n+i]=700;c[3*n+i]=20;c[4*n+i]=-.0065f;}
        }
        return new float[][]{e,c};
    }
    private static void same(LocalTerrainProvider.HeightmapData a,LocalTerrainProvider.HeightmapData b,int dx,int dz) {
        for(int z=0;z<b.height;z++)for(int x=0;x<b.width;x++) {
            assertEquals(a.heightmap[z+dz][x+dx],b.heightmap[z][x],"height");
            assertEquals(a.biomeIds[z+dz][x+dx],b.biomeIds[z][x],"biome");
            assertEquals(a.waterSurface[z+dz][x+dx],b.waterSurface[z][x],"water");
            assertEquals(a.blockHeights[z+dz][x+dx],b.blockHeights[z][x],"density height");
            assertEquals(a.contexts[z+dz][x+dx],b.contexts[z][x],"surface context");
        }
    }
}
