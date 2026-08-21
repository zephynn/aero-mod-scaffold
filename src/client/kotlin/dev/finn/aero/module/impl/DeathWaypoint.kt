package dev.finn.aero.module.impl

import dev.finn.aero.config.Theme
import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.DeathScreen
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.registry.RegistryKey
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

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
    private var deathPos: Vec3d? = null
    private var deathDimension: RegistryKey<World>? = null
    private var wasOnDeathScreen = false

    override fun onDisable() {
        deathPos = null
        deathDimension = null
        wasOnDeathScreen = false
    }

    override fun onTick() {
        val onDeathScreen = mc.currentScreen is DeathScreen
        if (onDeathScreen && !wasOnDeathScreen) {
            mc.player?.let {
                deathPos = it.entityPos
                deathDimension = mc.world?.registryKey
            }
        }
        wasOnDeathScreen = onDeathScreen
    }

    override fun onRender(context: DrawContext, tickCounter: RenderTickCounter) {
        val pos = deathPos ?: return
        val player = mc.player ?: return
        if (deathDimension != mc.world?.registryKey) return
        if (mc.currentScreen != null) return

        val dx = pos.x - player.x
        val dz = pos.z - player.z
        val distance = player.entityPos.distanceTo(pos)
        if (distance < 2.0) return

        val bearing = Math.toDegrees(Math.atan2(-dx, dz))
        var angleDiff = (bearing - player.yaw) % 360.0
        if (angleDiff > 180.0) angleDiff -= 360.0
        if (angleDiff < -180.0) angleDiff += 360.0

        val screenW = mc.window.scaledWidth
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
        val labelWidth = mc.textRenderer.getWidth(label)
        context.drawTextWithShadow(mc.textRenderer, label, screenW / 2 - labelWidth / 2, barY + 8, accent)
    }
}
