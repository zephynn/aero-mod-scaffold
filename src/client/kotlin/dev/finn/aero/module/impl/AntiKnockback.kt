package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting

/**
 * Scales down the velocity vanilla applies on the tick you take a hit.
 * There's no clean "you were just knocked back" event to hook, so this
 * watches LivingEntity.hurtTime (the red-flash timer vanilla already
 * ticks down after damage) for its rising edge -- the same tick knockback
 * lands -- and rescales deltaMovement right then, rather than fighting
 * velocity every tick (which would also cancel normal movement momentum).
 */
class AntiKnockback : Module(
    name = "AntiKnockback",
    description = "Reduces knockback taken from hits.",
    category = Category.COMBAT,
) {
    private val horizontal = register(
        SliderSetting(
            name = "Horizontal",
            description = "Fraction of horizontal knockback kept (0 = none, 1 = full).",
            default = 0.0,
            min = 0.0,
            max = 1.0,
            step = 0.05,
        ),
    )
    private val vertical = register(
        SliderSetting(
            name = "Vertical",
            description = "Fraction of vertical knockback kept (0 = none, 1 = full).",
            default = 0.3,
            min = 0.0,
            max = 1.0,
            step = 0.05,
        ),
    )

    private var wasHurt = false

    override fun onDisable() {
        wasHurt = false
    }

    override fun onTick() {
        val player = mc.player ?: return
        val hurting = player.hurtTime > 0
        val justHurt = hurting && !wasHurt
        wasHurt = hurting

        if (justHurt) {
            val vel = player.deltaMovement
            player.setDeltaMovement(vel.x * horizontal.value, vel.y * vertical.value, vel.z * horizontal.value)
        }
    }
}
