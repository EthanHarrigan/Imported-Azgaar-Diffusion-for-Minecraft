package com.github.xandergos.terraindiffusionmc.pipeline;

import com.github.xandergos.terraindiffusionmc.blueprint.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Conservative spatial candidates, retaining the original reservation tie order. */
final class ReservationIndex {
    record Entry(BiomeReservation reservation,WorldBlueprintManager.BlockLocation center,double aspect){}
    private record Cell(int x,int z){}
    final List<BiomeReservation> source;
    final BlueprintTileStore store;
    final int scale;
    final long seed;
    final List<Entry> all;
    private final ConcurrentHashMap<Cell,List<Entry>> cells=new ConcurrentHashMap<>();
    ReservationIndex(List<BiomeReservation> source,BlueprintTileStore store,int scale,long seed){
        this.source=source;this.store=store;this.scale=scale;this.seed=seed;
        var entries=new ArrayList<Entry>();
        for(var r:source){
            var p=WorldBlueprintManager.reservationCenter(r);
            double aspect=.82+.28*(BiomeClassifier.regionNoise(p.x(),p.z(),907+r.biomeId(),4000)+1)*.5;
            entries.add(new Entry(r,p,aspect));
        }
        all=List.copyOf(entries);
    }
    List<Entry> at(int x,int z){
        var key=new Cell(Math.floorDiv(x,512),Math.floorDiv(z,512));
        var found=cells.get(key);if(found!=null)return found;
        double x0=key.x*512.0,z0=key.z*512.0;
        var result=new ArrayList<Entry>();
        for(var e:all){var p=e.center();double r=Math.max(1,p.radius())*1.7;
            if(p.x()+r>=x0&&p.x()-r<=x0+511&&p.z()+r>=z0&&p.z()-r<=z0+511)result.add(e);
        }
        found=List.copyOf(result);
        if(cells.size()>2048)cells.clear();cells.put(key,found);return found;
    }
}
