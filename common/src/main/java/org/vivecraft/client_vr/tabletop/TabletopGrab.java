package org.vivecraft.client_vr.tabletop;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client_vr.tabletop.math.TabletopFrame;
import org.vivecraft.client_vr.tabletop.math.TabletopNavigation;

/**
 * moves the tabletop with the grip buttons.
 * one hand drags the world around inside the frame, both hands carry, turn and scale the whole tabletop
 */
public class TabletopGrab {

    // limits for the frame width when scaling it with both hands, in meters
    private static final float MIN_SIZE = 0.25F;
    private static final float MAX_SIZE = 4.0F;

    private enum Mode {
        NONE,
        LEFT,
        RIGHT,
        BOTH
    }

    private Mode mode = Mode.NONE;

    // state when the current grab started
    private final Vector3f leftStart = new Vector3f();
    private final Vector3f rightStart = new Vector3f();
    private TabletopFrame frameStart;
    private float zoomStart;
    private Vec3 focusStart;
    private float rotationStart;

    /**
     * updates the grab, should be called every frame
     *
     * @param manager     tabletop to move
     * @param left        left hand room position
     * @param right       right hand room position
     * @param leftDown    if the left grip is held
     * @param rightDown   if the right grip is held
     * @param player      player position, the world can only be dragged around near it
     * @param maxDistance how far the world focus can be dragged away from the player, in blocks
     * @param minY        lowest world height the focus can be dragged to
     * @param maxY        highest world height the focus can be dragged to
     */
    public void update(
        TabletopManager manager, Vector3fc left, Vector3fc right, boolean leftDown, boolean rightDown, Vec3 player,
        double maxDistance, double minY, double maxY)
    {
        Mode wanted = leftDown && rightDown ? Mode.BOTH : leftDown ? Mode.LEFT : rightDown ? Mode.RIGHT : Mode.NONE;
        if (wanted != this.mode) {
            // start again from the current state, so switching between one and two hands doesn't jump
            this.mode = wanted;
            this.leftStart.set(left);
            this.rightStart.set(right);
            this.frameStart = manager.getFrame();
            this.zoomStart = manager.getBlocksPerMeter();
            this.focusStart = manager.getFocus();
            this.rotationStart = manager.getMapping().rotation();
        }

        switch (this.mode) {
            case BOTH -> {
                TabletopNavigation.Held held = TabletopNavigation.holdWithBothHands(this.frameStart, this.zoomStart,
                    this.leftStart, this.rightStart, left, right, MIN_SIZE, MAX_SIZE);
                manager.setFrameAndZoom(held.frame(), held.blocksPerMeter());
            }
            case LEFT, RIGHT -> {
                Vector3fc hand = this.mode == Mode.LEFT ? left : right;
                Vector3fc start = this.mode == Mode.LEFT ? this.leftStart : this.rightStart;
                Vec3 focus = TabletopNavigation.drag(this.focusStart, hand.sub(start, new Vector3f()),
                    this.zoomStart, this.rotationStart);
                focus = TabletopNavigation.limitDistance(focus, player, maxDistance);
                manager.setFocus(new Vec3(focus.x, Math.max(minY, Math.min(maxY, focus.y)), focus.z));
            }
            case NONE -> {}
        }
    }

    /**
     * @return if a hand holds the tabletop right now
     */
    public boolean isHolding() {
        return this.mode != Mode.NONE;
    }

    /**
     * stops the current grab, without moving anything
     */
    public void reset() {
        this.mode = Mode.NONE;
    }
}
