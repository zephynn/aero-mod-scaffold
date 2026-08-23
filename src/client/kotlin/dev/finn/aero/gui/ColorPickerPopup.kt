package dev.finn.aero.gui

import dev.finn.aero.setting.ColorSetting
import net.minecraft.client.gui.GuiGraphics

/**
 * Photoshop-style colour picker popup: a saturation/value square plus a
 * vertical hue slider, both with draggable indicators, live-updating the
 * target [ColorSetting] as ARGB while dragging. Opened from a click on a
 * [ColorSetting] row in [ClickGuiScreen] (replacing the old
 * cycle-through-PRESET_COLORS behaviour) and closed on an outside click or
 * the small close affordance in its corner. No OK/Cancel -- every drag
 * commits straight to `setting.value`, matching every other setting in
 * this GUI.
 */
class ColorPickerPopup {
    companion object {
        const val SV_SIZE = 100
        const val HUE_WIDTH = 14
        const val GAP = 8
        const val PADDING = 8
        const val CLOSE_SIZE = 10

        const val CONTENT_WIDTH = SV_SIZE + GAP + HUE_WIDTH
        const val WIDTH = CONTENT_WIDTH + PADDING * 2
        const val HEIGHT = SV_SIZE + PADDING * 2 + 14

        private const val COLOR_BG = 0xF0111214.toInt()
        private const val COLOR_BORDER = 0x60FFFFFF.toInt()
    }

    private var target: ColorSetting? = null
    private var x = 0
    private var y = 0

    private var hue = 0f
    private var sat = 0f
    private var value = 0f
    private var alpha = 255

    private var draggingSv = false
    private var draggingHue = false

    val isOpen: Boolean get() = target != null

