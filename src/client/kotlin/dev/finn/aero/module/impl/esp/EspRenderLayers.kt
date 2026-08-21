package dev.finn.aero.module.impl.esp

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import dev.finn.aero.client.AeroClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.RenderSetup

/**
 * A "lines" render layer with depth testing disabled, so ESP boxes/tracers
 * draw through walls instead of being occluded like normal geometry --
 * vanilla's own outline system gets this for free for entities (see
 * EntityRendererMixin), but block-based ESP (Chest ESP) has no equivalent,
 * so it needs its own no-depth layer to draw into.
 *
 * There's no public API for "the LINES pipeline, but with depth off":
 * RenderPipelines only exposes the *finished* LINES/LINES_TRANSLUCENT
 * pipelines, not the RENDERTYPE_LINES_SNIPPET they're both built from, and
 * RenderLayer's constructor is private. Vanilla builds LINES_TRANSLUCENT as
 * exactly "the lines snippet plus one extra builder call"
 * (withDepthWrite(false)) -- this does the same thing, reflectively
 * borrowing that private snippet rather than re-declaring the vertex
 * format/shaders/blend mode by hand and risking a mismatch, then adding
 * withDepthTestFunction(NO_DEPTH_TEST) on top.
 *
 * If any of that reflection ever breaks (an obfuscation/name change), this
 * falls back to normal depth-tested lines rather than crashing -- ESP still
 * works, it just stops seeing through walls.
 */
object EspRenderLayers {
    val NO_DEPTH_LINES: RenderLayer by lazy { buildNoDepthLines() }

    /**
     * Filled-quad no-depth layer for X-Ray's "Solid"/"Both" highlight
     * style. Unlike [NO_DEPTH_LINES], this doesn't need reflection:
     * RenderPipelines.DEBUG_QUADS is a *finished* public RenderPipeline
     * (no private snippet field backs it the way RENDERTYPE_LINES_SNIPPET
     * does for lines), and RenderPipeline itself exposes public getters
     * for every field a Snippet needs -- so this just re-wraps
     * DEBUG_QUADS's own settings into a Snippet, then builds a copy with
     * depth testing/writing turned off on top.
     */
    val NO_DEPTH_QUADS: RenderLayer by lazy { buildNoDepthQuads() }

    private fun buildNoDepthLines(): RenderLayer {
        return try {
            val snippetField = RenderPipelines::class.java.getDeclaredField("RENDERTYPE_LINES_SNIPPET")
            snippetField.isAccessible = true
            val snippet = snippetField.get(null) as RenderPipeline.Snippet

            val pipeline = RenderPipeline.builder(snippet)
                .withLocation("aero/pipeline/esp_lines_no_depth")
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build()

            val renderSetup = RenderSetup.builder(pipeline).build()

            val ctor = RenderLayer::class.java.getDeclaredConstructor(String::class.java, RenderSetup::class.java)
            ctor.isAccessible = true
            val layer = ctor.newInstance("aero_esp_lines_no_depth", renderSetup) as RenderLayer

            AeroClient.LOGGER.info("Aero: built no-depth ESP line layer.")
            layer
        } catch (e: Exception) {
            AeroClient.LOGGER.warn("Aero: couldn't build a no-depth ESP line layer, falling back to depth-tested lines (ESP won't show through walls).", e)
            RenderLayers.lines()
        }
    }

    private fun buildNoDepthQuads(): RenderLayer {
        return try {
            val base = RenderPipelines.DEBUG_QUADS

            val snippet = RenderPipeline.Snippet(
                java.util.Optional.of(base.vertexShader),
                java.util.Optional.of(base.fragmentShader),
                java.util.Optional.of(base.shaderDefines),
                java.util.Optional.of(base.samplers),
                java.util.Optional.of(base.uniforms),
                base.blendFunction,
                java.util.Optional.of(base.depthTestFunction),
                java.util.Optional.of(base.polygonMode),
                java.util.Optional.of(base.isCull),
                java.util.Optional.of(base.isWriteColor),
                java.util.Optional.of(base.isWriteAlpha),
                java.util.Optional.of(base.isWriteDepth),
                java.util.Optional.of(base.colorLogic),
                java.util.Optional.of(base.vertexFormat),
                java.util.Optional.of(base.vertexFormatMode),
            )

            val pipeline = RenderPipeline.builder(snippet)
                .withLocation("aero/pipeline/esp_quads_no_depth")
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build()

            val renderSetup = RenderSetup.builder(pipeline).build()

            val ctor = RenderLayer::class.java.getDeclaredConstructor(String::class.java, RenderSetup::class.java)
            ctor.isAccessible = true
            val layer = ctor.newInstance("aero_esp_quads_no_depth", renderSetup) as RenderLayer

            AeroClient.LOGGER.info("Aero: built no-depth ESP quad layer.")
            layer
        } catch (e: Exception) {
            AeroClient.LOGGER.warn("Aero: couldn't build a no-depth ESP quad layer, falling back to depth-tested filled box.", e)
            RenderLayers.debugFilledBox()
        }
    }
}
