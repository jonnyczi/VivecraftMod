package org.vivecraft.mod_compat_vr.sodium;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.tabletop.math.ClipBox;
import org.vivecraft.client_vr.tabletop.render.TabletopClip;

import java.nio.ByteBuffer;

/**
 * clips sodium's terrain to the tabletop frame. sodium has its own chunk shaders, so those get patched when loaded,
 * and get an extra uniform block with the clip box, which works with sodium's OpenGL and Vulkan backends
 */
public class SodiumTabletopClip {

    private static final String SHADER_PATH = "shaders/blocks/block_layer_opaque";

    // vertex shader anchors
    private static final String VERTEX_OUTPUTS = "out vec2 v_TexCoord;";
    private static final String VERTEX_POSITION = "vec3 position = _vert_position + translation;";
    // fragment shader anchors
    private static final String FRAGMENT_OUTPUT = "out vec4 fragColor;";
    private static final String FRAGMENT_MAIN = "void main() {";

    public static final String UBO_NAME = "VivecraftSodiumClip";
    private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();

    /**
     * added to sodium's chunk pipelines, so the clip box can be bound
     */
    public static final BindGroupLayout CLIP_BIND_GROUP = BindGroupLayout.builder()
        .withUniform(UBO_NAME, UniformType.UNIFORM_BUFFER).build();

    private static GpuBuffer BUFFER;

    /**
     * @param location resource location of the shader file
     * @return if the given file is a sodium chunk shader
     */
    public static boolean isChunkShader(Identifier location, String extension) {
        return location.getNamespace().equals("sodium") && location.getPath().equals(SHADER_PATH + extension);
    }

    /**
     * passes the camera relative position from the vertex to the fragment shader
     *
     * @param source vertex shader source
     * @return patched source, or the unchanged source if it doesn't look like expected
     */
    public static String patchVertex(String source) {
        if (!source.contains(VERTEX_OUTPUTS) || !source.contains(VERTEX_POSITION)) {
            VRSettings.LOGGER.warn("Vivecraft: unknown sodium chunk vertex shader, tabletop clipping won't work");
            return source;
        }
        return source
            .replace(VERTEX_OUTPUTS, VERTEX_OUTPUTS + "\nout vec3 v_VivecraftClipPos;\n")
            .replace(VERTEX_POSITION, VERTEX_POSITION + "\n    v_VivecraftClipPos = position;\n");
    }

    /**
     * discards fragments outside the clip box
     *
     * @param source fragment shader source
     * @return patched source, or the unchanged source if it doesn't look like expected
     */
    public static String patchFragment(String source) {
        if (!source.contains(FRAGMENT_OUTPUT) || !source.contains(FRAGMENT_MAIN)) {
            VRSettings.LOGGER.warn("Vivecraft: unknown sodium chunk fragment shader, tabletop clipping won't work");
            return source;
        }
        return source
            .replace(FRAGMENT_OUTPUT, FRAGMENT_OUTPUT + """

                in vec3 v_VivecraftClipPos;
                layout(std140) uniform %s {
                    // camera relative clip box, w of the min is 1 when clipping is enabled
                    vec4 u_VivecraftClipMin;
                    vec4 u_VivecraftClipMax;
                };
                """.formatted(UBO_NAME))
            .replace(FRAGMENT_MAIN, FRAGMENT_MAIN + """

                    if (u_VivecraftClipMin.w > 0.5 &&
                        (any(lessThan(v_VivecraftClipPos, u_VivecraftClipMin.xyz)) ||
                            any(greaterThan(v_VivecraftClipPos, u_VivecraftClipMax.xyz))))
                    {
                        discard;
                    }
                """);
    }

    /**
     * writes the clip box relative to the camera of the current pass, needs to be called before sodium opens its
     * render pass. the write is ordered with the draws on the gpu, so one buffer works for all passes
     *
     * @param cameraX camera x position
     * @param cameraY camera y position
     * @param cameraZ camera z position
     */
    public static void upload(double cameraX, double cameraY, double cameraZ) {
        if (BUFFER == null || BUFFER.isClosed()) {
            BUFFER = RenderSystem.getDevice().createBuffer(() -> "Vivecraft Sodium Tabletop Clip",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(UBO_SIZE);
            Std140Builder builder = Std140Builder.intoBuffer(data);
            if (TabletopClip.isActive()) {
                ClipBox box = ClientDataHolderVR.getInstance().tabletop.getClipBox()
                    .shrink(TabletopClip.TERRAIN_INSET);
                builder.putVec4((float) (box.minX() - cameraX), (float) (box.minY() - cameraY),
                        (float) (box.minZ() - cameraZ), 1F)
                    .putVec4((float) (box.maxX() - cameraX), (float) (box.maxY() - cameraY),
                        (float) (box.maxZ() - cameraZ), 0F);
            } else {
                builder.putVec4(0F, 0F, 0F, 0F).putVec4(0F, 0F, 0F, 0F);
            }
            data.rewind();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(BUFFER.slice(), data);
        }
    }

    /**
     * binds the clip box to sodium's terrain render pass, the pipeline always expects it
     */
    public static void bind(RenderPass pass) {
        pass.setUniform(UBO_NAME, BUFFER);
    }
}
