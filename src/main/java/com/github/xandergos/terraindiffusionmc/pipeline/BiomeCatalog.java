package com.github.xandergos.terraindiffusionmc.pipeline;

import java.util.*;

/** Shared biome names and deliberately distinct explorer-map colors. */
public final class BiomeCatalog {
    public record Entry(short id,String key,String name,int color){
        public String hexColor(){return String.format("#%06X",color&0xFFFFFF);}
    }
    private static Entry e(short id,String key,String name,int color){return new Entry(id,key,name,color);}
    private static final List<Entry> ENTRIES=List.of(
            e(BiomeIds.PLAINS,"minecraft:plains","Plains",0x8DB360),
            e(BiomeIds.SUNFLOWER_PLAINS,"minecraft:sunflower_plains","Sunflower Plains",0xD9C94E),
            e(BiomeIds.SNOWY_PLAINS,"minecraft:snowy_plains","Snowy Plains",0xE4EFF6),
            e(BiomeIds.ICE_SPIKES,"minecraft:ice_spikes","Ice Spikes",0xB9DDF1),
            e(BiomeIds.DESERT,"minecraft:desert","Desert",0xEBCB74),
            e(BiomeIds.SWAMP,"minecraft:swamp","Swamp",0x596D43),
            e(BiomeIds.MANGROVE_SWAMP,"minecraft:mangrove_swamp","Mangrove Swamp",0x2F705B),
            e(BiomeIds.FOREST,"minecraft:forest","Forest",0x438B45),
            e(BiomeIds.FLOWER_FOREST,"minecraft:flower_forest","Flower Forest",0x79B94F),
            e(BiomeIds.BIRCH_FOREST,"minecraft:birch_forest","Birch Forest",0x6EA65F),
            e(BiomeIds.OLD_GROWTH_BIRCH_FOREST,"minecraft:old_growth_birch_forest","Old Growth Birch Forest",0x4F9D67),
            e(BiomeIds.DARK_FOREST,"minecraft:dark_forest","Dark Forest",0x35533A),
            e(BiomeIds.PALE_GARDEN,"minecraft:pale_garden","Pale Garden",0x899488),
            e(BiomeIds.TAIGA,"minecraft:taiga","Taiga",0x4F8A74),
            e(BiomeIds.SNOWY_TAIGA,"minecraft:snowy_taiga","Snowy Taiga",0xA6C9C2),
            e(BiomeIds.OLD_GROWTH_PINE_TAIGA,"minecraft:old_growth_pine_taiga","Old Growth Pine Taiga",0x3F705F),
            e(BiomeIds.OLD_GROWTH_SPRUCE_TAIGA,"minecraft:old_growth_spruce_taiga","Old Growth Spruce Taiga",0x315F55),
            e(BiomeIds.SAVANNA,"minecraft:savanna","Savanna",0xB6B34A),
            e(BiomeIds.SAVANNA_PLATEAU,"minecraft:savanna_plateau","Savanna Plateau",0x9D9B3E),
            e(BiomeIds.WINDSWEPT_HILLS,"minecraft:windswept_hills","Windswept Hills",0x7C8984),
            e(BiomeIds.WINDSWEPT_GRAVELLY_HILLS,"minecraft:windswept_gravelly_hills","Windswept Gravelly Hills",0x77746D),
            e(BiomeIds.WINDSWEPT_FOREST,"minecraft:windswept_forest","Windswept Forest",0x55735D),
            e(BiomeIds.WINDSWEPT_SAVANNA,"minecraft:windswept_savanna","Windswept Savanna",0x9A8840),
            e(BiomeIds.JUNGLE,"minecraft:jungle","Jungle",0x279239),
            e(BiomeIds.SPARSE_JUNGLE,"minecraft:sparse_jungle","Sparse Jungle",0x55A33F),
            e(BiomeIds.BAMBOO_JUNGLE,"minecraft:bamboo_jungle","Bamboo Jungle",0x3DBB5B),
            e(BiomeIds.BADLANDS,"minecraft:badlands","Badlands",0xB96A45),
            e(BiomeIds.ERODED_BADLANDS,"minecraft:eroded_badlands","Eroded Badlands",0xA9573E),
            e(BiomeIds.WOODED_BADLANDS,"minecraft:wooded_badlands","Wooded Badlands",0x8C6840),
            e(BiomeIds.MEADOW,"minecraft:meadow","Meadow",0x70B7D8),
            e(BiomeIds.CHERRY_GROVE,"minecraft:cherry_grove","Cherry Grove",0xE7A7C4),
            e(BiomeIds.GROVE,"minecraft:grove","Grove",0x91ADA2),
            e(BiomeIds.SNOWY_SLOPES,"minecraft:snowy_slopes","Snowy Slopes",0xF4F7F9),
            e(BiomeIds.FROZEN_PEAKS,"minecraft:frozen_peaks","Frozen Peaks",0xC8D5E2),
            e(BiomeIds.JAGGED_PEAKS,"minecraft:jagged_peaks","Jagged Peaks",0xAABACB),
            e(BiomeIds.STONY_PEAKS,"minecraft:stony_peaks","Stony Peaks",0x82898F),
            e(BiomeIds.RIVER,"minecraft:river","River",0x3B7BD6),
            e(BiomeIds.FROZEN_RIVER,"minecraft:frozen_river","Frozen River",0x85B9DD),
            e(BiomeIds.BEACH,"minecraft:beach","Beach",0xE9DA93),
            e(BiomeIds.SNOWY_BEACH,"minecraft:snowy_beach","Snowy Beach",0xD5E2DD),
            e(BiomeIds.STONY_SHORE,"minecraft:stony_shore","Stony Shore",0x999B91),
            e(BiomeIds.WARM_OCEAN,"minecraft:warm_ocean","Warm Ocean",0x29C6BC),
            e(BiomeIds.LUKEWARM_OCEAN,"minecraft:lukewarm_ocean","Lukewarm Ocean",0x2DA9C2),
            e(BiomeIds.DEEP_LUKEWARM_OCEAN,"minecraft:deep_lukewarm_ocean","Deep Lukewarm Ocean",0x237C9E),
            e(BiomeIds.OCEAN,"minecraft:ocean","Ocean",0x317FBD),
            e(BiomeIds.DEEP_OCEAN,"minecraft:deep_ocean","Deep Ocean",0x24558C),
            e(BiomeIds.COLD_OCEAN,"minecraft:cold_ocean","Cold Ocean",0x416DAA),
            e(BiomeIds.DEEP_COLD_OCEAN,"minecraft:deep_cold_ocean","Deep Cold Ocean",0x304F80),
            e(BiomeIds.FROZEN_OCEAN,"minecraft:frozen_ocean","Frozen Ocean",0x8CB7D4),
            e(BiomeIds.DEEP_FROZEN_OCEAN,"minecraft:deep_frozen_ocean","Deep Frozen Ocean",0x638AAE),
            e(BiomeIds.MUSHROOM_FIELDS,"minecraft:mushroom_fields","Mushroom Fields",0xA45B9D),
            e(BiomeIds.FOREST_SPARSE,"terrain-diffusion-mc:forest_sparse","Sparse Forest",0x75A94D),
            e(BiomeIds.TAIGA_SPARSE,"terrain-diffusion-mc:taiga_sparse","Sparse Taiga",0x6AA48A),
            e(BiomeIds.SNOWY_TAIGA_SPARSE,"terrain-diffusion-mc:snowy_taiga_sparse","Sparse Snowy Taiga",0xB8D7CF));
    private static final Map<Short,Entry> BY_ID;
    private static final Map<String,Short> BY_NAME;
    static{Map<Short,Entry>m=new LinkedHashMap<>();Map<String,Short>n=new HashMap<>();for(Entry e:ENTRIES){m.put(e.id,e);n.put(normalize(e.name),e.id);n.put(normalize(e.key.substring(e.key.indexOf(':')+1)),e.id);}BY_ID=Collections.unmodifiableMap(m);BY_NAME=Collections.unmodifiableMap(n);}
    private BiomeCatalog(){}
    public static List<Entry> entries(){return ENTRIES;}
    public static Entry byId(short id){return BY_ID.get(id);}
    public static int color(short id){Entry e=BY_ID.get(id);return e==null?0x777777:e.color;}
    public static String name(short id){Entry e=BY_ID.get(id);return e==null?"Unknown biome ("+id+")":e.name;}
    public static Short idByName(String name){return name==null?null:BY_NAME.get(normalize(name));}
    private static String normalize(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    public static List<Map<String,Object>> metadata(){List<Map<String,Object>>out=new ArrayList<>();for(Entry e:ENTRIES){Map<String,Object>m=new LinkedHashMap<>();m.put("id",(int)e.id);m.put("key",e.key);m.put("name",e.name);m.put("color",e.hexColor());out.add(m);}return out;}
}
