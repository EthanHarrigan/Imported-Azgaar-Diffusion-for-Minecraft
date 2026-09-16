package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded static wind-shaped dunes. No simulation, chunk ordering or regular crest lattice. */
public final class DuneField {
    private static final int CELL=96;
    private record Key(long seed,int x,int z){}
    private record Dune(double x,double z,double cos,double sin,double height,double halfWidth,double bend,boolean secondary){}
    private static final ConcurrentHashMap<Key,Dune[]> CACHE=new ConcurrentHashMap<>();
    private DuneField(){}
    public static double height(double x,double z,long seed,double sandSupply){
        int cx=(int)Math.floor(x/CELL),cz=(int)Math.floor(z/CELL);
        double result=0,rich=DesertTerrain.smooth((sandSupply-.25)/.5);
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            var key=new Key(seed,cx+dx,cz+dz);var dunes=CACHE.get(key);
            if(dunes==null){dunes=plan(key);if(CACHE.size()>4096)CACHE.clear();CACHE.put(key,dunes);}
            for(var dune:dunes){
                double px=x-dune.x,pz=z-dune.z;
                if(px*px+pz*pz>95*95)continue;
                double along=px*dune.cos+pz*dune.sin,across=-px*dune.sin+pz*dune.cos;
                double halfWidth=dune.halfWidth*(.55+.45*rich),side=Math.abs(across)/halfWidth;
                if(side>=1)continue;
                // Narrow barchan horns extend downwind; rich sand joins longer curved crests.
                double crest=dune.bend*side*side*(1-.65*rich);
                double taper=Math.pow(1-side*side,.65+.35*(1-rich));
                double height=dune.height*taper*(dune.secondary?rich:1);
                double q=along-crest;
                double windward=dune.height*3.8,lee=dune.height*1.65;
                double profile=q<0?DesertTerrain.smooth(1+q/windward):Math.max(0,1-q/lee);
                result=Math.max(result,height*profile);
            }
        }
        return result;
    }
    private static Dune[] plan(Key k){
        var sites=new java.util.ArrayList<Dune>(6);
        for(int i=0;i<6;i++){
            long s=k.seed^((i+1)*0x632BE59BD9B4E019L);
            double x=(k.x+SurfaceAccentRules.unit(s,k.x,k.z,1))*CELL;
            double z=(k.z+SurfaceAccentRules.unit(s,k.x,k.z,2))*CELL;
            double supply=DesertTerrain.unit(x,z,k.seed^0x44554E4550415443L,530);
            double density=.12+.80*DesertTerrain.smooth((supply-.22)/.55);
            if(SurfaceAccentRules.unit(s,k.x,k.z,3)>density)continue;
            double angle=.35+.48*DesertTerrain.noise(x,z,k.seed^0x57494E44524547L,2400);
            double height=4+12*Math.pow(SurfaceAccentRules.unit(s,k.x,k.z,4),1.5);
            double width=28+43*SurfaceAccentRules.unit(s,k.x,k.z,5);
            sites.add(new Dune(x,z,Math.cos(angle),Math.sin(angle),height,width,12+15*SurfaceAccentRules.unit(s,k.x,k.z,6),i>=2));
        }
        return sites.toArray(Dune[]::new);
    }
}
