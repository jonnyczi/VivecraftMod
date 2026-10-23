package org.vivecraft.client_vr.tabletop.math;

/**
 * how far the tabletop world can be zoomed out, without showing parts of the world that are not loaded or rendered
 */
public class ZoomLimits {

    // biggest blocks, 25cm per block
    public static final float MIN_BLOCKS_PER_METER = 4F;
    // smallest blocks, 2.5mm per block
    public static final float MAX_BLOCKS_PER_METER = 400F;

    /**
     * the frame needs to stay inside the render distance. if chunk rendering can't be centered on the frame
     * (sodium), the distance from the head to the frame also counts, since rendering is centered on the camera
     *
     * @param renderDistanceChunks render distance in chunks
     * @param frameHalfDiagonal    distance from the frame center to a corner, in meters
     * @param headDistance         distance from the head to the frame center, in meters, 0 if rendering is centered on the frame
     * @return the highest allowed blocks per meter
     */
    public static float maxBlocksPerMeter(int renderDistanceChunks, float frameHalfDiagonal, float headDistance) {
        // the outermost chunk is often not rendered yet
        float usableBlocks = Math.max(1, renderDistanceChunks - 1) * 16F;
        float limit = usableBlocks / Math.max(0.01F, frameHalfDiagonal + headDistance);
        return Math.max(MIN_BLOCKS_PER_METER, Math.min(MAX_BLOCKS_PER_METER, limit));
    }

    /**
     * @return {@code blocksPerMeter} limited to the allowed range
     */
    public static float clamp(float blocksPerMeter, float maxBlocksPerMeter) {
        return Math.max(MIN_BLOCKS_PER_METER, Math.min(maxBlocksPerMeter, blocksPerMeter));
    }
}
