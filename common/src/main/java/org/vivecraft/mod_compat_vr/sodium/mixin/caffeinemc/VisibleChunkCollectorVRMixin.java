package org.vivecraft.mod_compat_vr.sodium.mixin.caffeinemc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.render.TabletopCulling;

/**
 * only draws sodium sections that are inside the tabletop frame
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector", remap = false)
public class VisibleChunkCollectorVRMixin {

    @Inject(method = "visit(III)V", at = @At("HEAD"), cancellable = true)
    private void vivecraft$onlyTabletopSections(int x, int y, int z, CallbackInfo ci) {
        if (TabletopManager.isActive() && !TabletopCulling.isSectionVisible(x, y, z)) {
            ci.cancel();
        }
    }
}
