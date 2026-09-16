package com.github.xandergos.terraindiffusionmc.pipeline;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OnnxRecoveryTest {
 @Test void sessionCreationFailureReleasesGpuBeforeCpuRetry(){
  var events=new java.util.ArrayList<String>();RuntimeException failure=new RuntimeException("DirectML 8007000E");
  int result=OnnxModel.executeWithFallback(()->{events.add("create");throw failure;},
      e->{assertSame(failure,e);events.add("release");},()->{events.add("cpu");return 42;});
  assertEquals(42,result);assertEquals(java.util.List.of("create","release","cpu"),events);
 }
 @Test void successfulGpuDoesNotCreateCpuSession(){
  assertEquals(3,OnnxModel.executeWithFallback(()->3,e->fail("Unexpected cleanup"),()->{fail("Unexpected CPU");return 0;}));
 }
 @Test void bothFailuresPreserveOriginalMemoryCause(){
  RuntimeException gpu=new RuntimeException("GPU memory"),cpu=new RuntimeException("CPU memory");
  var error=assertThrows(RuntimeException.class,()->OnnxModel.executeWithFallback(()->{throw gpu;},e->{},()->{throw cpu;}));
  assertSame(cpu,error);assertSame(gpu,error.getSuppressed()[0]);
 }
}
