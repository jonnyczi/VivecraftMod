package org.vivecraft.client_vr.tabletop.math;

import net.minecraft.world.phys.Vec3;

/**
 * axis aligned box in world space, everything outside of it is not rendered in tabletop mode
 */
public record ClipBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    /**
     * the box always stays axis aligned in the world, because the world rotation is snapped to the frame,
     * see {@link TabletopMapping#worldRotation(float, int)}
     *
     * @param frame          frame the world is shown in
     * @param focus          world position that is shown at the frame center
     * @param blocksPerMeter how many blocks fit in one meter of the frame
     * @param quarterTurns   how often the world is rotated by 90° inside the frame
     * @return the part of the world that is visible in the frame
     */
    public static ClipBox of(TabletopFrame frame, Vec3 focus, float blocksPerMeter, int quarterTurns) {
        // with odd quarter turns the frame x axis is along the world z axis
        boolean swapped = Math.floorMod(quarterTurns, 2) == 1;
        double halfX = 0.5 * blocksPerMeter * (swapped ? frame.sizeZ() : frame.sizeX());
        double halfZ = 0.5 * blocksPerMeter * (swapped ? frame.sizeX() : frame.sizeZ());
        return new ClipBox(
            focus.x - halfX, focus.y - blocksPerMeter * frame.depthBelow(), focus.z - halfZ,
            focus.x + halfX, focus.y + blocksPerMeter * frame.heightAbove(), focus.z + halfZ);
    }

    /**
     * @return a copy of this box that is smaller by {@code amount} on every side
     */
    public ClipBox shrink(double amount) {
        return new ClipBox(this.minX + amount, this.minY + amount, this.minZ + amount,
            this.maxX - amount, this.maxY - amount, this.maxZ - amount);
    }

    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x <= this.maxX &&
            y >= this.minY && y <= this.maxY &&
            z >= this.minZ && z <= this.maxZ;
    }

    public boolean contains(Vec3 pos) {
        return this.contains(pos.x, pos.y, pos.z);
    }

    /**
     * @return if the given box overlaps with this box
     */
    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return minX <= this.maxX && maxX >= this.minX &&
            minY <= this.maxY && maxY >= this.minY &&
            minZ <= this.maxZ && maxZ >= this.minZ;
    }

    /**
     * @return the box relative to {@code camera}, as {minX, minY, minZ, maxX, maxY, maxZ} for upload to the gpu.
     * the subtraction is done in double precision, so that it stays accurate far away from the world origin
     */
    public float[] relativeTo(Vec3 camera) {
        return new float[]{
            (float) (this.minX - camera.x), (float) (this.minY - camera.y), (float) (this.minZ - camera.z),
            (float) (this.maxX - camera.x), (float) (this.maxY - camera.y), (float) (this.maxZ - camera.z)
        };
    }

    public double sizeX() {
        return this.maxX - this.minX;
    }

    public double sizeY() {
        return this.maxY - this.minY;
    }

    public double sizeZ() {
        return this.maxZ - this.minZ;
    }
}
