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
}
