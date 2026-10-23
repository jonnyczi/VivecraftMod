package org.vivecraft.gametest;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.ViewMode;
import org.vivecraft.client_vr.tabletop.math.TabletopFrame;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

/**
 * enters tabletop mode and checks that the world is mapped onto the frame, and the player stays where it is
 */
public class TabletopClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    // in front of and below the NullVR headset, which looks straight ahead from 1.62m, so the whole frame is visible
    private static final Vector3f FRAME_CENTER = new Vector3f(0F, 1.05F, -1.5F);

    @Override
    public void runTest(ClientGameTestContext context) {
        VRTestHelper.startNullVR(context);

        try (TestSingleplayerContext singleplayer = context.worldBuilder()
            // real terrain instead of the default superflat test world
            .setUseConsistentSettings(false)
            .adjustSettings(settings -> settings.setSeed("vivecraft tabletop"))
            .create())
        {
            singleplayer.getServer().runCommand("time set noon");
            VRTestHelper.waitForWorld(context, singleplayer);

            Vec3 playerBefore = context.computeOnClient(mc -> mc.player.position());
            int sectionsInVR = context.computeOnClient(mc -> mc.levelRenderer.visibleSections().size());

            // enter tabletop the same way the radial menu does
            pressToggleTabletop(context);
            context.waitFor(mc -> TabletopManager.isActive(), 100);

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.setFrame(
                new TabletopFrame(FRAME_CENTER, 0F, 1F, 1F, 0.5F, 0.15F)));
            // the camera moved far, give the chunk renderer time to update the visible sections
            context.waitTicks(60);

            Vec3 playerAfter = context.computeOnClient(mc -> mc.player.position());
            int sectionsInTabletop = context.computeOnClient(mc -> mc.levelRenderer.visibleSections().size());
            check(!isServerVR(context, singleplayer), "the server should treat a tabletop player like a regular player");
            LOGGER.info("Vivecraft gametest: visible sections in VR {}, in tabletop {}", sectionsInVR, sectionsInTabletop);
            // sodium doesn't use the vanilla section list
            if (sectionsInVR > 0) {
                check(sectionsInTabletop < sectionsInVR / 2,
                    "tabletop should draw far fewer sections, VR " + sectionsInVR + ", tabletop " + sectionsInTabletop);
            }
            check(playerBefore.distanceTo(playerAfter) < 0.5,
                "player should stay in place in tabletop mode, moved from " + playerBefore + " to " + playerAfter);

            // the frame center shows the world focus
            Vec3 focus = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFocus());
            Vec3 frameCenterWorld = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop
                .getMapping().roomToWorld(FRAME_CENTER));
            check(focus.distanceTo(frameCenterWorld) < 0.1,
                "frame center should show the focus " + focus + " but shows " + frameCenterWorld);

            VRScreenshot.takeLeftEye(context, "tabletop_sky");

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.renderTransparentBackground = true);
            VRScreenshot.takeLeftEye(context, "tabletop_passthrough");

            // lift the world to see a cross-section of the underground
            context.runOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.setFocus(
                ClientDataHolderVR.getInstance().tabletop.getFocus().subtract(0, 12, 0)));
            VRScreenshot.takeLeftEye(context, "tabletop_lifted");

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.renderTransparentBackground = false);
            pressToggleTabletop(context);
            context.waitFor(mc -> !TabletopManager.isActive(), 100);
            check(context.computeOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode == ViewMode.VR),
                "toggle should switch back to VR");
            context.waitTicks(5);
            check(isServerVR(context, singleplayer), "the server should know the player is in VR again");
        }

        VRTestHelper.stopVR(context);
    }

    /**
     * presses and releases the tabletop toggle action, like a radial menu button
     */
    private static void pressToggleTabletop(ClientGameTestContext context) {
        context.runOnClient(mc -> MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyToggleTabletop).holdBinding());
        context.waitTicks(2);
        context.runOnClient(
            mc -> MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyToggleTabletop).stopHoldingBinding(2));
        context.waitTick();
    }

    /**
     * @return if the integrated server thinks the player is in VR
     */
    private static boolean isServerVR(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.waitTicks(2);
        return singleplayer.getServer().computeOnServer(server -> {
            ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(server.getPlayerList().getPlayers().getFirst());
            return vivePlayer != null && vivePlayer.isVR();
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
