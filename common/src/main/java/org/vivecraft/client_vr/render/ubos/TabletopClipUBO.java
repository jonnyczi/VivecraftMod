package org.vivecraft.client_vr.render.ubos;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.vivecraft.client_vr.tabletop.math.ClipBox;

/**
 * clip box for the tabletop world, relative to an integer anchor block, so it is precise anywhere in the world.
 * written once per frame and shared by all passes, since it doesn't depend on the camera
 */
public class TabletopClipUBO {
    public static final String UBO_NAME = "VivecraftClip";
    private static final int UBO_SIZE = new Std140SizeCalculator().putIVec4().putVec4().putVec4().get();
    private final MappableRingBuffer buffer;

    public TabletopClipUBO() {
        this.buffer = new MappableRingBuffer(() -> "Tabletop Clip UBO",
            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
    }

    /**
     * @param box world box to clip to
     */
    public void updateBuffer(ClipBox box) {
        int anchorX = (int) Math.floor((box.minX() + box.maxX()) * 0.5);
        int anchorY = (int) Math.floor((box.minY() + box.maxY()) * 0.5);
        int anchorZ = (int) Math.floor((box.minZ() + box.maxZ()) * 0.5);
        try (GpuBufferSlice.MappedView mappedView = this.buffer.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(mappedView.data())
                .putIVec4(anchorX, anchorY, anchorZ, 0)
                .putVec4((float) (box.minX() - anchorX), (float) (box.minY() - anchorY),
                    (float) (box.minZ() - anchorZ), 0F)
                .putVec4((float) (box.maxX() - anchorX), (float) (box.maxY() - anchorY),
                    (float) (box.maxZ() - anchorZ), 0F);
        }
    }

    public GpuBuffer getBuffer() {
        return this.buffer.currentBuffer();
    }

    public void endFrame() {
        this.buffer.rotate();
    }

    public void close() {
        this.buffer.close();
    }
}
