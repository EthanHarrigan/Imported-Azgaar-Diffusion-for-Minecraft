package com.github.xandergos.terraindiffusionmc.hydrology;

import com.github.xandergos.terraindiffusionmc.blueprint.*;

/** Static wind/sand approximation. Native horizontal units are 30 physical metres.
 * Heights remain physical metres; no model calls, drainage recursion or chunk-state reads. */
public final class DesertTerrain {
    private DesertTerrain(){}
    public record Field(double dry,double sand,double scrub,double red,double slope,double waterProtection){}
    private static final Field NONE=new Field(0,0,0,0,0,0);
    /** A single broad contact line, without the old 12-block material lottery.
     * The weak outer half of the ecological feather remains ordinary mountain rock. */
    public static boolean aridRock(double dry,double nx,double nz,long seed){
        return dry>.60+.065*noise(nx,nz,seed^0x41524944524F434BL,180);
    }
    public static boolean aridRockAt(double dry,double nx,double nz,long seed){
        return aridRock(dry,nx,nz,seed)&&authoredArid(nx,nz);
    }
    public static boolean authoredArid(double nx,double nz){
        var s=WorldBlueprintManager.activeStore();var d=WorldBlueprintManager.detail();
        if(s==null||d==null)return false;
        int nw=s.manifest().width()*256,nh=s.manifest().height()*256;
        return d.warmDesertWeight(nx/nw+.5,nz/nh+.5,nw)>.45;
    }
    public static Field at(double x,double z,long seed){
        var store=WorldBlueprintManager.activeStore();var map=WorldBlueprintManager.detail();
        if(store==null||map==null||WorldBlueprintManager.generationVersion()<12)return NONE;
        return at(map,store.manifest().width()*256,store.manifest().height()*256,x,z,seed);
    }
    public static Field at(BlueprintDetailMap map,int nw,int nh,double x,double z,long seed){
        double u=x/nw+.5,v=z/nh+.5;
        if(u<=0||u>=1||v<=0||v>=1)return NONE;
        // Warped continuous weights give broad sand fingers instead of source-cell outlines.
        double wx=x+38*noise(x,z,seed^0x44525957415250L,125);
        double wz=z+38*noise(x,z,seed^0x44525957415251L,125);
        double dry=map.desertWeight(wx/nw+.5,wz/nh+.5,nw);
        double water=(1-smooth(map.sample(2,u,v)/.25))*(1-smooth((map.signedLakeDistance(u,v)*nw/map.width+80)/100));
        water*=smooth(map.signedCoastDistance(u,v)*nw/map.width/35);
        double east=map.sample(0,u+64.0/nw,v),west=map.sample(0,u-64.0/nw,v);
        double south=map.sample(0,u,v+64.0/nh),north=map.sample(0,u,v-64.0/nh);
        double dx=east-west,dz=south-north;
        double slope=Math.hypot(dx,dz)/(128*30);
        double supply=unit(x,z,seed^0x53414E44534541L,730);
        // Approximate lee shelter and depressions from the existing source samples.
        boolean newer=WorldBlueprintManager.generationVersion()>=13;
        double lee=Math.max(-1,Math.min(1,-((newer?.9396926:.88)*dx+(newer?.3420201:.475)*dz)/900));
        double hollow=Math.max(-1,Math.min(1,((east+west+south+north)*.25-map.sample(0,u,v))/500));
        supply=Math.max(0,Math.min(1,supply+.13*lee+.10*hollow));
        double sand=smooth((supply-.20)/.65)*(1-smooth((slope-.12)/.48));
        double scrub=(1-sand)*(.35+.65*unit(x,z,seed^0x5343525542L,220));
        double red=smooth((unit(x,z,seed^0x524544524F434BL,1600)-.35)/.35);
        double sea=map.sandSeaWeight(u,v,nw);
        sand=sand*(1-sea)+sea*(1-smooth((slope-.28)/.65));
        scrub*=1-sea;
        red=red*(1-sea)+.25*sea;
        return new Field(dry,sand,scrub,red,slope,water);
    }
    public static double sandSea(double x,double z){
        var s=WorldBlueprintManager.activeStore();var d=WorldBlueprintManager.detail();
        return s==null||d==null||WorldBlueprintManager.generationVersion()<13?0:d.sandSeaWeight(x/(s.manifest().width()*256.0)+.5,z/(s.manifest().height()*256.0)+.5,s.manifest().width()*256);
    }
    public static float shape(float e,double x,double z,long seed){
        Field f=at(x,z,seed);if(f.dry<.001||e<=0)return e;
        double gate=smooth((e-12)/140)*(1-smooth((e-2500)/1800))*f.waterProtection;
        double ledges=f.dry*(1-f.sand)*f.waterProtection*smooth((f.slope-.12)/.2)*(1-smooth((f.slope-.65)/.35))
                *45*smooth((unit(x,z,seed^0x4F555443524F50L,35)-.35)/.45);
        // Modern worlds add dunes exactly once, after drainage/biome protection.
        return (float)(e+((WorldBlueprintManager.generationVersion()>=13?0:duneOffset(x,z,seed,f))+ledges)*gate);
    }
    /** Gentle windward face, steeper lee face; smooth endpoints avoid mathematical cliffs. */
    public static double ridge(double phase){
        double p=phase-Math.floor(phase);
        return p<.76?smooth(p/.76):1-smooth((p-.76)/.24);
    }
    public static double duneOffset(double x,double z,long seed,Field f){
        if(WorldBlueprintManager.generationVersion()>=13){
            // Native coordinates become 500-block primary crests at every world scale.
            double k=500.0/(155*com.github.xandergos.terraindiffusionmc.world.WorldScaleManager.getCurrentScale());x/=k;z/=k;
        }
        // Regionally curved phase, not rotation by an unbounded angle*x (which creates seams).
        boolean newer=WorldBlueprintManager.generationVersion()>=13;
        double along=(newer?.9396926:.88)*x+(newer?.3420201:.475)*z,across=-(newer?.3420201:.475)*x+(newer?.9396926:.88)*z;
        double warp=70*noise(x,z,seed^0x44554E4543555256L,360);
        double phase=(along+warp+24*Math.sin(across/115))/155;
        double dune=ridge(phase);
        double crescents=smooth((unit(x,z,seed^0x4352455343454EL,1050)-.52)/.24);
        if(newer){
            // High supply joins ridges; lower supply permits separate crescent forms.
            crescents*=1-smooth((f.sand-.35)/.5);
            double linear=.35*smooth((unit(x,z,seed^0x534549465245474EL,1800)-.55)/.3)*f.sand;
            double secondary=ridge((across+.18*along+warp*.8+30*noise(x,z,seed^0x5345494657415250L,280))/170);
            // Mix bounded stationary fields, never rotate unbounded positions by a varying angle.
            dune=dune*(1-linear)+secondary*linear;
        }
        if(crescents>0)dune=dune*(1-crescents)+crescent(along,across,seed)*crescents;
        // Secondary ridges are subordinate and share the prevailing direction.
        double small=ridge((along+warp*.65+10*Math.sin(across/41))/48);
        double amplitude=55+290*f.sand*f.sand;
        double coarse=(.84*dune+.16*small)*amplitude;
        double ripple=9*ridge((along+7*noise(x,z,seed,24))/12);
        return f.dry*(coarse+ripple)*(1-smooth((f.slope-.2)/.6));
    }
    /** Nine bounded, jittered candidates; crescent horns taper into the sand sheet. */
    private static double crescent(double along,double across,long seed){
        int gx=(int)Math.floor(along/180),gz=(int)Math.floor(across/180);double result=0;
        for(int z=gz-1;z<=gz+1;z++)for(int x=gx-1;x<=gx+1;x++){
            double jitter=com.github.xandergos.terraindiffusionmc.world.SurfaceAccentRules.unit(seed,x,z,0x4241524348414EL);
            double a=along-(x+.2+.6*jitter)*180,b=across-(z+.8-.6*jitter)*180;
            double side=Math.abs(b)/100;if(side>=1)continue;
            double curved=a-40*side*side,phase=(curved+70)/100;
            if(phase<=0||phase>=1)continue;
            result=Math.max(result,ridge(phase)*(1-smooth(side)));
        }
        return result;
    }
    /** Low foredunes after coast profiling but BEFORE drainage; no added sill at waterline. */
    public static float beach(float e,double x,double z,int nw,int nh,long seed){
        var map=WorldBlueprintManager.detail();if(map==null||e<=18||e>=180)return e;
        double u=x/nw+.5,v=z/nh+.5;if(u<=0||u>=1||v<=0||v>=1)return e;
        double d=map.signedCoastDistance(u,v)*nw/map.width;
        double gate=smooth((d-8)/24)*(1-smooth((d-80)/90))*smooth((e-18)/25)*(1-smooth((e-100)/80));
        gate*=1-smooth(map.sample(2,u,v)/.15);
        gate*=1-smooth((map.signedLakeDistance(u,v)*nw/map.width+60)/80);
        return (float)(e+22*gate*ridge((x*.88+z*.475+14*noise(x,z,seed,55))/37));
    }
    public static double noise(double x,double z,long seed,double wavelength){return WorldHydrology.edgeNoise(x,z,seed,wavelength);}
    public static double unit(double x,double z,long seed,double wavelength){return (noise(x,z,seed,wavelength)+1)*.5;}
    public static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
