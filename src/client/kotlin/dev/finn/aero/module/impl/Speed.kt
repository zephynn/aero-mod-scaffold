package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting
import kotlin.math.sqrt

/**
 * Rescales the player's horizontal velocity to a fixed target speed every
 * tick, rather than multiplying it (which would compound with vanilla's
 * own acceleration/friction and run away). Vanilla has already computed a
 * horizontal direction from WASD + yaw by the time onTick runs each frame,
 * so this just takes that direction and stretches/shrinks its magnitude to
 * the configured blocks/second -- vertical velocity (jumping, falling) is
 * left untouched.
 *
 * Only does this while the player is actually holding a movement key.
 * Releasing WASD still leaves a tick or two of small residual velocity
 * from vanilla's own momentum/friction -- gating on *input*, not just
 * "is velocity nonzero", is what lets that residual actually decay
 * instead of getting re-inflated back up to full speed every tick
 * forever (which is what made it feel like the player couldn't stop).
 */
class Speed : Module(
    name = "Speed",
    description = "Moves at a fixed horizontal speed while walking.",
    category = Category.MOVEMENT,
) {
    private val speed = register(
        SliderSetting(
            name = "Speed",
            description = "Target horizontal speed, in blocks per second.",
            default = 6.0,
            min = 1.0,
            max = 15.0,
            step = 0.5,
        ),
    )

    override fun onTick() {
        val player = mc.player ?: return
        val input = player.input.moveVector
        if (input.x == 0f && input.y == 0f) return

        val vel = player.deltaMovement
        val horizontal = sqrt(vel.x * vel.x + vel.z * vel.z)
        if (horizontal < 0.001) return

        val targetPerTick = speed.value / 20.0
        val scale = targetPerTick / horizontal
        player.setDeltaMovement(vel.x * scale, vel.y, vel.z * scale)
    }
}
