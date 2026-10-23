package org.vivecraft.mixin.world.item;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.server.ServerVRPlayers;
import org.vivecraft.server.ServerVivePlayer;

import javax.annotation.Nullable;

@Mixin(Item.class)
public class ItemMixin {
    // these are used for bucket use/boat placement
    @WrapOperation(method = "getPlayerPOVHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 vivecraft$modifyAimPos(Player player, Operation<Vec3> original) {
        ServerVivePlayer vivePlayer = vivecraft$getAimingPlayer(player);
        if (vivePlayer != null) {
            return vivePlayer.getAimPos(false);
        } else if (player.isLocalPlayer() && VRState.VR_RUNNING) {
            Vec3 pos = ClientNetworking.getActiveAimPos();
            if (pos != null) {
                return pos;
            }
        }
        return original.call(player);
    }

    @WrapOperation(method = "getPlayerPOVHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 vivecraft$modifyAimDir(Player player, float xRot, float yRot, Operation<Vec3> original) {
        ServerVivePlayer vivePlayer = vivecraft$getAimingPlayer(player);
        if (vivePlayer != null) {
            return vivePlayer.getAimDir(false);
        } else if (player.isLocalPlayer() && VRState.VR_RUNNING) {
            return new Vec3(ClientNetworking.getActiveAimDir());
        }
        return original.call(player, xRot, yRot);
    }

    @ModifyReturnValue(method = "getPlayerPOVHitResult", at = @At("RETURN"))
    private static BlockHitResult vivecraft$tabletopReach(
        BlockHitResult hitResult, @Local(argsOnly = true) Player player)
    {
        // in tabletop mode the aim doesn't start at the player, only what the player can reach can be used
        if (hitResult.getType() == HitResult.Type.BLOCK && vivecraft$isTabletop(player) &&
            !player.isWithinBlockInteractionRange(hitResult.getBlockPos(), 1.0))
        {
            return BlockHitResult.miss(hitResult.getLocation(), hitResult.getDirection(), hitResult.getBlockPos());
        }
        return hitResult;
    }

    /**
     * @return the server player data of {@code player} if the aim comes from the client, {@code null} otherwise
     */
    @Unique
    @Nullable
    private static ServerVivePlayer vivecraft$getAimingPlayer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(serverPlayer);
        if (vivePlayer == null) {
            return null;
        }
        // tabletop players aim with the laser, even when they aren't in vr on the server
        boolean tabletopAim = vivePlayer.isTabletop() && vivePlayer.aimPosOverride != null &&
            vivePlayer.aimDirOverride != null;
        return vivePlayer.isVR() || tabletopAim ? vivePlayer : null;
    }

    @Unique
    private static boolean vivecraft$isTabletop(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ServerVivePlayer vivePlayer = ServerVRPlayers.getVivePlayer(serverPlayer);
            return vivePlayer != null && vivePlayer.isTabletop();
        } else {
            return player.isLocalPlayer() && VRState.VR_RUNNING && TabletopManager.isActive();
        }
    }
}
