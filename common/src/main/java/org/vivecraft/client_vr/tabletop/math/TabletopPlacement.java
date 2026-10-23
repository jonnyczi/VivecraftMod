package org.vivecraft.client_vr.tabletop.math;

import java.util.Arrays;

/**
 * chooses which height of the world is shown at the base of the tabletop
 */
public class TabletopPlacement {

    // free space in blocks that the avatar keeps to the bottom and top of the tabletop
    public static final double FEET_ROOM = 1.0;
    public static final double HEAD_ROOM = 1.0;
    // height of the avatar in blocks
    public static final double AVATAR_HEIGHT = 1.8;
    // when the avatar is this far below the surface it is underground
    public static final double UNDERGROUND_DEPTH = 4.0;
    // underground, the top of the tabletop is this far above the feet, so the cave is cut open right above the avatar
    public static final double CROSS_SECTION_ABOVE_FEET = 4.0;

    /**
     * outside the ground of the tabletop is at the usual surface height around the avatar, so hills and valleys both
     * show up. underground the top of the tabletop is just above the avatar, showing a cross-section of the cave.
     * the avatar always stays inside the tabletop
     *
     * @param feetY          height of the avatar's feet
     * @param surfaceAtFeet  surface height at the avatar's position
     * @param surfaceHeights surface heights sampled around the avatar, inside the tabletop area, may be empty
     * @param heightAbove    how many blocks the tabletop shows above its base
     * @param depthBelow     how many blocks the tabletop shows below its base
     * @return the world height that is shown at the base of the tabletop
     */
    public static double baseHeight(
        double feetY, double surfaceAtFeet, int[] surfaceHeights, double heightAbove, double depthBelow)
    {
        double base;
        if (feetY < surfaceAtFeet - UNDERGROUND_DEPTH) {
            base = feetY + CROSS_SECTION_ABOVE_FEET - heightAbove;
        } else if (surfaceHeights.length > 0) {
            base = median(surfaceHeights);
        } else {
            base = feetY;
        }
        // keep the whole avatar inside, the lowest base still has room above the head, the highest room below the feet
        double lowest = feetY + AVATAR_HEIGHT + HEAD_ROOM - heightAbove;
        double highest = feetY + depthBelow - FEET_ROOM;
        if (lowest > highest) {
            // the tabletop is too flat for the avatar, keep its feet just inside
            return highest;
        }
        return Math.max(lowest, Math.min(highest, base));
    }

    private static double median(int[] values) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) * 0.5;
    }
}
