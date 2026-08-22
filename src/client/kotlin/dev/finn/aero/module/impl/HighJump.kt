package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting

/** Boosts your jump to a configurable height instead of vanilla's fixed ~1.25 blocks. */
class HighJump : Module(
    name = "HighJump",
    description = "Jump higher than vanilla allows.",
    category = Category.MOVEMENT,
) {
    private val height = register(
        SliderSetting(
            name = "Height",
            description = "Upward velocity applied on jump (vanilla is ~0.42).",
            default = 1.0,
            min = 0.42,
            max = 3.0,
            step = 0.05,
        ),
    )

    private var wasJumping = false

    override fun onDisable() {
        wasJumping = false
    }

    override fun onTick() {
        val player = mc.player ?: return
        val jumping = player.input.keyPresses.jump()

        if (jumping && !wasJumping && player.onGround()) {
            val vel = player.deltaMovement
            player.setDeltaMovement(vel.x, height.value, vel.z)
        }
        wasJumping = jumping
    }
}
