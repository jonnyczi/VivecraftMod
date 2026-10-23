package org.vivecraft.mixin.client_vr.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.render.TabletopCulling;

import java.util.List;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphVRMixin {

    @WrapOperation(method = "lambda$addSectionsInFrustum$0", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private static boolean vivecraft$onlyTabletopSections(List<Object> list, Object section, Operation<Boolean> original)
    {
        // sections outside the tabletop frame don't need to be drawn
        if (TabletopManager.isActive() && section instanceof SectionRenderDispatcher.RenderSection renderSection) {
            long node = renderSection.getSectionNode();
            if (!TabletopCulling.isSectionVisible(SectionPos.x(node), SectionPos.y(node), SectionPos.z(node))) {
                return false;
            }
        }
        return original.call(list, section);
    }
}
