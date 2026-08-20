package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module

/**
 * Forces max brightness by pushing the gamma option past its normal 0-1
 * slider range. Vanilla's lighting never clamps gamma back down, so any
 * value that high reads as fully lit regardless of actual light level.
 */
class Fullbright : Module(
    name = "Fullbright",
    description = "Removes darkness by maxing out gamma.",
    category = Category.RENDER,
) {
    private var previousGamma = 1.0

    override fun onEnable() {
        val gamma = mc.options.gamma
        previousGamma = gamma.value
        gamma.value = 1000.0
    }

    override fun onDisable() {
        mc.options.gamma.value = previousGamma
    }
}
