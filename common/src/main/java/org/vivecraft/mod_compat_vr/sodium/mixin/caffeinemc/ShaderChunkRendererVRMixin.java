package org.vivecraft.mod_compat_vr.sodium.mixin.caffeinemc;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.mod_compat_vr.sodium.SodiumTabletopClip;

/**
 * adds the tabletop clip box to sodium's chunk pipelines
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public class ShaderChunkRendererVRMixin {

    @ModifyExpressionValue(method = "createShader", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderPipeline;builder([Lcom/mojang/blaze3d/pipeline/RenderPipeline$Snippet;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;"))
    private RenderPipeline.Builder vivecraft$addTabletopClip(RenderPipeline.Builder builder) {
        // only when the shaders got the clip box
        return SodiumTabletopClip.isPatched() ? builder.withBindGroupLayout(SodiumTabletopClip.CLIP_BIND_GROUP) :
            builder;
    }
}
