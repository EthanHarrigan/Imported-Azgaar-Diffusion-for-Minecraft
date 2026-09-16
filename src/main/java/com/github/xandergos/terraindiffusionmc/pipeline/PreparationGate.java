package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.concurrent.Callable;

/** One world-load preparation attempt. A fatal failure is shared by all queued requests. */
final class PreparationGate<T> {
    private boolean complete;
    private T value;
    private RuntimeException failure;
    synchronized T get(Callable<T> prepare) {
        if(failure!=null)throw failure;
        if(complete)return value;
        try {value=prepare.call();complete=true;return value;}
        catch(Exception e){
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            failure=new IllegalStateException("Terrain preparation failed; loading stopped. Completed checkpoints are preserved. Restart after resolving the reported cause.",e);
            throw failure;
        }
    }
}
