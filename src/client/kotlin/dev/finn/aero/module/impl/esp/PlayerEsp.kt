package dev.finn.aero.module.impl.esp

import dev.finn.aero.setting.ColorSetting
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity

/** Outlines every other nearby player, in [defaultColor]. */
class PlayerEsp : EspModule(
    name = "Player ESP",
    description = "Outlines nearby players.",
    defaultRange = 64.0,
    minRange = 16.0,
    maxRange = 128.0,
) {
    private val defaultColor = register(ColorSetting("Default Color", "Outline colour for other players.", 0xFF3FB6D6.toInt()))

    override fun collectTargets(world: ClientWorld, self: PlayerEntity, range: Double): List<Pair<Entity, Int>> =
        world.entities
            .filterIsInstance<PlayerEntity>()
            .filter { it !== self && it.isAlive }
            .filter { self.entityPos.distanceTo(it.entityPos) <= range }
            .map { it to defaultColor.value }
}
