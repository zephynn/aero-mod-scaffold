package dev.finn.aero.module.impl.xray

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.module.impl.esp.EspProjection
import dev.finn.aero.module.impl.esp.EspRenderLayers
import dev.finn.aero.module.impl.esp.EspRendering
import dev.finn.aero.notification.NotificationManager
import dev.finn.aero.notification.NotificationType
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ColorSetting
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.SliderSetting
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.block.Block
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.util.math.Box
import net.minecraft.util.math.BlockPos

/**
 * Real terrain X-Ray: unlike [dev.finn.aero.module.impl.esp.ChestEsp] (a
 * pure overlay), this actually culls non-target blocks out of the chunk
 * mesh via [ChunkRendererRegionMixin] + [XrayState] -- the block scan here
 * only exists to find *where* to draw highlight boxes/tracers over what's
 * left, reusing exactly the same box/tracer drawing ESP already has.
 */
class XrayModule : Module(
    name = "X-Ray",
    description = "See through terrain to ores, storage/utility blocks, or a custom list.",
    category = Category.RENDER,
) {
    private val mode = register(ModeSetting("Mode", "What X-Ray reveals.", listOf("Ores", "Base Finder", "Custom"), "Ores"))
    private val terrainOpacity = register(SliderSetting("Terrain Opacity", "How see-through non-target terrain is (0 = fully culled, 1 = fully opaque).", 0.0, 0.0, 1.0, 0.05))
    private val highlightStyle = register(ModeSetting("Highlight Style", "How found blocks are drawn. 'None' relies on terrain transparency alone.", listOf("Outline", "Solid", "Both", "None"), "Outline"))
    private val highlightColor = register(ColorSetting("Highlight Color", "Default highlight colour.", 0xFF55FF55.toInt()))
    private val baseFinderRadius = register(SliderSetting("Base Finder Radius", "Distance to join nearby indicators into one cluster.", 6.0, 1.0, 24.0, 1.0))
    private val minClusterSize = register(SliderSetting("Min Cluster Size", "Minimum indicators to count as a base.", 3.0, 1.0, 20.0, 1.0))
    private val showIndicators = register(BoolSetting("Show Indicators", "Draw individual block boxes.", true))
    private val showClusters = register(BoolSetting("Show Clusters", "Draw one bounding box + label per detected base.", true))
    private val tracer = register(BoolSetting("Tracer", "Draw a line from your view to each found block.", false))
    private val scanRange = register(SliderSetting("Scan Range", "How far (in chunks) to scan for target blocks.", 4.0, 1.0, 8.0, 1.0))

    // --- Scan cache, refreshed on a throttled tick cadence rather than every frame. ---
    private var cachedIndividual: List<BlockPos> = emptyList()
    private var cachedClusters: List<XrayCluster> = emptyList()
    private var tickCounter = 0
    private var lastClusterCount = -1
    private var lastMode = ""
    private var lastOpacity = -1f

    /** Set true by a setting change; consumed (and cleared) by the next tick's scan, which also re-publishes XrayState + reloads chunks. */
    private var targetsDirty = true

    override fun onEnable() {
        publishTargets()
        XrayState.active = true
        XrayState.terrainOpacity = terrainOpacity.value.toFloat()
        lastOpacity = terrainOpacity.value.toFloat()
        XrayState.requestChunkReload()
        NotificationManager.show("X-Ray", "${mode.value} mode enabled", NotificationType.INFO)
        lastMode = mode.value
    }

    override fun onDisable() {
        XrayState.active = false
        XrayState.requestChunkReload()
        cachedIndividual = emptyList()
        cachedClusters = emptyList()
        lastClusterCount = -1
    }

    override fun onTick() {
        if (mode.value != lastMode) {
            lastMode = mode.value
            targetsDirty = true
            NotificationManager.show("X-Ray", "${mode.value} mode enabled", NotificationType.INFO)
        }

        if (targetsDirty) {
            publishTargets()
            XrayState.requestChunkReload()
            targetsDirty = false
        }

        val opacityNow = terrainOpacity.value.toFloat()
        if (opacityNow != lastOpacity) {
            lastOpacity = opacityNow
            XrayState.terrainOpacity = opacityNow
            XrayState.requestChunkReload()
        }

        // Throttle the (heavier than entity-scan) block scan to every 4th
        // tick -- 5/s instead of 20/s -- per the plan's performance section.
        tickCounter++
        if (tickCounter % 4 != 0) return

        val world = mc.world ?: return
        val self = mc.player ?: return
        val positions = scanBlocks(world, self)

        if (mode.value == "Base Finder") {
            val clusters = XrayClustering.cluster(positions, baseFinderRadius.value, minClusterSize.value.toInt())
            cachedClusters = clusters
            cachedIndividual = positions
            if (clusters.size != lastClusterCount) {
                lastClusterCount = clusters.size
                if (clusters.isNotEmpty()) {
                    NotificationManager.show("Base Finder", "${clusters.size} indicator${if (clusters.size == 1) "" else "s"} detected", NotificationType.INFO)
                }
            }
        } else {
            cachedIndividual = positions
            cachedClusters = emptyList()
        }
    }

    /** Called on enable and whenever mode/custom-list changes -- rebuilds and republishes the active target block set. */
    fun publishTargets() {
        val blocks: Set<Block> = when (mode.value) {
            "Ores" -> XrayPresets.ORES
            "Base Finder" -> XrayPresets.BASE_FINDER
            "Custom" -> XrayCustomConfig.blocks()
            else -> emptySet()
        }
        XrayState.setTargets(blocks)
    }

    /** Call when the Custom mode block list is edited from [dev.finn.aero.gui.XrayCustomScreen], so the change takes effect. */
    fun markTargetsDirty() {
        targetsDirty = true
    }

    private fun scanBlocks(world: net.minecraft.client.world.ClientWorld, self: net.minecraft.entity.player.PlayerEntity): List<BlockPos> {
        val targets = when (mode.value) {
            "Ores" -> XrayPresets.ORES
            "Base Finder" -> XrayPresets.BASE_FINDER
            "Custom" -> XrayCustomConfig.blocks()
            else -> emptySet()
        }
        if (targets.isEmpty()) return emptyList()

        val results = mutableListOf<BlockPos>()
        val centerChunkX = self.blockPos.x shr 4
        val centerChunkZ = self.blockPos.z shr 4
        val chunkRadius = scanRange.value.toInt()
        val bottomSection = world.bottomSectionCoord
        val sectionCount = world.countVerticalSections()

        for (cx in (centerChunkX - chunkRadius)..(centerChunkX + chunkRadius)) {
            for (cz in (centerChunkZ - chunkRadius)..(centerChunkZ + chunkRadius)) {
                val chunk = world.getChunk(cx, cz) ?: continue
                for (sectionIndex in 0 until sectionCount) {
                    val section = chunk.getSectionArray().getOrNull(sectionIndex) ?: continue
                    if (section.isEmpty) continue
                    val sectionY = (bottomSection + sectionIndex) shl 4

                    for (lx in 0 until 16) {
                        for (ly in 0 until 16) {
                            for (lz in 0 until 16) {
                                val state = section.getBlockState(lx, ly, lz)
                                if (state.block in targets) {
                                    results.add(BlockPos(cx * 16 + lx, sectionY + ly, cz * 16 + lz))
                                }
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    override fun onWorldRender(context: WorldRenderContext) {
        if (cachedIndividual.isEmpty() && cachedClusters.isEmpty()) return
        // "None" relies on terrain transparency alone -- skip the ESP
        // outline/fill/tracer draw entirely (tracer is gated separately in
        // onRender, cluster labels stay since they're not part of the ESP
        // highlight itself).
        if (highlightStyle.value == "None") return

        val camPos = mc.gameRenderer.camera.cameraPos
        val matrices = context.matrices()
        val color = highlightColor.value
        val style = highlightStyle.value

        val drawIndividual = mode.value != "Base Finder" || showIndicators.value
        if (drawIndividual) {
            // Get and fully drain one ad-hoc render layer's buffer before
            // starting the other: context.consumers() is a
            // VertexConsumerProvider.Immediate, which auto-flushes/ends
            // whichever ad-hoc (non-standard) layer was previously
            // "building" as soon as a *different* one starts -- so getting
            // both buffers up front and interleaving vertex() calls across
            // them (as this used to do) ends up writing into an
            // already-ended BufferBuilder and crashes with
            // IllegalStateException("Not building!").
            if (style != "Outline") {
                val quadBuffer = context.consumers().getBuffer(EspRenderLayers.NO_DEPTH_QUADS)
                for (pos in cachedIndividual) {
                    EspRendering.drawSolidBox(matrices, quadBuffer, camPos, Box(pos), color)
                }
            }
            if (style != "Solid") {
                val lineBuffer = context.consumers().getBuffer(EspRenderLayers.NO_DEPTH_LINES)
                for (pos in cachedIndividual) {
                    EspRendering.drawBox(matrices, lineBuffer, camPos, Box(pos), color)
                }
            }
        }

        if (mode.value == "Base Finder" && showClusters.value && cachedClusters.isNotEmpty()) {
            val lineBuffer = context.consumers().getBuffer(EspRenderLayers.NO_DEPTH_LINES)
            for (cluster in cachedClusters) {
                EspRendering.drawBox(matrices, lineBuffer, camPos, cluster.box, color, width = 3f)
            }
        }
    }

    override fun onRender(context: DrawContext, tickCounter: RenderTickCounter) {
        if (mode.value == "Base Finder" && showClusters.value && cachedClusters.isNotEmpty()) {
            renderClusterLabels(context)
        }

        if (highlightStyle.value == "None") return
        if (!tracer.value) return
        if (cachedIndividual.isEmpty()) return

        val camera = mc.gameRenderer.camera
        val camPos = camera.cameraPos
        val fov = mc.options.fov.value.toFloat()
        val screenW = mc.window.scaledWidth
        val screenH = mc.window.scaledHeight
        val anchorX = screenW / 2
        val anchorY = screenH / 2
        val color = highlightColor.value

        for (pos in cachedIndividual) {
            val point = EspProjection.project(camPos, camera.yaw, camera.pitch, fov, screenW, screenH, Box(pos).center) ?: continue
            EspRendering.drawScreenLine(context, anchorX, anchorY, point.first, point.second, color)
        }
    }

    private fun renderClusterLabels(context: DrawContext) {
        val camera = mc.gameRenderer.camera
        val camPos = camera.cameraPos
        val fov = mc.options.fov.value.toFloat()
        val screenW = mc.window.scaledWidth
        val screenH = mc.window.scaledHeight

        for (cluster in cachedClusters) {
            val point = EspProjection.project(camPos, camera.yaw, camera.pitch, fov, screenW, screenH, cluster.center) ?: continue
            val label = "Base (${cluster.size})"
            val width = mc.textRenderer.getWidth(label)
            context.drawTextWithShadow(mc.textRenderer, label, point.first - width / 2, point.second, highlightColor.value)
        }
    }
}
