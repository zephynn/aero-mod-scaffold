package dev.finn.aero.module.impl.esp

import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge between EspModule and EntityRendererMixin: which entities should
 * currently be forced to render with a glow-style outline (the same
 * through-walls silhouette vanilla uses for the Glowing effect / spectral
 * arrows), and what colour.
 *
 * A plain concurrent map rather than per-module state because the mixin
 * needs one place to check regardless of which ESP module (or several at
 * once) is targeting a given entity.
 */
object EspHighlightState {
    private val colors = ConcurrentHashMap<Entity, Int>()

    fun set(entity: Entity, argb: Int) {
        colors[entity] = argb
    }

    fun unset(entity: Entity) {
        colors.remove(entity)
    }

    @JvmStatic
    fun colorFor(entity: Entity): Int? = colors[entity]
}
