package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module

/**
 * Zeroes the player's tracked fall distance every tick, so vanilla's own
 * `LivingEntity.handleFallDamage` (which reads `fallDistance` to compute
 * damage) never sees it climb high enough to hurt. This is a client-side
 * value only -- it doesn't touch movement packets or anything the server
 * would flag on its own, but note that on a server that authoritatively
 * (re)computes fall damage from its own tracked position rather than
 * trusting the client's fall distance, this alone won't stop the damage.
 */
class NoFall : Module(
    name = "NoFall",
    description = "Prevents fall damage by resetting tracked fall distance.",
    category = Category.MOVEMENT,
) {
    override fun onTick() {
        mc.player?.fallDistance = 0.0
    }

    override fun onDisable() {
        mc.player?.fallDistance = 0.0
    }
}
