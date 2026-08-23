package dev.finn.aero.module.impl.esp

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import dev.finn.aero.client.AeroClient
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes

/**
 * "Lines"/"quads" render types with depth testing disabled, so ESP
 * boxes/tracers draw through walls instead of being occluded like normal
 * geometry -- vanilla's own outline system gets this for free for entities
 * (see EntityRendererMixin), but block-based ESP (Chest ESP, X-Ray) has no
 * equivalent, so it needs its own no-depth types to draw into.
 *
 * 1.21.11's pipeline builder is simpler than 26.2's -- depth test is a
 * single [DepthTestFunction] enum value rather than a DepthStencilState
 * record, and there's no per-target bind-group-layout/vertex-binding
 * plumbing to copy, so this just carries over shader, polygon mode, cull,
 * and vertex format from the base pipeline. `RenderType.create` is still
 * package-private, so that part stays reflective.
 *
 * If any of that ever breaks, this falls back to normal depth-tested
 * lines/boxes rather than crashing -- ESP still works, it just stops seeing
 * through walls.
 */
object EspRenderLayers {
    val NO_DEPTH_LINES: RenderType by lazy {
        build("aero_esp_lines_no_depth", RenderPipelines.LINES) { RenderTypes.lines() }
    }

    val NO_DEPTH_QUADS: RenderType by lazy {
        build("aero_esp_quads_no_depth", RenderPipelines.DEBUG_QUADS) { RenderTypes.debugFilledBox() }
    }

    /** Copies [base] with depth testing and depth writing turned off, wrapped in a RenderType named [name]. */
    private fun build(name: String, base: RenderPipeline, fallback: () -> RenderType): RenderType {
        return try {
            val builder = RenderPipeline.builder()
                .withLocation("aero/pipeline/$name")
                .withVertexShader(base.vertexShader)
                .withFragmentShader(base.fragmentShader)
                .withPolygonMode(base.polygonMode)
                .withCull(base.isCull)
                .withVertexFormat(base.vertexFormat, base.vertexFormatMode)
                // NO_DEPTH_TEST + no depth write == "draw through walls".
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)

            base.blendFunction.ifPresentOrElse({ builder.withBlend(it) }, { builder.withoutBlend() })
            builder.withColorLogic(base.colorLogic)

            val defines = base.shaderDefines
            for (flag in defines.flags()) builder.withShaderDefine(flag)
            for ((key, value) in defines.values()) builder.withShaderDefine(key, value.toIntOrNull() ?: 0)

            val renderSetup = RenderSetup.builder(builder.build()).createRenderSetup()

            // RenderType.create is package-private; everything else above is public API.
            val create = RenderType::class.java.getDeclaredMethod("create", String::class.java, RenderSetup::class.java)
            create.isAccessible = true
            val layer = create.invoke(null, name, renderSetup) as RenderType

            AeroClient.LOGGER.info("Aero: built no-depth render type '{}'.", name)
            layer
        } catch (e: Exception) {
            AeroClient.LOGGER.warn("Aero: couldn't build no-depth render type '$name', falling back to a depth-tested one (ESP won't show through walls).", e)
            fallback()
        }
    }
}
