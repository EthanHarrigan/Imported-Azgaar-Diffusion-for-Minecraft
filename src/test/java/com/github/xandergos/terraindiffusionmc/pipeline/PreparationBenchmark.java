package com.github.xandergos.terraindiffusionmc.pipeline;

/** Bounded real-model comparison; never touches a Minecraft save. */
public final class PreparationBenchmark {
    public static void main(String[] args) throws Exception {
        PipelineModels.load();PipelineModels.awaitLoad();
        long seed=17321;int z=-29824,x=-8192,size=512;
        float[][] reference=new float[4][];
        long start=System.nanoTime();double baselineSeconds;long baselineWindows;
        try(var pipeline=new WorldPipeline(seed,PipelineModels.getInstance())) {
            for(int i=0;i<4;i++)reference[i]=pipeline.get(z+(i/2)*size,x+(i%2)*size,z+(i/2+1)*size,x+(i%2+1)*size,false)[0];
            baselineSeconds=(System.nanoTime()-start)/1e9;baselineWindows=pipeline.getTotalComputedWindowCount();
            System.out.println("BASELINE seconds="+baselineSeconds+" windows="+baselineWindows);
        }
        start=System.nanoTime();
        try(var pipeline=new WorldPipeline(seed,PipelineModels.getInstance())) {
            pipeline.prepareElevationRegion(z,x,z+1024,x+1024);
            double maxError=0;int changed=0;float[][] actualFields=new float[4][];
            for(int i=0;i<4;i++){
                float[] actual=pipeline.get(z+(i/2)*size,x+(i%2)*size,z+(i/2+1)*size,x+(i%2+1)*size,false)[0];
                actualFields[i]=actual;
                for(int p=0;p<actual.length;p++){maxError=Math.max(maxError,Math.abs(actual[p]-reference[i][p]));if(Float.floatToIntBits(actual[p])!=Float.floatToIntBits(reference[i][p]))changed++;}
            }
            double prefetchSeconds=(System.nanoTime()-start)/1e9;
            System.out.println("PREFETCH seconds="+prefetchSeconds+" windows="+pipeline.getTotalComputedWindowCount()+" maxElevationErrorMetres="+maxError+" changed="+changed);
            if(maxError>.05)throw new AssertionError("Prefetch changed terrain beyond numerical tolerance");
            float[] a=new float[64*64],b=new float[a.length],rain=new float[a.length],hints=new float[a.length];java.util.Arrays.fill(rain,1000);
            for(int r=0;r<64;r++)for(int c=0;c<64;c++){
                int tile=(r/32)*2+c/32,p=(r%32)*16*512+(c%32)*16;
                a[r*64+c]=reference[tile][p];b[r*64+c]=actualFields[tile][p];
            }
            var g1=new com.github.xandergos.terraindiffusionmc.hydrology.DrainageGrid(64,64,16,a,rain,hints);
            var g2=new com.github.xandergos.terraindiffusionmc.hydrology.DrainageGrid(64,64,16,b,rain,hints);
            int differingParents=0;for(int i=0;i<a.length;i++)if(g1.parent[i]!=g2.parent[i])differingParents++;
            var report=new java.util.LinkedHashMap<String,Object>();
            report.put("baselineSeconds",baselineSeconds);report.put("prefetchSeconds",prefetchSeconds);
            report.put("speedup",baselineSeconds/prefetchSeconds);report.put("baselineWindows",baselineWindows);
            report.put("prefetchWindows",pipeline.getTotalComputedWindowCount());report.put("maximumElevationErrorMetres",maxError);
            report.put("differingDrainageParents",differingParents);report.put("riverSegments",g1.riverCount());
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/preparation-benchmark.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
            if(differingParents!=0)throw new AssertionError("Prefetch changed drainage routes: "+differingParents);
        }
        PipelineModels.getInstance().close();
    }
}
