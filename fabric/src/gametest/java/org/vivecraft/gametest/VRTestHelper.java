package org.vivecraft.gametest;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.settings.VRSettings;

/**
 * shared setup for client gametests that need VR running, uses the NullVR provider so no headset is needed
 */
public class VRTestHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * enables VR with the NullVR provider in seated mode and waits until it is running
     */
    public static void startNullVR(ClientGameTestContext context) {
        startNullVR(context, true);
    }

    /**
     * enables VR with the NullVR provider and waits until it is running
     *
     * @param seated in seated mode the controllers follow the mouse, standing lets tests place them
     */
    public static void startNullVR(ClientGameTestContext context, boolean seated) {
        context.runOnClient(mc -> {
            VRSettings settings = ClientDataHolderVR.getInstance().vrSettings;
            settings.stereoProviderPluginID = VRSettings.VRProvider.NULLVR;
            settings.seated = seated;
            settings.displayMirrorMode = VRSettings.MirrorMode.CROPPED;
            // the GC warning is a pause screen, which would stop the integrated server from loading chunks
            settings.disableGarbageCollectorMessage = true;
            settings.vrEnabled = true;
            VRState.VR_ENABLED = true;
        });
        context.waitFor(mc -> VRState.VR_RUNNING, 20 * 60);
    }

    /**
     * waits until the chunks around the player are downloaded and mostly rendered.
     * in VR every eye pass reschedules sections, so {@code hasRenderedAllSections} might never be true, give up after a while
     */
    public static void waitForWorld(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getClientLevel().waitForChunksDownload();
        for (int i = 0; i < 20 * 30; i++) {
            if (context.computeOnClient(mc -> mc.levelRenderer.hasRenderedAllSections())) {
                return;
            }
            context.waitTick();
        }
        LOGGER.warn("Vivecraft gametest: not all sections rendered, continuing anyway");
    }

    /**
     * logs the current screen, to debug tests that get stuck
     */
    public static void logScreen(ClientGameTestContext context, String when) {
        LOGGER.info("Vivecraft gametest: screen {}: {}", when,
            context.computeOnClient(mc -> String.valueOf(mc.gui.screen())));
    }

    /**
     * disables VR again, so that following tests start from a clean state
     */
    public static void stopVR(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            ClientDataHolderVR.getInstance().vrSettings.vrEnabled = false;
            VRState.VR_ENABLED = false;
        });
        context.waitFor(mc -> !VRState.VR_INITIALIZED, 20 * 60);
    }
}
