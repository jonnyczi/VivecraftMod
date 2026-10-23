package org.vivecraft.client_vr.tabletop;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vivecraft.api.client.Tracker;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.MethodHolder;
import org.vivecraft.client_vr.VRData;
import org.vivecraft.client_vr.VRState;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.client_vr.tabletop.math.ClipBox;
import org.vivecraft.client_vr.tabletop.math.TabletopFrame;
import org.vivecraft.client_vr.tabletop.math.TabletopMapping;

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

    private final ClientDataHolderVR dh;

    private boolean active;
    private TabletopFrame frame;
    // world position that is shown at the frame center
    private Vec3 focus;
    // how often the world is rotated by 90° inside the frame
    private int quarterTurns;
    // how many blocks fit in one meter, starts at the setting and changes when scaling with both hands
    private float blocksPerMeter;

    private final TabletopGrab grab = new TabletopGrab();

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
        if (wanted != this.active) {
            if (wanted) {
                this.enter();
            } else {
                this.exit();
            }
        }
        // the grips hold the tabletop instead of switching hotbar slots
        boolean grab = this.active && MC.gui.screen() == null;
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyHotbarNext).setEnabled(!this.active);
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyHotbarPrev).setEnabled(!this.active);
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyTabletopGrab).setEnabled(ControllerType.LEFT, grab);
        // when the right hand touches the hotbar, its grip selects the slot instead
        this.dh.vr.getInputAction(VivecraftVRMod.INSTANCE.keyTabletopGrab).setEnabled(ControllerType.RIGHT,
            grab && this.dh.hotbarModule.hotbar < 0);
        if (this.active) {
            this.update();
        }
    }

    /**
     * moves the tabletop with the grips, called every frame, so it follows the hands smoothly
     */
    public void updateGrab() {
        if (!this.active || MC.player == null || MC.level == null) {
            return;
        }
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
        this.blocksPerMeter = settings.tabletopBlocksPerMeter;
        this.frame = new TabletopFrame(center, yaw, settings.tabletopFrameSize, settings.tabletopFrameSize,
            settings.tabletopFrameSize * 0.5F, settings.tabletopFrameSize * 0.15F);

        // show the world in the direction the player was looking, away from the user
        float playerYaw = (float) Math.toRadians(MC.player.getYRot());
        this.quarterTurns = Math.round((float) (Math.PI - playerYaw) / (float) (Math.PI * 0.5));

        // the avatar stands on the frame base
        this.focus = MC.player.position();

        this.grab.reset();
        this.active = true;
        this.update();
        VRSettings.LOGGER.info("Vivecraft: entered tabletop mode, frame at {}, focus {}", center, this.focus);
    }

    private void exit() {
        this.active = false;
        this.mapping = null;
        this.clipBox = null;
        // put the room back around the player
        this.dh.vrPlayer.snapRoomOriginToPlayerEntity(MC.player, true, true);
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
