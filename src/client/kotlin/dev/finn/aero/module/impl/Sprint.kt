package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import org.lwjgl.glfw.GLFW

/**
 * Auto-sprint: while enabled, forces the player to sprint whenever they
 * are moving forward, instead of requiring the vanilla double-tap-W /
 * held sprint key. This is the reference "fully wired" module -- copy
 * this file's shape when adding a new one.
 */
class Sprint : Module(
    name = "Sprint",
    description = "Automatically sprints while moving forward.",
    category = Category.MOVEMENT,
    defaultKeybind = GLFW.GLFW_KEY_R,
) {
    override fun onTick() {
        val player = mc.player ?: return

        // movementForward > 0 means the player is holding the "move forward" key.
        // We don't force-sprint while sneaking or moving backward, matching
        // vanilla's own restrictions on when sprinting is allowed.
        val movingForward = player.input.movementForward > 0f
        val canSprint = !player.isSneaking && !player.isSprinting && movingForward

        if (canSprint) {
            player.isSprinting = true
        } else if (!movingForward && player.isSprinting) {
            player.isSprinting = false
        }
    }

    override fun onDisable() {
        // Don't leave the player stuck sprinting when the module is turned off.
        mc.player?.isSprinting = false
    }
}
