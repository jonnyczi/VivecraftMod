package org.vivecraft.client_vr.tabletop.math;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ZoomLimitsTest {

    @Test
    public void frameStaysInsideRenderDistance() {
        // 8 chunks render distance, 7 usable chunks = 112 blocks, 0.5m half diagonal
        Assertions.assertEquals(224F, ZoomLimits.maxBlocksPerMeter(8, 0.5F, 0F), 1.0E-3F);
        // when rendering is centered on the head, the head distance counts too
        Assertions.assertEquals(112F, ZoomLimits.maxBlocksPerMeter(8, 0.5F, 0.5F), 1.0E-3F);
    }

    @Test
    public void limitsStayInRange() {
        Assertions.assertEquals(ZoomLimits.MAX_BLOCKS_PER_METER, ZoomLimits.maxBlocksPerMeter(64, 0.1F, 0F));
        Assertions.assertEquals(ZoomLimits.MIN_BLOCKS_PER_METER, ZoomLimits.maxBlocksPerMeter(2, 5F, 5F));
        Assertions.assertEquals(ZoomLimits.MIN_BLOCKS_PER_METER, ZoomLimits.clamp(1F, 100F));
        Assertions.assertEquals(100F, ZoomLimits.clamp(300F, 100F));
    }
}
