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
 * Only the two `color(...)` entry points need overriding -- everything
 * else (position/uv/light/normal/etc.) passes straight through to
 * [delegate] unchanged.
 */
class AlphaScalingVertexConsumer(
    private val delegate: VertexConsumer,
    private val alphaScale: Float,
) : VertexConsumer {
    override fun vertex(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.vertex(x, y, z)
        return this
    }

    override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
        val scaled = (alpha * alphaScale).toInt().coerceIn(0, 255)
        delegate.color(red, green, blue, scaled)
        return this
    }

    override fun color(argb: Int): VertexConsumer {
        val a = (argb ushr 24) and 0xFF
        val scaled = (a * alphaScale).toInt().coerceIn(0, 255)
        delegate.color((scaled shl 24) or (argb and 0x00FFFFFF))
        return this
    }

    override fun texture(u: Float, v: Float): VertexConsumer {
        delegate.texture(u, v)
        return this
    }

    override fun overlay(u: Int, v: Int): VertexConsumer {
        delegate.overlay(u, v)
        return this
    }

    override fun light(u: Int, v: Int): VertexConsumer {
        delegate.light(u, v)
        return this
    }

    override fun normal(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.normal(x, y, z)
        return this
    }

    override fun lineWidth(width: Float): VertexConsumer {
        delegate.lineWidth(width)
        return this
    }
}
