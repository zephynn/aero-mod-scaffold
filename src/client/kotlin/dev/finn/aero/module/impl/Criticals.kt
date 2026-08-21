package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module

/**
 * Vanilla only counts a hit as a critical (1.5x damage) when the attacker
 * is airborne -- not on the ground, not climbing, not in water, falling
 * (fallDistance > 0), etc. (see `PlayerEntity.attack`'s crit check).
 *
 * Two earlier versions of this got the timing backwards: the actual
 * attack fires the instant vanilla processes the mouse click -- on the
 * same input pass, well before our module's own onTick reacts to
 * anything. Waiting to *see* the click and only then popping the player
 * up is always one tick too late: the hit (and its crit/non-crit status)
 * has already resolved by the time our jump would land. Being airborne
 * has to be true *before* the swing, not triggered by it.
 *
 * So instead of reacting to clicks at all, this just keeps the player
 * continuously falling: the moment they touch the ground, jump again
 * (via the real `LivingEntity.jump()` vanilla itself calls off the space
 * bar -- proper jump velocity/particles/sound, not a hand-set nudge), so
 * whichever tick an attack actually happens on, the player is airborne
 * for it. This reads as constant auto-bunny-hopping while it's on,
 * which is the tradeoff for "always crit" actually being reliable.
 */
class Criticals : Module(
    name = "Criticals",
    description = "Keeps you falling so every attack that lands is a critical hit.",
    category = Category.COMBAT,
) {
    override fun onTick() {
        val player = mc.player ?: return
        if (!player.onGround() || player.isCrouching || player.isInWater || player.onClimbable()) return

        player.jumpFromGround()
    }
}
