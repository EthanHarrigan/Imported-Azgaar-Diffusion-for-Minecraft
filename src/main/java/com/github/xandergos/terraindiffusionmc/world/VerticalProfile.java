package com.github.xandergos.terraindiffusionmc.world;

/** Immutable vertical contract shared by terrain conversion and worldgen resources. */
public final class VerticalProfile {
    public static final int VERSION = 2;
    public static final int BOTTOM_Y = -2000;
    public static final int SEA_LEVEL = -1784;
    public static final int TOP_Y = 1967;
    public static final int HEIGHT = TOP_Y - BOTTOM_Y + 1;
    private VerticalProfile() {}
    public static String identity() { return "v3_" + BOTTOM_Y + "_" + SEA_LEVEL + "_" + TOP_Y; }
    public static int fallbackSpawnY() { return SEA_LEVEL + 1; }
}
