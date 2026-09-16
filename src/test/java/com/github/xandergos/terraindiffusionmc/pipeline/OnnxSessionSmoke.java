package com.github.xandergos.terraindiffusionmc.pipeline;
import java.nio.file.*;
/** Real native session swap smoke. Uses local weights only, never generates terrain or a save. */
public final class OnnxSessionSmoke {
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);
  var claim=OnnxModel.class.getDeclaredMethod("claimGpuSlot");claim.setAccessible(true);
  try(var coarse=new OnnxModel(root.resolve("coarse_model.onnx"),"coarse");
      var base=new OnnxModel(root.resolve("base_model.onnx"),"base");
      var decoder=new OnnxModel(root.resolve("decoder_model.onnx"),"decoder")){
   for(int round=0;round<3;round++)for(var model:new OnnxModel[]{coarse,base,decoder}){
    long start=System.nanoTime();claim.invoke(model);
    System.out.println("Native GPU session swap passed: round="+round+", elapsed="+(System.nanoTime()-start)/1e9+"s");
   }
   if(!OnnxModel.getResolvedInferenceProvider().equals("DirectML"))throw new AssertionError("Expected real DirectML");
  }
  System.out.println("PASS: nine real DirectML session creations/evictions; no world or river generation.");
 }
}
