package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module

/**
 * Forces max brightness by pushing the gamma option past its normal 0-1
 * slider range. Vanilla's lighting never clamps the *rendered* result back
 * down, so a high enough value reads as fully lit regardless of actual
 * light level -- but SimpleOption.setValue() runs every write through a
 * validator that clamps/rejects anything outside 0-1, so the field has to
 * be set directly via reflection to actually get an out-of-range value in.
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
        setGammaDirect(1000.0)
    }

    override fun onDisable() {
        setGammaDirect(previousGamma)
    }

    private fun setGammaDirect(value: Double) {
        val gamma = mc.options.gamma
        val field = gamma.javaClass.getDeclaredField("value")
        field.isAccessible = true
        field.set(gamma, value)
    }
}
