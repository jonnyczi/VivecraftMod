package org.vivecraft.client_vr.tabletop.render;

import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.render.ubos.TabletopClipUBO;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.math.ClipBox;
import org.vivecraft.client_xr.render_pass.RenderPassType;

/**
 * clips the terrain to the tabletop frame, by swapping the chunk pipelines for copies that discard everything outside
 */
public class TabletopClip {

    // terrain is clipped slightly inside the box, so that the cut faces on the box sides are not hidden by it
    public static final double TERRAIN_INSET = 1.0E-3;

    private static final BindGroupLayout CLIP_BIND_GROUP = BindGroupLayout.builder()
        .withUniform(TabletopClipUBO.UBO_NAME, UniformType.UNIFORM_BUFFER).build();

    private static final RenderPipeline.Snippet TERRAIN_CLIP_SNIPPET = RenderPipeline.builder(
            RenderPipelines.TERRAIN_SNIPPET)
        .withBindGroupLayout(CLIP_BIND_GROUP)
        .withVertexShader(Identifier.fromNamespaceAndPath("vivecraft", "core/terrain_clip"))
        .withFragmentShader(Identifier.fromNamespaceAndPath("vivecraft", "core/terrain_clip"))
        .buildSnippet();

    public static final RenderPipeline SOLID_TERRAIN_CLIP = RenderPipeline.builder(TERRAIN_CLIP_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("vivecraft", "pipeline/tabletop_solid_terrain"))
        .build();
    public static final RenderPipeline CUTOUT_TERRAIN_CLIP = RenderPipeline.builder(TERRAIN_CLIP_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("vivecraft", "pipeline/tabletop_cutout_terrain"))
        .withShaderDefine("ALPHA_CUTOUT", 0.5F)
        .build();
    public static final RenderPipeline TRANSLUCENT_TERRAIN_CLIP = RenderPipeline.builder(TERRAIN_CLIP_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("vivecraft", "pipeline/tabletop_translucent_terrain"))
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
        .build();

    private static TabletopClipUBO UBO;
    // if the clip ubo has valid data for the current frame
    private static boolean ACTIVE = false;

    /**
     * writes the clip box for this frame, needs to be called once per frame before rendering
     */
    public static void updateFrame() {
        ACTIVE = TabletopManager.isActive();
        if (ACTIVE) {
            if (UBO == null) {
                UBO = new TabletopClipUBO();
            }
            ClipBox box = ClientDataHolderVR.getInstance().tabletop.getClipBox();
            UBO.updateBuffer(box.shrink(TERRAIN_INSET));
        }
    }

    /**
     * needs to be called after all passes of a frame are rendered
     */
    public static void endFrame() {
        if (ACTIVE && UBO != null) {
            UBO.endFrame();
        }
    }

    /**
     * @return if terrain should be clipped this frame
     */
    public static boolean isActive() {
        // desktop rendering is never clipped, also when VR got turned off in tabletop mode
        return ACTIVE && !RenderPassType.isVanilla();
    }

    /**
     * @param layer chunk layer that is about to be rendered
     * @return the clipping variant of the pipeline for that layer
     */
    public static RenderPipeline getClipPipeline(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> SOLID_TERRAIN_CLIP;
            case CUTOUT -> CUTOUT_TERRAIN_CLIP;
            case TRANSLUCENT -> TRANSLUCENT_TERRAIN_CLIP;
        };
    }

    /**
     * binds the clip box to the given render pass
     */
    public static void bindUniform(RenderPass renderPass) {
        renderPass.setUniform(TabletopClipUBO.UBO_NAME, UBO.getBuffer());
    }

    /**
     * frees the clip box buffer
     */
    public static void close() {
        if (UBO != null) {
            UBO.close();
            UBO = null;
        }
        ACTIVE = false;
    }
}
