package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;

/** Deterministic broad vegetation provinces, with fine breakup so borders are never ruler-straight. */
public final class VegetationDensity {
    private VegetationDensity() {}
    public static float at(int x,int z,short biome,long seed){
        double broad=WorldHydrology.edgeNoise(x,z,seed^0x56454744454E5349L,760);
        double patch=WorldHydrology.edgeNoise(x,z,seed^0x554E44455253544FL,190);
        double base=switch(biome){
            case BiomeIds.JUNGLE,BiomeIds.BAMBOO_JUNGLE,BiomeIds.DARK_FOREST -> .82;
            case BiomeIds.FOREST,BiomeIds.BIRCH_FOREST,BiomeIds.OLD_GROWTH_BIRCH_FOREST,
                 BiomeIds.TAIGA,BiomeIds.OLD_GROWTH_PINE_TAIGA,BiomeIds.OLD_GROWTH_SPRUCE_TAIGA -> .68;
            case BiomeIds.FOREST_SPARSE,BiomeIds.TAIGA_SPARSE,BiomeIds.SPARSE_JUNGLE,
                 BiomeIds.SAVANNA,BiomeIds.SAVANNA_PLATEAU -> .45;
            case BiomeIds.PLAINS,BiomeIds.SUNFLOWER_PLAINS,BiomeIds.MEADOW -> .34;
            case BiomeIds.DESERT,BiomeIds.BADLANDS,BiomeIds.ERODED_BADLANDS -> .08;
            default -> .38;
        };
        if(SurfaceTransitions.enabled()&&ForestDistricts.woodland(biome)){
            var district=ForestDistricts.at(x,z,seed);
            return (float)(Math.clamp(base+broad*.20+patch*.08,0,1)*district.canopy());
        }
        return (float)Math.max(0,Math.min(1,base+broad*.20+patch*.08));
    }
}
