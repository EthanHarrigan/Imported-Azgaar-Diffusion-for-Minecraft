package com.github.xandergos.terraindiffusionmc.pipeline;
import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
class ReservationOptimizationTest {
 static boolean modern(){return WorldBlueprintManager.generationVersion()>=3;}
 static boolean blended(){return WorldBlueprintManager.generationVersion()>=4;}
 static float regionNoise(double x,double z,long salt,double period){return BiomeClassifier.regionNoise(x,z,salt,period);}
 static boolean isFlatStructureBiome(short id){return invokeBoolean("isFlatStructureBiome",new Class[]{short.class},id);}
 static boolean compatibleTerrain(short id,float e,float slope){return invokeBoolean("compatibleTerrain",new Class[]{short.class,float.class,float.class},id,e,slope);}
 static boolean invokeBoolean(String name,Class<?>[] types,Object... args){try{var m=BiomeClassifier.class.getDeclaredMethod(name,types);m.setAccessible(true);return (boolean)m.invoke(null,args);}catch(Exception e){throw new RuntimeException(e);}}
    private static short reference(short current,boolean ocean,float elev,float slope,int x,int z){
        short selected=current;double best=0;
        for(BiomeReservation reservation:WorldBlueprintManager.biomeReservations()){
            short target=reservation.biomeId();
            if(BiomeIds.isOcean(target)!=ocean&&!BiomeIds.isShore(target))continue;
            if(BiomeIds.isShore(target)&&(ocean||elev>220))continue;
            if(blended()&&BiomeIds.isShore(target)&&Math.abs(WorldBlueprintManager.ecologyAt(x,z).coastDistanceKm())>3.2)continue;
            if(!ocean&&!BiomeIds.isShore(target)&&isFlatStructureBiome(target)&&slope>.40f)continue;
            WorldBlueprintManager.BlockLocation p=WorldBlueprintManager.reservationCenter(reservation);
            double radius=Math.max(1,p.radius());
            if(modern()&&(Math.abs(x-p.x())>radius*1.7||Math.abs(z-p.z())>radius*1.7))continue;
            double dx=x-p.x()+regionNoise(x,z,701+target,Math.max(180,radius*.9))*radius*.24;
            double dz=z-p.z()+regionNoise(x,z,809+target,Math.max(180,radius*.9))*radius*.24;
            double aspect=.82+.28*(regionNoise(p.x(),p.z(),907+target,4000)+1)*.5;
            double d=Math.hypot(dx/(radius*aspect),dz/(radius/aspect));
            double strength=1-d+regionNoise(x,z,401+target,Math.max(90,p.radius()*.42))*.20;
            if(modern())strength-=(regionNoise(x,z,431+target,70*WorldScaleManager.getCurrentScale())+1)*.5*Math.min(.4,80*WorldScaleManager.getCurrentScale()/radius);
            if(strength>best&&compatibleTerrain(target,elev,slope)){best=strength;selected=target;}
        }
        if(blended()&&selected!=current&&best<.20){
            if(selected==WINDSWEPT_FOREST||selected==FOREST||selected==BIRCH_FOREST||selected==DARK_FOREST||selected==FLOWER_FOREST)return FOREST_SPARSE;
            if(selected==WINDSWEPT_SAVANNA)return SAVANNA;
            if(selected==OLD_GROWTH_PINE_TAIGA||selected==OLD_GROWTH_SPRUCE_TAIGA)return TAIGA_SPARSE;
            if(selected==STONY_SHORE)return current;
        }
        return selected;
    }


 @Test void indexedClassificationMatchesOriginalIncludingReservationEdges() throws Exception {
  String input=System.getProperty("terrainDiffusion.testBlueprint");assumeTrue(input!=null);
  var store=new BlueprintTileStore(Path.of(input));var detail=BlueprintDetailMap.read(Path.of(input).resolve("ecology.bin.gz"));
  var sf=WorldBlueprintManager.class.getDeclaredField("store");sf.setAccessible(true);var df=WorldBlueprintManager.class.getDeclaredField("detail");df.setAccessible(true);
  var rf=WorldBlueprintManager.class.getDeclaredField("finalReservations");rf.setAccessible(true);var sc=WorldScaleManager.class.getDeclaredField("currentScale");sc.setAccessible(true);
  var seedField=BiomeClassifier.class.getDeclaredField("contextSeed");seedField.setAccessible(true);
  Object os=sf.get(null),od=df.get(null),or=rf.get(null),oc=sc.get(null),oldSeed=seedField.get(null);
  var m=BiomeClassifier.class.getDeclaredMethod("applyCoverageReservation",short.class,boolean.class,float.class,float.class,int.class,int.class);m.setAccessible(true);
  try {
   sf.set(null,store);df.set(null,detail);rf.set(null,null);
   for(int scale:new int[]{1,3,5,6})for(long seed:new long[]{0,330971835197786486L,Long.MIN_VALUE}) {
   sc.set(null,scale);BiomeClassifier.configure(seed,store.manifest().generationFingerprint());
   var points=new java.util.ArrayList<int[]>();
   for(var r:WorldBlueprintManager.biomeReservations()) {var c=WorldBlueprintManager.reservationCenter(r);
    for(double dz:new double[]{-1.701,-1.7,-1,0,1,1.7,1.701})for(double dx:new double[]{-1.701,-1.7,-1,0,1,1.7,1.701})
     points.add(new int[]{(int)(c.x()+dx*c.radius()),(int)(c.z()+dz*c.radius())});
   }
   var random=new java.util.Random(34);for(int i=0;i<1000;i++)points.add(new int[]{random.nextInt(300000)-150000,random.nextInt(150000)-75000});
   long before=System.nanoTime();short[] expected=new short[points.size()*3];int k=0;
   for(var p:points)for(int mode=0;mode<3;mode++)expected[k++]=reference(PLAINS,mode==0,mode==0?-100:mode==1?100:2200,mode==2?.6f:.1f,p[0],p[1]);
   long referenceNs=System.nanoTime()-before;before=System.nanoTime();k=0;
   for(var p:points)for(int mode=0;mode<3;mode++)assertEquals(expected[k++],(short)m.invoke(null,PLAINS,mode==0,mode==0?-100f:mode==1?100f:2200f,mode==2?.6f:.1f,p[0],p[1]));
   System.out.println("Reservation comparisons="+k+" referenceMs="+referenceNs/1e6+" optimizedMs="+(System.nanoTime()-before)/1e6+" (includes reflection/assertions; not an end-to-end benchmark)");
   }
  }finally{sf.set(null,os);df.set(null,od);rf.set(null,or);sc.set(null,oc);seedField.set(null,oldSeed);}
 }
}
