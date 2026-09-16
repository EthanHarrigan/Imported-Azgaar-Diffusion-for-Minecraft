package com.github.xandergos.terraindiffusionmc.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class TerrainWorkQueueTest {
    @Test void interruptedWaiterDoesNotCancelOrDuplicateSharedWork() throws Exception {
        try(var queue=new TerrainWorkQueue(2,2);var callers=Executors.newCachedThreadPool()) {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var calls=new AtomicInteger();var interrupted=new CountDownLatch(1);
            Thread first=new Thread(()->{
                try{queue.await("tile",()->{calls.incrementAndGet();entered.countDown();release.await(5,TimeUnit.SECONDS);return 42;});}
                catch(InterruptedException e){interrupted.countDown();}
                catch(Exception e){throw new AssertionError(e);}
            });
            first.start();assertTrue(entered.await(3,TimeUnit.SECONDS));
            first.interrupt();assertTrue(interrupted.await(3,TimeUnit.SECONDS));
            var second=new FutureTask<Integer>(() -> queue.await("tile",()->{calls.incrementAndGet();return -1;}));
            Thread joining=new Thread(second);joining.start();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(joining.getState()!=Thread.State.WAITING && System.nanoTime()<deadline) Thread.onSpinWait();
            assertEquals(Thread.State.WAITING,joining.getState());
            release.countDown();assertEquals(42,second.get(3,TimeUnit.SECONDS));assertEquals(1,calls.get());
            first.join();
        }
    }

    @Test void cpuWorkOverlapsWhileModelCallsRemainSerialized() throws Exception {
        try(var queue=new TerrainWorkQueue(2,4);var callers=Executors.newCachedThreadPool();var model=Executors.newSingleThreadExecutor()) {
            var cpuA=new CountDownLatch(1);var releaseA=new CountDownLatch(1);var modelB=new CountDownLatch(1);
            var modelActive=new AtomicInteger();var modelMax=new AtomicInteger();
            Callable<Integer> inference=()->{int n=modelActive.incrementAndGet();modelMax.accumulateAndGet(n,Math::max);modelActive.decrementAndGet();return 1;};
            var a=callers.submit(()->queue.await("A",()->{int n=model.submit(inference).get();cpuA.countDown();releaseA.await(5,TimeUnit.SECONDS);return n;}));
            assertTrue(cpuA.await(3,TimeUnit.SECONDS));
            var b=callers.submit(()->queue.await("B",()->{int n=model.submit(inference).get();modelB.countDown();return n;}));
            assertTrue(modelB.await(3,TimeUnit.SECONDS),"B inference must proceed while A CPU processing is held");
            releaseA.countDown();assertEquals(1,a.get());assertEquals(1,b.get());assertEquals(1,modelMax.get());
        }
    }

    @Test void failedTaskCanBeRetriedImmediately() throws Exception {
        try(var queue=new TerrainWorkQueue(2,2)) {
            assertThrows(ExecutionException.class,()->queue.await("tile",()->{throw new IllegalStateException("test failure");}));
            assertEquals(17,queue.await("tile",()->17));
        }
    }

    @Test void worldChangeDrainsActiveWorkAndCancelsQueuedAndBlockedRequests() throws Exception {
        try(var queue=new TerrainWorkQueue(1,1);var callers=Executors.newCachedThreadPool()) {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var oldRan=new AtomicInteger();
            var running=callers.submit(()->queue.await("running",()->{entered.countDown();release.await(5,TimeUnit.SECONDS);return 1;}));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            var queued=callers.submit(()->queue.await("queued",()->{oldRan.incrementAndGet();return 2;}));
            var blocked=callers.submit(()->queue.await("blocked",()->{oldRan.incrementAndGet();return 3;}));
            var paused=callers.submit(queue::pauseAndDrain);
            assertThrows(TimeoutException.class,()->paused.get(100,TimeUnit.MILLISECONDS),"Must not replace globals while active work remains");
            release.countDown();assertEquals(1,running.get(3,TimeUnit.SECONDS));paused.get(3,TimeUnit.SECONDS);
            assertThrows(ExecutionException.class,()->queued.get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->blocked.get(3,TimeUnit.SECONDS));
            assertEquals(0,oldRan.get());queue.resume();assertEquals(4,queue.await("new-world",()->4));
        }
    }
    @Test void foregroundOvertakesExplorerButCannotStarveIt() throws Exception {
        try(var queue=new TerrainWorkQueue(1,8)) {
            var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
            var blocker=new FutureTask<Integer>(()->queue.await("held",()->{entered.countDown();release.await(5,TimeUnit.SECONDS);return 0;}));
            new Thread(blocker).start();assertTrue(entered.await(3,TimeUnit.SECONDS));
            var order=new java.util.ArrayList<String>();var results=new java.util.ArrayList<FutureTask<Integer>>();
            for(int i=0;i<6;i++) {
                final int id=i;
                var call=new FutureTask<Integer>(()->id==0
                        ?queue.awaitBackground("background",()->{order.add("background");return id;})
                        :queue.await("foreground-"+id,()->{order.add("foreground-"+id);return id;}));
                Thread t=new Thread(call);t.start();results.add(call);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while(t.getState()!=Thread.State.WAITING&&System.nanoTime()<deadline)Thread.onSpinWait();
                assertEquals(Thread.State.WAITING,t.getState());
            }
            release.countDown();blocker.get(3,TimeUnit.SECONDS);for(var r:results)r.get(3,TimeUnit.SECONDS);
            assertTrue(order.getFirst().startsWith("foreground"));
            assertTrue(order.indexOf("background")<=3,"Bounded fairness for explorer work");
        }
    }
}

