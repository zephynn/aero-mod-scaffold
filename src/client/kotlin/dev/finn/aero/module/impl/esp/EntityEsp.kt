package dev.finn.aero.module.impl.esp

import dev.finn.aero.setting.ColorSetting
import dev.finn.aero.setting.ModeSetting
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.CreeperEntity
import net.minecraft.entity.mob.EndermanEntity
import net.minecraft.entity.mob.SkeletonEntity
import net.minecraft.entity.mob.SpiderEntity
import net.minecraft.entity.mob.ZombieEntity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.player.PlayerEntity

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

    override fun collectTargets(world: ClientWorld, self: PlayerEntity, range: Double): List<Pair<Entity, Int>> =
        world.entities
            .filter { it !== self && it is LivingEntity && it !is PlayerEntity && it.isAlive }
            .filter { self.entityPos.distanceTo(it.entityPos) <= range }
            .map { it to colorFor(it) }

    private fun colorFor(entity: Entity): Int {
        val matches = when (overrideType.value) {
            "Zombie" -> entity is ZombieEntity
            "Skeleton" -> entity is SkeletonEntity
            "Creeper" -> entity is CreeperEntity
            "Spider" -> entity is SpiderEntity
            "Enderman" -> entity is EndermanEntity
            "Villager" -> entity is VillagerEntity
            else -> false
        }
        return if (matches) overrideColor.value else defaultColor.value
    }
}
