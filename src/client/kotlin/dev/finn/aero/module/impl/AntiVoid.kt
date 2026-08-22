package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting

/**
 * A client-side safety net for void falls: remembers the last Y you were
 * standing solidly on, and if you drop below the configured threshold
 * (falling into a void gap, a broken bridge, etc.) teleports you back
 * there and kills your fall velocity. Purely local repositioning -- on a
 * server that authoritatively tracks position this only saves you from
 * the client-side fall itself, not a server-side void-damage check.
 */
class AntiVoid : Module(
    name = "AntiVoid",
    description = "Teleports you back to safety if you fall below a Y threshold.",
    category = Category.MOVEMENT,
) {
    private val threshold = register(
        SliderSetting(
            name = "Threshold",
            description = "Y level below which you're rescued.",
            default = -10.0,
            min = -64.0,
            max = 60.0,
            step = 1.0,
        ),
    )

    private var lastSafe: Triple<Double, Double, Double>? = null

    override fun onDisable() {
        lastSafe = null
    }

    override fun onTick() {
        val player = mc.player ?: return

        if (player.onGround()) {
            lastSafe = Triple(player.x, player.y, player.z)
            return
        }

        if (player.y < threshold.value) {
            val safe = lastSafe ?: return
            player.setDeltaMovement(0.0, 0.0, 0.0)
            player.setPos(safe.first, safe.second, safe.third)
            player.fallDistance = 0.0
        }
    }
}
