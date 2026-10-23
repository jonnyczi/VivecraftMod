package org.vivecraft.client_vr.tabletop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.tabletop.TabletopManager;
import org.vivecraft.client_vr.tabletop.math.ClipBox;
import org.vivecraft.client_vr.tabletop.math.CutFacePlanner.Side;
import org.vivecraft.client_vr.tabletop.math.CutFacePlanner;

import java.util.ArrayList;
import java.util.List;

/**
 * draws the block faces on the sides of the tabletop clip box, so the world looks solid where it is cut off
 * instead of hollow, like a cross-section
 */
public class TabletopCutFaces {
    private static final Minecraft MC = Minecraft.getInstance();

    // the cut faces are rebuilt at least this often, to pick up block changes
    private static final int REBUILD_INTERVAL_TICKS = 10;

    /**
     * one block face on a side of the box
     *
     * @param side  side of the box
     * @param x     block x
     * @param y     block y
     * @param z     block z
     * @param quad  model quad of the block facing out of the box
     * @param color tint and shade of the quad
     */
    private record CutFace(Side side, int x, int y, int z, BakedQuad quad, int color) {}

    // x, y, z relative to the anchor, u, v
    private static final int FLOATS_PER_VERTEX = 5;

    private static final List<CutFace> FACES = new ArrayList<>();
    // what the cached faces were built for
    private static String BUILT_FOR = "";
    private static long BUILT_AT = Long.MIN_VALUE;

    // clipped vertices of the faces, only recalculated when the box moves, not for every eye
    private static float[] VERTICES = new float[0];
    private static int[] VERTEX_COLORS = new int[0];
    private static int VERTEX_COUNT = 0;
    private static ClipBox VERTICES_FOR = null;
    // block position the vertices are relative to, so they stay precise as floats
    private static int ANCHOR_X;
    private static int ANCHOR_Y;
    private static int ANCHOR_Z;

    /**
     * submits the cut faces of the current tabletop clip box
     *
     * @param output    collector to submit to
     * @param poseStack pose stack at the camera
     * @param camera    camera position for the current pass
     */
    /**
     * forgets the built cut faces, when leaving tabletop mode
     */
    public static void reset() {
        FACES.clear();
        BUILT_FOR = "";
        BUILT_AT = Long.MIN_VALUE;
        VERTICES = new float[0];
        VERTEX_COLORS = new int[0];
        VERTEX_COUNT = 0;
        VERTICES_FOR = null;
    }

    public static void submit(SubmitNodeCollector output, PoseStack poseStack, Vec3 camera) {
        if (!TabletopManager.isActive() || MC.level == null) {
            return;
        }
        ClipBox box = ClientDataHolderVR.getInstance().tabletop.getClipBox();
        if (rebuildIfNeeded(MC.level, box) || !box.equals(VERTICES_FOR)) {
            buildVertices(box);
        }
        if (VERTEX_COUNT == 0) {
            return;
        }

        float offsetX = (float) (ANCHOR_X - camera.x);
        float offsetY = (float) (ANCHOR_Y - camera.y);
        float offsetZ = (float) (ANCHOR_Z - camera.z);
        output.submitCustomGeometry(poseStack, RenderTypes.cutoutMovingBlock(), (pose, consumer) -> {
            for (int i = 0; i < VERTEX_COUNT; i++) {
                int index = i * FLOATS_PER_VERTEX;
                consumer.addVertex(pose,
                        VERTICES[index] + offsetX, VERTICES[index + 1] + offsetY, VERTICES[index + 2] + offsetZ)
                    .setColor(VERTEX_COLORS[i])
                    .setUv(VERTICES[index + 3], VERTICES[index + 4])
                    .setLight(LightCoordsUtil.FULL_BRIGHT);
            }
        });
    }

    /**
     * collects the faces again when the box moved to other blocks, or after some time
     *
     * @return if the faces were collected again
     */
    private static boolean rebuildIfNeeded(ClientLevel level, ClipBox box) {
        StringBuilder key = new StringBuilder().append(System.identityHashCode(level));
        for (Side side : Side.values()) {
            key.append(',').append(CutFacePlanner.sideBlock(box, side));
        }
        long time = level.getGameTime();
        if (key.toString().equals(BUILT_FOR) && time - BUILT_AT < REBUILD_INTERVAL_TICKS && time >= BUILT_AT) {
            return false;
        }
        BUILT_FOR = key.toString();
        BUILT_AT = time;

        FACES.clear();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        RandomSource random = RandomSource.create();
        List<BlockStateModelPart> parts = new ArrayList<>();
        for (Side side : Side.values()) {
            Direction direction = toDirection(side);
            CutFacePlanner.forEachCutFace(box, side,
                (x, y, z) -> level.getBlockState(pos.set(x, y, z)).isSolidRender(),
                (x, y, z) -> {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    parts.clear();
                    random.setSeed(state.getSeed(pos));
                    MC.getModelManager().getBlockStateModelSet().get(state).collectParts(random, parts);
                    for (BlockStateModelPart part : parts) {
                        for (BakedQuad quad : part.getQuads(direction)) {
                            FACES.add(new CutFace(side, x, y, z, quad, faceColor(level, pos, state, quad, side)));
                        }
                    }
                });
        }
        return true;
    }

