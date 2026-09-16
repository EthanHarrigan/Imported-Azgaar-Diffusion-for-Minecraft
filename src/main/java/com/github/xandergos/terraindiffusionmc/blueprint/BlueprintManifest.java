package com.github.xandergos.terraindiffusionmc.blueprint;

import java.util.List;

public record BlueprintManifest(
        int version, int width, int height, int tileSize, double physicalWidthKm,
        double coarseKmPerPixel, String sourceName, String sourceSha256, String dataSha256,
        float elevationNoiseRatio, float climateNoiseRatio, double edgeBlendKm,
        double southClimateLatitudeDeg, float southPrecipitationMultiplier,
        int climateAlgorithmVersion,
        int channelCount, double sourceLongitudeSpanDeg, int contentMinX, int contentMaxX,
        List<BiomeReservation> biomeReservations, int biomeClassifierVersion, List<ExceptionalSummit> exceptionalSummits) {
    public List<ExceptionalSummit> effectiveExceptionalSummits(){return exceptionalSummits==null?List.of():exceptionalSummits;}
    public static final int FORMAT_VERSION = 1;
    public static final int CLIMATE_VERSION = 4;

    /** Defaults used when loading a pre-v2 manifest that has no warm-south settings. */
    public double effectiveSouthClimateLatitudeDeg() {
        return climateAlgorithmVersion >= 2 ? southClimateLatitudeDeg : -90;
    }

    public float effectiveSouthPrecipitationMultiplier() {
        return climateAlgorithmVersion >= 2 ? southPrecipitationMultiplier : 1f;
    }

    public int effectiveChannelCount() {
        return channelCount >= 5 ? channelCount : 5;
    }

    public double effectiveLongitudeSpanDeg() {
        return sourceLongitudeSpanDeg > 0 ? sourceLongitudeSpanDeg : 360;
    }

    public int effectiveContentMinX() {
        return contentMaxX > contentMinX ? contentMinX : 0;
    }

    public int effectiveContentMaxX() {
        return contentMaxX > contentMinX ? contentMaxX : width - 1;
    }

    public List<BiomeReservation> effectiveBiomeReservations() {
        return biomeReservations == null ? List.of() : biomeReservations;
    }

    public int effectiveBiomeClassifierVersion() {
        return biomeClassifierVersion > 0 ? biomeClassifierVersion : 1;
    }
    public String generationFingerprint(){
        if(effectiveBiomeClassifierVersion()<3)return dataSha256;
        try{
            byte[] bytes=new com.google.gson.Gson().toJson(this).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
