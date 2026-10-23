package org.vivecraft.gametest;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.ViewMode;
import org.vivecraft.client_vr.tabletop.math.ClipBox;
import org.vivecraft.client_vr.tabletop.math.TabletopFrame;

/**
 * things a player does while in tabletop mode: sleeping, riding and opening the inventory
 */
public class TabletopSituationsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void runTest(ClientGameTestContext context) {
        VRTestHelper.startNullVR(context);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getServer().runCommand("difficulty peaceful");
            VRTestHelper.waitForWorld(context, singleplayer);

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.TABLETOP);
            context.waitFor(mc -> TabletopManager.isActive(), 100);
            BlockPos feet = context.computeOnClient(mc -> mc.player.blockPosition());

            // sleeping in a bed next to the avatar
            singleplayer.getServer().runCommand("time set midnight");
            // night vision, so the sleeping avatar can be seen on the screenshot
            singleplayer.getServer().runCommand("effect give @a minecraft:night_vision 600 0 true");
            // the sky gets dark on the next tick, only then beds can be used
            context.waitTicks(5);
            run(singleplayer, "setblock %d %d %d minecraft:red_bed[facing=east,part=foot]",
                feet.getX() + 2, feet.getY(), feet.getZ());
            run(singleplayer, "setblock %d %d %d minecraft:red_bed[facing=east,part=head]",
                feet.getX() + 3, feet.getY(), feet.getZ());
            BlockPos bed = feet.offset(3, 0, 0);
            // the table close in front of the eyes, so the avatar and the bed are well visible
            context.runOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.setFrame(
                new TabletopFrame(new Vector3f(0F, 1.25F, -1.0F), 0F, 1F, 1F, 0.5F, 0.15F)));
            context.waitTicks(5);
            VRScreenshot.takeLeftEye(context, "tabletop_before_sleeping");
            TabletopFrame frameAwake = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFrame());
            singleplayer.getServer().runOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().startSleepInBed(bed).ifLeft(problem -> {
                    throw new AssertionError("can't sleep: " + problem);
                }));
            context.waitFor(mc -> mc.player.isSleeping(), 100);
            // before the sleeping fades the view to black
            context.waitTicks(2);
            check(context.computeOnClient(mc -> TabletopManager.isActive()), "tabletop should stay on while sleeping");
            checkAvatarInside(context, "sleeping");
            TabletopFrame frameAsleep = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFrame());
            check(frameAsleep.equals(frameAwake), "the tabletop shouldn't move when sleeping");
            VRScreenshot.takeLeftEye(context, "tabletop_sleeping");
            singleplayer.getServer().runOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().stopSleepInBed(true, true));
            context.waitFor(mc -> !mc.player.isSleeping(), 100);
            singleplayer.getServer().runCommand("time set noon");

            // riding a boat, the world follows the boat
            run(singleplayer, "summon minecraft:oak_boat %d %d %d", feet.getX() - 3, feet.getY(), feet.getZ());
            singleplayer.getServer().runCommand("ride @p mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
            context.waitFor(mc -> mc.player.isPassenger(), 100);
            context.waitTicks(10);
            check(context.computeOnClient(mc -> TabletopManager.isActive()), "tabletop should stay on while riding");
            for (int step = 1; step <= 4; step++) {
                run(singleplayer, "tp @e[type=minecraft:oak_boat] %d %d %d", feet.getX() - 3 - 5 * step,
                    feet.getY(), feet.getZ());
                context.waitTicks(5);
            }
            checkAvatarInside(context, "after riding");
            VRScreenshot.takeLeftEye(context, "tabletop_riding");
            singleplayer.getServer().runCommand("ride @p dismount");
            context.waitFor(mc -> !mc.player.isPassenger(), 100);

            // the inventory opens in tabletop mode
            context.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
            context.waitTicks(10);
            check(context.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen),
                "the inventory should open");
            VRScreenshot.takeLeftEye(context, "tabletop_inventory");
            context.runOnClient(mc -> mc.gui.setScreen(null));
            context.waitTicks(5);

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.VR);
            context.waitFor(mc -> !TabletopManager.isActive(), 100);
        }

        VRTestHelper.stopVR(context);
    }

    private static void run(TestSingleplayerContext singleplayer, String command, Object... args) {
        singleplayer.getServer().runCommand(command.formatted(args));
    }

    private static void checkAvatarInside(ClientGameTestContext context, String when) {
        ClipBox box = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getClipBox());
        Vec3 avatar = context.computeOnClient(mc -> mc.player.position());
        LOGGER.info("Vivecraft gametest: {}, avatar {} tabletop {}", when, avatar, box);
        check(box.contains(avatar), when + ": the avatar at " + avatar + " should be inside the tabletop " + box);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
