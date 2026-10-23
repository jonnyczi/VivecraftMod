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

    @Test
    public void heldWithBothHandsKeepsTheWorldAttached() {
        TabletopFrame frame = new TabletopFrame(new Vector3f(0.2F, 0.9F, -0.6F), 0.3F, 1F, 0.8F, 0.5F, 0.15F);
        Vec3 focus = new Vec3(100.5, 64, -20.5);
        float blocksPerMeter = 40F;
        int quarterTurns = 1;
        TabletopMapping before = TabletopMapping.of(frame, focus, blocksPerMeter, quarterTurns);

        // hands move, turn by about 40 degrees and spread to 1.5 times the distance
        Vector3f leftStart = new Vector3f(-0.2F, 1.0F, -0.5F);
        Vector3f rightStart = new Vector3f(0.4F, 1.1F, -0.5F);
        Vector3f left = new Vector3f(-0.3F, 1.2F, -0.3F);
        Vector3f right = new Vector3f(0.39F, 1.35F, -0.88F);
        TabletopNavigation.Held held = TabletopNavigation.holdWithBothHands(frame, blocksPerMeter, leftStart,
            rightStart, left, right, 0.2F, 5F);
        TabletopMapping after = TabletopMapping.of(held.frame(), focus, held.blocksPerMeter(), quarterTurns);

        float scale = right.distance(left) / rightStart.distance(leftStart);
        Assertions.assertEquals(frame.sizeX() * scale, held.frame().sizeX(), 1.0E-4);
        Assertions.assertEquals(blocksPerMeter / scale, held.blocksPerMeter(), 1.0E-3);

        // the point between the hands still shows the same world position, the tabletop only turns around the up axis,
        // so each hand can only stay exactly on its point when the hands keep their height difference
        Vector3f startMid = leftStart.add(rightStart, new Vector3f()).mul(0.5F);
        Vector3f mid = left.add(right, new Vector3f()).mul(0.5F);
        Assertions.assertEquals(0.0, before.roomToWorld(startMid).distanceTo(after.roomToWorld(mid)), 1.0E-2,
            "point between the hands moved in the world");
        // and the frame center still shows the focus
        Assertions.assertEquals(0.0, after.roomToWorld(held.frame().center()).distanceTo(focus), 1.0E-3);
    }

    @Test
    public void levelHandsStayOnTheirPoints() {
        TabletopFrame frame = new TabletopFrame(new Vector3f(0F, 0.9F, -0.6F), -0.4F, 1F, 1F, 0.5F, 0.15F);
        Vec3 focus = new Vec3(-30.5, 70, 12.5);
        TabletopMapping before = TabletopMapping.of(frame, focus, 40F, 3);
        Vector3f leftStart = new Vector3f(-0.25F, 1.0F, -0.5F);
        Vector3f rightStart = new Vector3f(0.25F, 1.0F, -0.6F);
        Vector3f left = new Vector3f(-0.1F, 1.1F, -0.2F);
        Vector3f right = new Vector3f(0.5F, 1.1F, -0.9F);
        TabletopNavigation.Held held = TabletopNavigation.holdWithBothHands(frame, 40F, leftStart, rightStart, left,
            right, 0.2F, 5F);
        TabletopMapping after = TabletopMapping.of(held.frame(), focus, held.blocksPerMeter(), 3);
        Assertions.assertEquals(0.0, before.roomToWorld(leftStart).distanceTo(after.roomToWorld(left)), 1.0E-2);
        Assertions.assertEquals(0.0, before.roomToWorld(rightStart).distanceTo(after.roomToWorld(right)), 1.0E-2);
    }

    @Test
    public void sizeIsLimited() {
        TabletopFrame frame = new TabletopFrame(new Vector3f(0F, 1F, -0.6F), 0F, 1F, 1F, 0.5F, 0.15F);
        TabletopNavigation.Held held = TabletopNavigation.holdWithBothHands(frame, 40F, new Vector3f(-0.1F, 1F, 0F),
            new Vector3f(0.1F, 1F, 0F), new Vector3f(-2F, 1F, 0F), new Vector3f(2F, 1F, 0F), 0.3F, 3F);
        Assertions.assertEquals(3F, held.frame().sizeX(), 1.0E-4);
        Assertions.assertEquals(40F / 3F, held.blocksPerMeter(), 1.0E-3);
    }
}
