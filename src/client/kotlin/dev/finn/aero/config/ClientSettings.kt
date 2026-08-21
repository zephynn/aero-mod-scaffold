package dev.finn.aero.config

import org.lwjgl.glfw.GLFW

/**
 * General, non-module, non-theme client behaviour -- the rest of what
 * belongs in Settings rather than the ClickGUI (module keybinds live on
 * each Module; this is just the handful of things that apply to Aero as a
 * whole).
 */
object ClientSettings {
    const val DEFAULT_GUI_KEYBIND = GLFW.GLFW_KEY_RIGHT_SHIFT
    const val DEFAULT_NOTIFICATION_POSITION = "Top Left"
    const val DEFAULT_NOTIFICATION_DURATION_MS = 3000L

    val NOTIFICATION_POSITIONS = listOf("Top Left", "Top Right", "Bottom Left", "Bottom Right")

    /** Key that opens the ClickGUI. Separate from module keybinds so it can never collide with one. */
    var guiKeybind: Int = DEFAULT_GUI_KEYBIND

    /** When true, screens should apply state changes instantly instead of easing toward them. */
    var reducedMotion: Boolean = false

    /** Which screen corner notifications stack in. One of [NOTIFICATION_POSITIONS]. */
    var notificationPosition: String = DEFAULT_NOTIFICATION_POSITION

    /** Default lifetime of a notification before it drains/dismisses, in milliseconds. */
    var notificationDurationMs: Long = DEFAULT_NOTIFICATION_DURATION_MS

    fun reset() {
        guiKeybind = DEFAULT_GUI_KEYBIND
        reducedMotion = false
        notificationPosition = DEFAULT_NOTIFICATION_POSITION
        notificationDurationMs = DEFAULT_NOTIFICATION_DURATION_MS
    }
}
