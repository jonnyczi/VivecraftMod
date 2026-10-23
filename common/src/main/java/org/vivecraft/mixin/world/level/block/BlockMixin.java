package org.vivecraft.mixin.world.level.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.server.TabletopDrops;

@Mixin(Block.class)
public class BlockMixin {

    @WrapOperation(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean vivecraft$tabletopDropsToInventory(Level level, Entity entity, Operation<Boolean> original) {
        if (entity instanceof ItemEntity itemEntity && TabletopDrops.giveItem(itemEntity)) {
            return true;
        }
        return original.call(level, entity);
    }

    @WrapOperation(method = "popExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void vivecraft$tabletopExperienceToPlayer(ServerLevel level, Vec3 pos, int amount, Operation<Void> original) {
        if (!TabletopDrops.giveExperience(amount)) {
            original.call(level, pos, amount);
        }
    }
}
