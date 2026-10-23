package org.vivecraft.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.vivecraft.server.config.ServerConfig;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * in tabletop mode the broken blocks are far away from the player, so their drops and experience go straight to the
 * player instead of falling into the tiny world
 */
public class TabletopDrops {

    // player that is currently breaking a block in tabletop mode, only used on the server thread
    @Nullable
    private static ServerPlayer COLLECTOR = null;

    /**
     * runs {@code action} and gives all block drops and experience that it creates to the player, if the player is
     * in tabletop mode
     *
     * @param player player that breaks a block
     * @param action the block breaking
     * @return the result of {@code action}
     */
    public static <T> T collectFor(ServerPlayer player, Supplier<T> action) {
        ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(player);
        if (COLLECTOR != null || vivePlayer == null || !vivePlayer.isTabletop() ||
            !ServerConfig.TABLETOP_DROPS_TO_INVENTORY.get())
        {
            return action.get();
        }
        COLLECTOR = player;
        try {
            return action.get();
        } finally {
            COLLECTOR = null;
        }
    }

    /**
     * gives the item of a dropped item entity to the collecting player, what doesn't fit drops at the player
     *
     * @param entity item entity that would be added to the world
     * @return if the entity was handled, and shouldn't be added at its original position
     */
    public static boolean giveItem(ItemEntity entity) {
        ServerPlayer player = COLLECTOR;
        if (player == null) {
            return false;
        }
        ItemStack stack = entity.getItem();
        int count = stack.getCount();
        player.getInventory().add(stack);
        if (stack.getCount() < count) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS, 0.2F, (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 1.4F + 2.0F);
        }
        if (!stack.isEmpty()) {
            // inventory is full, drop the rest at the player instead
            entity.setItem(stack);
            entity.setPos(player.getX(), player.getY(), player.getZ());
            entity.setDeltaMovement(0.0, 0.0, 0.0);
            entity.setNoPickUpDelay();
            player.level().addFreshEntity(entity);
        }
        return true;
    }

    /**
     * gives experience to the collecting player
     *
     * @param amount experience points
     * @return if the experience was handled, and no orbs should be spawned
     */
    public static boolean giveExperience(int amount) {
        ServerPlayer player = COLLECTOR;
        if (player == null) {
            return false;
        }
        player.giveExperiencePoints(amount);
        return true;
    }
}
