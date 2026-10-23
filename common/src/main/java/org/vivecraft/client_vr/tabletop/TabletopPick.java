package org.vivecraft.client_vr.tabletop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
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
    // where the ray entered the tabletop on the last pick, and its direction
    @Nullable
    private static Vec3 LAST_START = null;
    private static Vec3 LAST_DIR = new Vec3(0.0, -1.0, 0.0);

    /**
     * a ray in the world
     *
     * @param pos start of the ray
     * @param dir direction of the ray
     */
    public record Aim(Vec3 pos, Vector3f dir) {}

    /**
     * @param player the local player, that interacts with the world
     * @param from   controller position in the world
     * @param dir    controller direction in the world
     * @param box    visible part of the world
     * @return the block or entity the controller points at, or a miss
     */
    public static HitResult pick(LocalPlayer player, Vec3 from, Vec3 dir, ClipBox box) {
        double[] range = box.intersectRay(from, dir);
        LAST_DIR = dir;
        if (range == null) {
            LAST_START = null;
            return result(Result.NONE, null, miss(from.add(dir), dir));
        }
        Vec3 start = from.add(dir.scale(range[0]));
        LAST_START = start;
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
     * clears the pick, when the hand doesn't point at the world
     *
     * @param location location for the miss result
     * @return a miss
     */
    public static HitResult none(Vec3 location) {
        LAST_START = null;
        return result(Result.NONE, null, miss(location, new Vec3(0.0, -1.0, 0.0)));
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
     * items like buckets and snowballs aim with the player's view, in tabletop mode that should go from the avatar to
     * where the laser points, instead of where the avatar looks
     *
     * @param player the local player
     * @return direction from the avatar's eyes to the laser target, or the avatar's view if the laser points nowhere
     */
    public static Vector3f getUseDirection(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 target = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ?
            mc.hitResult.getLocation() : LAST_END;
        Vec3 eye = player.getEyePosition();
        if (target == null || target.distanceToSqr(eye) < 1.0E-6) {
            return player.getLookAngle().toVector3f();
        }
        return target.subtract(eye).normalize().toVector3f();
    }

    /**
     * items like buckets aim like the crosshair, in tabletop mode they follow the laser through the tabletop, the same
     * way the crosshair would
     *
     * @param player the local player
     * @return where the laser enters the tabletop and its direction, or a ray that hits nothing if the laser doesn't
     * point at the tabletop
     */
    public static Aim getLaserAim(Player player) {
        if (LAST_START == null) {
            // pointing next to the tabletop is like pointing at the sky
            return new Aim(new Vec3(player.getX(), player.level().getMaxY() + 2.0, player.getZ()),
                new Vector3f(0.0F, 1.0F, 0.0F));
        }
        return new Aim(LAST_START, LAST_DIR.normalize().toVector3f());
    }

    /**
     * forgets the last pick, when leaving tabletop mode
     */
    public static void reset() {
        LAST_RESULT = Result.NONE;
        LAST_END = null;
        LAST_START = null;
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
