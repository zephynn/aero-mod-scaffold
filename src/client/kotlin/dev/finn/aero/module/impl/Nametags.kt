package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.module.impl.esp.EspProjection
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.DeltaTracker
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * Floating name/health/distance labels above nearby entities, projected
 * with the same EspProjection math the ESP modules' tracers use.
 */
class Nametags : Module(
    name = "Nametags",
    description = "Shows floating name, health, and distance labels above nearby entities.",
    category = Category.RENDER,
) {
    private val range = register(SliderSetting("Range", "How far to search for entities.", 48.0, 8.0, 128.0, 4.0))
    private val health = register(BoolSetting("Health", "Show current/max health.", true))
    private val distance = register(BoolSetting("Distance", "Show distance in blocks.", true))

    override fun onRender(context: GuiGraphics, tickCounter: DeltaTracker) {
        val world = mc.level ?: return
        val self = mc.player ?: return

        val camera = mc.gameRenderer.mainCamera
        val camPos = camera.position()
        val fov = mc.options.fov().get().toFloat()
        val screenW = mc.window.guiScaledWidth
        val screenH = mc.window.guiScaledHeight

        val targets = world.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .filter { it !== self && it.isAlive }
            .filter { self.position().distanceTo(it.position()) <= range.value }

        for (entity in targets) {
            val above = Vec3(entity.x, entity.y + entity.bbHeight + 0.35, entity.z)
            val point = EspProjection.project(camPos, camera.yRot(), camera.xRot(), fov, screenW, screenH, above) ?: continue

            val parts = mutableListOf(entity.name.string)
            if (health.value) parts.add("${entity.health.toInt()}/${entity.maxHealth.toInt()} HP")
            if (distance.value) parts.add("${self.position().distanceTo(entity.position()).toInt()}m")
            val label = parts.joinToString("  ")

            val width = mc.font.width(label)
            context.drawString(mc.font, label, point.first - width / 2, point.second, 0xFFFFFFFF.toInt(), true)
        }
    }
}
