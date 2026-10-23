package org.vivecraft.client_vr.render.helpers;

import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

/**
 * decides if the world should be rendered with a transparent background,
 * so that the camera passthrough of the headset can be seen behind it
 */
public class PassthroughHelper {
    private static final ClientDataHolderVR DATA_HOLDER = ClientDataHolderVR.getInstance();

    // if the current frame should have a transparent background, sampled once per frame
    private static boolean TRANSPARENT_BACKGROUND = false;

    /**
     * syncs the passthrough state of the VR runtime, needs to be called once per frame before rendering.
     */
    public static void updateFrame() {
        boolean wanted = DATA_HOLDER.vrSettings.renderTransparentBackground;
        if (DATA_HOLDER.vr.isPassthroughActive() != wanted) {
            DATA_HOLDER.vr.setPassthroughActive(wanted);
        }
        TRANSPARENT_BACKGROUND = wanted;
    }

    /**
     * @return if the current RenderPass should be rendered with a transparent background
     */
    public static boolean hasTransparentBackground() {
        return VRState.VR_RUNNING && TRANSPARENT_BACKGROUND && isHeadsetPass(DATA_HOLDER.currentPass);
    }

    /**
     * @param pass RenderPass to check
     * @return if the RenderPass ends up in the headset, or shows the headset view on the mirror
     */
    private static boolean isHeadsetPass(RenderPass pass) {
        return pass == RenderPass.LEFT || pass == RenderPass.RIGHT || pass == RenderPass.CENTER;
    }
}
