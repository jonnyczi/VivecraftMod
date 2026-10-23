package org.vivecraft.client_vr.tabletop;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.api.client.Tracker;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client.utils.ClientUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.provider.control.ControllerType;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.tabletop.math.ClipBox;
import org.vivecraft.client_vr.tabletop.math.TabletopFrame;
import org.vivecraft.client_vr.tabletop.math.TabletopMapping;
import org.vivecraft.client_vr.tabletop.math.TabletopNavigation;
import org.vivecraft.client_vr.tabletop.math.TabletopPlacement;
import org.vivecraft.client_vr.tabletop.render.TabletopCutFaces;
import org.vivecraft.mod_compat_vr.shaders.ShadersHelper;

import java.util.Arrays;

/**
 * state of the tabletop mode, where the world is shown as a small diorama in the room.
 * the player entity stays in the world as an avatar, and the room is mapped onto the world with a fixed frame
 * instead of following the player
 */
public class TabletopManager {
    private static final Minecraft MC = Minecraft.getInstance();

    // where the frame is placed when entering, relative to the head
    private static final float FRAME_DISTANCE = 0.6F;
    private static final float FRAME_BELOW_HEAD = 0.65F;
    private static final float MIN_FRAME_HEIGHT = 0.3F;
    // the world scrolls when the avatar walks closer than this part of the width to an edge
    private static final double FOLLOW_EDGE = 0.25;
    // moving further than this in one frame is a teleport, the tabletop centers on the avatar again
    private static final double TELEPORT_DISTANCE = 8.0;
    // grid of surface samples for choosing the base height, per side
    private static final int SURFACE_SAMPLES = 9;
    // how long after entering the player is told that the server doesn't give the extra reach, in ticks
    private static final int REACH_NOTICE_DELAY = 100;

    private final ClientDataHolderVR dh;

    private boolean active;
    // ticks since entering tabletop mode
    private int activeTicks;
    private TabletopFrame frame;
    // world position that is shown at the frame center
    private Vec3 focus;
    // how often the world is rotated by 90° inside the frame
    private int quarterTurns;
    // how many blocks fit in one meter, starts at the setting and changes when scaling with both hands
    private float blocksPerMeter;

    private final TabletopGrab grab = new TabletopGrab();
    // the settings the tabletop was last made with, changing them changes the tabletop right away
    private int appliedChunks;
    private float appliedSize;
    private int appliedRenderDistance;
    // where the avatar was last frame, and in which level, to follow it
    private Vec3 lastAvatar;
    private Level lastLevel;

    // derived from the above, updated with update()
    private TabletopMapping mapping;
    private ClipBox clipBox;

    public TabletopManager(ClientDataHolderVR dh) {
        this.dh = dh;
    }

    /**
     * @return if tabletop mode is currently active
     */
    public static boolean isActive() {
        TabletopManager manager = ClientDataHolderVR.getInstance().tabletop;
        return VRState.VR_RUNNING && manager != null && manager.active;
    }

