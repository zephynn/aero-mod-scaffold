package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting

/**
 * A real freecam: CameraMixin renders from FreecamState.position instead of
 * following the player, and KeyboardInputMixin cancels the player's own
 * WASD input while active, so the actual entity never moves -- only the
 * render camera does.
 *
 * This module just owns enable/disable state and the Speed setting; the
 * actual per-frame movement math lives in CameraMixin (Java), since it
 * needs to run at render rate, not the 20/s tick rate onTick() gets.
 */
class Freecam : Module(
    name = "Freecam",
    description = "Fly the camera around freely without moving your real position.",
    category = Category.MOVEMENT,
) {
    private val speed = register(
        SliderSetting(
            name = "Speed",
            description = "Blocks per second.",
            default = 8.0,
            min = 1.0,
            max = 40.0,
            step = 0.5,
        ),
    )

    private var wasFlying = false
    private var wasAllowFlying = false

    override fun onEnable() {
        val player = mc.player ?: return
        FreecamState.position = mc.gameRenderer.camera.cameraPos
        FreecamState.speed = speed.value
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

    override fun onTick() {
        // Keep the mixin's speed in sync if the slider is dragged live.
        FreecamState.speed = speed.value
    }
}
