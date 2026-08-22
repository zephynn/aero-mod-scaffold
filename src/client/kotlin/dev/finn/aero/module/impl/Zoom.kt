package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting

/**
 * A spotting-scope zoom: narrows the FOV option while enabled and restores
 * it on disable. Reads the slider live every tick (rather than only at
 * enable time) so dragging it while zoomed updates immediately.
 */
class Zoom : Module(
    name = "Zoom",
    description = "Narrows your field of view for a scoped-in look.",
    category = Category.RENDER,
) {
    private val fov = register(
        SliderSetting(
            name = "FOV",
            description = "Field of view while zoomed.",
            default = 30.0,
            min = 5.0,
            max = 90.0,
            step = 1.0,
        ),
    )

    private var originalFov: Int? = null

    override fun onEnable() {
        originalFov = mc.options.fov().get()
        mc.options.fov().set(fov.value.toInt())
    }

    override fun onDisable() {
        originalFov?.let { mc.options.fov().set(it) }
        originalFov = null
    }

    override fun onTick() {
        mc.options.fov().set(fov.value.toInt())
    }
}
