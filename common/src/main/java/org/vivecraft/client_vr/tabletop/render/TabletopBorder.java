package org.vivecraft.client_vr.tabletop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.rendertypes.VRRenderTypes;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.math.ClipBox;

/**
 * draws a thin rim around the bottom edge of the tabletop, so it can be found and grabbed, even when the world is
 * moved so far that no blocks are visible
 */
public class TabletopBorder {

    // width of the flat rim and height of the edge below it, in meters, in the room
    private static final float RIM_WIDTH = 0.015F;
    private static final float EDGE_HEIGHT = 0.012F;

    private static final int COLOR_RIM = 0xD2E6E6E6;
    private static final int COLOR_EDGE = 0xD2404040;

    /**
     * submits the border for the current pass
     *
     * @param output    collector to submit to
     * @param poseStack pose stack at the camera
     * @param camera    camera position for the current pass
     */
    public static void submit(SubmitNodeCollector output, PoseStack poseStack, Vec3 camera) {
        if (!TabletopManager.isActive()) return;
        TabletopManager tabletop = ClientDataHolderVR.getInstance().tabletop;

        ClipBox box = tabletop.getClipBox();
        double scale = tabletop.getMapping().scale();
        double rim = RIM_WIDTH * scale;

        // corners relative to the camera, inside and outside of the rim
        float y = (float) (box.minY() - camera.y);
        float bottom = (float) (box.minY() - EDGE_HEIGHT * scale - camera.y);
        float innerMinX = (float) (box.minX() - camera.x);
        float innerMaxX = (float) (box.maxX() - camera.x);
        float innerMinZ = (float) (box.minZ() - camera.z);
        float innerMaxZ = (float) (box.maxZ() - camera.z);
        float outerMinX = (float) (box.minX() - rim - camera.x);
        float outerMaxX = (float) (box.maxX() + rim - camera.x);
        float outerMinZ = (float) (box.minZ() - rim - camera.z);
        float outerMaxZ = (float) (box.maxZ() + rim - camera.z);

        output.submitCustomGeometry(poseStack, VRRenderTypes.quads(false), (pose, consumer) -> {
            // flat rim, one strip per side
            quad(consumer, pose, COLOR_RIM,
                outerMinX, y, outerMinZ, outerMaxX, y, outerMinZ, innerMaxX, y, innerMinZ, innerMinX, y, innerMinZ);
            quad(consumer, pose, COLOR_RIM,
                outerMaxX, y, outerMinZ, outerMaxX, y, outerMaxZ, innerMaxX, y, innerMaxZ, innerMaxX, y, innerMinZ);
            quad(consumer, pose, COLOR_RIM,
                outerMaxX, y, outerMaxZ, outerMinX, y, outerMaxZ, innerMinX, y, innerMaxZ, innerMaxX, y, innerMaxZ);
            quad(consumer, pose, COLOR_RIM,
                outerMinX, y, outerMaxZ, outerMinX, y, outerMinZ, innerMinX, y, innerMinZ, innerMinX, y, innerMaxZ);
            // edge below the outside of the rim, so it is also visible from the side
            quad(consumer, pose, COLOR_EDGE,
                outerMinX, y, outerMinZ, outerMaxX, y, outerMinZ, outerMaxX, bottom, outerMinZ, outerMinX, bottom,
                outerMinZ);
            quad(consumer, pose, COLOR_EDGE,
                outerMaxX, y, outerMinZ, outerMaxX, y, outerMaxZ, outerMaxX, bottom, outerMaxZ, outerMaxX, bottom,
                outerMinZ);
            quad(consumer, pose, COLOR_EDGE,
                outerMaxX, y, outerMaxZ, outerMinX, y, outerMaxZ, outerMinX, bottom, outerMaxZ, outerMaxX, bottom,
                outerMaxZ);
            quad(consumer, pose, COLOR_EDGE,
                outerMinX, y, outerMaxZ, outerMinX, y, outerMinZ, outerMinX, bottom, outerMinZ, outerMinX, bottom,
                outerMaxZ);
        });
    }

    private static void quad(
        VertexConsumer consumer, PoseStack.Pose pose, int color, float x0, float y0, float z0, float x1, float y1, float z1,
        float x2, float y2, float z2, float x3, float y3, float z3)
    {
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x2, y2, z2).setColor(color);
        consumer.addVertex(pose, x3, y3, z3).setColor(color);
    }
}
