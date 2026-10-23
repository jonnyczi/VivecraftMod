package org.vivecraft.client_vr.tabletop.math;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TabletopPlacementTest {

    private static final double ABOVE = 20.0;
    private static final double BELOW = 6.0;

    @Test
    public void groundIsAtTheUsualSurfaceHeight() {
        // standing on a small hill, most of the area is lower
        double base = TabletopPlacement.baseHeight(68, 68, new int[]{64, 64, 65, 63, 64, 70, 68}, ABOVE, BELOW);
        Assertions.assertEquals(64, base, 1.0E-9);
    }

    @Test
    public void avatarOnAHighDuneStaysInside() {
        // the dune is far above the rest, the base can't drop so far that the avatar's feet leave the bottom
        double base = TabletopPlacement.baseHeight(90, 90, new int[]{64, 64, 64, 64, 90}, ABOVE, BELOW);
        Assertions.assertEquals(90 + 1.8 + 1.0 - ABOVE, base, 1.0E-9);
        Assertions.assertTrue(90 + 1.8 <= base + ABOVE, "head inside");
    }

    @Test
    public void avatarInAValleyStaysInside() {
        double base = TabletopPlacement.baseHeight(50, 50, new int[]{80, 80, 80, 50}, ABOVE, BELOW);
        Assertions.assertEquals(50 + BELOW - 1.0, base, 1.0E-9);
        Assertions.assertTrue(50 >= base - BELOW, "feet inside");
    }

    @Test
    public void undergroundShowsACrossSectionAboveTheAvatar() {
        // in a cave 30 blocks below the surface
        double base = TabletopPlacement.baseHeight(34, 64, new int[]{64, 65, 63}, ABOVE, BELOW);
        Assertions.assertEquals(34 + 4.0, base + ABOVE, 1.0E-9);
    }

    @Test
    public void noSamplesUsesTheFeet() {
        Assertions.assertEquals(70, TabletopPlacement.baseHeight(70, 70, new int[0], ABOVE, BELOW), 1.0E-9);
    }

    @Test
    public void flatTabletopKeepsTheFeetInside() {
        // a tabletop that is lower than the avatar can't show all of it, the feet stay inside
        double base = TabletopPlacement.baseHeight(70, 70, new int[]{70}, 1.0, 0.5);
        Assertions.assertEquals(70 + 0.5 - 1.0, base, 1.0E-9);
    }
}
