package org.vivecraft.client_vr.tabletop.math;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TabletopNavigationTest {

    private static final TabletopFrame FRAME = new TabletopFrame(new Vector3f(0.2F, 0.9F, -0.5F), 0.8F, 1F, 1F,
        0.5F, 0.1F);
    private static final Vec3 FOCUS = new Vec3(500.5, 64, -200.25);

    @Test
    public void draggedWorldPointStaysUnderTheHand() {
        float scale = 50F;
        TabletopMapping before = TabletopMapping.of(FRAME, FOCUS, scale, 1);
        Vector3f hand = new Vector3f(0.3F, 1.0F, -0.4F);
        Vector3f delta = new Vector3f(0.05F, 0.1F, -0.02F);

        Vec3 newFocus = TabletopNavigation.drag(FOCUS, delta, scale, before.rotation());
        TabletopMapping after = TabletopMapping.of(FRAME, newFocus, scale, 1);

        Vec3 grabbed = before.roomToWorld(hand);
        Vec3 underHand = after.roomToWorld(hand.add(delta, new Vector3f()));
        Assertions.assertTrue(grabbed.distanceTo(underHand) < 1.0E-2, grabbed + " moved to " + underHand);
    }

    @Test
    public void liftingShowsDeeperLayers() {
        Vec3 lifted = TabletopNavigation.drag(FOCUS, new Vector3f(0F, 0.2F, 0F), 50F, 0F);
        // lifting the world by 20cm moves 10 blocks deeper to the frame base
        Assertions.assertEquals(FOCUS.y - 10.0, lifted.y, 1.0E-4);
        Assertions.assertEquals(FOCUS.x, lifted.x, 1.0E-4);
    }

    @Test
    public void zoomKeepsThePivotInPlace() {
        Vector3f pivot = new Vector3f(0.4F, 1.1F, -0.3F);
        TabletopMapping before = TabletopMapping.of(FRAME, FOCUS, 50F, 2);
        Vec3 newFocus = TabletopNavigation.zoomAround(FOCUS, FRAME.center(), pivot, 50F, 120F, before.rotation(),
            false);
        TabletopMapping after = TabletopMapping.of(FRAME, newFocus, 120F, 2);
        Vec3 pivotBefore = before.roomToWorld(pivot);
        Vec3 pivotAfter = after.roomToWorld(pivot);
        Assertions.assertTrue(pivotBefore.distanceTo(pivotAfter) < 1.0E-2, pivotBefore + " moved to " + pivotAfter);
    }

    @Test
    public void zoomCanKeepTheBaseLevel() {
        Vector3f pivot = new Vector3f(0.4F, 1.1F, -0.3F);
        Vec3 newFocus = TabletopNavigation.zoomAround(FOCUS, FRAME.center(), pivot, 50F, 120F, 0.3F, true);
        Assertions.assertEquals(FOCUS.y, newFocus.y, 1.0E-6);
    }

    @Test
    public void followOnlyMovesWhenTheTargetIsNearTheEdge() {
        ClipBox box = new ClipBox(0, 50, 0, 50, 90, 50);
        Vec3 focus = new Vec3(25, 60, 25);
        Vec3 margin = new Vec3(5, 2, 5);

        Assertions.assertEquals(focus, TabletopNavigation.follow(focus, box, new Vec3(30, 64, 20), margin));

        // target 3 blocks past the east margin moves the world by 3 blocks
        Vec3 moved = TabletopNavigation.follow(focus, box, new Vec3(48, 64, 20), margin);
        Assertions.assertEquals(28.0, moved.x, 1.0E-9);
        Assertions.assertEquals(25.0, moved.z, 1.0E-9);

        // target below the box moves the world down
        Vec3 down = TabletopNavigation.follow(focus, box, new Vec3(25, 45, 25), margin);
        Assertions.assertEquals(53.0, down.y, 1.0E-9);
    }

    @Test
    public void focusStaysNearThePlayer() {
        Vec3 player = new Vec3(0, 64, 0);
        Vec3 limited = TabletopNavigation.limitDistance(new Vec3(300, 70, 400), player, 100);
        Assertions.assertEquals(60.0, limited.x, 1.0E-9);
        Assertions.assertEquals(80.0, limited.z, 1.0E-9);
        Assertions.assertEquals(70.0, limited.y, 1.0E-9);
        Vec3 close = new Vec3(10, 64, 10);
        Assertions.assertEquals(close, TabletopNavigation.limitDistance(close, player, 100));
    }
}
