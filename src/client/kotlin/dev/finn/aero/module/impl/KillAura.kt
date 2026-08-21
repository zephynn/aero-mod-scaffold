package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.player.PlayerEntity

/**
 * Automatically faces and attacks the nearest valid target every tick the
 * game's own attack cooldown allows. Turns to actually look at the target
 * before attacking (a real `setYaw`/`setPitch`, not a fake-only rotation
 * that leaves the client showing something different from what's sent) so
 * this behaves like a player who's just very fast and accurate, not like
 * something spoofing its own state.
 */
class KillAura : Module(
    name = "KillAura",
    description = "Automatically attacks nearby entities.",
    category = Category.COMBAT,
) {
    private val range = register(
        SliderSetting(
            name = "Range",
            description = "Max distance to a target, in blocks.",
            default = 4.0,
            min = 2.0,
            max = 8.0,
            step = 0.5,
        ),
    )
    private val attackPlayers = register(BoolSetting("Players", "Attack other players.", true))
    private val attackHostiles = register(BoolSetting("Hostiles", "Attack hostile mobs.", true))
    private val attackAnimals = register(BoolSetting("Animals", "Attack passive/animal mobs.", false))

    override fun onTick() {
        val player = mc.player ?: return
        val world = mc.world ?: return
        val interactionManager = mc.interactionManager ?: return

        // Respect vanilla's own attack-speed cooldown instead of spamming
        // every tick -- an attack before the cooldown's refilled just does
        // reduced damage in vanilla anyway, so there's no benefit to it,
        // and it reads as a real player's attack rate rather than a
        // constant every-tick click.
        if (player.getAttackCooldownProgress(0f) < 1f) return

        val searchBox = player.boundingBox.expand(range.value)
        val target = world.getOtherEntities(player, searchBox) { entity ->
            entity is LivingEntity &&
                !entity.isDead &&
                entity.health > 0f &&
                isValidTargetType(entity) &&
                player.distanceTo(entity) <= range.value
        }.minByOrNull { player.distanceTo(it) } as? LivingEntity ?: return

        facePosition(player, target.eyePos)
        interactionManager.attackEntity(player, target)
        player.swingHand(net.minecraft.util.Hand.MAIN_HAND)
    }

    private fun isValidTargetType(entity: LivingEntity): Boolean = when (entity) {
        is PlayerEntity -> attackPlayers.value && entity != mc.player
        is HostileEntity -> attackHostiles.value
        is AnimalEntity -> attackAnimals.value
        else -> false
    }

    private fun facePosition(player: net.minecraft.entity.player.PlayerEntity, target: net.minecraft.util.math.Vec3d) {
        val eyePos = player.eyePos
        val dx = target.x - eyePos.x
        val dy = target.y - eyePos.y
        val dz = target.z - eyePos.z
        val horizontalDist = Math.sqrt(dx * dx + dz * dz)

        val yaw = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-Math.atan2(dy, horizontalDist)).toFloat()

        player.setYaw(yaw)
        player.setPitch(pitch)
    }
}
