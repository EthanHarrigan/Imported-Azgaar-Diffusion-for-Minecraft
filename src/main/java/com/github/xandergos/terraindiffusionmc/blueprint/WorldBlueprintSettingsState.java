package com.github.xandergos.terraindiffusionmc.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class WorldBlueprintSettingsState extends PersistentState {
    private static final Codec<WorldBlueprintSettingsState> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.BOOL.optionalFieldOf("explicit",false).forGetter(s->s.explicit),Codec.BOOL.optionalFieldOf("enabled",false).forGetter(s->s.enabled),
            Codec.STRING.optionalFieldOf("blueprint_hash","").forGetter(s->s.hash),Codec.DOUBLE.optionalFieldOf("physical_width_km",40075d).forGetter(s->s.widthKm),
            Codec.INT.optionalFieldOf("map_width",0).forGetter(s->s.mapWidth),Codec.INT.optionalFieldOf("map_height",0).forGetter(s->s.mapHeight),
            Codec.FLOAT.optionalFieldOf("elevation_guidance",.5f).forGetter(s->s.elevationRatio),Codec.FLOAT.optionalFieldOf("climate_guidance",.2f).forGetter(s->s.climateRatio),
            Codec.DOUBLE.optionalFieldOf("edge_blend_km",250d).forGetter(s->s.blendKm),
            Codec.DOUBLE.optionalFieldOf("south_climate_latitude_deg",-90d).forGetter(s->s.southLatitude),
            Codec.FLOAT.optionalFieldOf("south_precipitation_multiplier",1f).forGetter(s->s.southRainMultiplier),
            Codec.INT.optionalFieldOf("climate_algorithm_version",1).forGetter(s->s.climateVersion)
    ).apply(i,WorldBlueprintSettingsState::new));
    boolean explicit,enabled;String hash;double widthKm;int mapWidth,mapHeight;float elevationRatio,climateRatio;double blendKm,southLatitude;float southRainMultiplier;int climateVersion;
    private WorldBlueprintSettingsState(boolean explicit,boolean enabled,String hash,double widthKm,int mapWidth,int mapHeight,float er,float cr,double blend,double southLat,float southRain,int cv){this.explicit=explicit;this.enabled=enabled;this.hash=hash;this.widthKm=widthKm;this.mapWidth=mapWidth;this.mapHeight=mapHeight;this.elevationRatio=er;this.climateRatio=cr;this.blendKm=blend;this.southLatitude=southLat;this.southRainMultiplier=southRain;this.climateVersion=cv;}
    public static WorldBlueprintSettingsState create(){return new WorldBlueprintSettingsState(false,false,"",40075,0,0,.5f,.2f,250,-90,1,1);}
    public static final PersistentStateType<WorldBlueprintSettingsState> TYPE=new PersistentStateType<>("terrain_diffusion_blueprint_settings",WorldBlueprintSettingsState::create,CODEC,null);
    public void disable(){explicit=true;enabled=false;markDirty();}
    public void configure(BlueprintManifest m){explicit=true;enabled=true;hash=m.dataSha256();widthKm=m.physicalWidthKm();mapWidth=m.width();mapHeight=m.height();elevationRatio=m.elevationNoiseRatio();climateRatio=m.climateNoiseRatio();blendKm=m.edgeBlendKm();southLatitude=m.effectiveSouthClimateLatitudeDeg();southRainMultiplier=m.effectiveSouthPrecipitationMultiplier();climateVersion=m.climateAlgorithmVersion();markDirty();}
    public boolean explicit(){return explicit;} public boolean enabled(){return enabled;} public String hash(){return hash;} public double widthKm(){return widthKm;} public int mapWidth(){return mapWidth;} public int mapHeight(){return mapHeight;} public float elevationRatio(){return elevationRatio;} public float climateRatio(){return climateRatio;} public double blendKm(){return blendKm;} public double southLatitude(){return southLatitude;} public float southRainMultiplier(){return southRainMultiplier;} public int climateVersion(){return climateVersion;}
}
