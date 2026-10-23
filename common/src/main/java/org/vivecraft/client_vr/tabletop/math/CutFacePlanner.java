package org.vivecraft.client_vr.tabletop.math;

/**
 * finds the blocks that need a cut face where the clip box slices through the world.
 * the chunk mesher hides faces between two solid blocks, so without these the world would look hollow at the box edges
 */
public class CutFacePlanner {

    // a plane closer than this to a block border counts as being on that border, against floating point noise
    public static final double EPSILON = 1.0E-4;

    /**
     * the six sides of the clip box, each face points out of the box
     */
    public enum Side {
        WEST, EAST, DOWN, UP, NORTH, SOUTH
    }

    /**
     * query for the world blocks
     */
    @FunctionalInterface
    public interface BlockQuery {
        /**
         * @return if the block at the position is a full, opaque cube, those get a cut face
         */
        boolean isSolidCube(int x, int y, int z);
    }

    @FunctionalInterface
    public interface CutFaceConsumer {
        void accept(int x, int y, int z);
    }

    /**
     * the block coordinate along the side axis, of the blocks that the side passes through
     *
     * @return x for WEST/EAST, y for DOWN/UP, z for NORTH/SOUTH
     */
    public static int sideBlock(ClipBox box, Side side) {
        return switch (side) {
            case WEST -> lowBlock(box.minX());
            case EAST -> highBlock(box.maxX());
            case DOWN -> lowBlock(box.minY());
            case UP -> highBlock(box.maxY());
            case NORTH -> lowBlock(box.minZ());
            case SOUTH -> highBlock(box.maxZ());
        };
    }

    /**
     * calls {@code consumer} for every block on the given side of the box that needs a cut face
     *
     * @return number of cut faces
     */
    public static int forEachCutFace(ClipBox box, Side side, BlockQuery query, CutFaceConsumer consumer) {
        int minX = lowBlock(box.minX());
        int maxX = highBlock(box.maxX());
        int minY = lowBlock(box.minY());
        int maxY = highBlock(box.maxY());
        int minZ = lowBlock(box.minZ());
        int maxZ = highBlock(box.maxZ());

        int fixed = sideBlock(box, side);
        switch (side) {
            case WEST, EAST -> {
                minX = fixed;
                maxX = fixed;
            }
            case DOWN, UP -> {
                minY = fixed;
                maxY = fixed;
            }
            case NORTH, SOUTH -> {
                minZ = fixed;
                maxZ = fixed;
            }
        }

        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (query.isSolidCube(x, y, z)) {
                        consumer.accept(x, y, z);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * @return the block that contains the lower edge of a range
     */
    private static int lowBlock(double min) {
        return (int) Math.floor(min + EPSILON);
    }

    /**
     * @return the block that contains the upper edge of a range
     */
    private static int highBlock(double max) {
        return (int) Math.ceil(max - EPSILON) - 1;
    }
}
