package org.vivecraft.gametest;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.ViewMode;
import org.vivecraft.client_vr.tabletop.math.ClipBox;

/**
 * checks where the tabletop shows the world: the base height, following the avatar, and centering again after
 * teleports, respawning and changing the dimension
 */
public class TabletopNavigationClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void runTest(ClientGameTestContext context) {
        VRTestHelper.startNullVR(context);

        try (TestSingleplayerContext singleplayer = context.worldBuilder()
            .setUseConsistentSettings(false)
            .adjustSettings(settings -> settings.setSeed("vivecraft tabletop"))
            .create())
        {
            singleplayer.getServer().runCommand("time set noon");
            // creative, so teleporting into blocks doesn't hurt
            singleplayer.getServer().runCommand("gamemode creative @a");
            VRTestHelper.waitForWorld(context, singleplayer);

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.TABLETOP);
            context.waitFor(mc -> TabletopManager.isActive(), 100);
            context.waitTicks(5);
            checkAvatarInside(context, "entering on the dunes");

            BlockPos start = context.computeOnClient(mc -> mc.player.blockPosition());
            int x = start.getX();
            int z = start.getZ();

            // a flat platform in the sky: the base is at its surface, and the avatar in the middle
            run(singleplayer, "fill %d 149 %d %d 149 %d minecraft:stone", x - 30, z - 30, x + 30, z + 30);
            teleport(context, singleplayer, x + 0.5, 150, z + 0.5);
            Vec3 focus = focus(context);
            check(Math.abs(focus.y - 150.0) < 0.01, "the base should be at the platform surface, but is at " + focus.y);
            check(Math.abs(focus.x - (x + 0.5)) < 0.01 && Math.abs(focus.z - (z + 0.5)) < 0.01,
                "a teleport should center the tabletop on the avatar, focus " + focus);

            // walking towards an edge scrolls the world, so the avatar stays a quarter of the width away from it
            for (int step = 1; step <= 3; step++) {
                teleport(context, singleplayer, x + 0.5 + 6 * step, 150, z + 0.5);
            }
            double edgeDistance = context.computeOnClient(
                mc -> 0.25 * ClientDataHolderVR.getInstance().tabletop.getClipBox().sizeX());
            focus = focus(context);
            double avatarX = context.computeOnClient(mc -> mc.player.getX());
            LOGGER.info("Vivecraft gametest: after walking, avatar x {} focus x {}", avatarX, focus.x);
            check(Math.abs(avatarX - focus.x - edgeDistance) < 0.6,
                "the world should follow the avatar, avatar x " + avatarX + " focus x " + focus.x);
            checkAvatarInside(context, "after walking");

            // without moving, a world that was scrolled away stays where it is
            Vec3 scrolled = focus.add(15, 0, 0);
            context.runOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.setFocus(scrolled));
            context.waitTicks(20);
            check(focus(context).distanceTo(scrolled) < 1.0E-6,
                "the world shouldn't follow an avatar that doesn't move");

            // a cave inside a block of stone, underground the tabletop shows a cross-section just above the avatar
            run(singleplayer, "fill %d 160 %d %d 170 %d minecraft:stone", x + 45, z - 3, x + 51, z + 3);
            run(singleplayer, "fill %d 161 %d %d 162 %d minecraft:air", x + 48, z, x + 48, z);
            teleport(context, singleplayer, x + 48.5, 161, z + 0.5);
            ClipBox box = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getClipBox());
            LOGGER.info("Vivecraft gametest: in the cave, tabletop top at {}", box.maxY());
            check(Math.abs(box.maxY() - 165.0) < 0.01,
                "underground the tabletop should end 4 blocks above the feet, but ends at " + box.maxY());
            checkAvatarInside(context, "in the cave");

            // dying and respawning centers the tabletop on the respawned avatar
            singleplayer.getServer().runCommand("gamemode survival @a");
            singleplayer.getServer().runCommand("kill @a");
            context.waitFor(mc -> mc.gui.screen() instanceof DeathScreen, 100);
            context.runOnClient(mc -> mc.player.respawn());
            context.waitFor(mc -> mc.player.isAlive() && !(mc.gui.screen() instanceof DeathScreen), 200);
            context.waitTicks(20);
            check(context.computeOnClient(mc -> TabletopManager.isActive()), "tabletop should stay on after respawning");
            checkCentered(context, "after respawning");
            checkAvatarInside(context, "after respawning");

            // changing the dimension centers it on the avatar in the new level
            singleplayer.getServer().runCommand("gamemode creative @a");
            singleplayer.getServer().runCommand("execute in minecraft:the_nether run tp @a 0.5 70 0.5");
            context.waitFor(mc -> mc.level.dimension() == Level.NETHER, 400);
            context.waitTicks(60);
            check(context.computeOnClient(mc -> TabletopManager.isActive()), "tabletop should stay on in the nether");
            checkCentered(context, "in the nether");
            checkAvatarInside(context, "in the nether");
            VRScreenshot.takeLeftEye(context, "tabletop_nether");

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.VR);
            context.waitFor(mc -> !TabletopManager.isActive(), 100);
        }

        VRTestHelper.stopVR(context);
    }

    private static void run(TestSingleplayerContext singleplayer, String command, Object... args) {
        singleplayer.getServer().runCommand(command.formatted(args));
    }

    private static void teleport(ClientGameTestContext context, TestSingleplayerContext singleplayer, double x,
        double y, double z)
    {
        singleplayer.getServer().runCommand("tp @a %s %s %s".formatted(x, y, z));
        context.waitFor(mc -> mc.player.position().distanceTo(new Vec3(x, y, z)) < 0.01, 100);
        context.waitTicks(3);
    }

    private static Vec3 focus(ClientGameTestContext context) {
        return context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFocus());
    }

    private static void checkCentered(ClientGameTestContext context, String when) {
        Vec3 focus = focus(context);
        Vec3 avatar = context.computeOnClient(mc -> mc.player.position());
        double horizontal = Math.hypot(focus.x - avatar.x, focus.z - avatar.z);
        LOGGER.info("Vivecraft gametest: {}, focus {} avatar {}", when, focus, avatar);
        check(horizontal < 1.0, when + ": the tabletop should be centered on the avatar, focus " + focus +
            " avatar " + avatar);
    }

    private static void checkAvatarInside(ClientGameTestContext context, String when) {
        ClipBox box = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getClipBox());
        Vec3 avatar = context.computeOnClient(mc -> mc.player.position());
        check(box.contains(avatar) && box.contains(avatar.add(0, 1.8, 0)),
            when + ": the avatar at " + avatar + " should be inside the tabletop " + box);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
