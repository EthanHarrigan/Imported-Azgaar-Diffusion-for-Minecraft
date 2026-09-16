package com.github.xandergos.terraindiffusionmc.world;
import com.google.gson.*;
import com.github.xandergos.terraindiffusionmc.pipeline.LocalTerrainProvider;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class GenerationOverheadTest {
 @Test void snowBiomePredicateIsProtectedByUnchangedSurfaceDepthCondition() throws Exception {
  var root=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/terrain-diffusion-mc/worldgen/noise_settings/terrain_diffusion.json"))).getAsJsonObject();
  var rule=root.getAsJsonObject("surface_rule").getAsJsonArray("sequence").get(2).getAsJsonObject();
  var depth=rule.getAsJsonObject("if_true");assertEquals("minecraft:stone_depth",depth.get("type").getAsString());
  assertEquals(-1,depth.get("offset").getAsInt());assertFalse(depth.get("add_surface_depth").getAsBoolean());
  var biome=rule.getAsJsonObject("then_run");assertEquals("minecraft:biome",biome.getAsJsonObject("if_true").get("type").getAsString());
  assertTrue(biome.getAsJsonObject("if_true").getAsJsonArray("biome_is").size()>5);
 }
 @Test void cachedDensityHeightsMatchHeightConversionAtAllElevations() throws Exception {
  int width=65536;short[][] h=new short[1][width];for(int i=0;i<width;i++)h[0][i]=(short)(i+Short.MIN_VALUE);
  var scaleField=WorldScaleManager.class.getDeclaredField("currentScale");scaleField.setAccessible(true);Object old=scaleField.get(null);
  try {for(int scale=1;scale<=6;scale++) {
   scaleField.set(null,scale);var data=new LocalTerrainProvider.HeightmapData(h,new short[1][width],width,1);
   for(int i=0;i<width;i++)assertEquals(HeightConverter.convertToMinecraftHeight(h[0][i],scale),data.blockHeights[0][i]);
  }} finally {scaleField.set(null,old);}
 }
 @Test void actualMinecraftSurfaceRulesPreserveOutputAndSkipDeepBiomeLookup() throws Exception {
  net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
  var registry=net.minecraft.registry.BuiltinRegistries.createWrapperLookup().getOrThrow(net.minecraft.registry.RegistryKeys.BIOME);
  var root=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/terrain-diffusion-mc/worldgen/noise_settings/terrain_diffusion.json"))).getAsJsonObject();
  var optimized=root.getAsJsonObject("surface_rule").getAsJsonArray("sequence").get(2).getAsJsonObject();
  var original=optimized.deepCopy();var inner=optimized.getAsJsonObject("then_run");
  original.add("if_true",inner.get("if_true").deepCopy());
  var oldInner=inner.deepCopy();oldInner.add("if_true",optimized.get("if_true").deepCopy());original.add("then_run",oldInner);
  var codec=net.minecraft.world.gen.surfacebuilder.MaterialRules.MaterialRule.CODEC;
  var a=codec.parse(com.mojang.serialization.JsonOps.INSTANCE,original).getOrThrow();
  var b=codec.parse(com.mojang.serialization.JsonOps.INSTANCE,optimized).getOrThrow();
  var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
  var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
  var contextType=Class.forName("net.minecraft.world.gen.surfacebuilder.MaterialRules$MaterialRuleContext");
  var context=unsafe.allocateInstance(contextType);
  var depth=contextType.getDeclaredField("stoneDepthAbove");depth.setAccessible(true);
  var position=contextType.getDeclaredField("uniquePosValue");position.setAccessible(true);
  var supplier=contextType.getDeclaredField("biomeSupplier");supplier.setAccessible(true);
  var apply=Class.forName("net.minecraft.world.gen.surfacebuilder.MaterialRules$BlockStateRule").getDeclaredMethod("tryApply",int.class,int.class,int.class);apply.setAccessible(true);
  var oldRule=((java.util.function.Function)a).apply(context);
  var newRule=((java.util.function.Function)b).apply(context);long stamp=10;
  for(var key:java.util.List.of(net.minecraft.world.biome.BiomeKeys.SNOWY_PLAINS,net.minecraft.world.biome.BiomeKeys.PLAINS)) {
   var entry=registry.getOrThrow(key);var queries=new java.util.concurrent.atomic.AtomicInteger();
   supplier.set(context,(java.util.function.Supplier<net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.biome.Biome>>)()->{queries.incrementAndGet();return entry;});
   for(int d=0;d<32;d++)for(int y:new int[]{-1900,-1784,0,1900}) {
    depth.setInt(context,d);position.setLong(context,stamp++);var expected=apply.invoke(oldRule,5,y,-7);
    position.setLong(context,stamp++);queries.set(0);var actual=apply.invoke(newRule,5,y,-7);
    assertEquals(expected,actual);
    if(d>0)assertEquals(0,queries.get(),"Rejected depths must not invoke biome lookup");
   }
  }
 }
}
