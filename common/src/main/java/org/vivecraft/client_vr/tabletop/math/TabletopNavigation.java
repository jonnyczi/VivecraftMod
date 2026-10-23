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
     * changes the zoom while keeping the world point under {@code roomPivot} where it is
     *
     * @param focus         current world focus
     * @param frameCenter   center of the frame base in the room
     * @param roomPivot     point in the room to zoom around, usually between the hands
     * @param oldScale      current blocks per meter
     * @param newScale      new blocks per meter
     * @param rotation      current world rotation, see {@link TabletopMapping#rotation()}
     * @param keepBaseLevel if true only zooms horizontally around the pivot, so the layer at the frame base stays the same
     * @return new world focus
     */
    public static Vec3 zoomAround(
        Vec3 focus, Vector3fc frameCenter, Vector3fc roomPivot, float oldScale, float newScale, float rotation,
        boolean keepBaseLevel)
    {
        // the pivot shows focus + rotate((pivot - center) * scale), keep that the same for both scales
        Vector3f offset = roomPivot.sub(frameCenter, new Vector3f());
        if (keepBaseLevel) {
            offset.y = 0F;
        }
        offset.mul(oldScale - newScale).rotateY(rotation);
        return focus.add(offset.x, offset.y, offset.z);
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
