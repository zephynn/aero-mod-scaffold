package dev.finn.aero.module

import dev.finn.aero.setting.Setting
import net.fabricmc.fabric.api.client.rendering.v1.level.WorldRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.DeltaTracker
import org.lwjgl.glfw.GLFW

enum class Category {
    MOVEMENT,
    RENDER,
    COMBAT,
    PLAYER,
    MISC,
}

/**
 * Base class every Aero module extends. A module is a single toggleable
 * feature with an optional keybind and a list of typed Settings.
 *
 * To add a new module you: (1) write one file extending Module and (2)
 * add one line registering it in ModuleManager -- see AeroClient.kt.
 */
abstract class Module(
    val name: String,
    val description: String,
    val category: Category,
    defaultKeybind: Int = GLFW.GLFW_KEY_UNKNOWN,
) {
    /** GLFW key code that toggles this module, or GLFW_KEY_UNKNOWN if unbound. */
    var keybind: Int = defaultKeybind

    var enabled: Boolean = false
        private set

    /** Whether this module is surfaced in the Radial Menu / Command Bar as a pinned item. */
    var pinned: Boolean = false

    protected val mc: Minecraft get() = Minecraft.getInstance()

    /** Ordered list of this module's settings, for both the GUI and config (de)serialization. */
    val settings: MutableList<Setting<*>> = mutableListOf()

    protected fun <T : Setting<*>> register(setting: T): T {
        settings.add(setting)
        return setting
    }

    fun toggle() {
        if (enabled) disable() else enable()
    }

    fun enable() {
        if (enabled) return
        enabled = true
        onEnable()
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        onDisable()
    }

    /** Called once when the module is turned on. */
    open fun onEnable() {}

    /** Called once when the module is turned off. */
    open fun onDisable() {}

    /** Called every client tick (~20/s) while enabled. */
    open fun onTick() {}

    /** Called every rendered frame while enabled, for 2D HUD overlays (drawn over the whole screen). */
    open fun onRender(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {}

    /** Called every rendered frame while enabled, for 3D world-space overlays (ESP boxes, waypoints). */
    open fun onWorldRender(context: WorldRenderContext) {}
}
