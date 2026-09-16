package com.github.xandergos.terraindiffusionmc.mixin;

import com.github.xandergos.terraindiffusionmc.world.VerticalProfile;
import net.minecraft.structure.OceanMonumentGenerator;
import net.minecraft.structure.StructurePiece;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/** Move the base and its nested pieces together, including pieces reconstructed after reload. */
@Mixin(OceanMonumentGenerator.Base.class)
public abstract class MonumentHeightMixin implements com.github.xandergos.terraindiffusionmc.world.MovableMonument {
    @Shadow @Final private List<StructurePiece> children;
    @Inject(method="generate",at=@At("HEAD"))
    private void terrainDiffusion$height(StructureWorldAccess world, StructureAccessor accessor,
        ChunkGenerator generator, Random random, BlockBox box, ChunkPos chunk, BlockPos pivot, CallbackInfo ci) {
        if(world.getBottomY()!=VerticalProfile.BOTTOM_Y || generator.getSeaLevel()!=VerticalProfile.SEA_LEVEL)return;
        terrainDiffusion$moveToSeaLevel();
    }
    @Unique public void terrainDiffusion$moveToSeaLevel() {
        var self=(StructurePiece)(Object)this;
        int delta=VerticalProfile.SEA_LEVEL-24-self.getBoundingBox().getMinY();
        if(delta!=0) { self.translate(0,delta,0); for(var child:children)child.translate(0,delta,0); }
    }
}
