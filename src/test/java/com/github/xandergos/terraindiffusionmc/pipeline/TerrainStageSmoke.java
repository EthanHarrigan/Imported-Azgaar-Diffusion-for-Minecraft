package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.Arrays;
import java.util.concurrent.Executors;

/** Explicit native integration smoke against local weights; never opens a Minecraft save. */
public final class TerrainStageSmoke {
    public static void main(String[] args) throws Exception {
        try {
            LocalTerrainProvider.beginWorldLoad();
            LocalTerrainProvider.init(17321);
            var provider=LocalTerrainProvider.getInstance();
            var original=LocalTerrainProvider.getPipelineData(0,0,8,8,true);
            float[][] expected=Arrays.stream(original).map(float[]::clone).toArray(float[][]::new);
            for(var channel:original)Arrays.fill(channel,Float.NaN);
            var next=LocalTerrainProvider.getPipelineData(0,0,8,8,true);
            for(int c=0;c<next.length;c++)if(!Arrays.equals(expected[c],next[c]))
                throw new AssertionError("Model result aliases cached/shared arrays");
            var a=provider.fetchHeightmap(0,0,16,16);
            var b=provider.fetchHeightmap(-16,-16,0,0);
            LocalTerrainProvider.clearCache();
            try(var callers=Executors.newFixedThreadPool(2)) {
                var pa=callers.submit(()->provider.fetchHeightmap(0,0,16,16));
                var pb=callers.submit(()->provider.fetchHeightmap(-16,-16,0,0));
                same(a,pa.get());same(b,pb.get());
            }
            LocalTerrainProvider.beginWorldLoad();
            LocalTerrainProvider.init(17321);
            if(provider==LocalTerrainProvider.getInstance())throw new AssertionError("Reused old world provider");
            try {provider.fetchHeightmap(0,0,16,16);throw new AssertionError("Old provider accepted");}
            catch(java.util.concurrent.CancellationException expectedCancellation) { }
            System.out.println("PASS: real model outputs are isolated; production parallel tile results match serial; same-seed reload retires old provider. Backend="+OnnxModel.getResolvedInferenceProvider());
        } finally {
            LocalTerrainProvider.beginWorldLoad();
            var models=PipelineModels.getInstance();if(models!=null)models.close();
        }
    }
    private static void same(LocalTerrainProvider.HeightmapData a,LocalTerrainProvider.HeightmapData b) {
        if(!Arrays.deepEquals(a.heightmap,b.heightmap)||!Arrays.deepEquals(a.biomeIds,b.biomeIds)
                ||!Arrays.deepEquals(a.blockHeights,b.blockHeights))throw new AssertionError("Parallel tile changed output");
    }
}
