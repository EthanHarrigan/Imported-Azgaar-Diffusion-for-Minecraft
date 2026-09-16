package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintManager;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import com.github.xandergos.terraindiffusionmc.world.*;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.structure.*;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rejects bad surface footprints after vanilla has selected valid pieces and biomes. */
@Mixin(Structure.class)
public abstract class StructureTerrainGuardMixin {
    @Inject(method="createStructureStart",at=@At("RETURN"),cancellable=true)
    private void terrainDiffusion$guard(RegistryEntry<Structure> entry,RegistryKey<World> dimension,
            DynamicRegistryManager registries,ChunkGenerator generator,BiomeSource source,NoiseConfig noise,
            StructureTemplateManager templates,long seed,ChunkPos chunkPos,int references,HeightLimitView world,
            Predicate<RegistryEntry<Biome>> predicate,CallbackInfoReturnable<StructureStart> cir){
        if(!(source instanceof TerrainDiffusionBiomeSource)||WorldBlueprintManager.generationVersion()<3)return;
        StructureStart start=cir.getReturnValue();if(start==null||!start.hasChildren())return;
        String id=entry.getKey().map(k->k.getValue().getPath()).orElse("");
        boolean monument=id.equals("monument"),ship=id.startsWith("shipwreck"),ruin=id.startsWith("ocean_ruin");
        if(monument) {
            // Base.generate reconstructs nested pieces at the matching height on reload.
            for(var piece:start.getChildren())if(piece instanceof MovableMonument movable)movable.terrainDiffusion$moveToSeaLevel();
        }
        if(monument&&WorldBlueprintManager.generationVersion()>=7&&!SurfaceAccentRules.monument(seed,chunkPos.x,chunkPos.z,WorldScaleManager.getCurrentScale())){
            cir.setReturnValue(StructureStart.DEFAULT);return;
        }
        boolean flat=id.startsWith("village_")||id.equals("mansion")||id.equals("swamp_hut")||id.equals("desert_pyramid")||id.equals("jungle_pyramid")||id.equals("pillager_outpost")||id.equals("igloo");
        if(!monument&&!ship&&!ruin&&!flat)return;
        BlockBox box=start.getBoundingBox();int spanX=box.getMaxX()-box.getMinX(),spanZ=box.getMaxZ()-box.getMinZ();
        // Sample a bounded grid across the complete structure, not only its spawn point.
        int stride=Math.max(8,Math.max(spanX,spanZ)/16),lo=Integer.MAX_VALUE,hi=Integer.MIN_VALUE;
        int size=com.github.xandergos.terraindiffusionmc.config.TerrainDiffusionConfig.tileSize();
        java.util.Map<Long,LocalTerrainProvider.HeightmapData> sampledTiles=new java.util.HashMap<>();
        for(int z=box.getMinZ();z<=box.getMaxZ()+stride;z+=stride)for(int x=box.getMinX();x<=box.getMaxX()+stride;x+=stride){
            int xx=Math.min(x,box.getMaxX()),zz=Math.min(z,box.getMaxZ());
            int tx=Math.floorDiv(xx,size)*size,tz=Math.floorDiv(zz,size)*size;
            long key=((long)tx<<32)^(tz&0xffffffffL);
            var data=sampledTiles.computeIfAbsent(key,k->LocalTerrainProvider.getInstance().fetchHeightmap(tz,tx,tz+size,tx+size));
            int r=zz-tz,c=xx-tx,y=HeightConverter.convertToMinecraftHeight(data.heightmap[r][c])-1;
            lo=Math.min(lo,y);hi=Math.max(hi,y);
            boolean inland=data.waterSurface!=null&&data.waterSurface[r][c]!=Short.MIN_VALUE;
            int sea=HeightConverter.seaLevel();
            if((flat&&(inland||y<sea))||(monument&&(y>box.getMinY()+3||y>sea-23))||(ship&&y>sea+2)||(ruin&&y>=sea)){
                cir.setReturnValue(StructureStart.DEFAULT);return;
            }
        }
        int allowed=monument?14:ship?22:id.startsWith("village_")?28:12;
        if(hi-lo>allowed)cir.setReturnValue(StructureStart.DEFAULT);
    }
}
