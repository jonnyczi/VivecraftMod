package org.vivecraft.client_vr.tabletop.math;

import org.joml.Vector3fc;

/**
 * the area in the room that the tabletop world is shown in, all values are in meters and room space
 *
 * @param center      center of the frame base, the world focus point is shown here
 * @param yaw         rotation of the frame around the up axis, in radians
 * @param sizeX       width of the frame along its local x axis
 * @param sizeZ       depth of the frame along its local z axis
 * @param heightAbove how far above the base the world is shown
 * @param depthBelow  how far below the base the world is shown
 */
public record TabletopFrame(Vector3fc center, float yaw, float sizeX, float sizeZ, float heightAbove,
                            float depthBelow)
{
    /**
     * @return distance from the center of the base to a corner of the base
     */
    public float halfDiagonal() {
        return 0.5F * (float) Math.sqrt(this.sizeX * this.sizeX + this.sizeZ * this.sizeZ);
    }
}
