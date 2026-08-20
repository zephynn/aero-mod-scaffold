package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module

/**
 * Detaches movement from the player's usual physics by riding vanilla's
 * own creative-flight code: flip `flying`/`allowFlying`/`noClip` on and
 * the existing WASD+space/shift flight handling just works, no custom
 * movement code needed. Everything is restored on disable.
 *
 * Known limitation: this moves the *real* player entity (not a detached
 * camera), so in survival on a server the abilities packet round-trip can
 * get your flight/noclip silently reverted by the server. Fine for
 * singleplayer/creative, which is this mod's primary use case.
 */
class Freecam : Module(
    name = "Freecam",
    description = "Fly around freely, ignoring collision.",
    category = Category.MOVEMENT,
) {
    private var wasFlying = false
    private var wasAllowFlying = false
    private var wasNoClip = false

    override fun onEnable() {
        val player = mc.player ?: return
        val abilities = player.abilities

        wasFlying = abilities.flying
        wasAllowFlying = abilities.allowFlying
        wasNoClip = player.noClip

        abilities.allowFlying = true
        abilities.flying = true
        player.noClip = true
        player.sendAbilitiesUpdate()
    }

    override fun onDisable() {
        val player = mc.player ?: return
        val abilities = player.abilities

        abilities.flying = wasFlying
        abilities.allowFlying = wasAllowFlying
        player.noClip = wasNoClip
        player.sendAbilitiesUpdate()
    }
}
