package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult

/**
 * Attacks whatever's already under your crosshair the instant the attack
 * cooldown allows -- unlike KillAura, it never turns your view, it only
 * reacts to wherever you're already looking.
 */
class TriggerBot : Module(
    name = "TriggerBot",
    description = "Attacks whatever's under your crosshair, without turning to aim.",
    category = Category.COMBAT,
) {
    private val attackPlayers = register(BoolSetting("Players", "Attack other players.", true))
    private val attackHostiles = register(BoolSetting("Hostiles", "Attack hostile mobs.", true))
    private val attackAnimals = register(BoolSetting("Animals", "Attack passive/animal mobs.", false))

    override fun onTick() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return

        if (player.getAttackStrengthScale(0f) < 1f) return

        val target = (mc.hitResult as? EntityHitResult)?.entity as? LivingEntity ?: return
        if (target.isDeadOrDying || target.health <= 0f) return
        if (!isValidTargetType(target)) return

        gameMode.attack(player, target)
        player.swing(InteractionHand.MAIN_HAND)
    }

    private fun isValidTargetType(entity: LivingEntity): Boolean = when (entity) {
        is Player -> attackPlayers.value && entity != mc.player
        is Monster -> attackHostiles.value
        is Animal -> attackAnimals.value
        else -> false
    }
}
