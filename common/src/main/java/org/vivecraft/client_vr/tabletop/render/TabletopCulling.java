package org.vivecraft.client_vr.tabletop.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.math.ClipBox;

/**
 * hides things outside the tabletop clip box, that are not part of the clipped terrain
 */
public class TabletopCulling {

    // particles can drift a bit out of the box before they disappear
    private static final double PARTICLE_MARGIN = 1.0;

    /**
     * in tabletop mode the camera is far away from the world, so the regular distance culling would hide most entities,
     * use the clip box instead
     *
     * @param entity  entity to check
     * @param frustum view frustum of the current pass
     * @return if the entity should be rendered
     */
    public static boolean isEntityVisible(Entity entity, Frustum frustum) {
        // a bit bigger, since models can extend past the hitbox
        AABB aabb = entity.getBoundingBox().inflate(0.5);
        ClipBox box = getBox();
        return box.intersects(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ) &&
            frustum.isVisible(aabb);
    }

    /**
     * @return if the block entity at the given position should be rendered
     */
    public static boolean isBlockVisible(BlockPos pos) {
        return getBox().intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1,
            pos.getZ() + 1);
    }

    /**
     * terrain sections outside the box don't need to be drawn at all, which saves the most work in tabletop mode
     *
     * @param sectionX section x coordinate
     * @param sectionY section y coordinate
     * @param sectionZ section z coordinate
     * @return if the section should be drawn
     */
    public static boolean isSectionVisible(int sectionX, int sectionY, int sectionZ) {
        int x = sectionX << 4;
        int y = sectionY << 4;
        int z = sectionZ << 4;
        return getBox().intersects(x, y, z, x + 16, y + 16, z + 16);
    }

    /**
     * @return if a particle at the given position should be spawned
     */
    public static boolean isParticleVisible(double x, double y, double z) {
        return getBox().shrink(-PARTICLE_MARGIN).contains(x, y, z);
    }

    /**
     * vanilla only spawns particles close to the camera, in tabletop mode use the frame center instead
     *
     * @param camera camera position
     * @return the position that particle distances should be measured from
     */
    public static Vec3 particleDistanceOrigin(Vec3 camera) {
        return TabletopManager.isActive() ? ClientDataHolderVR.getInstance().tabletop.getFocus() : camera;
    }

    private static ClipBox getBox() {
        return ClientDataHolderVR.getInstance().tabletop.getClipBox();
    }
}
