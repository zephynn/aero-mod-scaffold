package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Direct velocity manipulation rather than vanilla's own flying ability:
 * every tick this fully overwrites the player's deltaMovement from the
 * current WASD + space/shift input, the same way Speed rescales
 * horizontal velocity instead of relying on movement-speed attributes.
 * Deliberately does NOT touch Player.abilities.flying/mayfly or send a
 * PlayerAbilitiesC2SPacket -- an unauthorized "flying = true" from a
 * non-creative player is exactly what most anti-cheat plugins watch for,
 * so this stays entirely client-side velocity, like Freecam's approach.
 */
class Fly : Module(
    name = "Fly",
    description = "Fly freely using WASD and space/shift for vertical movement.",
    category = Category.MOVEMENT,
) {
    private val speed = register(
        SliderSetting(
            name = "Speed",
            description = "Blocks per second.",
            default = 10.0,
            min = 1.0,
            max = 40.0,
            step = 0.5,
        ),
    )

    private var wasNoGravity = false

    override fun onEnable() {
        val player = mc.player ?: return
        wasNoGravity = player.isNoGravity()
        player.setNoGravity(true)
    }

    override fun onDisable() {
        val player = mc.player ?: return
        player.setNoGravity(wasNoGravity)
        player.fallDistance = 0.0
    }

    override fun onTick() {
        val player = mc.player ?: return
        val window = mc.window

        val input = player.input.moveVector
        val forward = input.y
        val strafe = input.x

        val yaw = Math.toRadians(player.yRot.toDouble())
        var dx = 0.0
        var dz = 0.0
        if (forward != 0f || strafe != 0f) {
            dx = -sin(yaw) * forward + cos(yaw) * strafe
            dz = cos(yaw) * forward + sin(yaw) * strafe
        }

        val up = GLFW.glfwGetKey(window.handle(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS
        val down = GLFW.glfwGetKey(window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
        val dy = when {
            up && !down -> 1.0
            down && !up -> -1.0
            else -> 0.0
        }

        val length = sqrt(dx * dx + dz * dz)
        if (length > 0.001) {
            dx /= length
            dz /= length
        }

        val magnitude = sqrt(dx * dx + dy * dy + dz * dz)
        val perTick = speed.value / 20.0
        if (magnitude < 0.001) {
            player.setDeltaMovement(0.0, 0.0, 0.0)
        } else {
            player.setDeltaMovement(dx / magnitude * perTick, dy / magnitude * perTick, dz / magnitude * perTick)
        }
        player.fallDistance = 0.0
    }
}
