package dev.finn.aero.module.impl.xray

import com.mojang.blaze3d.vertex.VertexConsumer

/**
 * Thin decorator around a real chunk-build [VertexConsumer] that scales
 * every emitted vertex's alpha channel by [alphaScale]. Used by
 * `BlockModelRendererMixin` to give non-target blocks real, continuously
 * adjustable transparency while X-Ray is active, instead of the binary
 * AIR-swap [dev.finn.aero.mixin.ChunkRendererRegionMixin] does at
 * `terrainOpacity == 0f`.
 *
 * 1.21.11 still meshes blocks vertex-by-vertex through a VertexConsumer
 * (26.x's per-quad BlockQuadOutput doesn't exist here), so this wraps
 * [delegate] via Kotlin interface delegation and only overrides the two
 * setColor overloads -- ModelBlockRenderer calls addVertex/setColor/setUv/
 * etc. sequentially on the same consumer reference rather than chaining
 * off each call's return value, so intercepting just setColor is enough;
 * everything else forwards straight through unmodified.
 */
class AlphaScalingVertexConsumer(
    private val delegate: VertexConsumer,
    private val alphaScale: Float,
) : VertexConsumer by delegate {
    override fun setColor(argb: Int): VertexConsumer {
        val alpha = (argb ushr 24) and 0xFF
        val scaled = (alpha * alphaScale).toInt().coerceIn(0, 255)
        delegate.setColor((scaled shl 24) or (argb and 0x00FFFFFF))
        return this
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
        val scaled = (alpha * alphaScale).toInt().coerceIn(0, 255)
        delegate.setColor(red, green, blue, scaled)
        return this
    }
}
