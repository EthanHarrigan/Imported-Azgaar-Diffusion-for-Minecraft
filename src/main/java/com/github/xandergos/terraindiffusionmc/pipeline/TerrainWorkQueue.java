package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/** Bounded, deduplicated CPU work. Waiting callers never own/cancel shared work. */
final class TerrainWorkQueue implements AutoCloseable {
    private record Work<T>(Object key, Callable<T> action, CompletableFuture<T> result, boolean background) {}
    private final ArrayDeque<Work<?>> queue = new ArrayDeque<>();
    private final Map<Object, Work<?>> pending = new HashMap<>();
    private final int capacity;
    private int active, foregroundBurst;
    private long generation;
    private boolean accepting = true, stopped;

    TerrainWorkQueue(int workers, int capacity) {
        if (workers < 1 || capacity < 1) throw new IllegalArgumentException();
        this.capacity = capacity;
        for (int i = 0; i < workers; i++) {
            Thread thread = new Thread(this::work, "terrain-diffusion-terrain-" + i);
            thread.setDaemon(true);
            thread.start();
        }
    }

    // A key must always identify the same result type. Regions use their coordinate record;
    // unrelated explorer operations use unique keys.
    <T> T await(Object key, Callable<T> action) throws Exception {
        return await(key,action,false);
    }

    <T> T awaitBackground(Object key, Callable<T> action) throws Exception {
        return await(key,action,true);
    }

    @SuppressWarnings("unchecked")
    private <T> T await(Object key, Callable<T> action, boolean background) throws Exception {
        CompletableFuture<T> result;
        synchronized (this) {
            long admittedGeneration = generation;
            for (;;) {
                if (generation != admittedGeneration || !accepting || stopped) throw new CancellationException("Terrain world is changing");
                Work<?> existing = pending.get(key);
                if (existing != null) { result = (CompletableFuture<T>) existing.result; break; }
                if (queue.size() < capacity) {
                    result = new CompletableFuture<>();
                    Work<T> work = new Work<>(key, action, result, background);
                    pending.put(key, work);
                    queue.addLast(work);
                    notifyAll();
                    break;
                }
                wait(); // Backpressure holds no inferred arrays and runs no work on callers.
            }
        }
        return result.get();
    }

    private void work() {
        for (;;) {
            Work<?> work;
            synchronized (this) {
                while (queue.isEmpty() && !stopped) {
                    try { wait(); } catch (InterruptedException ignored) { /* lifecycle uses stop */ }
                }
                if (stopped) return;
                // Prefer chunk work to explorer requests, but grant a waiting background
                // request a turn after at most three foreground dequeues.
                work = null;
                boolean wantBackground = foregroundBurst >= 3;
                for (var candidate : queue) if (candidate.background == wantBackground) { work=candidate; break; }
                if (work == null) work=queue.getFirst();
                queue.remove(work);
                foregroundBurst = work.background ? 0 : Math.min(3,foregroundBurst+1);
                active++;
                notifyAll();
            }
            run(work);
        }
    }

    private <T> void run(Work<T> work) {
        T value = null;
        Throwable failure = null;
        try { value = work.action.call(); }
        catch (Throwable e) { failure = e; }
        synchronized (this) {
            pending.remove(work.key, work);
            active--;
            if (failure == null) work.result.complete(value);
            else work.result.completeExceptionally(failure);
            notifyAll();
        }
    }

    /** Stop admission, abandon queued requests, and drain active work before globals change.
     * Never interrupt native inference or change its seed while it is still using the model. */
    synchronized void pauseAndDrain() {
        accepting = false;
        generation++;
        foregroundBurst=0;
        for (Work<?> work : queue) {
            work.result.cancel(false);
            pending.remove(work.key, work);
        }
        queue.clear();
        notifyAll();
        boolean interrupted = false;
        while (active != 0) {
            try { wait(); } catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    synchronized void resume() {
        if (stopped) throw new IllegalStateException("Queue closed");
        accepting = true;
        notifyAll();
    }

    @Override public synchronized void close() {
        pauseAndDrain();
        stopped = true;
        notifyAll();
    }
}
