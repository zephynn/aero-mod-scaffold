package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ColorSetting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.DeltaTracker

/**
 * A small always-on corner overlay -- coordinates, facing direction, and
 * FPS -- built from the same GuiGraphicsExtractor.text() call DeathWaypoint
 * and X-Ray's block labels already use, so it needs no new rendering
 * machinery.
 */
class HudInfo : Module(
    name = "HUD Info",
    description = "Shows coordinates, facing direction, and FPS in the corner of the screen.",
    category = Category.RENDER,
) {
    private val coords = register(BoolSetting("Coordinates", "Show your current position.", true))
    private val direction = register(BoolSetting("Direction", "Show which way you're facing.", true))
    private val fps = register(BoolSetting("FPS", "Show the current frame rate.", true))
    private val color = register(ColorSetting("Text Color", "Colour of the HUD text.", 0xFFFFFFFF.toInt()))

    override fun onRender(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        val player = mc.player ?: return

        var y = 4
        val x = 4
        val lineHeight = mc.font.lineHeight + 1

        if (coords.value) {
            val pos = player.blockPosition()
            context.text(mc.font, "XYZ: ${pos.x}, ${pos.y}, ${pos.z}", x, y, color.value, true)
            y += lineHeight
        }
        if (direction.value) {
            context.text(mc.font, "Facing: ${facing(player.yRot)}", x, y, color.value, true)
            y += lineHeight
        }
        if (fps.value) {
            context.text(mc.font, "FPS: ${mc.fps}", x, y, color.value, true)
            y += lineHeight
        }
    }

    private fun facing(yaw: Float): String {
        val normalized = ((yaw % 360) + 360) % 360
        return when {
            normalized < 22.5 -> "South"
            normalized < 67.5 -> "Southwest"
            normalized < 112.5 -> "West"
            normalized < 157.5 -> "Northwest"
            normalized < 202.5 -> "North"
            normalized < 247.5 -> "Northeast"
            normalized < 292.5 -> "East"
            normalized < 337.5 -> "Southeast"
            else -> "South"
        }
    }
}
