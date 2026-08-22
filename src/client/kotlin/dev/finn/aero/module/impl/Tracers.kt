package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.module.impl.esp.EspProjection
import dev.finn.aero.module.impl.esp.EspRendering
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ColorSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.DeltaTracker
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

/**
 * A standalone 2D tracer line to every nearby entity, independent of the
 * ESP modules' glow outline -- some players want the tracers without the
 * through-walls silhouette. Reuses EspProjection/EspRendering, the same
 * screen-space line drawing the ESP modules and X-Ray labels share.
 */
class Tracers : Module(
    name = "Tracers",
    description = "Draws lines from your view to nearby entities.",
    category = Category.RENDER,
) {
    private val range = register(SliderSetting("Range", "How far to search for entities.", 48.0, 8.0, 128.0, 4.0))
    private val players = register(BoolSetting("Players", "Include other players.", true))
    private val mobs = register(BoolSetting("Mobs", "Include mobs.", true))
    private val playerColor = register(ColorSetting("Player Color", "Tracer colour for players.", 0xFF3FB6D6.toInt()))
    private val mobColor = register(ColorSetting("Mob Color", "Tracer colour for mobs.", 0xFFE0A030.toInt()))

    override fun onRender(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        val world = mc.level ?: return
        val self = mc.player ?: return

        val camera = mc.gameRenderer.mainCamera()
        val camPos = camera.position()
        val fov = mc.options.fov().get().toFloat()
        val screenW = mc.window.guiScaledWidth
        val screenH = mc.window.guiScaledHeight
        val anchorX = screenW / 2
        val anchorY = screenH / 2

        val targets = world.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .filter { it !== self && it.isAlive }
            .filter { self.position().distanceTo(it.position()) <= range.value }

        for (entity in targets) {
            val isPlayer = entity is Player
            if (isPlayer && !players.value) continue
            if (!isPlayer && !mobs.value) continue

            val color = if (isPlayer) playerColor.value else mobColor.value
            val point = EspProjection.project(camPos, camera.yRot(), camera.xRot(), fov, screenW, screenH, entity.boundingBox.center) ?: continue
            EspRendering.drawScreenLine(context, anchorX, anchorY, point.first, point.second, color)
        }
    }
}
