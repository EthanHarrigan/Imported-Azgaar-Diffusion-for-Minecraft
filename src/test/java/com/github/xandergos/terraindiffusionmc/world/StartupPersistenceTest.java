package com.github.xandergos.terraindiffusionmc.world;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.datafixer.Schemas;
import net.minecraft.registry.BuiltinRegistries;
class StartupPersistenceTest {
 @TempDir Path root;
 @Test void profileScaleAndBlueprintSurviveBeforeNormalWorldSave()throws Exception{
  net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
  var registries=BuiltinRegistries.createWrapperLookup();
  try(var state=new PersistentStateManager(root,Schemas.getFixer(),registries)){
   state.getOrCreate(VerticalProfileState.TYPE).markDirty();
   state.getOrCreate(WorldScaleSettingsState.TYPE).setScale(5);
   state.getOrCreate(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintSettingsState.TYPE).disable();
   state.save();
   // Fresh reader BEFORE close: checks the startup flush, not shutdown saving.
   try(var reopened=new PersistentStateManager(root,Schemas.getFixer(),registries)){
    assertNotNull(reopened.get(VerticalProfileState.TYPE));assertEquals(5,reopened.get(WorldScaleSettingsState.TYPE).getScale());
    assertTrue(reopened.get(com.github.xandergos.terraindiffusionmc.blueprint.WorldBlueprintSettingsState.TYPE).explicit());
   }
  }
 }
 @Test void emptyRegionPlaceholdersDoNotLockOutInterruptedCreation()throws Exception{
  Path region=root.resolve("region");java.nio.file.Files.createDirectories(region);
  java.nio.file.Files.createFile(region.resolve("r.0.0.mca"));
  java.nio.file.Files.write(region.resolve("r.0.1.mca"),new byte[8192]);
  assertFalse(VerticalProfileState.hasSavedChunks(region));
  byte[] populated=new byte[8192];populated[2]=2;populated[3]=1;
  java.nio.file.Files.write(region.resolve("r.0.2.mca"),populated);
  assertTrue(VerticalProfileState.hasSavedChunks(region));
 }
 @Test void truncatedRegionsCannotBypassProfileGuard()throws Exception{
  java.nio.file.Files.write(root.resolve("r.0.0.mca"),new byte[12]);
  assertThrows(java.io.IOException.class,()->VerticalProfileState.hasSavedChunks(root));
 }

}
