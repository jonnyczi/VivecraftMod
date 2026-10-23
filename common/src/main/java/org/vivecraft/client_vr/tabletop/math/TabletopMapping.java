package org.vivecraft.client_vr.tabletop.math;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * maps room positions to world positions in tabletop mode.
 * uses the same convention as {@link org.vivecraft.client_vr.VRData}: {@code world = origin + rotateY(room * scale)},
 * so these values can be used directly as the room origin, world scale and world rotation
 *
 * @param origin   world position of the room origin
 * @param scale    world blocks per room meter
 * @param rotation rotation around the up axis from room to world, in radians
 */
public record TabletopMapping(Vec3 origin, float scale, float rotation) {

    private static final float QUARTER_TURN = (float) (Math.PI * 0.5);

    /**
     * @param frame          frame the world is shown in
     * @param focus          world position that should be shown at the frame center
     * @param blocksPerMeter how many blocks fit in one meter of the frame
     * @param quarterTurns   how often the world is rotated by 90° inside the frame
     * @return the mapping that shows {@code focus} at the frame center, with the world axes aligned to the frame
     */
    public static TabletopMapping of(TabletopFrame frame, Vec3 focus, float blocksPerMeter, int quarterTurns) {
        float rotation = worldRotation(frame.yaw(), quarterTurns);
        Vector3f center = frame.center().mul(blocksPerMeter, new Vector3f()).rotateY(rotation);
        return new TabletopMapping(focus.subtract(center.x, center.y, center.z), blocksPerMeter, rotation);
    }

    /**
     * the world rotation is chosen so that the world axes are parallel to the frame edges,
     * that keeps the clip box axis aligned and lets cut faces line up with block faces
     *
     * @param frameYaw     rotation of the frame in the room, in radians
     * @param quarterTurns how often the world is rotated by 90° inside the frame
     * @return world rotation in radians
     */
    public static float worldRotation(float frameYaw, int quarterTurns) {
        return Math.floorMod(quarterTurns, 4) * QUARTER_TURN - frameYaw;
    }

    /**
     * @return the world position of the given room position
     */
    public Vec3 roomToWorld(Vector3fc room) {
        Vector3f offset = room.mul(this.scale, new Vector3f()).rotateY(this.rotation);
        return this.origin.add(offset.x, offset.y, offset.z);
    }

    /**
     * @return the room position of the given world position
     */
    public Vector3f worldToRoom(Vec3 world) {
        Vector3f offset = new Vector3f(
            (float) (world.x - this.origin.x),
            (float) (world.y - this.origin.y),
            (float) (world.z - this.origin.z));
        return offset.rotateY(-this.rotation).div(this.scale);
    }
}
