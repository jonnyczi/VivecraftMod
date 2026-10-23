package org.vivecraft.client_vr.tabletop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.vivecraft.client_vr.tabletop.math.ClipBox;

import javax.annotation.Nullable;

/**
 * finds what the controller points at in tabletop mode.
 * the vanilla pick only reaches a few blocks in front of the controller, which is far away from the world in tabletop
 * mode, so this follows the ray through the visible part of the world instead, and only accepts things that the player
 * can reach from where it stands
 */
public class TabletopPick {

    /**
     * what the last pick found, used to draw the laser
     */
    public enum Result {
        // the ray doesn't go through the tabletop
        NONE,
        // the ray goes through the tabletop without hitting anything
        EMPTY,
        // the ray hits something, that the player can't reach
        OUT_OF_REACH,
        // the ray hits something the player can interact with
        HIT
    }

    private static Result LAST_RESULT = Result.NONE;
    // where the ray ended on the last pick
    @Nullable
    private static Vec3 LAST_END = null;

    /**
     * @param player the local player, that interacts with the world
     * @param from   controller position in the world
     * @param dir    controller direction in the world
     * @param box    visible part of the world
     * @return the block or entity the controller points at, or a miss
     */
    public static HitResult pick(LocalPlayer player, Vec3 from, Vec3 dir, ClipBox box) {
        double[] range = box.intersectRay(from, dir);
        if (range == null) {
            return result(Result.NONE, null, miss(from.add(dir), dir));
        }
        Vec3 start = from.add(dir.scale(range[0]));
        Vec3 end = from.add(dir.scale(range[1]));

        // a ray that enters through a cut face starts inside the cut block, which counts as a hit on that block
        BlockHitResult blockHit = player.level().clip(
            new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDistanceSq = blockHit.getType() == HitResult.Type.MISS ? start.distanceToSqr(end) :
            blockHit.getLocation().distanceToSqr(start);

        // only check for entities in front of the block
        Vec3 entityEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB searchBox = new AABB(start, entityEnd).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, start, entityEnd, searchBox,
            EntitySelector.CAN_BE_PICKED, blockDistanceSq);

        if (entityHit != null && entityHit.getLocation().distanceToSqr(start) < blockDistanceSq) {
            return player.isWithinEntityInteractionRange(entityHit.getEntity(), 0.0) ?
                result(Result.HIT, entityHit.getLocation(), entityHit) :
                result(Result.OUT_OF_REACH, entityHit.getLocation(), miss(entityHit.getLocation(), dir));
        }
        if (blockHit.getType() == HitResult.Type.MISS) {
            return result(Result.EMPTY, end, miss(end, dir));
        }
        if (!player.isWithinBlockInteractionRange(blockHit.getBlockPos(), 0.0)) {
            // too far away from the player to interact with
            return result(Result.OUT_OF_REACH, blockHit.getLocation(), miss(blockHit.getLocation(), dir));
        }
        return result(Result.HIT, blockHit.getLocation(), blockHit);
    }

    /**
     * while a block is being broken, the target stays on it, so that the hand doesn't need to stay perfectly still
     *
     * @param mc        the minecraft instance
     * @param hitResult the current hit result
     * @return if {@code hitResult} should be kept instead of picking again
     */
    public static boolean shouldKeepTarget(Minecraft mc, @Nullable HitResult hitResult) {
        return mc.options.keyAttack.isDown() && mc.gameMode != null && mc.gameMode.isDestroying() &&
            hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK &&
            mc.level != null && !mc.level.getBlockState(blockHit.getBlockPos()).isAir();
    }

    /**
     * @return if {@code current} targets a different block or entity than {@code previous}
     */
    public static boolean isNewTarget(@Nullable HitResult previous, HitResult current) {
        if (current instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
            return !(previous instanceof BlockHitResult previousBlock &&
                previousBlock.getType() == HitResult.Type.BLOCK &&
                previousBlock.getBlockPos().equals(block.getBlockPos()));
        } else if (current instanceof EntityHitResult entity) {
            return !(previous instanceof EntityHitResult previousEntity &&
                previousEntity.getEntity() == entity.getEntity());
        }
        return false;
    }

    /**
     * @return what the last pick found
     */
    public static Result getLastResult() {
        return LAST_RESULT;
    }

    /**
     * @return where the ray of the last pick ended, {@code null} if it didn't go through the tabletop
     */
    @Nullable
    public static Vec3 getLastEnd() {
        return LAST_END;
    }

    private static HitResult result(Result result, @Nullable Vec3 end, HitResult hitResult) {
        LAST_RESULT = result;
        LAST_END = end;
        return hitResult;
    }

    private static BlockHitResult miss(Vec3 location, Vec3 dir) {
        return BlockHitResult.miss(location, Direction.getApproximateNearest(dir.x, dir.y, dir.z),
            BlockPos.containing(location));
    }
}
