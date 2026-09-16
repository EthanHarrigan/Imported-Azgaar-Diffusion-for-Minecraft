package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, deterministic rock fields with freely scattered sites and broad clearings. */
public final class DesertLandforms {
    public static final int SPACING=512,SUPPORT=128;
    public record Sample(double duneHeight,double rockHeight,double apron,int archetype,long siteSeed){
        public double offset(){return duneHeight+rockHeight;}
    }
    private record SiteKey(long seed,int x,int z){}
    private record Site(long seed,int type,double angle,double x,double z,double scale,double height){}
    private static final ConcurrentHashMap<SiteKey,Site[]> SITES=new ConcurrentHashMap<>();
    private DesertLandforms(){}
    public static Sample sample(double x,double z,long seed){return sample(x,z,seed,.5);}
    private static Sample sample(double x,double z,long seed,double sandSupply){
        int sx=(int)Math.floor(x/SPACING),sz=(int)Math.floor(z/SPACING);
        double rock=0,apron=0,shelter=1;int type=0;long siteSeed=seed;
        // Cells only index independent candidates; footprints cross cell boundaries freely.
        for(int cz=sz-1;cz<=sz+1;cz++)for(int cx=sx-1;cx<=sx+1;cx++){
            var key=new SiteKey(seed,cx,cz);var sites=SITES.get(key);
            if(sites==null){sites=plan(key);if(SITES.size()>4096)SITES.clear();SITES.put(key,sites);}
            for(var site:sites){
                double dx=(x-site.x)/site.scale,dz=(z-site.z)/site.scale;
                double radius=Math.hypot(dx,dz);
                if(radius>=SUPPORT+70)continue;
                double edge=80-shape(dx,dz,site.type,site.angle,site.seed);
                double footprint=DesertTerrain.smooth(edge/12);
                double crown=.72+.20*DesertTerrain.noise(dx,dz,site.seed,38)+.08*DesertTerrain.noise(dx,dz,site.seed^17,9);
                double raw=site.height*footprint*crown;
                double step=2+4*site.scale,phase=raw/step;
                double raised=Math.max(0,Math.floor(phase)*step+step*DesertTerrain.smooth((phase-Math.floor(phase)-.35)/.65));
                if(raised>rock){rock=raised;type=site.type;siteSeed=site.seed;}
                apron=Math.max(apron,DesertTerrain.smooth((edge+24)/24)*(1-footprint));
                shelter=Math.min(shelter,DesertTerrain.smooth((radius-SUPPORT)/70));
            }
        }
        double dune=DuneField.height(x,z,seed,sandSupply);
        dune*=.12+.88*shelter;
        return new Sample(dune+2*apron,rock,apron,type,siteSeed);
    }
    static double density(double x,double z,long seed){
        double warp=200*DesertTerrain.noise(x,z,seed^92,700);
        double broad=DesertTerrain.unit(x+warp,z-warp,seed^0x434C454152L,1400);
        double cluster=DesertTerrain.unit(x,z,seed^0x434C555354L,430);
        return DesertTerrain.smooth((broad-.34)/.34)*(.12+.88*DesertTerrain.smooth((cluster-.25)/.5));
    }
    public record Completion(short metres,int baseY,int topY,Sample land){}
    public static Completion complete(float elevation,int x,int z,short biome,boolean wet,double boundary,double water,
                                      DesertTerrain.Field f,long seed,int scale){
        int base=HeightConverter.convertToMinecraftHeight((short)elevation,scale);
        double gate=protectedGate(biome,wet,boundary,water,f.slope(),f.waterProtection(),base);
        if(gate==0)return new Completion((short)elevation,base,base,new Sample(0,0,0,0,seed));
        Sample land=sample(x,z,seed,f.sand());
        short metres=(short)Math.clamp((int)Math.floor(elevation+gate*land.offset()*30/scale),Short.MIN_VALUE,Short.MAX_VALUE);
        return new Completion(metres,base,HeightConverter.convertToMinecraftHeight(metres,scale),
                new Sample(land.duneHeight()*gate,land.rockHeight()*gate,land.apron()*gate,land.archetype(),land.siteSeed()));
    }
    private static Site[] plan(SiteKey k){
        var result=new java.util.ArrayList<Site>();
        // Independent occupied slots approximate a Poisson process; no mandatory rock per cell.
        for(int i=0;i<6;i++){
            long seed=k.seed^(0x632BE59BD9B4E019L*(i+1));
            double x=(k.x+SurfaceAccentRules.unit(seed,k.x,k.z,1))*SPACING;
            double z=(k.z+SurfaceAccentRules.unit(seed,k.x,k.z,2))*SPACING;
            if(SurfaceAccentRules.unit(seed,k.x,k.z,3)>.68*density(x,z,k.seed))continue;
            double size=.25+.75*Math.pow(SurfaceAccentRules.unit(seed,k.x,k.z,4),1.35);
            int type=(int)(SurfaceAccentRules.unit(seed,k.x,k.z,5)*4);
            double angle=SurfaceAccentRules.unit(seed,k.x,k.z,6)*Math.PI*2;
            double height=(28+65*SurfaceAccentRules.unit(seed,k.x,k.z,7))*(.35+.65*size);
            result.add(new Site(seed,type,angle,x,z,size,height));
        }
        return result.toArray(Site[]::new);
    }
    private static double shape(double x,double z,int type,double angle,long seed){
        if(x*x+z*z>=SUPPORT*SUPPORT)return 10000;
        double a=x*Math.cos(angle)+z*Math.sin(angle),b=-x*Math.sin(angle)+z*Math.cos(angle);
        double d=switch(type){
            case 0->Math.hypot(a,b*.96); // broken disc/table
            case 1->Math.min(Math.hypot(a+35,b+12),Math.hypot((a-32)*1.1,b-21)); // twin massif
            case 2->Math.min(Math.hypot(a*.72,(b+25)*2),Math.hypot((a-26)*1.4,(b-35)*1.6)); // fins/eruption
            default->Math.min(Math.hypot(a*.64,b*1.6),Math.hypot((a+45),b+38)); // hooked chain (minority)
        };
        d+=13*DesertTerrain.noise(x,z,seed,31)+4*DesertTerrain.noise(x,z,seed^19,7);
        // Subtractive notch intersects a crown, creating a saddle with broken scarps.
        d+=24*Math.exp(-Math.pow((a-10)/17,2))*DesertTerrain.smooth((b+10)/40);
        return d;
    }
    public static double protectedGate(short biome,boolean wet,double boundary,double water,double slope,double sourceProtection,int baseY){
        if(biome!=com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds.DESERT||wet)return 0;
        return DesertTerrain.smooth(boundary/32)*DesertTerrain.smooth((water-12)/40)
                *(1-DesertTerrain.smooth((slope-.18)/.45))*sourceProtection
                *DesertTerrain.smooth((baseY-HeightConverter.seaLevel()-5)/24.0);
    }
}
