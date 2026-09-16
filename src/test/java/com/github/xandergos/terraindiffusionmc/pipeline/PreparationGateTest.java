package com.github.xandergos.terraindiffusionmc.pipeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
class PreparationGateTest {
 @Test void fatalFailureIsNotRetriedBy129DifferentRequests(){
  var gate=new PreparationGate<Object>();var attempts=new AtomicInteger();
  var cause=new RuntimeException("DirectML out of memory");
  RuntimeException first=null;
  for(int i=0;i<129;i++){
   var error=assertThrows(IllegalStateException.class,()->gate.get(()->{attempts.incrementAndGet();throw cause;}));
   if(first==null)first=error;else assertSame(first,error);assertSame(cause,error.getCause());
  }
  assertEquals(1,attempts.get());
 }
 @Test void concurrentPreparationRunsOnceAndRestartGetsANewAttempt()throws Exception{
  var gate=new PreparationGate<Object>();var attempts=new AtomicInteger();Object prepared=new Object();
  try(var pool=Executors.newFixedThreadPool(8)){
   var tasks=new java.util.ArrayList<Future<Object>>();
   for(int i=0;i<32;i++)tasks.add(pool.submit(()->gate.get(()->{attempts.incrementAndGet();return prepared;})));
   for(var task:tasks)assertSame(prepared,task.get());
  }
  assertEquals(1,attempts.get());assertSame(prepared,new PreparationGate<>().get(()->prepared));
 }
}
