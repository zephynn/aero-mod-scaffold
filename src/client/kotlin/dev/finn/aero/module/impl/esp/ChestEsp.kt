package dev.finn.aero.module.impl.esp

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ColorSetting
import dev.finn.aero.setting.SliderSetting
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.block.enums.ChestType
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box
import net.minecraft.util.math.BlockPos

/**
 * Outlines nearby containers -- block-based, so it can't reuse the
 * entity-only glow-outline mixin [EspModule] relies on. Draws its own box
 * outline instead, via the same manual line-segment code [EspRendering]
 * uses for the tracer.
 *
 * Each container type gets its own colour rather than one shared colour,
 * since telling a shulker box apart from a trapped chest at a glance is
 * the actual point of this module.
 */
class ChestEsp : Module(
    name = "Chest ESP",
    description = "Outlines nearby chests, ender chests, and shulker boxes.",
    category = Category.RENDER,
) {
    private val range = register(SliderSetting("Range", "How far to search for containers.", 32.0, 8.0, 96.0, 4.0))
    private val tracer = register(BoolSetting("Tracer", "Draw a line from your view to each container.", true))

    private val chestColor = register(ColorSetting("Chest Color", "Outline colour for normal chests.", 0xFFC17F3E.toInt()))
    private val trappedColor = register(ColorSetting("Trapped Chest Color", "Outline colour for trapped chests.", 0xFFE53935.toInt()))
    private val enderColor = register(ColorSetting("Ender Chest Color", "Outline colour for ender chests.", 0xFF30D5C8.toInt()))
    private val shulkerColor = register(ColorSetting("Shulker Color", "Outline colour for shulker boxes.", 0xFF9B59B6.toInt()))

    /**
     * World.getBlockEntities() only ever turned up empty client-side (it's
     * populated for server ticking, not for the client's own book-keeping)
     * -- so instead this walks the same per-chunk block entity maps the
     * vanilla block-entity renderer itself uses, over just the chunks
     * within range.
     */
    private fun collectTargets(world: ClientWorld, self: PlayerEntity, maxRange: Double): List<Pair<Box, Int>> {
        val selfPos = self.entityPos
        val chestPositions = mutableMapOf<BlockPos, Int>()
        val results = mutableListOf<Pair<Box, Int>>()

        val centerChunkX = self.blockPos.x shr 4
        val centerChunkZ = self.blockPos.z shr 4
        val chunkRadius = (maxRange / 16.0).toInt() + 1

        for (cx in (centerChunkX - chunkRadius)..(centerChunkX + chunkRadius)) {
            for (cz in (centerChunkZ - chunkRadius)..(centerChunkZ + chunkRadius)) {
                val chunk = world.getChunk(cx, cz) ?: continue
                for ((pos, blockEntity) in chunk.blockEntities) {
                    if (selfPos.distanceTo(Box(pos).center) > maxRange) continue

                    when (blockEntity) {
                        is ChestBlockEntity -> {
                            val color = if (world.getBlockState(pos).block === Blocks.TRAPPED_CHEST) trappedColor.value else chestColor.value
                            chestPositions[pos.toImmutable()] = color
                        }
                        is EnderChestBlockEntity -> results.add(Box(pos) to enderColor.value)
                        is ShulkerBoxBlockEntity -> results.add(Box(pos) to shulkerColor.value)
                        else -> {}
                    }
                }
            }
        }

        // Double chests are two adjacent single-wide blocks the game already
        // pairs together (CHEST_TYPE != SINGLE) -- merge those into one long
        // box instead of drawing two full-block outlines glued together.
        val merged = mutableSetOf<BlockPos>()
        for ((pos, color) in chestPositions) {
            if (pos in merged) continue
            val state = world.getBlockState(pos)
            val chestType = state.getOrEmpty(ChestBlock.CHEST_TYPE).orElse(ChestType.SINGLE)
            val partner = if (chestType == ChestType.SINGLE) {
                null
            } else {
                listOf(pos.north(), pos.south(), pos.east(), pos.west())
                    .firstOrNull { neighbor -> neighbor in chestPositions && chestPositions[neighbor] == color && neighbor !in merged }
            }

            if (partner != null) {
                merged.add(pos)
                merged.add(partner)
                results.add(Box(pos).union(Box(partner)) to color)
            } else {
                merged.add(pos)
                results.add(Box(pos) to color)
            }
        }

        return results
    }

    override fun onWorldRender(context: WorldRenderContext) {
        val world = mc.world ?: return
        val self = mc.player ?: return

        val targets = collectTargets(world, self, range.value)
        if (targets.isEmpty()) return

        val camPos = mc.gameRenderer.camera.cameraPos
        val matrices = context.matrices()
        val buffer = context.consumers().getBuffer(EspRenderLayers.NO_DEPTH_LINES)

        for ((box, color) in targets) {
            EspRendering.drawBox(matrices, buffer, camPos, box, color)
        }
    }

    override fun onRender(context: DrawContext, tickCounter: RenderTickCounter) {
        if (!tracer.value) return
        val world = mc.world ?: return
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

        for ((box, color) in targets) {
            val point = EspProjection.project(camPos, camera.yaw, camera.pitch, fov, screenW, screenH, box.center) ?: continue
            EspRendering.drawScreenLine(context, anchorX, anchorY, point.first, point.second, color)
        }
    }
}
