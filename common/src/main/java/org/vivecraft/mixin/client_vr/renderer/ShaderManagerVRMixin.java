package org.vivecraft.mixin.client_vr.renderer;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.mod_compat_vr.sodium.SodiumTabletopClip;

import java.util.Map;

@Mixin(ShaderManager.class)
public class ShaderManagerVRMixin {

    @ModifyExpressionValue(method = "loadShader", at = @At(value = "INVOKE", target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/Reader;)Ljava/lang/String;"))
    private static String vivecraft$patchSodiumChunkShader(
        String source, @Local(argsOnly = true) Identifier location,
        @Local(argsOnly = true) Map<Identifier, Resource> files)
    {
        // sodium renders chunks with its own shaders, those need the tabletop clipping too
        return SodiumTabletopClip.patch(location, source, files);
    }
}
