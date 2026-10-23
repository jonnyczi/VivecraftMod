package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.vivecraft.client.gui.framework.screens.GuiListScreen;
import org.vivecraft.client.gui.framework.widgets.SettingsList;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.LinkedList;
import java.util.List;

public class GuiTabletopSettings extends GuiListScreen {
    public GuiTabletopSettings(Screen lastScreen) {
        super(Component.translatable("vivecraft.options.screen.tabletop"), lastScreen);
    }

    @Override
    protected List<SettingsList.BaseEntry> getEntries() {
        List<SettingsList.BaseEntry> entries = new LinkedList<>();
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.TABLETOP_FRAME_SIZE));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.TABLETOP_CHUNKS));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.TABLETOP_REACH));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.TABLETOP_PASSTHROUGH));
        return entries;
    }
}
