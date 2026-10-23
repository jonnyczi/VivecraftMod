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
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.MCVR;
import org.vivecraft.client_vr.provider.nullvr.NullVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.ViewMode;
import org.vivecraft.client_vr.tabletop.math.TabletopFrame;

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
