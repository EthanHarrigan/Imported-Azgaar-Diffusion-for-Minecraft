package com.github.xandergos.terraindiffusionmc.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class DhStructureLockTest {
    @Test void failingDhGenerationReleasesLockAndPreservesOriginalError() throws Exception {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        Class<?> type;
        try{type=Class.forName("com.seibel.distanthorizons.common.wrappers.worldGeneration.step.StepStructureStart");}
        catch(ClassNotFoundException e){assumeTrue(false,"Requires -PtestDhPreview");return;}
        var field=type.getDeclaredField("STRUCTURE_PLACEMENT_LOCK");field.setAccessible(true);
        var lock=(ReentrantLock)field.get(null);
        var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
        var instance=((sun.misc.Unsafe)uf.get(null)).allocateInstance(type);
        var hook=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("terrainDiffusion$releaseStructureLock")).findFirst().orElseThrow();hook.setAccessible(true);
        for(Throwable failure:new Throwable[]{new IllegalStateException("simulated terrain allocation failure"),new AssertionError("simulated generation error")}){
            Operation<Void> operation=args->{lock.lock();if(failure instanceof Error e)throw e;throw (RuntimeException)failure;};
            var thrown=assertThrows(InvocationTargetException.class,()->hook.invoke(instance,null,null,null,operation));
            assertSame(failure,thrown.getCause());assertFalse(lock.isLocked());
            try(var worker=Executors.newSingleThreadExecutor()){
                assertTrue(worker.submit(()->{boolean acquired=lock.tryLock(2,TimeUnit.SECONDS);if(acquired)lock.unlock();return acquired;}).get(3,TimeUnit.SECONDS),"Next DH worker must be able to continue");
            }
        }
        lock.lock();
        try{
            Operation<Void> nested=args->{lock.lock();throw new IllegalStateException("nested failure");};
            assertThrows(InvocationTargetException.class,()->hook.invoke(instance,null,null,null,nested));
            assertEquals(1,lock.getHoldCount(),"Do not release enclosing caller ownership");
        }finally{lock.unlock();}
        Operation<Void> success=args->{lock.lock();lock.unlock();return null;};
        hook.invoke(instance,null,null,null,success);assertFalse(lock.isLocked());
    }
}
