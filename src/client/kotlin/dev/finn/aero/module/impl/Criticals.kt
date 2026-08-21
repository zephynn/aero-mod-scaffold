package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module

/**
 * Vanilla only counts a hit as a critical (1.5x damage) when the attacker
 * is airborne -- not on the ground, not climbing, not in water, falling
 * (fallDistance > 0), etc. (see `PlayerEntity.attack`'s crit check). This
 * keeps that condition satisfied essentially all the time by nudging the
 * player a hair off the ground every tick: 0.01 blocks of upward velocity
 * is well below anything visible (vanilla itself has similar-sized
 * sub-pixel float noise in normal movement) but is enough that `isOnGround()`
 * reads false and fallDistance ticks above zero, so any attack that lands
 * qualifies as a crit without an actual visible hop.
 */
class Criticals : Module(
    name = "Criticals",
    description = "Keeps attacks landing as critical hits.",
    category = Category.COMBAT,
) {
    override fun onTick() {
        val player = mc.player ?: return
        if (!player.isOnGround || player.isSneaking || player.isTouchingWater || player.isClimbing) return

        val vel = player.velocity
        player.setVelocity(vel.x, 0.01, vel.z)
    }
}
