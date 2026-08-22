package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.ModeSetting
import org.lwjgl.glfw.GLFW

/**
 * Keeps you from sinking in water. Two modes:
 *
 * - "Surface" bobs you up to the top of whatever water block you're in --
 *   looks like actually walking on the water's surface, most convincing
 *   if you care about looking legitimate to onlookers.
 * - "Solid" just zeroes vertical velocity outright wherever you currently
 *   are, so water stops applying any physics at all and behaves like solid
 *   ground under your feet at whatever height you entered it -- no bob,
 *   more obviously not-vanilla but simpler and steadier for building/
 *   bridging over water.
 *
 * Holding the dive key (sneak) suspends either mode so you can still swim
 * down on purpose.
 */
class Jesus : Module(
    name = "Jesus",
    description = "Walk on top of water instead of sinking. Hold sneak to dive.",
    category = Category.MOVEMENT,
) {
    private val mode = register(ModeSetting("Mode", "How to treat water underfoot.", listOf("Surface", "Solid"), "Surface"))

    override fun onTick() {
        val player = mc.player ?: return
        val world = mc.level ?: return
        val window = mc.window
        if (!player.isInWater) return

        val diving = GLFW.glfwGetKey(window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
        if (diving) return

        val feetPos = player.blockPosition()
        if (world.getFluidState(feetPos).isEmpty) return

        when (mode.value) {
            "Solid" -> {
                val vel = player.deltaMovement
                player.setDeltaMovement(vel.x, 0.0, vel.z)
                player.fallDistance = 0.0
            }
            else -> {
                val vel = player.deltaMovement
                if (vel.y < 0.0) player.setDeltaMovement(vel.x, 0.0, vel.z)

                val surfaceY = feetPos.y + 1.0
                if (player.y < surfaceY - 0.05) {
                    player.setPos(player.x, surfaceY, player.z)
                }
            }
        }
    }
}
