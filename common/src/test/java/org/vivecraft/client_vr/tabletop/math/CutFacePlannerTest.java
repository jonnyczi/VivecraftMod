package org.vivecraft.client_vr.tabletop.math;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.vivecraft.client_vr.tabletop.math.CutFacePlanner.Side;

import java.util.ArrayList;
import java.util.List;

public class CutFacePlannerTest {

    // solid ground up to and including y 63
    private static final CutFacePlanner.BlockQuery GROUND = (x, y, z) -> y < 64;

    @Test
    public void sideBlocksForFractionalPlanes() {
        ClipBox box = new ClipBox(0.5, 60.25, -3.5, 3.5, 70.75, 2.5);
        Assertions.assertEquals(0, CutFacePlanner.sideBlock(box, Side.WEST));
        Assertions.assertEquals(3, CutFacePlanner.sideBlock(box, Side.EAST));
        Assertions.assertEquals(60, CutFacePlanner.sideBlock(box, Side.DOWN));
        Assertions.assertEquals(70, CutFacePlanner.sideBlock(box, Side.UP));
        Assertions.assertEquals(-4, CutFacePlanner.sideBlock(box, Side.NORTH));
        Assertions.assertEquals(2, CutFacePlanner.sideBlock(box, Side.SOUTH));
    }

    @Test
    public void sideBlocksForPlanesOnBlockBorders() {
        // planes exactly on block borders, with a bit of floating point noise, should use the block inside the box
        ClipBox box = new ClipBox(9.9999999999, 60, 0.0000000001, 20.0000000001, 70, 10);
        Assertions.assertEquals(10, CutFacePlanner.sideBlock(box, Side.WEST));
        Assertions.assertEquals(19, CutFacePlanner.sideBlock(box, Side.EAST));
        Assertions.assertEquals(0, CutFacePlanner.sideBlock(box, Side.NORTH));
        Assertions.assertEquals(9, CutFacePlanner.sideBlock(box, Side.SOUTH));
    }

    @Test
    public void groundGetsCutFacesOnAllSidesBelowTheSurface() {
        // 4 x 4 blocks wide, from y 60 to 68
        ClipBox box = new ClipBox(0, 60, 0, 4, 68, 4);
        // the bottom is completely solid
        Assertions.assertEquals(16, count(box, Side.DOWN, GROUND));
        // the top is in the air
        Assertions.assertEquals(0, count(box, Side.UP, GROUND));
        // the sides are solid from y 60 to 63
        Assertions.assertEquals(16, count(box, Side.WEST, GROUND));
        Assertions.assertEquals(16, count(box, Side.SOUTH, GROUND));
    }

    @Test
    public void cutFacesAreOnTheBoundaryBlocks() {
        ClipBox box = new ClipBox(0.5, 62.5, 0, 2.5, 64, 2);
        List<int[]> faces = new ArrayList<>();
        CutFacePlanner.forEachCutFace(box, Side.WEST, GROUND, (x, y, z) -> faces.add(new int[]{x, y, z}));
        // x 0, y 62 and 63, z 0 and 1
        Assertions.assertEquals(4, faces.size());
        for (int[] face : faces) {
            Assertions.assertEquals(0, face[0]);
            Assertions.assertTrue(face[1] == 62 || face[1] == 63);
            Assertions.assertTrue(face[2] == 0 || face[2] == 1);
        }
    }

    @Test
    public void airHasNoCutFaces() {
        ClipBox box = new ClipBox(-10, 100, -10, 10, 120, 10);
        for (Side side : Side.values()) {
            Assertions.assertEquals(0, count(box, side, GROUND), side.name());
        }
    }

    private static int count(ClipBox box, Side side, CutFacePlanner.BlockQuery query) {
        return CutFacePlanner.forEachCutFace(box, side, query, (x, y, z) -> {});
    }
}
