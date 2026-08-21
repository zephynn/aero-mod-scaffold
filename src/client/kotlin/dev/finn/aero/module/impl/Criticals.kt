package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import org.lwjgl.glfw.GLFW

/**
 * Vanilla only counts a hit as a critical (1.5x damage) when the attacker
 * is airborne -- not on the ground, not climbing, not in water, falling
 * (fallDistance > 0), etc. (see `PlayerEntity.attack`'s crit check).
 *
 * The first version of this nudged the player upward by a fixed 0.01
 * every single tick regardless of anything else, which turned out to be
 * both unreliable (setVelocity *sets* rather than accumulates, so if
 * 0.01 wasn't quite enough to clear vanilla's ground-contact epsilon in
 * one tick, it never built up to more -- it just reset to 0.01 again
 * next tick, forever) and, even if it had worked, would've looked like a
 * constant low-amplitude vibration instead of a real player's movement.
 *
 * This instead watches for the actual left-click (attack) press -- same
 * "was it down last tick" edge-detection AeroClient already uses for
 * keybinds -- and only pops the player up once, right as the swing
 * happens, exactly like a player manually bunny-hop-timing their crits.
 * One real (if tiny) hop per attack reads as normal jumping/movement
 * noise instead of a constant tell, and a bigger one-shot velocity is
 * enough to reliably clear the ground-contact check that a continuous
 * fixed 0.01 wasn't.
 */
class Criticals : Module(
    name = "Criticals",
    description = "Pops you slightly off the ground right as you attack, so it lands as a critical hit.",
    category = Category.COMBAT,
) {
    private companion object {
        const val HOP_VELOCITY = 0.1
    }

    private var attackWasDown = false

    override fun onTick() {
        val player = mc.player ?: return
        val window = mc.window ?: return

        val attackDown = GLFW.glfwGetMouseButton(window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val justPressed = attackDown && !attackWasDown
        attackWasDown = attackDown

        if (!justPressed) return
        if (!player.isOnGround || player.isSneaking || player.isTouchingWater || player.isClimbing) return

        val vel = player.velocity
        player.setVelocity(vel.x, HOP_VELOCITY, vel.z)
    }

    override fun onDisable() {
        attackWasDown = false
    }
}
