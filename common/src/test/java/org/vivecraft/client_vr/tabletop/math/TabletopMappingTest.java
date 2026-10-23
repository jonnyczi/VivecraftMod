package org.vivecraft.client_vr.tabletop.math;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TabletopMappingTest {

    private static final float[] YAWS = {0F, 0.3F, -1.2F, (float) Math.PI, 2.5F};
    private static final float[] SCALES = {4F, 50F, 400F};

    private static TabletopFrame frame(float yaw) {
        return new TabletopFrame(new Vector3f(0.4F, 0.9F, -0.7F), yaw, 1.2F, 0.8F, 0.5F, 0.1F);
    }

    @Test
    public void frameCenterShowsFocus() {
        Vec3 focus = new Vec3(1234.5, 70.0, -5678.25);
        for (float yaw : YAWS) {
            for (float scale : SCALES) {
                for (int turns = -1; turns <= 4; turns++) {
                    TabletopFrame frame = frame(yaw);
                    TabletopMapping mapping = TabletopMapping.of(frame, focus, scale, turns);
                    assertClose(focus, mapping.roomToWorld(frame.center()), 1.0E-3 * scale);
                }
            }
        }
    }

    @Test
    public void roomWorldRoundTrip() {
        TabletopMapping mapping = TabletopMapping.of(frame(0.7F), new Vec3(100, 64, 100), 50F, 1);
        Vector3f room = new Vector3f(1.1F, 0.3F, -0.4F);
        Vector3f back = mapping.worldToRoom(mapping.roomToWorld(room));
        Assertions.assertTrue(room.distance(back) < 1.0E-4F, "round trip moved the point to " + back);
    }

    @Test
    public void frameCornersAreClipBoxCorners() {
        Vec3 focus = new Vec3(-300.5, 12.0, 77.0);
        for (float yaw : YAWS) {
            for (int turns = 0; turns < 4; turns++) {
                TabletopFrame frame = frame(yaw);
                float scale = 50F;
                TabletopMapping mapping = TabletopMapping.of(frame, focus, scale, turns);
                ClipBox box = ClipBox.of(frame, focus, scale, turns);
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        Vector3f corner = new Vector3f(sx * frame.sizeX() * 0.5F, 0F, sz * frame.sizeZ() * 0.5F)
                            .rotateY(frame.yaw()).add(frame.center());
                        Vec3 world = mapping.roomToWorld(corner);
                        double tolerance = 1.0E-3 * scale;
                        Assertions.assertTrue(
                            Math.abs(world.x - box.minX()) < tolerance || Math.abs(world.x - box.maxX()) < tolerance,
                            "corner x " + world.x + " not on box edge " + box + " yaw " + yaw + " turns " + turns);
                        Assertions.assertTrue(
                            Math.abs(world.z - box.minZ()) < tolerance || Math.abs(world.z - box.maxZ()) < tolerance,
                            "corner z " + world.z + " not on box edge " + box + " yaw " + yaw + " turns " + turns);
                        Assertions.assertEquals(focus.y, world.y, tolerance);
                    }
                }
            }
        }
    }

    @Test
    public void quarterTurnsRotateTheWorld() {
        TabletopFrame frame = frame(0F);
        Vec3 focus = Vec3.ZERO;
        Vector3f right = new Vector3f(frame.center()).add(1F, 0F, 0F);
        Vec3 turn0 = TabletopMapping.of(frame, focus, 10F, 0).roomToWorld(right);
        Vec3 turn1 = TabletopMapping.of(frame, focus, 10F, 1).roomToWorld(right);
        Vec3 turn4 = TabletopMapping.of(frame, focus, 10F, 4).roomToWorld(right);
        assertClose(new Vec3(10, 0, 0), turn0, 1.0E-3);
        // one quarter turn moves the frame x axis onto a world z axis
        Assertions.assertEquals(0.0, turn1.x, 1.0E-3);
        Assertions.assertEquals(10.0, Math.abs(turn1.z), 1.0E-3);
        assertClose(turn0, turn4, 1.0E-3);
    }

    private static void assertClose(Vec3 expected, Vec3 actual, double tolerance) {
        Assertions.assertTrue(expected.distanceTo(actual) < tolerance,
            "expected " + expected + " but was " + actual);
    }
}
