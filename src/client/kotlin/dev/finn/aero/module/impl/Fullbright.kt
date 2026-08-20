package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module

/**
 * Forces the lightmap texture's ambient-light blend to 1.0 via
 * LightmapTextureManagerMixin, replacing every texel with white regardless
 * of actual block/sky light -- a real fullbright for this lighting model.
 * (Overdriving the gamma option, the classic older-MC trick, doesn't work
 * here: this shader's gamma-driven term only reshapes light that already
 * exists, so a texel with zero real light stays zero no matter how high
 * gamma goes. See the mixin for the full explanation.)
 */
class Fullbright : Module(
    name = "Fullbright",
    description = "Removes darkness by maxing out ambient light.",
    category = Category.RENDER,
) {
    override fun onEnable() {
        FullbrightState.active = true
    }

    override fun onDisable() {
        FullbrightState.active = false
    }
}
