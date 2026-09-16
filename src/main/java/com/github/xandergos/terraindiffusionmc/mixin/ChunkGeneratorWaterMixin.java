package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.world.*;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorWaterMixin {
    @Shadow public abstract BiomeSource getBiomeSource();
    @Inject(method="generateFeatures",at=@At("TAIL"))
    private void terrainDiffusion$finishMountainSurface(StructureWorldAccess world,Chunk chunk,StructureAccessor accessor,CallbackInfo ci){
        if(!(getBiomeSource() instanceof TerrainDiffusionBiomeSource)||WorldBlueprintManager.generationVersion()<6)return;
        int tile=com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig.tileSize();
        int tx=Math.floorDiv(chunk.getPos().getStartX(),tile)*tile,tz=Math.floorDiv(chunk.getPos().getStartZ(),tile)*tile;
        var data=com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.getInstance().fetchHeightmap(tz,tx,tz+tile,tx+tile);
        TerrainDiffusionSurfaceDecorator.decorateMountainZones(chunk,data,tx,tz,true);
        if(WorldBlueprintManager.generationVersion()>=7)
            MarineDecorator.decorate(world,chunk,data,tx,tz);
        if(WorldBlueprintManager.generationVersion()>=11)
            LocalLandscapeDecorator.decorate(world,chunk,data,tx,tz);
    }
    @Inject(method="generateFeatures",at=@At("HEAD"))
    private void terrainDiffusion$water(StructureWorldAccess world,Chunk chunk,StructureAccessor accessor,CallbackInfo ci){
        if(getBiomeSource() instanceof TerrainDiffusionBiomeSource&&WorldBlueprintManager.generationVersion()>=3){
            if(WorldBlueprintManager.generationVersion()>=4){
                int tile=com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig.tileSize();
                int tx=Math.floorDiv(chunk.getPos().getStartX(),tile)*tile,tz=Math.floorDiv(chunk.getPos().getStartZ(),tile)*tile;
                var data=com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider.getInstance().fetchHeightmap(tz,tx,tz+tile,tx+tile);
                TerrainDiffusionSurfaceDecorator.decorate(chunk,data,tx,tz);
                TerrainDiffusionWaterDecorator.decorate(chunk,data,tx,tz);
                if(WorldBlueprintManager.generationVersion()>=12)
                    DesertSurface.decorate(chunk,data,tx,tz);
                if(WorldBlueprintManager.generationVersion()>=13)
                    RegionalSurfaceDecorator.decorate(chunk,data,tx,tz);
                if(WorldBlueprintManager.generationVersion()>=5)
                    TerrainDiffusionMicroDecorator.decorate(world,chunk,(ChunkGenerator)(Object)this,data,tx,tz);
                if(WorldBlueprintManager.generationVersion()>=7)
                    SandyDecorator.decorate(world,chunk,(ChunkGenerator)(Object)this,data,tx,tz);
                if(WorldBlueprintManager.generationVersion()>=12)
                    DesertVegetation.decorate(world,chunk,data,tx,tz);
                ForestDispatcher.decorate(chunk,data,tx,tz);
            }else{
                TerrainDiffusionWaterDecorator.decorate(chunk);
            }
        }
    }
}
