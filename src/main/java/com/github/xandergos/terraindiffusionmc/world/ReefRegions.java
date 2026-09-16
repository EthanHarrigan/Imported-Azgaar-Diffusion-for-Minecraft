package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import java.io.IOException;
import java.util.*;

/** At most two additional reef provinces per authored world; no full-resolution terrain work. */
public final class ReefRegions {
    private ReefRegions(){}
    public record Site(double x,double z,double radius,double score){}
    private record Cache(BlueprintTileStore store,long seed,List<Site> sites){}
    private static volatile Cache cache;
    public static List<Site> sites(BlueprintTileStore store,long seed){
        if(store==null)return List.of();Cache old=cache;if(old!=null&&old.store==store&&old.seed==seed)return old.sites;
        synchronized(ReefRegions.class){
            old=cache;if(old!=null&&old.store==store&&old.seed==seed)return old.sites;
            List<Site> result=plan(store,seed);cache=new Cache(store,seed,result);return result;
        }
    }
    public static List<Site> plan(BlueprintTileStore store,long seed){
        ArrayList<Site> candidates=new ArrayList<>();var m=store.manifest();
        try{
            for(int z=1;z<m.height()-1;z++)for(int x=1;x<m.width()-1;x++){
                float q=store.value(0,x,z),e=Math.copySign(q*q,q),temp=store.value(1,x,z);
                var prepared=WorldHydrology.preview();
                if(prepared!=null){
                    float actual=prepared.previewElevation((x+.5-m.width()/2.0)*256,(z+.5-m.height()/2.0)*256);
                    // A province requires several usable shallow samples, not one lucky pixel.
                    int usable=0;for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)
                        if(suitable(prepared.previewElevation((x+.5-m.width()/2.0)*256+dx*100,(z+.5-m.height()/2.0)*256+dz*100),temp))usable++;
                    if(usable<5)continue;e=actual;
                }
                if(!suitable(e,temp))continue;
                // Reef provinces prefer shallow warm seas, not the cold exterior or deep ocean floor.
                double score=SurfaceAccentRules.unit(seed,x,z,0x5245454653495445L)*.6+Math.min(1,(temp-18)/12.0)*.3+(1-Math.abs(e+180)/550)*.1;
                candidates.add(new Site((x+.5-m.width()/2.0)*256,(z+.5-m.height()/2.0)*256,450,score));
            }
        }catch(IOException e){throw new IllegalStateException("Cannot read reef climate",e);}
        candidates.sort(Comparator.comparingDouble(Site::score).reversed());ArrayList<Site> selected=new ArrayList<>();
        for(Site p:candidates){
            if(selected.stream().anyMatch(a->Math.hypot(p.x-a.x,p.z-a.z)<2400))continue;
            selected.add(p);if(selected.size()==2)break;
        }
        return List.copyOf(selected);
    }
    public static boolean suitable(float elevation,float temperature){return elevation< -20&&elevation> -650&&temperature>=19;}
    public static double weight(List<Site> sites,double nx,double nz,long seed){
        double weight=0;
        for(Site p:sites){
            double d=Math.hypot(nx-p.x,nz-p.z),edge=1+.18*WorldHydrology.edgeNoise(nx,nz,seed^0x5245454645444745L,90);
            weight=Math.max(weight,Math.max(0,Math.min(1,(1-d/(p.radius*edge))*3)));
        }
        return weight;
    }
}
