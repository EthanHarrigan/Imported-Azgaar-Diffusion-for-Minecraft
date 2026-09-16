package com.github.xandergos.terraindiffusionmc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.params.ThreadWorldGenParams;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject.DhLitWorldGenRegion;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.util.gridList.ArrayGridList;
import org.spongepowered.asm.mixin.*;
import java.util.concurrent.locks.ReentrantLock;

/** DH 3.2.1 does not unlock its static structure lock when chunk generation throws. */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepStructureStart",remap=false)
public abstract class DhStructureLockMixin {
    @Shadow @Final private static ReentrantLock STRUCTURE_PLACEMENT_LOCK;

    @WrapMethod(method="generateGroup",remap=false)
    private void terrainDiffusion$releaseStructureLock(ThreadWorldGenParams params,
            DhLitWorldGenRegion region,ArrayGridList<ChunkWrapper> chunks,Operation<Void> original){
        int heldBefore=STRUCTURE_PLACEMENT_LOCK.getHoldCount();
        try{
            original.call(params,region,chunks);
        }finally{
            // Preserve any enclosing caller's reentrant ownership; never unlock another thread.
            while(STRUCTURE_PLACEMENT_LOCK.getHoldCount()>heldBefore)STRUCTURE_PLACEMENT_LOCK.unlock();
        }
    }
}
