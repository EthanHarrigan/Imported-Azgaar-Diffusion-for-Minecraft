package com.github.xandergos.terraindiffusionmc.blueprint;

import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import com.github.xandergos.terraindiffusionmc.pipeline.*;
import com.github.xandergos.terraindiffusionmc.world.WorldScaleManager;
import java.nio.file.*;
import java.util.*;

/** Adversarial hot/dry classifier sweep against the actual new compiled map, without inference. */
public final class NewMapAridAudit {
    static boolean arid(short id){return id==BiomeIds.DESERT||id==BiomeIds.BADLANDS||id==BiomeIds.ERODED_BADLANDS||id==BiomeIds.WOODED_BADLANDS;}
    public static void main(String[] args)throws Exception{
        Path path=Path.of(args[0]);var store=new BlueprintTileStore(path);
        var map=BlueprintDetailMap.read(path.resolve("ecology.bin.gz"));
        var sf=WorldBlueprintManager.class.getDeclaredField("store");sf.setAccessible(true);sf.set(null,store);
        var df=WorldBlueprintManager.class.getDeclaredField("detail");df.setAccessible(true);df.set(null,map);
        var sc=WorldScaleManager.class.getDeclaredField("currentScale");sc.setAccessible(true);sc.set(null,5);
        var m=store.manifest();int nw=m.width()*256,nh=m.height()*256;
        int w=600,h=300,n=w*h,step=nw*5/w,x0=-nw*5/2,z0=-nh*5/2;
        float[] e=new float[n],climate=new float[4*n],padded=new float[(w+2)*(h+2)];
        Arrays.fill(e,500);Arrays.fill(padded,500);
        Arrays.fill(climate,0,n,29);Arrays.fill(climate,n,2*n,100);
        Arrays.fill(climate,2*n,3*n,5);Arrays.fill(climate,3*n,4*n,20);
        long checked=0,blocked=0,core=0,sea=0,ne=0;int[] totals=new int[2];
        for(long seed:new long[]{330971835197786486L,-71}){
            BiomeClassifier.configure(seed,m.generationFingerprint());
            short[] ids=BiomeClassifier.classify(e,climate,z0,x0,padded,h,w,step*6f,step);
            for(int z=0;z<h;z++)for(int x=0;x<w;x++){
                double nx=(x0+x*step)/5.0,nz=(z0+z*step)/5.0,u=nx/nw+.5,v=nz/nh+.5;
                boolean allowed=DesertTerrain.authoredArid(nx,nz);short id=ids[z*w+x];checked++;
                if(!allowed){blocked++;if(arid(id))throw new AssertionError("Arid biome outside map at "+nx+","+nz);}
                if(arid(id))core++;
                if(map.sandSeaWeight(u,v,nw)>.90){sea++;if(!arid(id))throw new AssertionError("Missing Sand Sea interior");}
                // Northeastern land, safely above the authored southern desert belt.
                if(u>.72&&v<.62&&map.sample(0,u,v)>0){ne++;if(arid(id))throw new AssertionError("Northeast desert");
                    if(DesertTerrain.aridRockAt(1,nx,nz,seed))throw new AssertionError("Northeast terracotta permission");}
            }
        }
        if(core<500||sea<50||ne<1000)throw new AssertionError("Insufficient positive/negative map coverage");
        String report="Hot/dry stress samples: "+checked+"\nOutside authored warm deserts, all non-arid: "+blocked+
            "\nRetained arid samples: "+core+"\nSand Sea interior samples: "+sea+"\nNortheast land samples with no arid biome or terracotta permission: "+ne+
            "\nSeed-independent map permission checked at scale 5 with two classifier seeds. Synthetic flat terrain, not an in-game visual test.\n";
        Files.writeString(path.resolve("arid-policy-audit.txt"),report);System.out.println(report);
    }
}
