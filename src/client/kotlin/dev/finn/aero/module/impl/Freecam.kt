package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting
import net.minecraft.util.math.Vec3d
import org.lwjgl.glfw.GLFW

/**
 * A real freecam: CameraMixin renders from FreecamState.position instead of
 * following the player, and KeyboardInputMixin cancels the player's own
 * WASD input while active, so the actual entity never moves -- only the
 * render camera does. This module just owns the "where is the camera and
 * which way is it moving" math; the mixins are the actual decoupling.
 */
class Freecam : Module(
    name = "Freecam",
    description = "Fly the camera around freely without moving your real position.",
    category = Category.MOVEMENT,
) {
    private val speed = register(
        SliderSetting(
            name = "Speed",
            description = "Blocks moved per tick.",
            default = 0.75,
            min = 0.1,
            max = 5.0,
            step = 0.05,
        ),
    )

    override fun onEnable() {
        val player = mc.player ?: return
        FreecamState.position = mc.gameRenderer.camera.cameraPos
        FreecamState.active = true

        // Flying stops gravity from pulling the (now-stationary) player
        // down; KeyboardInputMixin is what actually stops it from walking.
        wasFlying = player.abilities.flying
        wasAllowFlying = player.abilities.allowFlying
        player.abilities.allowFlying = true
        player.abilities.flying = true
        player.sendAbilitiesUpdate()
    }

    override fun onDisable() {
        FreecamState.active = false
        FreecamState.position = null

        val player = mc.player ?: return
        player.abilities.flying = wasFlying
        player.abilities.allowFlying = wasAllowFlying
        player.sendAbilitiesUpdate()
    }

    private var wasFlying = false
    private var wasAllowFlying = false

    override fun onTick() {
        val window = mc.window ?: return
        val camera = mc.gameRenderer.camera
        val pos = FreecamState.position ?: return

        val forward = pressed(window, GLFW.GLFW_KEY_W) - pressed(window, GLFW.GLFW_KEY_S)
        val strafe = pressed(window, GLFW.GLFW_KEY_D) - pressed(window, GLFW.GLFW_KEY_A)
        val vertical = pressed(window, GLFW.GLFW_KEY_SPACE) - pressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
        if (forward == 0.0 && strafe == 0.0 && vertical == 0.0) return

        val yaw = Math.toRadians(camera.yaw.toDouble())
        val pitch = Math.toRadians(camera.pitch.toDouble())

        // Full 3D look-direction vector: holding W while looking up/down
        // moves the camera up/down too, not just horizontally.
        val forwardVec = Vec3d(
            -Math.sin(yaw) * Math.cos(pitch),
            -Math.sin(pitch),
            Math.cos(yaw) * Math.cos(pitch),
        )
        // Horizontal-only forward, used to derive strafe so A/D never tilt
        // the camera up or down.
        val horizontalForward = Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw))
        val rightVec = horizontalForward.crossProduct(Vec3d(0.0, 1.0, 0.0))

        var movement = forwardVec.multiply(forward).add(rightVec.multiply(strafe))
        if (movement.lengthSquared() > 0.0) movement = movement.normalize()
        movement = movement.add(0.0, vertical, 0.0)

        FreecamState.position = pos.add(movement.multiply(speed.value))
    }

    private fun pressed(window: net.minecraft.client.util.Window, key: Int): Double =
        if (GLFW.glfwGetKey(window.handle, key) == GLFW.GLFW_PRESS) 1.0 else 0.0
}
