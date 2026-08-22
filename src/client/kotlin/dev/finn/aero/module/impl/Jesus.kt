package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import org.lwjgl.glfw.GLFW

/**
 * Walk on top of water instead of sinking into it: while standing in a
 * water block and not holding the dive key, this zeroes downward velocity
 * and snaps the player to the surface of the block they're standing in,
 * every tick. Holding the dive key (sneak) suspends it so you can still
 * swim down on purpose.
 */
class Jesus : Module(
    name = "Jesus",
    description = "Walk on top of water instead of sinking. Hold sneak to dive.",
    category = Category.MOVEMENT,
) {
    override fun onTick() {
        val player = mc.player ?: return
        val world = mc.level ?: return
        val window = mc.window
        if (!player.isInWater) return

        val diving = GLFW.glfwGetKey(window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
        if (diving) return

        val feetPos = player.blockPosition()
        if (world.getFluidState(feetPos).isEmpty) return

        val vel = player.deltaMovement
        if (vel.y < 0.0) player.setDeltaMovement(vel.x, 0.0, vel.z)

        val surfaceY = feetPos.y + 1.0
        if (player.y < surfaceY - 0.05) {
            player.setPos(player.x, surfaceY, player.z)
        }
    }
}
