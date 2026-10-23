package org.vivecraft.gametest;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.screenhandlers.GuiHandler;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;
import org.vivecraft.client_vr.provider.nullvr.NullVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.TabletopPick;
import org.vivecraft.client_vr.tabletop.ViewMode;
import org.vivecraft.client_vr.tabletop.math.TabletopFrame;
import org.vivecraft.common.utils.MathUtils;

/**
 * points a controller into the tabletop in survival, and breaks and places a block there
 */
public class TabletopInteractionClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    // in front of and below the NullVR headset, so the whole frame is visible
    private static final Vector3f FRAME_CENTER = new Vector3f(0F, 1.05F, -1.5F);

    @Override
    public void runTest(ClientGameTestContext context) {
        // standing, so the controller can be pointed independent of the mouse
        VRTestHelper.startNullVR(context, false);

        try (TestSingleplayerContext singleplayer = context.worldBuilder()
            .setUseConsistentSettings(false)
            .adjustSettings(settings -> settings.setSeed("vivecraft tabletop"))
            .create())
        {
            singleplayer.getServer().runCommand("time set noon");
            singleplayer.getServer().runCommand("gamemode survival @a");
            singleplayer.getServer().runCommand("give @a dirt 16");
            VRTestHelper.waitForWorld(context, singleplayer);

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.TABLETOP);
            context.waitFor(mc -> TabletopManager.isActive(), 100);
            context.runOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.setFrame(
                new TabletopFrame(FRAME_CENTER, 0F, 1F, 1F, 0.5F, 0.15F)));
            context.waitTicks(20);

            check(context.computeOnClient(mc -> mc.gameMode.getPlayerMode() == GameType.SURVIVAL),
                "player should be in survival");
            double serverReach = singleplayer.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE));
            check(serverReach > 60.0, "the server should give a tabletop player extra reach, got " + serverReach);

            BlockPos target = context.computeOnClient(TabletopInteractionClientGameTest::findTarget);
            LOGGER.info("Vivecraft gametest: tabletop target {} {}", target,
                context.computeOnClient(mc -> mc.level.getBlockState(target)));

            // break the top block, by pointing at its top face and holding attack.
            // setPressed instead of holdBinding, which lets go as soon as the first click is used
            pointAt(context, Vec3.atCenterOf(target).add(0, 0.5, 0));
            checkHit(context, target, Direction.UP);
            // from further away, so the laser is visible in the screenshot
            context.runOnClient(mc -> {
                Vector3f controller = new Vector3f(0.25F, 1.35F, -0.8F);
                Vector3f dir = ClientDataHolderVR.getInstance().tabletop.getMapping()
                    .worldToRoom(Vec3.atCenterOf(target).add(0, 0.5, 0)).sub(controller).normalize();
                NullVR.get().setDevicePose(MCVR.RIGHT_CONTROLLER, controller,
                    new Quaternionf().rotationTo(new Vector3f(0F, 0F, -1F), dir));
            });
            context.waitTicks(3);
            VRScreenshot.takeLeftEye(context, "tabletop_laser");
            pointAt(context, Vec3.atCenterOf(target).add(0, 0.5, 0));
            context.runOnClient(mc -> MCVR.get().getInputAction(mc.options.keyAttack).setPressed(true));
            // moving the hand while breaking keeps the target on the block
            context.waitTicks(4);
            pointAt(context, Vec3.atCenterOf(target).add(2.0, 0.5, 1.0));
            checkHit(context, target, Direction.UP);
            for (int i = 0; i < 200 && !context.computeOnClient(mc -> mc.level.getBlockState(target).isAir()); i++) {
                if (i % 20 == 0) {
                    LOGGER.info("Vivecraft gametest: breaking {}", (Object) context.computeOnClient(mc ->
                        "attack down " + mc.options.keyAttack.isDown() + ", destroying " + mc.gameMode.isDestroying() +
                            ", hit " + describe(mc.hitResult) + ", screen " + mc.gui.screen() + ", using " +
                            mc.player.isUsingItem() + ", grabbed " + mc.mouseHandler.isMouseGrabbed()));
                }
                context.waitTick();
            }
            context.runOnClient(mc -> MCVR.get().getInputAction(mc.options.keyAttack).setPressed(false));
            context.waitTicks(5);
            check(singleplayer.getServer().computeOnServer(server -> server.overworld().getBlockState(target).isAir()),
                "the server should have broken " + target);
            check(singleplayer.getServer().computeOnServer(server ->
                    server.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.SAND) > 0),
                "the broken sand should be in the inventory");
            VRScreenshot.takeLeftEye(context, "tabletop_broken");

            // place dirt back on the block below
            BlockPos below = target.below();
            pointAt(context, Vec3.atCenterOf(below).add(0, 0.5, 0));
            checkHit(context, below, Direction.UP);
            context.runOnClient(mc -> MCVR.get().getInputAction(mc.options.keyUse).holdBinding());
            context.waitTicks(2);
            context.runOnClient(mc -> MCVR.get().getInputAction(mc.options.keyUse).stopHoldingBinding(1));
            context.waitTicks(5);
            check(singleplayer.getServer().computeOnServer(server ->
                    server.overworld().getBlockState(target).is(Blocks.DIRT)),
                "the server should have placed dirt at " + target);
            VRScreenshot.takeLeftEye(context, "tabletop_placed");

            // drag the world inside the frame with the right grip, the grip doesn't switch hotbar slots
            int slot = context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
            Vec3 focusBefore = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFocus());
            float rotation = context.computeOnClient(
                mc -> ClientDataHolderVR.getInstance().tabletop.getMapping().rotation());
            float zoom = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getBlocksPerMeter());
            placeController(context, MCVR.RIGHT_CONTROLLER, new Vector3f(0.2F, 1.1F, -0.5F));
            grip(context, ControllerType.RIGHT, true);
            placeController(context, MCVR.RIGHT_CONTROLLER, new Vector3f(0.3F, 1.1F, -0.5F));
            grip(context, ControllerType.RIGHT, false);
            Vector3f worldMove = new Vector3f(0.1F * zoom, 0F, 0F).rotateY(rotation);
            Vec3 expectedFocus = focusBefore.subtract(worldMove.x, worldMove.y, worldMove.z);
            Vec3 focusAfter = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFocus());
            check(focusAfter.distanceTo(expectedFocus) < 0.2,
                "dragging should move the focus to " + expectedFocus + " but it is at " + focusAfter);
            check(context.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()) == slot,
                "the grip should not switch the hotbar slot in tabletop mode");

            // hold the tabletop with both grips, spreading the hands makes it bigger, showing the same blocks
            float sizeBefore = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFrame().sizeX());
            placeController(context, MCVR.LEFT_CONTROLLER, new Vector3f(-0.2F, 1.1F, -0.5F));
            placeController(context, MCVR.RIGHT_CONTROLLER, new Vector3f(0.2F, 1.1F, -0.5F));
            grip(context, ControllerType.LEFT, true);
            grip(context, ControllerType.RIGHT, true);
            placeController(context, MCVR.LEFT_CONTROLLER, new Vector3f(-0.4F, 1.1F, -0.5F));
            placeController(context, MCVR.RIGHT_CONTROLLER, new Vector3f(0.4F, 1.1F, -0.5F));
            grip(context, ControllerType.LEFT, false);
            grip(context, ControllerType.RIGHT, false);
            float sizeAfter = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getFrame().sizeX());
            float zoomAfter = context.computeOnClient(mc -> ClientDataHolderVR.getInstance().tabletop.getBlocksPerMeter());
            check(Math.abs(sizeAfter - sizeBefore * 2F) < 0.05F,
                "spreading the hands should double the size from " + sizeBefore + " but it is " + sizeAfter);
            check(Math.abs(zoomAfter - zoom * 0.5F) < 0.5F,
                "a bigger tabletop should show the same blocks, zoom " + zoom + " became " + zoomAfter);
            VRScreenshot.takeLeftEye(context, "tabletop_scaled");

            // touching a hotbar slot on the arm with the right hand selects it, and doesn't point at the world
            placeController(context, MCVR.RIGHT_CONTROLLER, context.computeOnClient(mc -> hotbarSlot(mc, 4)));
            check(context.computeOnClient(mc -> ClientDataHolderVR.getInstance().hotbarModule.hotbar) == 4,
                "the right hand on the hotbar should touch slot 4");
            check(context.computeOnClient(mc -> mc.hitResult.getType() == HitResult.Type.MISS &&
                    TabletopPick.getLastResult() == TabletopPick.Result.NONE),
                "while touching the hotbar the hand shouldn't point at the world");
            placeController(context, MCVR.RIGHT_CONTROLLER, new Vector3f(0.3F, 1.25F, -0.5F));

            context.runOnClient(mc -> ClientDataHolderVR.getInstance().vrSettings.viewMode = ViewMode.VR);
            context.waitFor(mc -> !TabletopManager.isActive(), 100);
            context.waitTicks(5);
            double reachAfter = singleplayer.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE));
            check(reachAfter < 6.0, "the extra reach should be removed after tabletop mode, got " + reachAfter);
        }

        VRTestHelper.stopVR(context);
    }

    /**
     * @return a full top block a few blocks away from the player, inside the tabletop
     */
    private static BlockPos findTarget(Minecraft mc) {
        BlockPos player = mc.player.blockPosition();
        for (int dx = 3; dx < 10; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = player.getX() + dx;
                int z = player.getZ() + dz;
                BlockPos top = new BlockPos(x, mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                if (mc.level.getBlockState(top).isSolidRender() && mc.level.getBlockState(top.below()).isSolidRender() &&
                    ClientDataHolderVR.getInstance().tabletop.getClipBox().contains(Vec3.atCenterOf(top)))
                {
                    return top;
                }
            }
        }
        throw new AssertionError("no full block found next to the player at " + player);
    }

    /**
     * holds the right controller straight above the given world position, and points it there.
     * from above, so that hills at the edge of the tabletop or next to the block don't block the view
     */
    private static void pointAt(ClientGameTestContext context, Vec3 world) {
        context.runOnClient(mc -> {
            Vector3f room = ClientDataHolderVR.getInstance().tabletop.getMapping().worldToRoom(world);
            Vector3f controller = room.add(0F, 0.2F, 0F, new Vector3f());
            Vector3f dir = room.sub(controller, new Vector3f()).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0F, 0F, -1F), dir);
            NullVR.get().setDevicePose(MCVR.RIGHT_CONTROLLER, controller, rotation);
        });
        context.waitTicks(3);
    }

    /**
     * @return room position of the middle of a hotbar slot, the same way the hotbar module places the slots
     */
    private static Vector3f hotbarSlot(Minecraft mc, int slot) {
        float scale = GuiHandler.GUI_SCALE_APPLIED * (float) mc.getWindow().getGuiScale() / GuiHandler.GUI_SCALE_FACTOR_MAX;
        Vector3f start = GuiHandler.GUI_OFFSET_LOCAL.add(-0.32F * scale, -0.38F * scale, 0F, new Vector3f());
        GuiHandler.GUI_RENDER_ROTATION_ROOM.transformDirection(start);
        start.add(GuiHandler.GUI_RENDER_POS_ROOM);
        Vector3f end = GuiHandler.GUI_RENDER_ROTATION_ROOM.transformDirection(MathUtils.LEFT, new Vector3f())
            .mul(0.64F * scale).add(start);
        return start.lerp(end, (slot + 0.5F) / 9F, new Vector3f());
    }

    /**
     * moves a controller to the given room position, pointing forward
     */
    private static void placeController(ClientGameTestContext context, int device, Vector3f position) {
        context.runOnClient(mc -> NullVR.get().setDevicePose(device, position, new Quaternionf()));
        context.waitTicks(3);
    }

    /**
     * presses or releases the tabletop grab on one hand
     */
    private static void grip(ClientGameTestContext context, ControllerType hand, boolean down) {
        context.runOnClient(mc -> {
            VRInputAction action = MCVR.get().getInputAction(VivecraftVRMod.INSTANCE.keyTabletopGrab);
            if (down) {
                action.setCurrentHand(hand);
                action.pressBinding();
            } else {
                action.unpressBinding(1, hand);
            }
        });
        context.waitTicks(3);
    }

    private static void checkHit(ClientGameTestContext context, BlockPos pos, Direction face) {
        HitResult hit = context.computeOnClient(mc -> mc.hitResult);
        check(hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK &&
                blockHit.getBlockPos().equals(pos) && blockHit.getDirection() == face,
            "controller should point at " + pos + " " + face + " but hits " + describe(hit));
    }

    private static String describe(HitResult hit) {
        if (hit instanceof BlockHitResult blockHit) {
            return hit.getType() + " " + blockHit.getBlockPos() + " " + blockHit.getDirection() + " at " +
                hit.getLocation();
        }
        return hit == null ? "null" : hit.getType() + " at " + hit.getLocation();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
