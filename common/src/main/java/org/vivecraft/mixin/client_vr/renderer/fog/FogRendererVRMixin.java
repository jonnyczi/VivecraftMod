package org.vivecraft.mixin.client_vr.renderer.fog;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_vr.tabletop.TabletopManager;

@Mixin(FogRenderer.class)
public class FogRendererVRMixin {

    @ModifyExpressionValue(method = "getBuffer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;fogEnabled:Z"))
    private boolean vivecraft$noFogInTabletop(boolean fogEnabled) {
        // the tabletop world is small and the camera is outside of it, fog would only hide parts of it
        return fogEnabled && !TabletopManager.isActive();
    }
}
