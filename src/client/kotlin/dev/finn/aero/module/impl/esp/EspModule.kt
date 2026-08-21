package dev.finn.aero.module.impl.esp

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.DeltaTracker
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/**
 * Shared core for entity-based ESP. Rather than drawing our own bounding-box
 * wireframe, this forces the real vanilla glow outline -- the same
 * through-walls, entity-shaped silhouette a spectral arrow or the Glowing
 * effect gives an entity -- via EspHighlightState + EntityRendererMixin. An
 * optional tracer is drawn separately as a 2D HUD line (see EspProjection
 * for why it's 2D and not a 3D world-space line), sharing the drawing code
 * with the block-based ChestEsp via EspRendering.
 *
 * EntityEsp and PlayerEsp are thin subclasses that only implement
 * [collectTargets], each entity paired with the ARGB colour it should be
 * outlined in -- letting a subclass mix a default colour with per-type
 * overrides without this base needing to know anything about that.
 */
abstract class EspModule(
    name: String,
    description: String,
    defaultRange: Double = 32.0,
    minRange: Double = 8.0,
    maxRange: Double = 128.0,
) : Module(name, description, Category.RENDER) {

    private val range = register(SliderSetting("Range", "How far to search for targets.", defaultRange, minRange, maxRange, 4.0))
    private val outline = register(BoolSetting("Outline", "Highlight each target with a glow outline, through walls.", true))
    private val tracer = register(BoolSetting("Tracer", "Draw a line from your view to each target.", true))

    /** Every currently-visible target paired with the ARGB colour it should be outlined in, within [range] blocks of [self]. */
    protected abstract fun collectTargets(world: ClientLevel, self: Player, range: Double): List<Pair<Entity, Int>>

    /** Entities we personally applied a highlight to last tick, so we can clear exactly those and no one else's. */
    private var highlighted: Set<Entity> = emptySet()

    override fun onDisable() {
        highlighted.forEach { EspHighlightState.unset(it) }
        highlighted = emptySet()
    }

    override fun onTick() {
        val world = mc.level ?: return
        val self = mc.player ?: return

        val targets = if (outline.value) collectTargets(world, self, range.value) else emptyList()
        val targetSet = targets.mapTo(mutableSetOf()) { it.first }

        for (previous in highlighted) {
            if (previous !in targetSet) EspHighlightState.unset(previous)
        }
        for ((entity, color) in targets) {
            EspHighlightState.set(entity, color)
        }
        highlighted = targetSet
    }

    override fun onRender(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (!tracer.value) return
        val world = mc.level ?: return
        val self = mc.player ?: return

        val targets = collectTargets(world, self, range.value)
        if (targets.isEmpty()) return

        val camera = mc.gameRenderer.camera
        val camPos = camera.cameraPos
        val fov = mc.options.fov.value.toFloat()
        val screenW = mc.window.scaledWidth
        val screenH = mc.window.scaledHeight
        val anchorX = screenW / 2
        val anchorY = screenH / 2

        for ((entity, color) in targets) {
            val point = EspProjection.project(camPos, camera.yRot, camera.xRot, fov, screenW, screenH, entity.bb.center) ?: continue
            EspRendering.drawScreenLine(context, anchorX, anchorY, point.first, point.second, color)
        }
    }
}