    fun open(setting: ColorSetting, anchorX: Int, anchorY: Int, panelX0: Int, panelY0: Int, panelX1: Int, panelY1: Int) {
        target = setting
        val hsv = FloatArray(3)
        java.awt.Color.RGBtoHSB((setting.value shr 16) and 0xFF, (setting.value shr 8) and 0xFF, setting.value and 0xFF, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        alpha = (setting.value ushr 24) and 0xFF

        // Anchor near the click, then clamp so the whole popup stays inside the GUI panel.
        x = anchorX.coerceIn(panelX0, (panelX1 - WIDTH).coerceAtLeast(panelX0))
        y = anchorY.coerceIn(panelY0, (panelY1 - HEIGHT).coerceAtLeast(panelY0))
    }

    fun close() {
        target = null
        draggingSv = false
        draggingHue = false
    }

    private fun svX() = x + PADDING
    private fun svY() = y + PADDING
    private fun hueX() = svX() + SV_SIZE + GAP
    private fun hueY() = svY()

    fun render(context: GuiGraphics, mouseX: Int, mouseY: Int) {
        val setting = target ?: return

        context.fill(x, y, x + WIDTH, y + HEIGHT, COLOR_BG)
        drawBorder(context, x, y, WIDTH, HEIGHT, COLOR_BORDER)

        // Close affordance, top-right corner.
        val closeX = x + WIDTH - PADDING - CLOSE_SIZE
        val closeY = y + 3
        val closeHovered = mouseX in closeX..(closeX + CLOSE_SIZE) && mouseY in closeY..(closeY + CLOSE_SIZE)
        context.drawString(
            net.minecraft.client.Minecraft.getInstance().font,
            "x", closeX, closeY, if (closeHovered) 0xFFFFFFFF.toInt() else 0xFF83878E.toInt(), true,
        )

        renderSvSquare(context)
        renderHueSlider(context)

        // Live swatch preview at the bottom.
        val swatchY = y + PADDING + SV_SIZE + 4
        context.fill(svX(), swatchY, svX() + CONTENT_WIDTH, swatchY + 10, setting.value)
        drawBorder(context, svX(), swatchY, CONTENT_WIDTH, 10, COLOR_BORDER)
    }

    private fun renderSvSquare(context: GuiGraphics) {
        val sx = svX()
        val sy = svY()
        // Coarse-grained gradient: cheap per-pixel-ish blend using a small
        // grid of fill() calls rather than a real shader -- plenty smooth
        // at this popup's size.
        val steps = 20
        val cell = SV_SIZE / steps
        for (ix in 0 until steps) {
            for (iy in 0 until steps) {
                val s = ix.toFloat() / (steps - 1)
                val v = 1f - iy.toFloat() / (steps - 1)
                val rgb = java.awt.Color.HSBtoRGB(hue, s, v) and 0xFFFFFF
                val px = sx + ix * cell
                val py = sy + iy * cell
                context.fill(px, py, px + cell + 1, py + cell + 1, (0xFF shl 24) or rgb)
            }
        }
        drawBorder(context, sx, sy, SV_SIZE, SV_SIZE, COLOR_BORDER)

        // SV indicator.
        val indX = sx + (sat * SV_SIZE).toInt()
        val indY = sy + ((1f - value) * SV_SIZE).toInt()
        drawRing(context, indX, indY, 3, if (value > 0.6f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    }

    private fun renderHueSlider(context: GuiGraphics) {
        val hx = hueX()
        val hy = hueY()
        val steps = 30
        val cell = SV_SIZE / steps
        for (i in 0 until steps) {
            val h = i.toFloat() / steps
            val rgb = java.awt.Color.HSBtoRGB(h, 1f, 1f) and 0xFFFFFF
            val py = hy + i * cell
            context.fill(hx, py, hx + HUE_WIDTH, py + cell + 1, (0xFF shl 24) or rgb)
        }
        drawBorder(context, hx, hy, HUE_WIDTH, SV_SIZE, COLOR_BORDER)

        val indY = hy + (hue * SV_SIZE).toInt()
        context.fill(hx - 2, indY - 1, hx + HUE_WIDTH + 2, indY + 1, 0xFFFFFFFF.toInt())
    }

    private fun drawRing(context: GuiGraphics, cx: Int, cy: Int, r: Int, color: Int) {
        context.fill(cx - r, cy - r, cx + r, cy - r + 1, color)
        context.fill(cx - r, cy + r - 1, cx + r, cy + r, color)
        context.fill(cx - r, cy - r, cx - r + 1, cy + r, color)
        context.fill(cx + r - 1, cy - r, cx + r, cy + r, color)
    }

    private fun drawBorder(context: GuiGraphics, bx: Int, by: Int, w: Int, h: Int, color: Int) {
        context.fill(bx, by, bx + w, by + 1, color)
        context.fill(bx, by + h - 1, bx + w, by + h, color)
        context.fill(bx, by, bx + 1, by + h, color)
        context.fill(bx + w - 1, by, bx + w, by + h, color)
    }

    /** Returns true if the click was consumed (either it hit something inside the popup, or closed it). */
    fun mouseClicked(mouseX: Double, mouseY: Double): Boolean {
        if (!isOpen) return false

        val closeX = x + WIDTH - PADDING - CLOSE_SIZE
        val closeY = y + 3
        if (mouseX in closeX.toDouble()..(closeX + CLOSE_SIZE).toDouble() && mouseY in closeY.toDouble()..(closeY + CLOSE_SIZE).toDouble()) {
            close()
            return true
        }

        val sx = svX()
        val sy = svY()
        if (mouseX in sx.toDouble()..(sx + SV_SIZE).toDouble() && mouseY in sy.toDouble()..(sy + SV_SIZE).toDouble()) {
            draggingSv = true
            applySvDrag(mouseX, mouseY)
            return true
        }

        val hx = hueX()
        val hy = hueY()
        if (mouseX in hx.toDouble()..(hx + HUE_WIDTH).toDouble() && mouseY in hy.toDouble()..(hy + SV_SIZE).toDouble()) {
            draggingHue = true
            applyHueDrag(mouseY)
            return true
        }

        if (mouseX in x.toDouble()..(x + WIDTH).toDouble() && mouseY in y.toDouble()..(y + HEIGHT).toDouble()) {
            // Clicked inside the popup chrome but not an interactive part -- swallow it, stay open.
            return true
        }

        // Outside click -- close, but don't consume so the caller can still process it if needed.
        close()
        return false
    }

    fun mouseDragged(mouseX: Double, mouseY: Double): Boolean {
        if (draggingSv) {
            applySvDrag(mouseX, mouseY)
            return true
        }
        if (draggingHue) {
            applyHueDrag(mouseY)
            return true
        }
        return false
    }

    fun mouseReleased(): Boolean {
        val wasDragging = draggingSv || draggingHue
        draggingSv = false
        draggingHue = false
        return wasDragging
    }

    private fun applySvDrag(mouseX: Double, mouseY: Double) {
        val sx = svX()
        val sy = svY()
        sat = (((mouseX - sx) / SV_SIZE)).toFloat().coerceIn(0f, 1f)
        value = (1f - ((mouseY - sy) / SV_SIZE)).toFloat().coerceIn(0f, 1f)
        commit()
    }

    private fun applyHueDrag(mouseY: Double) {
        val hy = hueY()
        hue = (((mouseY - hy) / SV_SIZE)).toFloat().coerceIn(0f, 1f)
        commit()
    }

    private fun commit() {
        val setting = target ?: return
        val rgb = java.awt.Color.HSBtoRGB(hue, sat, value) and 0xFFFFFF
        setting.value = (alpha shl 24) or rgb
    }
}
