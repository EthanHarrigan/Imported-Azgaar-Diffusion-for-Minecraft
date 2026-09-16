package com.github.xandergos.terraindiffusionmc.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.structure.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import java.nio.file.*;
import com.google.gson.*;
class LoweredCompatibilityTest {
    @Test void cacheIdentityIsAValidWindowsFilename() {
        assertFalse(VerticalProfile.identity().matches(".*[<>:\"/\\\\|?*].*"));
        assertDoesNotThrow(()->Path.of("cache",VerticalProfile.identity()));
    }
    @Test void monumentAndNestedRoomsTranslateTogether() throws Exception {
        net.minecraft.SharedConstants.createGameVersion();net.minecraft.Bootstrap.initialize();
        var base=new OceanMonumentGenerator.Base(Random.create(42),0,0,Direction.NORTH);
        var field=OceanMonumentGenerator.Base.class.getDeclaredField("children");field.setAccessible(true);
        var children=(java.util.List<?>)field.get(base);
        int old=((StructurePiece)children.getFirst()).getBoundingBox().getMinY();
        ((MovableMonument)base).terrainDiffusion$moveToSeaLevel();
        assertEquals(VerticalProfile.SEA_LEVEL-24,base.getBoundingBox().getMinY());
        assertEquals(old+VerticalProfile.SEA_LEVEL-63,((StructurePiece)children.getFirst()).getBoundingBox().getMinY());
        ((MovableMonument)base).terrainDiffusion$moveToSeaLevel();
        assertEquals(VerticalProfile.SEA_LEVEL-24,base.getBoundingBox().getMinY());
    }
    @Test void worldgenResourcesMatchProfileAndKeepBedrockBelowSurface() throws Exception {
        var root=Path.of("src/main/resources/data/terrain-diffusion-mc");
        try(var paths=Files.list(root.resolve("dimension_type"))) {
            for(var p:paths.filter(p->p.toString().endsWith(".json")).toList()){
                var json=JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                assertEquals(VerticalProfile.BOTTOM_Y,json.get("min_y").getAsInt());
                assertEquals(VerticalProfile.HEIGHT,json.get("height").getAsInt());
            }
        }
        var noise=JsonParser.parseString(Files.readString(root.resolve("worldgen/noise_settings/terrain_diffusion.json"))).getAsJsonObject();
        assertEquals(VerticalProfile.SEA_LEVEL,noise.get("sea_level").getAsInt());
        var rules=noise.getAsJsonObject("surface_rule").getAsJsonArray("sequence");
        var bedrock=rules.get(0).getAsJsonObject().getAsJsonObject("if_true");
        assertEquals(0,bedrock.getAsJsonObject("true_at_and_below").get("above_bottom").getAsInt());
        assertEquals(5,bedrock.getAsJsonObject("false_at_and_above").get("above_bottom").getAsInt());
        assertTrue(VerticalProfile.BOTTOM_Y+5<HeightConverter.convertToMinecraftHeight(Short.MIN_VALUE));
    }
}
