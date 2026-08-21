package dev.finn.aero.module.impl.xray

import com.mojang.blaze3d.vertex.QuadInstance
import net.minecraft.client.renderer.block.BlockQuadOutput
import net.minecraft.client.resources.model.geometry.BakedQuad

/**
 * Thin decorator around a real chunk-build [BlockQuadOutput] that scales
 * every emitted quad's alpha channel by [alphaScale]. Used by
 * `BlockModelRendererMixin` to give non-target blocks real, continuously
 * adjustable transparency while X-Ray is active, instead of the binary
 * AIR-swap [dev.finn.aero.mixin.ChunkRendererRegionMixin] does at
 * `terrainOpacity == 0f`.
 *
 * 26.x meshes blocks a whole quad at a time (BlockQuadOutput#put) rather
 * than vertex by vertex through a VertexConsumer, so the alpha edit lands
 * on the four packed ARGB corner colours [QuadInstance] carries. That
 * object is vanilla's own scratch instance, reused per quad and consumed
 * synchronously by [delegate], so editing it in place is safe.
 */
class AlphaScalingQuadOutput(
    private val delegate: BlockQuadOutput,
    private val alphaScale: Float,
) : BlockQuadOutput {
    override fun put(x: Float, y: Float, z: Float, quad: BakedQuad, instance: QuadInstance) {
        for (corner in 0 until 4) {
            val argb = instance.getColor(corner)
            val alpha = ((argb ushr 24) and 0xFF)
            val scaled = (alpha * alphaScale).toInt().coerceIn(0, 255)
            instance.setColor(corner, (scaled shl 24) or (argb and 0x00FFFFFF))
        }
        delegate.put(x, y, z, quad, instance)
    }
}
