package dev.finn.aero.gui

import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Thin wrapper around [GuiGraphicsExtractor.enableScissor]/[GuiGraphicsExtractor.disableScissor]
 * (vanilla API, confirmed present -- just unused anywhere in `gui/` before
 * this) for clipping a rect of content, e.g. an expanded dropdown or popup
 * that would otherwise draw outside the ClickGUI panel bounds.
 */
object Scissor {
    /** Clips [block]'s draw calls to the given rect (in screen/GUI coordinates), always restoring afterward. */
    inline fun clip(context: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, block: () -> Unit) {
        context.enableScissor(x0, y0, x1, y1)
        try {
            block()
        } finally {
            context.disableScissor()
        }
    }
}
