package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import kotlin.math.abs

/**
 * Automatically faces and attacks the nearest valid target every tick the
 * game's own attack cooldown allows.
 *
 * Legacy mode snaps the view to the target's exact eye position and
 * attacks the instant the cooldown refills -- a real setYaw/setPitch, not
 * a fake-only rotation, so it's not spoofing anything the server can see
 * -- but the rotation itself is pixel-perfect and instant every time, and
 * that consistency is its own tell. New mode (default) turns toward the
 * target at a capped, randomized degrees-per-tick instead of snapping,
 * and only swings once the aim has actually converged to within a small
 * margin -- closer to how a human's mouse flick actually looks, arriving
 * over a couple of ticks rather than teleporting exactly onto the target.
 */
class KillAura : Module(
    name = "KillAura",
    description = "Automatically attacks nearby entities.",
    category = Category.COMBAT,
) {
    private val mode = register(
        ModeSetting("Mode", "New turns toward the target over a few ticks instead of snapping. Legacy snaps and attacks instantly.", listOf("New", "Legacy"), "New"),
    )
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
    private val minTurnSpeed = register(
        SliderSetting("Min Turn Speed", "New mode only: slowest allowed turn rate, in degrees per tick.", 15.0, 5.0, 90.0, 5.0),
    )
    private val maxTurnSpeed = register(
        SliderSetting("Max Turn Speed", "New mode only: fastest allowed turn rate, in degrees per tick.", 40.0, 5.0, 90.0, 5.0),
    )
    private val attackPlayers = register(BoolSetting("Players", "Attack other players.", true))
    private val attackHostiles = register(BoolSetting("Hostiles", "Attack hostile mobs.", true))
    private val attackAnimals = register(BoolSetting("Animals", "Attack passive/animal mobs.", false))

    private companion object {
        const val AIM_MARGIN_DEGREES = 3.0
    }

    override fun onTick() {
        val player = mc.player ?: return
        val world = mc.level ?: return
        val interactionManager = mc.gameMode ?: return

        val searchBox = player.boundingBox.inflate(range.value)
        val target = world.getEntities(player, searchBox) { entity ->
            entity is LivingEntity &&
                !entity.isDeadOrDying &&
                entity.health > 0f &&
                isValidTargetType(entity) &&
                player.distanceTo(entity) <= range.value
        }.minByOrNull { player.distanceTo(it) } as? LivingEntity ?: return

        val (desiredYaw, desiredPitch) = anglesTo(player.eyePosition, target.eyePosition)

        if (mode.value == "Legacy") {
            // Respect vanilla's own attack-speed cooldown instead of spamming
            // every tick -- an attack before the cooldown's refilled just does
            // reduced damage in vanilla anyway.
            if (player.getAttackStrengthScale(0f) < 1f) return
            player.setYRot(desiredYaw)
            player.setXRot(desiredPitch)
            attack(interactionManager, player, target)
            return
        }

        val min = minTurnSpeed.value.coerceAtMost(maxTurnSpeed.value)
        val max = maxTurnSpeed.value.coerceAtLeast(minTurnSpeed.value)
        val step = (if (max > min) kotlin.random.Random.nextDouble(min, max) else min).toFloat()
        val yawError = turnToward(player.yRot, desiredYaw, step) { player.setYRot(it) }
        val pitchError = turnToward(player.xRot, desiredPitch, step) { player.setXRot(it) }

        if (player.getAttackStrengthScale(0f) < 1f) return
        if (abs(yawError) > AIM_MARGIN_DEGREES || abs(pitchError) > AIM_MARGIN_DEGREES) return
        attack(interactionManager, player, target)
    }

    private fun attack(interactionManager: net.minecraft.client.multiplayer.MultiPlayerGameMode, player: Player, target: LivingEntity) {
        interactionManager.attack(player, target)
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
    }

    private fun isValidTargetType(entity: LivingEntity): Boolean = when (entity) {
        is Player -> attackPlayers.value && entity != mc.player
        is Monster -> attackHostiles.value
        is Animal -> attackAnimals.value
        else -> false
    }

    private fun anglesTo(eyePos: net.minecraft.world.phys.Vec3, target: net.minecraft.world.phys.Vec3): Pair<Float, Float> {
        val dx = target.x - eyePos.x
        val dy = target.y - eyePos.y
        val dz = target.z - eyePos.z
        val horizontalDist = Math.sqrt(dx * dx + dz * dz)

        val yaw = Math.toDegrees(Math.atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-Math.atan2(dy, horizontalDist)).toFloat()
        return yaw to pitch
    }

    /** Rotates [current] toward [desired] by at most [maxStep] degrees, applies it via [apply], and returns the remaining error after the step. */
    private fun turnToward(current: Float, desired: Float, maxStep: Float, apply: (Float) -> Unit): Float {
        var error = (desired - current) % 360f
        if (error > 180f) error -= 360f
        if (error < -180f) error += 360f

        val step = error.coerceIn(-maxStep, maxStep)
        apply(current + step)
        return error - step
    }
}
