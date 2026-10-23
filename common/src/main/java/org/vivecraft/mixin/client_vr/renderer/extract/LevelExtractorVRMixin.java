package org.vivecraft.mixin.client_vr.renderer.extract;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.extensions.LevelRenderStateExtension;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.gameplay.interact_modules.BlockInteractionModule;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.render.TabletopCulling;
import org.vivecraft.client_xr.render_pass.RenderPassType;

@Mixin(LevelExtractor.class)
public class LevelExtractorVRMixin {

    @Shadow
    private @Nullable ClientLevel level;

    @ModifyExpressionValue(method = "isEntityVisible", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    private boolean vivecraft$dontCullPlayer(boolean doRender, @Local(argsOnly = true) Entity entity) {
        return doRender ||
            (ClientDataHolderVR.getInstance().vrSettings.shouldRenderSelf && entity == Minecraft.getInstance().player);
    }

    @ModifyReturnValue(method = "isEntityVisible", at = @At("RETURN"))
    private boolean vivecraft$tabletopEntityCulling(boolean visible, @Local(argsOnly = true) Entity entity,
        @Local(argsOnly = true) Frustum frustum)
    {
        // the camera is far away in tabletop mode, so cull by the clip box instead of the distance
        return TabletopManager.isActive() ? TabletopCulling.isEntityVisible(entity, frustum) : visible;
    }

    @WrapOperation(method = "extractVisibleBlockEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;"))
    private BlockEntityRenderState vivecraft$tabletopBlockEntityCulling(
        BlockEntityRenderDispatcher instance, BlockEntity blockEntity, float partialTicks,
        ModelFeatureRenderer.CrumblingOverlay breakProgress, boolean isGloballyRendered,
        Operation<BlockEntityRenderState> original)
    {
        if (TabletopManager.isActive() && !TabletopCulling.isBlockVisible(blockEntity.getBlockPos())) {
            return null;
        }
        return original.call(instance, blockEntity, partialTicks, breakProgress, isGloballyRendered);
    }

    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"))
    private boolean vivecraft$noPlayerWhenSleeping(boolean isSleeping) {
        // no self render, we don't want an out-of-body experience
        return isSleeping && RenderPassType.isVanilla();
    }

    @Inject(method = "extractBlockOutline", at = @At("HEAD"))
    private void vivecraft$extractInteractOutline(
        CallbackInfo ci, @Local(argsOnly = true) Camera camera,
        @Local(argsOnly = true) LevelRenderState levelRenderState)
    {
        if (RenderPassType.isVanilla()) return;

        BlockInteractionModule blockModule = ClientDataHolderVR.getInstance().blockModule;
        for (int c = 0; c < 2; c++) {
            if (blockModule.isActive(c)) {
                BlockPos blockPos = blockModule.inBlockHit[c] != null ? blockModule.inBlockHit[c].getBlockPos() :
                    BlockPos.containing(
                        ClientDataHolderVR.getInstance().vrPlayer.vrdata_world_render.getController(c).getPosition());
                BlockState blockState = this.level.getBlockState(blockPos);
                BlockStateModel blockStateModel = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                    .get(blockState);
                ((LevelRenderStateExtension) levelRenderState).vivecraft$setInteractOutlineState(c,
                    new BlockOutlineRenderState(blockPos,
                        blockStateModel.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT), false,
                        blockState.getShape(this.level, blockPos, CollisionContext.of(camera.entity()))));
            } else {
                ((LevelRenderStateExtension) levelRenderState).vivecraft$setInteractOutlineState(c, null);
            }
        }
    }
}
