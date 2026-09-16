package com.github.xandergos.terraindiffusionmc.world;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.datafixer.Schemas;
import net.minecraft.registry.BuiltinRegistries;
import com.github.xandergos.terraindiffusionmc.blueprint.*;
/** Explicit offline metadata repair. Caller must first back up and prove the creation profile from logs. */
public final class RecoverInterruptedWorld {
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);
  var level=NbtIo.readCompressed(root.resolve("level.dat"),NbtSizeTracker.of(64L*1024*1024)).getCompoundOrEmpty("Data");
  System.out.println("Level keys: "+level.getKeys());
  System.out.println("World generation: "+level.getCompoundOrEmpty("WorldGenSettings"));
  if(args.length<4)return;
  long seed=Long.parseLong(args[1]);String fingerprint=args[2];int scale=Integer.parseInt(args[3]);
  if(level.getCompoundOrEmpty("WorldGenSettings").getLong("seed",Long.MIN_VALUE)!=seed)throw new IllegalStateException("Seed mismatch");
  var store=new BlueprintTileStore(root.resolve("terrain-diffusion-blueprint"));
  if(!store.manifest().generationFingerprint().equals(fingerprint))throw new IllegalStateException("Blueprint mismatch");
  boolean verifyOnly=args.length>4&&args[4].equals("verify");
  if(!verifyOnly)for(String n:new String[]{"terrain_diffusion_vertical_profile","terrain_diffusion_world_settings","terrain_diffusion_blueprint_settings"})
   if(Files.exists(root.resolve("data/"+n+".dat")))throw new IllegalStateException("Refusing to overwrite existing "+n);
  net.minecraft.SharedConstants.createGameVersion();
  if(!verifyOnly)try(var states=new PersistentStateManager(root.resolve("data"),Schemas.getFixer(),net.minecraft.registry.RegistryWrapper.WrapperLookup.of(java.util.stream.Stream.empty()))){
   states.getOrCreate(VerticalProfileState.TYPE).markDirty();
   states.getOrCreate(WorldScaleSettingsState.TYPE).setScale(scale);
   states.getOrCreate(WorldBlueprintSettingsState.TYPE).configure(store.manifest());states.save();
  }
  try(var states=new PersistentStateManager(root.resolve("data"),Schemas.getFixer(),net.minecraft.registry.RegistryWrapper.WrapperLookup.of(java.util.stream.Stream.empty()))){
   if(states.get(VerticalProfileState.TYPE)==null||states.get(WorldScaleSettingsState.TYPE).getScale()!=scale||!states.get(WorldBlueprintSettingsState.TYPE).hash().equals(fingerprint))throw new IllegalStateException("Repair failed readback");
  }
  System.out.println("Restored only profile, scale and blueprint metadata; chunks and checkpoints unchanged.");
 }
}
