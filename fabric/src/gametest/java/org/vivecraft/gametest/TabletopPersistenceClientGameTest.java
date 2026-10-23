package org.vivecraft.gametest;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.ViewMode;

/**
 * loads worlds while tabletop mode is still selected from before, the toggle has to work in them
 */
public class TabletopPersistenceClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void runTest(ClientGameTestContext context) {
        VRTestHelper.startNullVR(context);
        // tabletop was selected in a previous session
        context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.TABLETOP);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            VRTestHelper.waitForWorld(context, singleplayer);
            context.waitFor(mc -> TabletopManager.isActive(), 100);
            checkFocusAtPlayer(context, "first world");

            pressToggleTabletop(context);
            context.waitTicks(5);
            logState(context, "after toggle in first world");
            check(context.computeOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode == ViewMode.VR),
                "the toggle should switch to VR");
            check(!context.computeOnClient(mc -> TabletopManager.isActive()), "tabletop should be off after the toggle");

            // back to tabletop, and leave the world like that
            pressToggleTabletop(context);
            context.waitFor(mc -> TabletopManager.isActive(), 100);
        }

        // a different world, while tabletop is still selected
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
            .adjustSettings(settings -> settings.setSeed("another world"))
            .create())
        {
            VRTestHelper.waitForWorld(context, singleplayer);
            context.waitFor(mc -> TabletopManager.isActive(), 100);
            checkFocusAtPlayer(context, "second world");

            pressToggleTabletop(context);
            context.waitTicks(5);
            logState(context, "after toggle in second world");
            check(context.computeOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode == ViewMode.VR),
                "the toggle should switch to VR in the second world");
            check(!context.computeOnClient(mc -> TabletopManager.isActive()),
                "tabletop should be off after the toggle in the second world");
        }

        VRTestHelper.stopVR(context);
    }

    /**
     * the tabletop should show the player, not where it was in a previous world
     */
    private static void checkFocusAtPlayer(ClientGameTestContext context, String when) {
        Vec3 focus = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFocus());
        Vec3 player = context.computeOnClient(mc -> mc.player.position());
        LOGGER.info("Vivecraft gametest: {} focus {} player {}", when, focus, player);
        check(focus.distanceTo(player) < 2.0, when + ": tabletop focus " + focus + " should be at the player " + player);
    }

    private static void logState(ClientGameTestContext context, String when) {
        LOGGER.info("Vivecraft gametest: {}: viewMode {}, active {}", when,
            context.computeOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode),
            context.computeOnClient(mc -> TabletopManager.isActive()));
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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
