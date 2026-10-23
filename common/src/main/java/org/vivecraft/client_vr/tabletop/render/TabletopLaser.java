package org.vivecraft.client_vr.tabletop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.TabletopPick;
import org.vivecraft.client_vr.render.rendertypes.VRRenderTypes;

/**
 * draws a laser from the controller to what it points at in tabletop mode, so it is clear which hand selects the block
 */
public class TabletopLaser {
    private static final Minecraft MC = Minecraft.getInstance();

    // laser width in meters, in the room
    private static final float WIDTH = 0.003F;
    // length in meters when the controller doesn't point at the tabletop
    private static final float STUB_LENGTH = 0.15F;

    // same color as the high contrast block outline
    private static final int COLOR_HIT = 0xE657FFE1;
    private static final int COLOR_OUT_OF_REACH = 0xE6FF4040;
    private static final int COLOR_EMPTY = 0x66FFFFFF;

    /**
     * submits the laser for the current pass
     *
     * @param output    collector to submit to
     * @param poseStack pose stack at the camera
     * @param camera    camera position for the current pass
     */
    public static void submit(SubmitNodeCollector output, PoseStack poseStack, Vec3 camera) {
        ClientDataHolderVR dh = ClientDataHolderVR.getInstance();
        if (!TabletopManager.isActive() || MC.player == null || MC.gui.screen() != null ||
            dh.vrPlayer.vrdata_world_render == null || !RenderPass.isFirstPerson(dh.currentPass) ||
            dh.interactTracker.isActiveModule(dh.hotbarModule))
        {
            // no laser while the hand selects a hotbar slot
            return;
        }

        float scale = dh.tabletop.getMapping().scale();
        Vec3 start = dh.vrPlayer.vrdata_world_render.getAim().getPosition();
        Vec3 end;
        int color;
        HitResult hit = MC.hitResult;
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            // also follows a block that is locked while it is broken
            end = hit.getLocation();
            color = COLOR_HIT;
        } else if (TabletopPick.getLastResult() == TabletopPick.Result.OUT_OF_REACH &&
            TabletopPick.getLastEnd() != null)
        {
            end = TabletopPick.getLastEnd();
            color = COLOR_OUT_OF_REACH;
        } else if (TabletopPick.getLastResult() == TabletopPick.Result.EMPTY && TabletopPick.getLastEnd() != null) {
            end = TabletopPick.getLastEnd();
            color = COLOR_EMPTY;
        } else {
            end = start.add(new Vec3(dh.vrPlayer.vrdata_world_render.getAim().getDirection())
                .scale(STUB_LENGTH * scale));
            color = COLOR_EMPTY;
        }

        Vec3 axis = end.subtract(start);
        // turn the flat laser towards the camera
        Vec3 side = axis.cross(start.add(end).scale(0.5).subtract(camera));
        if (side.lengthSqr() < 1.0E-12) {
            return;
        }
        side = side.normalize().scale(0.5 * WIDTH * scale);

        Vec3 a = start.add(side).subtract(camera);
        Vec3 b = start.subtract(side).subtract(camera);
        Vec3 c = end.subtract(side).subtract(camera);
        Vec3 d = end.add(side).subtract(camera);
        output.submitCustomGeometry(poseStack, VRRenderTypes.quads(false), (pose, consumer) -> {
            consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color);
            consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color);
            consumer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(color);
            consumer.addVertex(pose, (float) d.x, (float) d.y, (float) d.z).setColor(color);
        });
    }
}
