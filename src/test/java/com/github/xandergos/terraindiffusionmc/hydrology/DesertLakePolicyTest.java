package com.github.xandergos.terraindiffusionmc.hydrology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DesertLakePolicyTest {
    @Test void isolatedDryPondsAreRareButImportantWaterSurvives(){
        int retained=0;for(int i=0;i<100;i++)if(DesertLakePolicy.retain(8,false,false,true,i/100.0))retained++;
        assertEquals(10,retained);
        assertTrue(DesertLakePolicy.retain(64,false,false,true,.9));
        assertTrue(DesertLakePolicy.retain(8,true,false,true,.9));
        assertTrue(DesertLakePolicy.retain(8,false,true,true,.9));
        assertTrue(DesertLakePolicy.retain(8,false,false,false,.9));
    }
    @Test void connectedBasinHasOneDecision(){
        int n=100;float[] bed=new float[n],surface=new float[n],flow=new float[n];int[] parent=new int[n];boolean[] river=new boolean[n];
        java.util.Arrays.fill(bed,100);java.util.Arrays.fill(surface,100);java.util.Arrays.fill(parent,-1);
        for(int i:new int[]{44,45,54,55})surface[i]=150;
        var grid=new DrainageGrid(10,10,16,bed,surface,flow,parent,river);
        boolean[] removed=DesertLakePolicy.suppressed(grid,new float[n],i->true);
        for(int i:new int[]{45,54,55})assertEquals(removed[44],removed[i]);
        float[] cuts=new float[n];cuts[44]=5;
        removed=DesertLakePolicy.suppressed(grid,cuts,i->true);
        for(int i:new int[]{44,45,54,55})assertFalse(removed[i]);
    }
    @Test void auditSavedScreenshotRegionWithoutGeneratingTerrain() throws Exception {
        String input=System.getProperty("terrainDiffusion.testBlueprint");org.junit.jupiter.api.Assumptions.assumeTrue(input!=null);
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var path=java.nio.file.Path.of(input);java.nio.file.Path routing;
        try(var files=java.nio.file.Files.walk(path.resolve("hydrology"))){routing=files.filter(p->p.getFileName().toString().equals("prepared-routing-v1.bin.gz")).findFirst().orElseThrow();}
        var store=new com.github.xandergos.terraindiffusionmc.blueprint.BlueprintTileStore(path);
        var detail=com.github.xandergos.terraindiffusionmc.blueprint.BlueprintDetailMap.read(path.resolve("ecology.bin.gz"));
        var manager=com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.class;
        var sf=manager.getDeclaredField("store");sf.setAccessible(true);var df=manager.getDeclaredField("detail");df.setAccessible(true);
        var sc=com.github.xandergos.terraindiffusionmc.world.WorldScaleManager.class.getDeclaredField("currentScale");sc.setAccessible(true);
        Object oldS=sf.get(null),oldD=df.get(null),oldC=sc.get(null);
        try{
            sf.set(null,store);df.set(null,detail);sc.set(null,5);
            String identity;try(var in=new java.io.DataInputStream(new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(routing)))){in.readInt();identity=in.readUTF();}
            int nw=store.manifest().width()*256,nh=store.manifest().height()*256;
            var hydrology=WorldHydrology.readPrepared(routing,identity,nw,nh);
            var gf=WorldHydrology.class.getDeclaredField("grid");gf.setAccessible(true);var grid=(DrainageGrid)gf.get(hydrology);
            hydrology.previewWater(54021/5.0,21052/5.0);
            var mf=WorldHydrology.class.getDeclaredField("suppressedDesertLakes");mf.setAccessible(true);var mask=(boolean[])mf.get(hydrology);
            int lakes=0,removed=0;
            for(int i=0;i<mask.length;i++){
                double x=(i%grid.width*16-nw/2.0)*5,z=(i/grid.width*16-nh/2.0)*5;
                if(x<49000||x>59000||z<16000||z>26000)continue;
                if(grid.lakeDepth(i)>15)lakes++;if(mask[i])removed++;
            }
            assertTrue(removed>0,"Screenshot district must lose excess isolated pond nodes");
            var preview=new com.github.xandergos.terraindiffusionmc.world.TerrainPreview(store,detail,hydrology,5);
            var tile=preview.sample(52000,19000,128,32);var counts=new java.util.TreeMap<Short,Integer>();
            for(short id:tile.biomes())counts.merge(id,1,Integer::sum);
            String report="Screenshot +/-5000 blocks: lake nodes="+lakes+", suppressed="+removed+"; preview biome counts="+counts;
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("build/desert-review"));
            java.nio.file.Files.writeString(java.nio.file.Path.of("build/desert-review/saved-world-audit.txt"),report);System.out.println(report);
        }finally{sf.set(null,oldS);df.set(null,oldD);sc.set(null,oldC);}
    }
}
