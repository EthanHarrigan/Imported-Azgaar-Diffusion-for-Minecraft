package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.hydrology.WorldHydrology;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/** Coherent surface rock provinces and elevation bands; this never edits subsurface/cave geology. */
public final class SurfaceGeology {
    private SurfaceGeology() {}
    public static Block rock(int x,int y,int z,short biome,long seed){
        if(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager.generationVersion()>=12){
            int scale=WorldScaleManager.getCurrentScale();
            var f=com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.at(x/(double)scale,z/(double)scale,seed);
            if(SurfaceTransitions.enabled()?com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain.aridRockAt(f.dry(),x/(double)scale,z/(double)scale,seed):f.dry()>.5)return DesertSurface.rock(x/(double)scale,y,z/(double)scale,f,seed);
        }
        double province=WorldHydrology.edgeNoise(x,z,seed^0x47454F50524F564CL,2600);
        double exposure=WorldHydrology.edgeNoise(x,z,seed^0x47454F5041544348L,125);
        double band=WorldHydrology.edgeNoise(x,y*.72+z,seed^0x535452415441L,58);
        if(SurfaceTransitions.enabled()){
            double warm=SurfaceTransitions.smooth((province+.1)/.9),pale=SurfaceTransitions.smooth((-province-.1)/.9);
            return switch(SurfaceTransitions.choose(SurfaceTransitions.grain(x,z,seed^((long)y/5)),1,.6*(1-Math.abs(province)),warm*.5,pale*.5,pale*.15,.12*(band+1))){
                case 1->Blocks.ANDESITE;case 2->Blocks.GRANITE;case 3->Blocks.DIORITE;case 4->Blocks.CALCITE;case 5->Blocks.TUFF;default->Blocks.STONE;
            };
        }
        if(biome==BiomeIds.BADLANDS||biome==BiomeIds.ERODED_BADLANDS||biome==BiomeIds.WOODED_BADLANDS)
            return exposure>.28?Blocks.RED_SANDSTONE:Blocks.TERRACOTTA;
        if(biome==BiomeIds.DESERT)return Blocks.SANDSTONE;
        if(province>.58)return band>.18?Blocks.GRANITE:Blocks.ANDESITE;
        if(province<-.62)return band>.38?Blocks.CALCITE:Blocks.DIORITE;
        if(province>.12&&exposure>-.32)return Blocks.ANDESITE;
        if(province<-.18&&band>.56)return Blocks.TUFF;
        return Blocks.STONE;
    }
}
