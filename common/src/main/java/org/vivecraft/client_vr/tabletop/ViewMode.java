package org.vivecraft.client_vr.tabletop;

import org.vivecraft.client_vr.settings.OptionEnum;

/**
 * how the world is presented to the VR player
 */
public enum ViewMode implements OptionEnum<ViewMode> {
    // regular life size VR
    VR,
    // the world is shown as a small diorama in the room
    TABLETOP
}
