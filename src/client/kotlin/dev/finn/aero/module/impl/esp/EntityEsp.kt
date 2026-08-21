package dev.finn.aero.module.impl.esp

import dev.finn.aero.setting.ColorSetting
import dev.finn.aero.setting.ModeSetting
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.npc.villager.Villager
import net.minecraft.world.entity.player.Player

/**
 * Outlines nearby non-player living entities (mobs, animals). Players get
 * their own module, [PlayerEsp]. Every target uses [defaultColor] unless
 * [overrideType] picks a specific mob to recolour via [overrideColor] --
 * one override at a time for now, on top of the shared default.
 */
class EntityEsp : EspModule(
    name = "Entity ESP",
    description = "Outlines nearby mobs and animals.",
    defaultRange = 32.0,
    minRange = 8.0,
    maxRange = 96.0,
) {
    private val defaultColor = register(ColorSetting("Default Color", "Outline colour for anything not overridden below.", 0xFF3FB6D6.toInt()))

    private val overrideType = register(
        ModeSetting(
            "Override Type",
            "Give one specific mob type its own colour.",
            listOf("None", "Zombie", "Skeleton", "Creeper", "Spider", "Enderman", "Villager"),
            "None",
        ),
    )
    private val overrideColor = register(ColorSetting("Override Color", "Outline colour for the mob type selected above.", 0xFFE74C3C.toInt()))

    override fun collectTargets(world: ClientLevel, self: Player, range: Double): List<Pair<Entity, Int>> =
        world.entitiesForRendering()
            .filter { it !== self && it is LivingEntity && it !is Player && it.isAlive }
            .filter { self.position().distanceTo(it.position()) <= range }
            .map { it to colorFor(it) }

    private fun colorFor(entity: Entity): Int {
        val matches = when (overrideType.value) {
            "Zombie" -> entity is Zombie
            "Skeleton" -> entity is Skeleton
            "Creeper" -> entity is Creeper
            "Spider" -> entity is Spider
            "Enderman" -> entity is EnderMan
            "Villager" -> entity is Villager
            else -> false
        }
        return if (matches) overrideColor.value else defaultColor.value
    }
}
