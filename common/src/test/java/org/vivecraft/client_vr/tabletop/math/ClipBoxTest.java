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
    public void containsAndIntersects() {
        ClipBox box = new ClipBox(0, 0, 0, 10, 10, 10);
        Assertions.assertTrue(box.contains(5, 5, 5));
        Assertions.assertFalse(box.contains(-0.1, 5, 5));
        Assertions.assertTrue(box.intersects(-1, -1, -1, 0.5, 0.5, 0.5));
        Assertions.assertFalse(box.intersects(10.5, 0, 0, 11, 1, 1));
        Assertions.assertFalse(box.shrink(1.0).contains(0.5, 5, 5));
    }

    @Test
    public void rayIntersection() {
        ClipBox box = new ClipBox(0, 0, 0, 10, 10, 10);

        // from above, straight down through the middle
        double[] hit = box.intersectRay(new Vec3(5, 20, 5), new Vec3(0, -1, 0));
        Assertions.assertNotNull(hit);
        Assertions.assertEquals(10.0, hit[0], 1.0E-9);
        Assertions.assertEquals(20.0, hit[1], 1.0E-9);

        // diagonal, direction not normalized
        hit = box.intersectRay(new Vec3(-5, 5, 5), new Vec3(2, 0, 0));
        Assertions.assertNotNull(hit);
        Assertions.assertEquals(2.5, hit[0], 1.0E-9);
        Assertions.assertEquals(7.5, hit[1], 1.0E-9);

        // starting inside enters at 0
        hit = box.intersectRay(new Vec3(5, 5, 5), new Vec3(1, 1, 0));
        Assertions.assertNotNull(hit);
        Assertions.assertEquals(0.0, hit[0], 1.0E-9);
        Assertions.assertEquals(5.0, hit[1], 1.0E-9);

        // pointing away, parallel outside, and passing next to it
        Assertions.assertNull(box.intersectRay(new Vec3(5, 20, 5), new Vec3(0, 1, 0)));
        Assertions.assertNull(box.intersectRay(new Vec3(5, 20, 5), new Vec3(1, 0, 0)));
        Assertions.assertNull(box.intersectRay(new Vec3(-5, 20, 5), new Vec3(1, -0.1, 0)));
    }
}
