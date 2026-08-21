package dev.finn.aero.gui

import dev.finn.aero.config.ClientSettings
import net.minecraft.client.gui.screens.Screen

/**
 * Single place that decides which ClickGUI layout to open, keyed off
 * [ClientSettings.guiStyle]. Every spot that used to hard-code
 * `ClickGuiScreen()` (the guiKeybind poll in AeroClient, the "back" button
 * in [XrayCustomScreen] and [SettingsScreen]) goes through this instead, so
 * switching styles in Settings takes effect everywhere consistently.
 */
object GuiOpener {
    fun clickGuiScreen(): Screen =
        if (ClientSettings.guiStyle == "Meteor") MeteorGuiScreen() else ClickGuiScreen()
}
