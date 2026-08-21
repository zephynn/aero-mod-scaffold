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
 * chunkCullingEnabled ("smartCull" in some other mods, e.g. the MIT
 * https://github.com/MinecraftFreecam/Freecam) is what makes vanilla
 * spectator mode able to see caves/terrain behind walls around the camera:
 * normally the renderer's visibility graph assumes the camera can't be
 * standing inside solid blocks and culls geometry accordingly, which looks
 * like "walls block your view" the moment freecam pushes the camera through
 * one. Turning it off while freecam is active gets the same see-everything
 * behaviour spectator mode has.
 *
 * Keeping the real player stationary is done with Entity.setNoGravity(),
 * not the flying ability -- setNoGravity is purely local client-side
 * simulation state that's never sent to the server. The flying ability, by
 * contrast, is announced with a real PlayerAbilitiesC2SPacket, and most
 * anti-cheat plugins treat an unauthorized "flying = true" from a
 * non-creative player as a fly-hack signal. This gets the same "the entity
 * doesn't fall while the camera wanders off" result without that packet
 * ever going out.
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

    private var wasNoGravity = false
    private var wasChunkCullingEnabled = true

    override fun onEnable() {
        val player = mc.player ?: return
        FreecamState.position = player.eyePosition
        FreecamState.speed = speed.value
        FreecamState.active = true

        wasChunkCullingEnabled = mc.smartCull
        mc.smartCull = false

        // Local-only: stops gravity from pulling the (now-stationary) player
        // down without telling the server it's flying. KeyboardInputMixin is
        // what actually stops it from walking.
        wasNoGravity = player.isNoGravity()
        player.setNoGravity(true)
    }

    override fun onDisable() {
        FreecamState.active = false
        FreecamState.position = null
        mc.smartCull = wasChunkCullingEnabled

        val player = mc.player ?: return
        player.setNoGravity(wasNoGravity)
    }

    override fun onTick() {
        // Keep the mixin's speed in sync if the slider is dragged live.
        FreecamState.speed = speed.value
    }
}