    /**
     * clips the collected faces to the box and stores their vertices
     */
    private static void buildVertices(ClipBox box) {
        VERTICES_FOR = box;
        ANCHOR_X = (int) Math.floor(box.minX());
        ANCHOR_Y = (int) Math.floor(box.minY());
        ANCHOR_Z = (int) Math.floor(box.minZ());
        int maxVertices = FACES.size() * 4;
        if (VERTEX_COLORS.length < maxVertices) {
            VERTICES = new float[maxVertices * FLOATS_PER_VERTEX];
            VERTEX_COLORS = new int[maxVertices];
        }
        VERTEX_COUNT = 0;
        for (CutFace face : FACES) {
            addFace(face, box);
        }
    }

    /**
     * @return tint of the quad, darkened like vanilla shades block sides
     */
    private static int faceColor(ClientLevel level, BlockPos pos, BlockState state, BakedQuad quad, Side side) {
        int color = 0xFFFFFFFF;
        if (quad.materialInfo().isTinted()) {
            BlockTintSource tint = MC.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
            if (tint != null) {
                color = ARGB.opaque(tint.colorInWorld(state, level, pos));
            }
        }
        if (quad.materialInfo().shade()) {
            color = ARGB.scaleRGB(color, switch (side) {
                case UP -> 1.0F;
                case DOWN -> 0.5F;
                case NORTH, SOUTH -> 0.8F;
                case WEST, EAST -> 0.6F;
            });
        }
        return color;
    }

    /**
     * adds the part of the block face that is inside the box, on the box side
     */
    private static void addFace(CutFace face, ClipBox box) {
        int aAxis = axisA(face.side);
        int bAxis = axisB(face.side);
        // corners of the face in block local coordinates, along the two in plane axes a and b of the side
        double aMin = clamp01(boxMin(box, aAxis) - blockCoord(face, aAxis));
        double aMax = clamp01(boxMax(box, aAxis) - blockCoord(face, aAxis));
        double bMin = clamp01(boxMin(box, bAxis) - blockCoord(face, bAxis));
        double bMax = clamp01(boxMax(box, bAxis) - blockCoord(face, bAxis));
        if (aMax <= aMin || bMax <= bMin) {
            return;
        }
        double plane = switch (face.side) {
            case WEST -> box.minX();
            case EAST -> box.maxX();
            case DOWN -> box.minY();
            case UP -> box.maxY();
            case NORTH -> box.minZ();
            case SOUTH -> box.maxZ();
        };

        // counter clockwise when seen from outside the box
        addVertex(face, plane, aMin, bMin);
        addVertex(face, plane, aMax, bMin);
        addVertex(face, plane, aMax, bMax);
        addVertex(face, plane, aMin, bMax);
    }

    private static void addVertex(CutFace face, double plane, double a, double b) {
        int normalAxis = normalAxis(face.side);
        int aAxis = axisA(face.side);
        int bAxis = axisB(face.side);

        // texture coordinates, interpolated from the quad corners, so rotated textures stay correct
        float u = 0F;
        float v = 0F;
        BakedQuad quad = face.quad;
        for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
            Vector3fc corner = quad.position(i);
            boolean cornerA = component(corner, aAxis) > 0.5F;
            boolean cornerB = component(corner, bAxis) > 0.5F;
            float weight = (float) ((cornerA ? a : 1.0 - a) * (cornerB ? b : 1.0 - b));
            long uv = quad.packedUV(i);
            u += weight * UVPair.unpackU(uv);
            v += weight * UVPair.unpackV(uv);
        }

        int index = VERTEX_COUNT * FLOATS_PER_VERTEX;
        VERTICES[index + normalAxis] = (float) (plane - anchor(normalAxis));
        VERTICES[index + aAxis] = (float) (blockCoord(face, aAxis) + a - anchor(aAxis));
        VERTICES[index + bAxis] = (float) (blockCoord(face, bAxis) + b - anchor(bAxis));
        VERTICES[index + 3] = u;
        VERTICES[index + 4] = v;
        VERTEX_COLORS[VERTEX_COUNT] = face.color;
        VERTEX_COUNT++;
    }

    private static int anchor(int axis) {
        return axis == 0 ? ANCHOR_X : axis == 1 ? ANCHOR_Y : ANCHOR_Z;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static float component(Vector3fc vec, int axis) {
        return axis == 0 ? vec.x() : axis == 1 ? vec.y() : vec.z();
    }

    private static int blockCoord(CutFace face, int axis) {
        return axis == 0 ? face.x : axis == 1 ? face.y : face.z;
    }

    private static double boxMin(ClipBox box, int axis) {
        return axis == 0 ? box.minX() : axis == 1 ? box.minY() : box.minZ();
    }

    private static double boxMax(ClipBox box, int axis) {
        return axis == 0 ? box.maxX() : axis == 1 ? box.maxY() : box.maxZ();
    }

    /**
     * @return 0 for x, 1 for y, 2 for z
     */
    private static int normalAxis(Side side) {
        return switch (side) {
            case WEST, EAST -> 0;
            case DOWN, UP -> 1;
            case NORTH, SOUTH -> 2;
        };
    }

    /**
     * first in plane axis, chosen so that {@code a x b} points out of the box
     */
    private static int axisA(Side side) {
        return switch (side) {
            case EAST -> 1;
            case WEST -> 2;
            case UP -> 2;
            case DOWN -> 0;
            case SOUTH -> 0;
            case NORTH -> 1;
        };
    }

    /**
     * second in plane axis, chosen so that {@code a x b} points out of the box
     */
    private static int axisB(Side side) {
        return switch (side) {
            case EAST -> 2;
            case WEST -> 1;
            case UP -> 0;
            case DOWN -> 2;
            case SOUTH -> 1;
            case NORTH -> 0;
        };
    }

    private static Direction toDirection(Side side) {
        return switch (side) {
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
        };
    }
}
