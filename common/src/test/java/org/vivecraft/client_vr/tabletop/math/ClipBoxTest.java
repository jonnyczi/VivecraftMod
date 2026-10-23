package org.vivecraft.client_vr.tabletop.math;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClipBoxTest {

    private static final TabletopFrame FRAME = new TabletopFrame(new Vector3f(0F, 1F, 0F), 0.5F, 1.2F, 0.8F, 0.5F,
        0.1F);

    @Test
    public void sizeFollowsFrameAndZoom() {
        ClipBox box = ClipBox.of(FRAME, new Vec3(10, 64, 20), 50F, 0);
        Assertions.assertEquals(60.0, box.sizeX(), 1.0E-4);
        Assertions.assertEquals(40.0, box.sizeZ(), 1.0E-4);
        Assertions.assertEquals(64.0 - 5.0, box.minY(), 1.0E-4);
        Assertions.assertEquals(64.0 + 25.0, box.maxY(), 1.0E-4);
        Assertions.assertEquals(10.0, (box.minX() + box.maxX()) * 0.5, 1.0E-4);
    }

    @Test
    public void oddQuarterTurnsSwapAxes() {
        ClipBox box = ClipBox.of(FRAME, Vec3.ZERO, 50F, 3);
        Assertions.assertEquals(40.0, box.sizeX(), 1.0E-4);
        Assertions.assertEquals(60.0, box.sizeZ(), 1.0E-4);
    }

    @Test
    public void relativeToCameraStaysPreciseFarAway() {
        ClipBox box = new ClipBox(29_999_000.25, 60, -29_999_000.75, 29_999_050.25, 90, -29_998_950.75);
        float[] relative = box.relativeTo(new Vec3(29_999_020.0, 100.0, -29_998_980.0));
        Assertions.assertEquals(-19.75F, relative[0], 1.0E-4F);
        Assertions.assertEquals(-40.0F, relative[1], 1.0E-4F);
        Assertions.assertEquals(-20.75F, relative[2], 1.0E-4F);
        Assertions.assertEquals(30.25F, relative[3], 1.0E-4F);
    }

    @Test
    public void containsAndIntersects() {
        ClipBox box = new ClipBox(0, 0, 0, 10, 10, 10);
        Assertions.assertTrue(box.contains(5, 5, 5));
        Assertions.assertFalse(box.contains(-0.1, 5, 5));
        Assertions.assertTrue(box.intersects(-1, -1, -1, 0.5, 0.5, 0.5));
        Assertions.assertFalse(box.intersects(10.5, 0, 0, 11, 1, 1));
        Assertions.assertFalse(box.shrink(1.0).contains(0.5, 5, 5));
    }
}
