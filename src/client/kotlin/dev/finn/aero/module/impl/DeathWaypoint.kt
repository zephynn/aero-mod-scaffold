package dev.finn.aero.module.impl

import dev.finn.aero.config.Theme
import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.DeathScreen
import net.minecraft.client.DeltaTracker
import net.minecraft.resources.ResourceKey
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.Level

/**
 * Remembers where you last died and points to it on the HUD -- a small
 * compass bar near the top of the screen, plus distance. Detected via the
 * vanilla death screen opening (still your death position at that point,
 * before the respawn teleport happens), so no networking/event hooks are
 * needed.
 */
class DeathWaypoint : Module(
    name = "Death Waypoint",
    description = "Points toward your last death location.",
    category = Category.MISC,
) {
    private var deathPos: Vec3? = null
    private var deathDimension: ResourceKey<Level>? = null
    private var wasOnDeathScreen = false

    override fun onDisable() {
        deathPos = null
        deathDimension = null
        wasOnDeathScreen = false
    }

    override fun onTick() {
        val onDeathScreen = mc.screen is DeathScreen
        if (onDeathScreen && !wasOnDeathScreen) {
            mc.player?.let {
                deathPos = it.position()
                deathDimension = mc.level?.dimension()
            }
        }
        wasOnDeathScreen = onDeathScreen
    }

    override fun onRender(context: GuiGraphics, tickCounter: DeltaTracker) {
        val pos = deathPos ?: return
        val player = mc.player ?: return
        if (deathDimension != mc.level?.dimension()) return
        if (mc.screen != null) return

        val dx = pos.x - player.x
        val dz = pos.z - player.z
        val distance = player.position().distanceTo(pos)
        if (distance < 2.0) return

        val bearing = Math.toDegrees(Math.atan2(-dx, dz))
        var angleDiff = (bearing - player.yRot) % 360.0
        if (angleDiff > 180.0) angleDiff -= 360.0
        if (angleDiff < -180.0) angleDiff += 360.0

        val screenW = mc.window.guiScaledWidth
        val barWidth = 120
        val barLeft = (screenW - barWidth) / 2
        val barY = 14
        val accent = (0xFF shl 24) or (Theme.accent and 0xFFFFFF)
        val dim = 0x60FFFFFF.toInt()

        context.fill(barLeft, barY, barLeft + barWidth, barY + 1, dim)

        val clamped = angleDiff.coerceIn(-90.0, 90.0)
        val markerX = barLeft + ((clamped + 90.0) / 180.0 * barWidth).toInt()
        context.fill(markerX - 2, barY - 3, markerX + 2, barY + 4, accent)

        val label = "Death · ${distance.toInt()}m"
        val labelWidth = mc.font.width(label)
        context.drawString(mc.font, label, screenW / 2 - labelWidth / 2, barY + 8, accent, true)
    }
}
