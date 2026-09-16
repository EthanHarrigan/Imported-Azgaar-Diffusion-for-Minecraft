package com.github.xandergos.terraindiffusionmc.world;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.Bootstrap;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.structure.JigsawStructure;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UndergroundStructureHeightTest {
    private static HeightProvider parse(String json){
        SharedConstants.createGameVersion();Bootstrap.initialize();
        return HeightProvider.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(json)).getOrThrow();
    }
    private static JsonElement encode(HeightProvider p){return HeightProvider.CODEC.encodeStart(JsonOps.INSTANCE,p).getOrThrow();}
    private static HeightProvider adjust(HeightProvider p,String pool){
        return UndergroundStructureHeight.adjust(p,true,false,Identifier.of(pool),VerticalProfile.SEA_LEVEL);
    }
    @Test void vanillaUndergroundDefinitionsAreRebasedBeforePieceGeneration() throws Exception {
        for(String name:new String[]{"trial_chambers","ancient_city"}){
            try(var stream=getClass().getResourceAsStream("/data/minecraft/worldgen/structure/"+name+".json")){
                assertNotNull(stream);
                var definition=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                var original=parse(definition.get("start_height").toString());
                var mapped=encode(adjust(original,definition.get("start_pool").getAsString())).getAsJsonObject();
                if(name.equals("trial_chambers")){
                    assertEquals(-1887,mapped.getAsJsonObject("min_inclusive").get("absolute").getAsInt());
                    assertEquals(-1867,mapped.getAsJsonObject("max_inclusive").get("absolute").getAsInt());
                }else assertEquals(-1874,mapped.get("absolute").getAsInt());
                assertEquals(definition.get("start_height"),encode(original),"Shared registry provider is not mutated");
            }
        }
        assertTrue(java.util.Arrays.stream(JigsawStructure.class.getDeclaredMethods())
                .anyMatch(m->m.getName().contains("terrainDiffusion$undergroundHeight")),"Real Minecraft jigsaw hook must apply");
    }
    @Test void otherDimensionsSurfaceStructuresAndCustomAnchorsKeepTheirHeights(){
        var p=parse("{\"absolute\":-27}");
        assertSame(p,UndergroundStructureHeight.adjust(p,false,false,Identifier.of("minecraft:ancient_city/city_center"),VerticalProfile.SEA_LEVEL));
        assertSame(p,UndergroundStructureHeight.adjust(p,true,true,Identifier.of("minecraft:ancient_city/city_center"),VerticalProfile.SEA_LEVEL));
        assertSame(p,UndergroundStructureHeight.adjust(p,true,false,Identifier.of("minecraft:ancient_city/city_center"),63));
        for(String pool:new String[]{"minecraft:village/plains/town_centers","minecraft:trail_ruins/tower","minecraft:bastion/starts","custom:ancient_city/city_center"})assertSame(p,adjust(p,pool));
        for(String json:new String[]{"{\"above_bottom\":37}","{\"absolute\":-1874}",
                "{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"above_bottom\":110},\"max_inclusive\":{\"above_bottom\":130}}"}){
            var custom=parse(json);assertSame(custom,adjust(custom,"minecraft:trial_chambers/chamber/end"));
        }
    }
}
