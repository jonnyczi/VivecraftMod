package org.vivecraft.client_vr.tabletop.math;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * moving the world around inside the tabletop frame, all methods return the new world focus point
 */
public class TabletopNavigation {

    /**
     * moves the world along with a hand that is dragging it, a vertical drag lifts the world,
     * which shows deeper layers at the frame base
     *
     * @param focus          current world focus
     * @param roomDelta      how far the hand moved in the room, in meters
     * @param blocksPerMeter current zoom
     * @param rotation       current world rotation, see {@link TabletopMapping#rotation()}
     * @return new world focus
     */
    public static Vec3 drag(Vec3 focus, Vector3fc roomDelta, float blocksPerMeter, float rotation) {
        Vector3f worldDelta = roomDelta.mul(blocksPerMeter, new Vector3f()).rotateY(rotation);
        return focus.subtract(worldDelta.x, worldDelta.y, worldDelta.z);
    }

    /**
     * moves the world so that {@code target} stays inside the box, at least {@code margin} blocks away from the edges.
     * this is used to scroll the world when the avatar walks towards the frame edge
     *
     * @param focus  current world focus
     * @param box    current clip box
     * @param target world position that should stay visible
     * @param margin distance to the box edges in blocks, per axis
     * @return new world focus
     */
    public static Vec3 follow(Vec3 focus, ClipBox box, Vec3 target, Vec3 margin) {
        return focus.add(
            overshoot(target.x, box.minX() + margin.x, box.maxX() - margin.x),
            overshoot(target.y, box.minY() + margin.y, box.maxY() - margin.y),
            overshoot(target.z, box.minZ() + margin.z, box.maxZ() - margin.z));
    }

    /**
     * @return how far {@code value} is outside of {@code min} to {@code max}, if the range is empty, the distance to its center
     */
    private static double overshoot(double value, double min, double max) {
        if (min > max) {
            return value - (min + max) * 0.5;
        } else if (value < min) {
            return value - min;
        } else if (value > max) {
            return value - max;
        } else {
            return 0.0;
        }
    }

    /**
     * the whole tabletop held with both hands: moving the hands carries it, twisting them turns it, and pulling them
     * apart or together scales it. the same part of the world stays visible, so the zoom changes with the size
     *
     * @param frame          frame when the hands started holding
     * @param blocksPerMeter zoom when the hands started holding
     * @param leftStart      left hand room position when it started holding
     * @param rightStart     right hand room position when it started holding
     * @param left           current left hand room position
     * @param right          current right hand room position
     * @param minSize        smallest allowed frame width in meters
     * @param maxSize        largest allowed frame width in meters
     * @return the moved, turned and scaled frame with its zoom
     */
    public static Held holdWithBothHands(
        TabletopFrame frame, float blocksPerMeter, Vector3fc leftStart, Vector3fc rightStart, Vector3fc left,
        Vector3fc right, float minSize, float maxSize)
    {
        Vector3f startSpan = rightStart.sub(leftStart, new Vector3f());
        Vector3f span = right.sub(left, new Vector3f());

        // turn by how much the hands turned around the up axis, same convention as Vector3f#rotateY
        float angle = 0F;
        if (horizontalLength(startSpan) > 1.0E-3F && horizontalLength(span) > 1.0E-3F) {
            angle = (float) (Math.atan2(-span.z, span.x) - Math.atan2(-startSpan.z, startSpan.x));
        }

        // scale by how much the hands moved apart, limited by the frame size
        float scale = startSpan.length() > 1.0E-3F ? span.length() / startSpan.length() : 1F;
        float width = Math.max(frame.sizeX(), frame.sizeZ());
        scale = Math.max(minSize / width, Math.min(maxSize / width, scale));

        // everything turns and scales around the point between the hands, and moves with it
        Vector3f startMid = leftStart.add(rightStart, new Vector3f()).mul(0.5F);
        Vector3f mid = left.add(right, new Vector3f()).mul(0.5F);
        Vector3f center = frame.center().sub(startMid, new Vector3f()).mul(scale).rotateY(angle).add(mid);

        return new Held(new TabletopFrame(center, frame.yaw() + angle, frame.sizeX() * scale,
            frame.sizeZ() * scale, frame.heightAbove() * scale, frame.depthBelow() * scale),
            blocksPerMeter / scale);
    }

    private static float horizontalLength(Vector3fc vec) {
        return (float) Math.sqrt(vec.x() * vec.x() + vec.z() * vec.z());
    }

    /**
     * result of {@link #holdWithBothHands}
     *
     * @param frame          new frame
     * @param blocksPerMeter new zoom
     */
    public record Held(TabletopFrame frame, float blocksPerMeter) {}

    /**
     * limits how far the focus can be moved horizontally away from {@code center}, since only chunks around the player are loaded
     *
     * @param focus       wanted world focus
     * @param center      position the focus has to stay close to, usually the player
     * @param maxDistance maximum horizontal distance in blocks
     * @return the focus moved back into range
     */
    public static Vec3 limitDistance(Vec3 focus, Vec3 center, double maxDistance) {
        double dx = focus.x - center.x;
        double dz = focus.z - center.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= maxDistance) {
            return focus;
        }
        double factor = Math.max(0.0, maxDistance) / distance;
        return new Vec3(center.x + dx * factor, focus.y, center.z + dz * factor);
    }
}
