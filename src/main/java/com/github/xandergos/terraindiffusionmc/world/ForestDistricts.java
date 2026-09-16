package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;

/** Shared broad canopy, clearing and desire-line masks. Ponds remain saved-hydrology-owned. */
public final class ForestDistricts {
    public record District(double canopy,double clearing,double path){}
    private ForestDistricts(){}
    public static District at(double x,double z,long seed){
        double clearing=DesertTerrain.smooth((DesertTerrain.unit(x,z,seed^0x434C454152L,330)-.60)/.18);
        double line=Math.abs(DesertTerrain.noise(x,z,seed^0x50415448L,480));
        double path=1-DesertTerrain.smooth((line-.004)/.012);
        return new District((1-clearing)*(1-path),clearing,path);
    }
    public static boolean woodland(short id){return switch(id){
        case BiomeIds.FOREST,BiomeIds.FOREST_SPARSE,BiomeIds.BIRCH_FOREST,BiomeIds.OLD_GROWTH_BIRCH_FOREST,
             BiomeIds.DARK_FOREST,BiomeIds.TAIGA,BiomeIds.TAIGA_SPARSE,BiomeIds.OLD_GROWTH_PINE_TAIGA,
             BiomeIds.OLD_GROWTH_SPRUCE_TAIGA,BiomeIds.JUNGLE,BiomeIds.SPARSE_JUNGLE,BiomeIds.BAMBOO_JUNGLE -> true;
        default -> false;};}
}
