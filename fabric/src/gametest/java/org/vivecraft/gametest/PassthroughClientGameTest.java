package org.vivecraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.vivecraft.client_vr.ClientDataHolderVR;

/**
 * renders a world in VR with and without the transparent passthrough background,
 * the eye screenshots show transparent parts as a checkerboard
 */
public class PassthroughClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        VRTestHelper.startNullVR(context);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getServer().runCommand("time set noon");
            VRTestHelper.waitForWorld(context, singleplayer);

            VRScreenshot.EyeImage off = VRScreenshot.takeLeftEye(context, "passthrough_off");
            check(off.topCenterAlpha() == 255, "sky should be opaque without passthrough, " + off);

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.renderTransparentBackground = true);
            VRScreenshot.EyeImage on = VRScreenshot.takeLeftEye(context, "passthrough_on");
            checkTransparentBackground(on);

            // night and rain should not change anything with a transparent background
            singleplayer.getServer().runCommand("time set midnight");
            singleplayer.getServer().runCommand("weather rain");
            VRScreenshot.EyeImage night = VRScreenshot.takeLeftEye(context, "passthrough_on_night_rain");
            checkTransparentBackground(night);

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.renderTransparentBackground = false);
        }

        VRTestHelper.stopVR(context);
    }

    private static void checkTransparentBackground(VRScreenshot.EyeImage image) {
        check(image.topCenterAlpha() == 0, "sky should be transparent with passthrough, " + image);
        // passthrough expects premultiplied alpha, any color in transparent pixels would glow over the real world
        check(image.glowingPixels() == 0, "transparent pixels should be black, " + image);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
