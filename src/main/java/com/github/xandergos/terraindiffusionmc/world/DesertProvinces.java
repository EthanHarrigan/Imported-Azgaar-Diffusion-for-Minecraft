package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import net.minecraft.block.*;

/** Broad mountain-contact belt above two coherent sand provinces. */
public final class DesertProvinces {
    private DesertProvinces(){}
    public static boolean mountainContact(double x,double z,long seed){
        var map=WorldBlueprintManager.detail();var store=WorldBlueprintManager.activeStore();if(map==null||store==null)return false;
        double nw=store.manifest().width()*256.0,nh=store.manifest().height()*256.0,u=x/nw+.5,v=z/nh+.5;
        float center=map.sample(0,u,v),high=center;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)
            if(dx!=0||dz!=0)high=Math.max(high,map.sample(0,u+dx*160/nw,v+dz*160/nh));
        double weight=contactWeight(center,high);
        return weight>.38+.09*DesertTerrain.noise(x,z,seed^0x464F4F544CL,210);
    }
    static double contactWeight(double center,double high){
        return DesertTerrain.smooth((high-1400)/900)*DesertTerrain.smooth((center-250)/650);
    }
    public static Block strata(double x,int y,double z,long seed){
        // Depositional beds persist across a mountainside. Independent boundary offsets
        // make 24..104-block packages, without repeating the old 37-block color cycle.
        double bed=y+18*DesertTerrain.noise(x,z,seed^0x424544535441L,900)
                +5*DesertTerrain.noise(x,z,seed^0x464F4C4453L,260);
        int band=(int)Math.floor(bed/64);
        if(bed<boundary(band,seed))band--;
        else if(bed>=boundary(band+1,seed))band++;
        double bottom=boundary(band,seed),thickness=boundary(band+1,seed)-bottom;
        double position=bed-bottom;
        double tint=unit(band,seed,0x434C4159L);
        // Lighten the main body one palette step, retaining broad dark mineral
        // patches at multiple elevations rather than a uniform altitude gradient.
        double patch=darkExposure(x,y,z,seed);
        Block base=tint<.70?(patch>(tint<.44?.18:.38)?Blocks.BROWN_TERRACOTTA:Blocks.TERRACOTTA):
                tint<.82?(patch>.30?Blocks.BROWN_TERRACOTTA:Blocks.RED_TERRACOTTA):
                tint<.92?(patch>.50?Blocks.GRAY_TERRACOTTA:patch>.10?Blocks.BROWN_TERRACOTTA:Blocks.TERRACOTTA):
                (patch>-.12?Blocks.TERRACOTTA:Blocks.ORANGE_TERRACOTTA);
        double seam=unit(band,seed,0x5345414DL);
        double seamWidth=1+7*unit(band,seed,0x5749445448L);
        // Some contacts have pale sediment; others are uninterrupted or dark seams.
        if(seam<.64&&position<seamWidth)
            return seam<.23?Blocks.WHITE_TERRACOTTA:seam<.45?Blocks.LIGHT_GRAY_TERRACOTTA:Blocks.GRAY_TERRACOTTA;
        // Occasional secondary lenses pinch out gradually over hundreds of blocks.
        double lens=DesertTerrain.noise(x,z,seed^((long)band*0x9E3779B97F4A7C15L),440);
        if(seam>.68&&Math.abs(position-thickness*.62)<Math.max(0,lens)*9)
            return tint<.5?Blocks.TERRACOTTA:Blocks.BROWN_TERRACOTTA;
        return base;
    }
    /** Smooth 3D volumes: both peaks and depressions can expose the darker clay.
     * Horizontal coordinates are native map units; vertical coordinates are blocks. */
    static double darkExposure(double x,double y,double z,long seed){
        double height=(y+48*DesertTerrain.noise(x,z,seed^0x5041544348574152L,320))/150.0;
        int slice=(int)Math.floor(height);double t=DesertTerrain.smooth(height-slice);
        double a=DesertTerrain.noise(x,z,seed^((long)slice*0x9E3779B97F4A7C15L)^0x4441524B434CL,170);
        double b=DesertTerrain.noise(x,z,seed^((long)(slice+1)*0x9E3779B97F4A7C15L)^0x4441524B434CL,170);
        return a+(b-a)*t;
    }
    private static double boundary(int band,long seed){return band*64.0+40*(unit(band,seed,0x424F554E44L)-.5);}
    private static double unit(int band,long seed,long salt){
        long h=seed^salt^((long)band*0x9E3779B97F4A7C15L);
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;h=(h^(h>>>27))*0x94D049BB133111EBL;
        return ((h^(h>>>31))>>>11)*0x1.0p-53;
    }
}