    /**
     * enters or leaves tabletop mode depending on the setting, needs to be called before the VRPlayer tick
     */
    public void tick() {
        boolean wanted = this.dh.vrSettings.viewMode == ViewMode.TABLETOP && VRState.VR_RUNNING &&
            MC.player != null && !MethodHolder.isInMenuRoom();
        if (wanted && this.blockedByShaders()) {
            wanted = false;
        }
        if (wanted != this.active) {
            if (wanted) {
                this.enter();
            } else {
                this.exit();
            }
        }
        // the grips hold the tabletop instead of switching hotbar slots
        boolean grab = this.active && MC.gui.screen() == null;
        if (this.active) {
            this.setHotbarScrolling(false);
        }
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyTabletopGrab).setEnabled(ControllerType.LEFT, grab);
        // when the right hand touches the hotbar, its grip selects the slot instead
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyTabletopGrab).setEnabled(ControllerType.RIGHT,
            grab && this.dh.hotbarModule.hotbar < 0);
        if (this.active) {
            this.applySettings();
            this.update();
            this.activeTicks++;
            this.checkServerReach();
        }
    }

    /**
     * tells the player once per server when it doesn't give the extra reach, so the red laser isn't a surprise.
     * waits a few seconds after entering, so that the server had time to say what it allows
     */
    private void checkServerReach() {
        if (this.activeTicks == REACH_NOTICE_DELAY && !ClientNetworking.SERVER_TABLETOP_ALLOWED &&
            !ClientNetworking.DISPLAYED_TABLETOP_REACH)
        {
            ClientNetworking.DISPLAYED_TABLETOP_REACH = true;
            ClientUtils.addChatMessage(Component.translatable("vivecraft.messages.tabletop.noReach"));
        }
    }

    /**
     * makes the tabletop as wide and big as the settings say, when they changed, keeps where it is in the room
     */
    private void applySettings() {
        VRSettings settings = this.dh.vrSettings;
        // the render distance limits how many chunks can be shown
        int renderDistance = MC.options.getEffectiveRenderDistance();
        if (settings.tabletopChunks == this.appliedChunks && settings.tabletopFrameSize == this.appliedSize &&
            renderDistance == this.appliedRenderDistance)
        {
            return;
        }
        this.appliedChunks = settings.tabletopChunks;
        this.appliedSize = settings.tabletopFrameSize;
        this.appliedRenderDistance = renderDistance;
        this.frame = new TabletopFrame(this.frame.center(), this.frame.yaw(), settings.tabletopFrameSize,
            settings.tabletopFrameSize, settings.tabletopFrameSize * 0.5F, settings.tabletopFrameSize * 0.15F);
        this.blocksPerMeter = blocksPerMeter(settings);
    }

    /**
     * the tabletop shows as many chunks as set, but not more than the render distance has loaded
     *
     * @return the zoom for the tabletop size and width in the settings
     */
    private static float blocksPerMeter(VRSettings settings) {
        int chunks = Math.min(settings.tabletopChunks, Math.max(1, 2 * MC.options.getEffectiveRenderDistance() - 2));
        return chunks * 16.0F / settings.tabletopFrameSize;
    }

    /**
     * lets the world follow the avatar and moves the tabletop with the grips, called every frame, so both are smooth
     *
     * @param partialTick partial tick of the frame, for the avatar position
     */
    public void updateFrame(float partialTick) {
        if (!this.active || MC.player == null || MC.level == null) {
            return;
        }
        this.follow(MC.player.getPosition(partialTick));
        boolean allowed = MC.gui.screen() == null;
        // room positions of the controllers this frame
        VRData room = new VRData(Vec3.ZERO, 1.0F, 1.0F, 0.0F);
        this.grab.update(this, room.getController(ControllerType.LEFT.ordinal()).getPositionF(),
            room.getController(ControllerType.RIGHT.ordinal()).getPositionF(),
            allowed && VivecraftVRMod.INSTANCE.keyTabletopGrab.isDown(ControllerType.LEFT),
            allowed && VivecraftVRMod.INSTANCE.keyTabletopGrab.isDown(ControllerType.RIGHT),
            MC.player.position(), Math.max(0, MC.options.getEffectiveRenderDistance() - 1) * 16.0,
            MC.level.getMinY(), MC.level.getMaxY());
    }

    /**
     * the world scrolls when the avatar walks close to an edge, but only while it moves and the world isn't held, so
     * the world can be dragged away from the avatar to look around.
     * after a teleport, a respawn or a dimension change the tabletop centers on the avatar again
     */
    private void follow(Vec3 avatar) {
        if (MC.level != this.lastLevel || this.lastAvatar == null ||
            avatar.distanceTo(this.lastAvatar) > TELEPORT_DISTANCE)
        {
            this.grab.reset();
            this.setFocus(this.placeFocus(avatar));
        } else if (!this.grab.isHolding() && avatar.distanceToSqr(this.lastAvatar) > 1.0E-8) {
            // follow the middle of the avatar, so its feet and head stay inside
            double halfHeight = TabletopPlacement.AVATAR_HEIGHT * 0.5;
            Vec3 margin = new Vec3(FOLLOW_EDGE * this.clipBox.sizeX(), halfHeight + TabletopPlacement.FEET_ROOM,
                FOLLOW_EDGE * this.clipBox.sizeZ());
            Vec3 focus = TabletopNavigation.follow(this.focus, this.clipBox, avatar.add(0.0, halfHeight, 0.0), margin);
            if (!focus.equals(this.focus)) {
                this.setFocus(focus);
            }
        }
        this.lastAvatar = avatar;
        this.lastLevel = MC.level;
    }

    /**
     * @return the focus that shows the avatar in the middle, with the base at the height of the surrounding ground
     */
    private Vec3 placeFocus(Vec3 feet) {
        double halfWidth = 0.5 * this.blocksPerMeter * Math.max(this.frame.sizeX(), this.frame.sizeZ());
        int[] samples = new int[SURFACE_SAMPLES * SURFACE_SAMPLES];
        int count = 0;
        for (int i = 0; i < SURFACE_SAMPLES; i++) {
            for (int j = 0; j < SURFACE_SAMPLES; j++) {
                int x = Mth.floor(feet.x + halfWidth * (2.0 * i / (SURFACE_SAMPLES - 1) - 1.0));
                int z = Mth.floor(feet.z + halfWidth * (2.0 * j / (SURFACE_SAMPLES - 1) - 1.0));
                // unloaded chunks report the bottom of the world
                if (MC.level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                    samples[count++] = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                }
            }
        }
        double surfaceAtFeet = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(feet.x),
            Mth.floor(feet.z));
        double base = TabletopPlacement.baseHeight(feet.y, surfaceAtFeet, Arrays.copyOf(samples, count),
            this.blocksPerMeter * this.frame.heightAbove(), this.blocksPerMeter * this.frame.depthBelow());
        return new Vec3(feet.x, base, feet.z);
    }

    /**
     * the grips switch hotbar slots outside of tabletop mode
     */
    private void setHotbarScrolling(boolean enabled) {
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyHotbarNext).setEnabled(enabled);
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyHotbarPrev).setEnabled(enabled);
    }

    /**
     * shader packs can be turned on at any time, this leaves tabletop mode before a frame is rendered with them.
     * needs to be called every frame, before the tabletop mapping is used
     */
    public void leaveIfUnsupported() {
        if (this.active && this.blockedByShaders()) {
            this.exit();
        }
    }

    /**
     * tabletop mode doesn't work with shader packs, they replace the shaders that cut the world at the tabletop
     * edges. switches the view mode back and tells the player why
     *
     * @return if tabletop mode can't be used right now
     */
    private boolean blockedByShaders() {
        if (!ShadersHelper.isShaderActive()) {
            return false;
        }
        if (this.dh.vrSettings.viewMode == ViewMode.TABLETOP) {
            this.dh.vrSettings.viewMode = ViewMode.VR;
            ClientUtils.addChatMessage(Component.translatable("vivecraft.messages.tabletop.shaders"));
        }
        return true;
    }

    private void enter() {
        VRData room = this.dh.vrPlayer.vrdata_room_pre;
        Vec3 head = room.hmd.getPosition();
        Vector3f forward = room.hmd.getDirection();
        forward.y = 0F;
        if (forward.lengthSquared() < 1.0E-4F) {
            forward.set(0F, 0F, -1F);
        }
        forward.normalize();

        // the frame faces the player, so that its local -z axis points away from them
        float yaw = (float) Math.atan2(-forward.x, -forward.z);
        Vector3f center = new Vector3f(
            (float) head.x + forward.x * FRAME_DISTANCE,
            Math.max(MIN_FRAME_HEIGHT, (float) head.y - FRAME_BELOW_HEAD),
            (float) head.z + forward.z * FRAME_DISTANCE);

        VRSettings settings = this.dh.vrSettings;
        this.blocksPerMeter = blocksPerMeter(settings);
        this.appliedChunks = settings.tabletopChunks;
        this.appliedSize = settings.tabletopFrameSize;
        this.appliedRenderDistance = MC.options.getEffectiveRenderDistance();
        this.frame = new TabletopFrame(center, yaw, settings.tabletopFrameSize, settings.tabletopFrameSize,
            settings.tabletopFrameSize * 0.5F, settings.tabletopFrameSize * 0.15F);

        // show the world in the direction the player was looking, away from the user
        float playerYaw = (float) Math.toRadians(MC.player.getYRot());
        this.quarterTurns = Math.round((float) (Math.PI - playerYaw) / (float) (Math.PI * 0.5));

        // the avatar stands in the middle, the base shows the surrounding ground
        this.focus = this.placeFocus(MC.player.position());
        this.lastAvatar = MC.player.position();
        this.lastLevel = MC.level;

        this.grab.reset();
        this.activeTicks = 0;
        this.active = true;
        this.update();
        VRSettings.LOGGER.info("Vivecraft: entered tabletop mode, frame at {}, focus {}", center, this.focus);
    }

    private void exit() {
        this.active = false;
        this.mapping = null;
        this.clipBox = null;
        // don't keep the old level alive after leaving it
        this.lastLevel = null;
        this.lastAvatar = null;
        TabletopPick.reset();
        TabletopCutFaces.reset();
        this.setHotbarScrolling(true);
        // put the room back around the player
        this.dh.vrPlayer.leaveTabletop();
        VRSettings.LOGGER.info("Vivecraft: left tabletop mode");
    }

    /**
     * recalculates the mapping and clip box from the current frame, focus and zoom
     */
    public void update() {
        this.mapping = TabletopMapping.of(this.frame, this.focus, this.blocksPerMeter, this.quarterTurns);
        this.clipBox = ClipBox.of(this.frame, this.focus, this.blocksPerMeter, this.quarterTurns);
    }

    /**
     * most trackers implement roomscale actions for a life size player, those don't make sense in tabletop mode
     *
     * @param tracker tracker to check
     * @return if the tracker should be processed
     */
    public boolean isTrackerAllowed(Tracker tracker) {
        // the interact tracker only does the hotbar in tabletop mode
        return !isActive() || tracker == this.dh.cameraTracker || tracker == this.dh.interactTracker;
    }

    /**
     * @return how the room maps onto the world, only valid while active
     */
    public TabletopMapping getMapping() {
        return this.mapping;
    }

    /**
     * @return the part of the world that is visible, only valid while active
     */
    public ClipBox getClipBox() {
        return this.clipBox;
    }

    /**
     * @return where the tabletop is in the room
     */
    public TabletopFrame getFrame() {
        return this.frame;
    }

    /**
     * moves the frame in the room, only has an effect while active
     */
    public void setFrame(TabletopFrame frame) {
        if (this.active) {
            this.frame = frame;
            this.update();
        }
    }

    /**
     * @return how many blocks fit in one meter of the frame
     */
    public float getBlocksPerMeter() {
        return this.blocksPerMeter;
    }

    /**
     * moves and scales the frame in the room, with the matching zoom, only has an effect while active
     */
    public void setFrameAndZoom(TabletopFrame frame, float blocksPerMeter) {
        if (this.active) {
            this.frame = frame;
            this.blocksPerMeter = blocksPerMeter;
            this.update();
        }
    }

    /**
     * @return the world position shown at the center of the tabletop base
     */
    public Vec3 getFocus() {
        return this.focus;
    }

    /**
     * sets the world position shown at the frame center, only has an effect while active
     */
    public void setFocus(Vec3 focus) {
        if (this.active) {
            this.focus = focus;
            this.update();
        }
    }
}
