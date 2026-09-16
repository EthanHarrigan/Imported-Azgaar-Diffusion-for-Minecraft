package com.github.xandergos.terraindiffusionmc.world;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class DuneFieldTest {
 @Test void boundedVariedDunesHaveOpenGroundAndNoCellEdgeSteps() throws Exception {
  long seed=330971835197786486L;int open=0,raised=0,total=0;double sparseSum=0,richSum=0;double max=0,maxStep=0;
  var out=Path.of("build/dune34-review");Files.createDirectories(out);
  long started=System.nanoTime();
  try(var w=Files.newBufferedWriter(out.resolve("height.csv"))){
   w.write("x,z,height\n");
   for(int z=-512;z<512;z+=4)for(int x=-512;x<512;x+=4){
    double h=DuneField.height(x,z,seed,.6);assertTrue(h>=0&&h<=16);
    sparseSum+=DuneField.height(x,z,seed,.1);richSum+=DuneField.height(x,z,seed,1);
    if(h<.1)open++;if(h>4)raised++;total++;max=Math.max(max,h);
    maxStep=Math.max(maxStep,Math.abs(h-DuneField.height(x+.01,z,seed,.6)));
    w.write(x+","+z+","+h+"\n");
   }
  }
  assertTrue(open>total*.15&&open<total*.9,"Open interdunes and substantial sand forms coexist");
  assertTrue(richSum>sparseSum*1.5,"Sand-rich districts must carry denser connected dune forms");
  assertTrue(raised>total*.025);assertTrue(max>10);assertTrue(maxStep<.08);
  for(int z=-384;z<=384;z+=96)for(int x=-384;x<=384;x+=96){
   assertEquals(DuneField.height(x-.00001,z,seed,.6),DuneField.height(x+.00001,z,seed,.6),.001);
   assertEquals(DuneField.height(x,z-.00001,seed,.6),DuneField.height(x,z+.00001,seed,.6),.001);
  }
  System.out.println("Dune samples="+total+" open="+open/(double)total+" raised="+raised/(double)total+" max="+max+" elapsedMs="+(System.nanoTime()-started)/1e6+" including CSV and checks");
 }
 @Test void queryOrderAndChunkPartitionDoNotChangeDunes(){
  long seed=41;double[] expected=new double[32*32];
  for(int z=0;z<32;z++)for(int x=0;x<32;x++)expected[z*32+x]=DuneField.height(x-17,z-17,seed,.3);
  for(int z=31;z>=0;z--)for(int x=31;x>=0;x--)assertEquals(expected[z*32+x],DuneField.height(x-17,z-17,seed,.3));
  assertNotEquals(DuneField.height(0,0,seed,.3),DuneField.height(0,0,seed+1,.3));
 }
 @Test void fullCellEdgesAndCacheEvictionRemainContinuousAcrossSeeds(){
  double[] supplies={0,.3,.7,1};
  for(long seed:new long[]{0,41,330971835197786486L,Long.MIN_VALUE}) {
   for(int boundary=-960;boundary<=960;boundary+=96)for(int along=-701;along<703;along+=23)for(double supply:supplies) {
    assertEquals(DuneField.height(boundary-.0001,along,seed,supply),DuneField.height(boundary+.0001,along,seed,supply),.003);
    assertEquals(DuneField.height(along,boundary-.0001,seed,supply),DuneField.height(along,boundary+.0001,seed,supply),.003);
   }
  }
  double[] before=new double[256];
  for(int i=0;i<before.length;i++)before[i]=DuneField.height(i*7-800,i*11-1200,41,.7);
  for(int i=0;i<6000;i++)DuneField.height(i*192.0,-i*192.0,73,.5);
  for(int i=255;i>=0;i--)assertEquals(before[i],DuneField.height(i*7-800,i*11-1200,41,.7));
 }
}
