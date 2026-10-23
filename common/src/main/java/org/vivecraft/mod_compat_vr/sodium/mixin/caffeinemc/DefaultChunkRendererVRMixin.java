package org.vivecraft.mod_compat_vr.sodium.mixin.caffeinemc;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.mod_compat_vr.sodium.SodiumTabletopClip;

/**
 * sets the tabletop clip box for sodium's chunk rendering
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public class DefaultChunkRendererVRMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void vivecraft$uploadTabletopClip(CallbackInfo ci, @Local(argsOnly = true) CameraTransform camera) {
        // needs to happen before sodium opens its render pass
        SodiumTabletopClip.upload(camera.x, camera.y, camera.z);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void vivecraft$bindTabletopClip(CallbackInfo ci, @Local RenderPass pass) {
        SodiumTabletopClip.bind(pass);
    }
}
