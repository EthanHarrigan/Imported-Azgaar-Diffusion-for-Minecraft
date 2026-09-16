package com.github.xandergos.terraindiffusionmc.world;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.hydrology.DesertTerrain;
import com.github.xandergos.terraindiffusionmc.pipeline.BiomeIds;

/** Immutable adapter over final categorical ownership and existing hydrology. Distances are blocks.
 * Signed boundary distance is positive on the owner's side; a neighbor's context has its own owner.
 * Artistic terrain never writes water levels or changes the biome ID. */
public record ColumnWorldContext(int x,int z,short biome,short neighbor,int baseY,int surfaceY,
        short waterMetres,double boundaryDistance,double boundaryWidth,double transition,
        double waterDistance,double slope,
        DesertTerrain.Field desert,DesertLandforms.Sample landform) {
    public double signedBoundaryDistance(){return biome<neighbor?-boundaryDistance:boundaryDistance;}
    public boolean dryInterior(){return biome==BiomeIds.DESERT&&transition==0;}
    public boolean wet(){return waterMetres!=Short.MIN_VALUE;}
    public boolean rock(){return biome==BiomeIds.DESERT&&landform.rockHeight()>1;}
}
