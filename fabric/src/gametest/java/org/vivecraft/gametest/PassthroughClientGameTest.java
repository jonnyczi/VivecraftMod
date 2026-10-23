package org.vivecraft.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.ViewMode;

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

            // fabulous graphics sort the transparent layers in a post pass, that needs to keep the background
            // transparent with passthrough, and opaque without
            singleplayer.getServer().runCommand("time set noon");
            singleplayer.getServer().runCommand("weather clear");
            // water around the player, that is sorted by the fabulous post pass
            singleplayer.getServer().runCommand("execute at @p run fill ~-8 ~-1 ~-8 ~8 ~-1 ~8 minecraft:water");
            singleplayer.getServer().runCommand("execute at @p run fill ~-1 ~-1 ~-1 ~1 ~-1 ~1 minecraft:grass_block");
            context.runOnClient(mc -> {
                ClientDataHolderVR.getInstance().vrSettings.renderTransparentBackground = false;
                mc.options.improvedTransparency().set(true);
            });
            context.waitTicks(5);
            VRScreenshot.EyeImage fabulousOff = VRScreenshot.takeLeftEye(context, "passthrough_off_fabulous");
            check(fabulousOff.topCenterAlpha() == 255,
                "sky should be opaque without passthrough with fabulous graphics, " + fabulousOff);

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.renderTransparentBackground = true);
            VRScreenshot.EyeImage fabulousOn = VRScreenshot.takeLeftEye(context, "passthrough_on_fabulous");
            checkTransparentBackground(fabulousOn);
            check(fabulousOn.transparentPixels() < fabulousOn.totalPixels(),
                "the world should still be visible with fabulous graphics, " + fabulousOn);

            // the tabletop with its cut faces and border
            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.TABLETOP);
            context.waitFor(mc -> TabletopManager.isActive(), 100);
            context.waitTicks(5);
            VRScreenshot.EyeImage tabletop = VRScreenshot.takeLeftEye(context, "tabletop_fabulous");
            checkTransparentBackground(tabletop);
            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.VR);
            context.waitFor(mc -> !TabletopManager.isActive(), 100);

            context.runOnClient(mc -> {
                ClientDataHolderVR.getInstance().vrSettings.renderTransparentBackground = false;
                mc.options.improvedTransparency().set(false);
            });
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
