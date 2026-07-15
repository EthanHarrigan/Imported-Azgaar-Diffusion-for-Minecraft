package com.github.xandergos.terraindiffusionmc.blueprint;

import java.util.concurrent.atomic.AtomicReference;

/** Process-local handoff from the world-creation screen to integrated-server initialization. */
public final class BlueprintSelectionState {
    private static final AtomicReference<CompiledBlueprint> PENDING=new AtomicReference<>();
    private BlueprintSelectionState(){}
    public static void set(CompiledBlueprint b){PENDING.set(b);}
    public static CompiledBlueprint consume(){return PENDING.getAndSet(null);}
    public static void clear(){PENDING.set(null);}
}
