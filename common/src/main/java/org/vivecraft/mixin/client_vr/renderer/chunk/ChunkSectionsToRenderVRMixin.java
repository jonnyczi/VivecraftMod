package org.vivecraft.mixin.client_vr.renderer.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_vr.tabletop.render.TabletopClip;

@Mixin(ChunkSectionsToRender.class)
public class ChunkSectionsToRenderVRMixin {

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/blaze3d/systems/RenderPass;)V"))
    private void vivecraft$bindTabletopClip(RenderPass renderPass, Operation<Void> original) {
        original.call(renderPass);
        if (TabletopClip.isActive()) {
            TabletopClip.bindUniform(renderPass);
        }
    }

    @WrapOperation(method = "renderGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;pipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
    private RenderPipeline vivecraft$tabletopClipPipeline(ChunkSectionLayer layer, Operation<RenderPipeline> original) {
        // clip the terrain to the tabletop frame
        return TabletopClip.isActive() ? TabletopClip.getClipPipeline(layer) : original.call(layer);
    }
}
