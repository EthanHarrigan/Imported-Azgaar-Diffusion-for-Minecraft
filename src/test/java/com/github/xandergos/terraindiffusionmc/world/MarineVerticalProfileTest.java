package com.github.xandergos.terraindiffusionmc.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarineVerticalProfileTest {
    @Test void marineDepthBandsFollowConfiguredSeaLevel() {
        int sea=VerticalProfile.SEA_LEVEL;
        assertFalse(MarineDecorator.reefDepthAllowed(sea-4));
        assertTrue(MarineDecorator.reefDepthAllowed(sea-5));
        assertTrue(MarineDecorator.reefDepthAllowed(sea-34));
        assertFalse(MarineDecorator.reefDepthAllowed(sea-35));

        assertFalse(MarineDecorator.plantDepthAllowed(sea-3));
        assertTrue(MarineDecorator.plantDepthAllowed(sea-4));
        assertTrue(MarineDecorator.plantDepthAllowed(sea-43));
        assertFalse(MarineDecorator.plantDepthAllowed(sea-44));
    }

    @Test void oldAbsoluteVanillaBandsAreRejectedInLoweredWorld() {
        assertFalse(MarineDecorator.reefDepthAllowed(58));
        assertFalse(MarineDecorator.plantDepthAllowed(59));
    }
}
