package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;

/** Climate-relative mountain ecology in physical metres, independent of block scale. */
public final class MountainZones {
    private MountainZones(){}
    public record Bands(float treeLine,float snowLine){}
    public static Bands bands(float elevation,float temperature){
        float seaTemperature=temperature+.0065f*elevation;
        float tree=Math.max(1500,Math.min(4000,(seaTemperature-3)/.0065f));
        float snow=Math.max(tree+650,Math.min(6700,(seaTemperature+7)/.0065f));
        return new Bands(tree,snow);
    }
    public static short biome(short fallback,float elevation,float temperature,float rain,float slope,
                              float concavity,int x,int z,int scale,long seed){
        if(elevation<1800||BiomeIds.isOcean(fallback)||BiomeIds.isShore(fallback))return fallback;
        Bands bands=bands(elevation,temperature);
        double nx=x/(double)Math.max(1,scale),nz=z/(double)Math.max(1,scale);
        double patch=WorldHydrology.edgeNoise(nx,nz,seed^0x414C50494E45L,110);
        if(SurfaceTransitions.enabled())patch=WorldHydrology.edgeNoise(nx,nz,seed^0x414C50494E45L,110*SurfaceTransitions.mapFactor());
        float treeLine=bands.treeLine()+(float)patch*180,snowLine=bands.snowLine()+(float)patch*280;
        if(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=9
                &&rain>400&&elevation<snowLine-400&&concavity>-180
                &&foothillGrass(elevation,treeLine,slope,nx,nz,seed))return BiomeIds.MEADOW;
        // Vegetation belongs on shoulders and benches, never on cliffs or arid highlands.
        if(elevation<treeLine&&rain>400&&slope<.32f){
            if(elevation>treeLine-350)return patch>.15?BiomeIds.GROVE:BiomeIds.MEADOW;
            return patch<-.15?BiomeIds.TAIGA_SPARSE:BiomeIds.TAIGA;
        }
        if(elevation<snowLine-450){
            if(slope<.26f&&rain>300&&elevation<treeLine+700)return BiomeIds.MEADOW;
            return BiomeIds.STONY_PEAKS;
        }
        if(elevation<snowLine+450){
            float coverage=(elevation-(snowLine-450))/900f;
            if(SurfaceTransitions.enabled()){
                double grain=(WorldHydrology.edgeNoise(nx,nz,seed^0x534E4F574652494EL,9)+1)*.5;
                if(grain>coverage||slope>.65f)return BiomeIds.STONY_PEAKS;
                return BiomeIds.SNOWY_SLOPES;
            }
            if((patch+1)*.5>coverage||slope>.65f)return BiomeIds.STONY_PEAKS;
            return BiomeIds.SNOWY_SLOPES;
        }
        if(slope>.85f)return BiomeIds.JAGGED_PEAKS;
        if(concavity>25&&slope<.28f&&rain>800)return BiomeIds.FROZEN_PEAKS;
        return slope>.42f?BiomeIds.FROZEN_PEAKS:BiomeIds.SNOWY_SLOPES;
    }
    public static boolean mountainBiome(short id){
        return id==BiomeIds.STONY_PEAKS||id==BiomeIds.FROZEN_PEAKS||id==BiomeIds.JAGGED_PEAKS||id==BiomeIds.SNOWY_SLOPES||id==BiomeIds.MEADOW||id==BiomeIds.GROVE;
    }
    public static boolean foothillGrass(float elevation,float treeLine,float slope,double x,double z,long seed){
        if(slope>.42||elevation<treeLine-500)return false;
        double broad=WorldHydrology.edgeNoise(x,z,seed^0x4752415353524944L,90);
        double grain=WorldHydrology.edgeNoise(x,z,seed^0x464F4F5448494CL,12);
        double upper=treeLine+650+broad*350;
        double coverage=Math.max(0,Math.min(1,(upper-elevation)/650))*(1-Math.max(0,(slope-.16)/.30));
        return (grain+1)*.5<coverage;
    }
}
