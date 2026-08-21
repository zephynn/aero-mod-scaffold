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
 * Only the two `setColor(...)` entry points need overriding -- everything
 * else (position/uv/light/normal/etc.) passes straight through to
 * [delegate] unchanged.
 */
class AlphaScalingVertexConsumer(
    private val delegate: VertexConsumer,
    private val alphaScale: Float,
) : VertexConsumer {
    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.addVertex(x, y, z)
        return this
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
        val scaled = (alpha * alphaScale).toInt().coerceIn(0, 255)
        delegate.setColor(red, green, blue, scaled)
        return this
    }

    override fun setColor(argb: Int): VertexConsumer {
        val a = (argb ushr 24) and 0xFF
        val scaled = (a * alphaScale).toInt().coerceIn(0, 255)
        delegate.setColor((scaled shl 24) or (argb and 0x00FFFFFF))
        return this
    }

    override fun setUv(u: Float, v: Float): VertexConsumer {
        delegate.setUv(u, v)
        return this
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer {
        delegate.setUv1(u, v)
        return this
    }

    override fun setUv2(u: Int, v: Int): VertexConsumer {
        delegate.setUv2(u, v)
        return this
    }

    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.setNormal(x, y, z)
        return this
    }

    override fun setLineWidth(width: Float): VertexConsumer {
        delegate.setLineWidth(width)
        return this
    }
}
