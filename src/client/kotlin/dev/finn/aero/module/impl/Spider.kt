package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting

/**
 * Climbs any wall you walk into, the same way vanilla treats a ladder --
 * this just applies the ladder-climb upward velocity whenever
 * horizontalCollision is true and you're pressing a movement key, instead
 * of requiring the block itself to be climbable.
 */
class Spider : Module(
    name = "Spider",
    description = "Climb walls when moving into them, like a ladder.",
    category = Category.MOVEMENT,
) {
    private val speed = register(
        SliderSetting(
            name = "Speed",
            description = "Climbing speed, in blocks per second.",
            default = 4.0,
            min = 1.0,
            max = 10.0,
            step = 0.5,
        ),
    )

    override fun onTick() {
        val player = mc.player ?: return
        val input = player.input.moveVector
        val moving = input.x != 0f || input.y != 0f
        if (!moving || !player.horizontalCollision) return

        val vel = player.deltaMovement
        player.setDeltaMovement(vel.x, speed.value / 20.0, vel.z)
        player.fallDistance = 0.0
    }
}
