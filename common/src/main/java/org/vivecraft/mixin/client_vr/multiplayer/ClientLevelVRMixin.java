package org.vivecraft.mixin.client_vr.multiplayer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.render.TabletopCulling;

@Mixin(ClientLevel.class)
public class ClientLevelVRMixin {

    @Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
    private void vivecraft$noParticlesOutsideTabletop(
        ParticleOptions particle, boolean overrideLimiter, boolean alwaysShowParticles, double x, double y, double z,
        double xd, double yd, double zd, CallbackInfo ci)
    {
        if (TabletopManager.isActive() && !TabletopCulling.isParticleVisible(x, y, z)) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "doAddParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 vivecraft$tabletopParticleDistance(Vec3 camera) {
        // the camera is far away from the world in tabletop mode
        return TabletopCulling.particleDistanceOrigin(camera);
    }
}
