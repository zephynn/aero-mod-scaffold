package dev.finn.aero.gui

import dev.finn.aero.config.ClientSettings
import dev.finn.aero.config.ConfigManager
import dev.finn.aero.config.Theme
import dev.finn.aero.setting.ColorSetting
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Aero's general client settings -- deliberately its own screen rather than
 * folded into the ClickGUI (which stays module-only): theme and behaviour
 * aren't per-module concerns. Opened from the gear icon in the ClickGUI's
 * chrome bar.
 *
 * Same immediate-mode rendering approach as ClickGuiScreen, kept as a
 * separate, smaller implementation rather than sharing a base class --
 * there's not enough shared behaviour yet to be worth the abstraction.
 */
class SettingsScreen : Screen(Component.literal("Aero Settings")) {

    private companion object {
        const val WIDTH = 220
        const val HEIGHT = 228
        const val CHROME_HEIGHT = 20
        const val ROW_HEIGHT = 20

        const val SWATCH_SIZE = 18
        const val SWATCH_GAP = 3

        const val COLOR_DIM = 0x38000000.toInt()
        const val COLOR_BORDER = 0x50FFFFFF.toInt()
        const val COLOR_TEXT = 0xFFE4E7EB.toInt()
        const val COLOR_TEXT_DIM = 0xFF83878E.toInt()
        const val COLOR_TEXT_FAINT = 0xFF4C4F55.toInt()
        const val COLOR_TRACK_OFF = 0x50FFFFFF.toInt()

        const val ANIM_FAST = 0.55f
        const val ANIM_WINDOW = 0.45f
    }

    private var guiX = 0
    private var guiY = 0

    private var windowAnim = 0f
    private var closing = false

    private var rebindingGuiKey = false

    private val hoverAnim = HashMap<Any, Float>()

    /** Photoshop-style popup opened by the "custom" accent swatch, same component ClickGuiScreen's ColorSettings use. */
    private val colorPicker = ColorPickerPopup()

    /** Live-bound while the picker's open -- Theme.accent is synced from this every frame the popup is open. */
    private var accentColorSetting: ColorSetting? = null

    private val colorChrome get() = (0xD0 shl 24) or Theme.darkenedBackground(0.20f)
    private val colorFieldBg get() = (0xC8 shl 24) or Theme.darkenedBackground(0.22f)
    private val colorBg get() = (0xC0 shl 24) or Theme.darkenedBackground(0f)
    private val accent get() = (0xFF shl 24) or Theme.accent

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        guiX = (width - WIDTH) / 2
        guiY = (height - HEIGHT) / 2
    }

    override fun onClose() {
        if (!closing) {
            closing = true
            return
        }
        super.onClose()
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        advanceWindowAnim()
        if (windowAnim <= 0.001f && closing) {
            minecraft?.setScreen(null)
            return
        }

        val eased = easeOutCubic(windowAnim)
        val slide = ((1f - eased) * 6).toInt()
        context.fill(0, 0, width, height, scaleAlpha(COLOR_DIM, eased))

        val gy = guiY + slide
        fill(context, guiX, gy, guiX + WIDTH, gy + HEIGHT, colorBg)
        renderChrome(context, gy, mouseX, mouseY)

        var y = gy + CHROME_HEIGHT + 8
        y = renderAccentSection(context, y, mouseX, mouseY)
        y += 6
        y = renderKeybindRow(context, y, mouseX, mouseY)
        y = renderGuiStyleRow(context, y, mouseX, mouseY)
        y = renderReducedMotionRow(context, y, mouseX, mouseY)
        y += 6
        y = renderNotificationPositionRow(context, y, mouseX, mouseY)
        y = renderNotificationDurationRow(context, y, mouseX, mouseY)
        y += 6
        renderResetButton(context, y, mouseX, mouseY)

        if (colorPicker.isOpen) {
            // Live-apply while dragging, same as ClickGuiScreen's ColorSetting
            // popups do -- Theme.accent isn't itself a Setting, so this is
            // the sync point instead of the popup writing straight into it.
            accentColorSetting?.let { Theme.accent = it.value and 0x00FFFFFF }
            Scissor.clip(context, guiX, gy, guiX + WIDTH, gy + HEIGHT) {
                colorPicker.render(context, mouseX, mouseY)
            }
        }
    }

    private fun advanceWindowAnim() {
        val target = if (closing) 0f else 1f
        if (ClientSettings.reducedMotion) {
            windowAnim = target
            return
        }
        windowAnim = when {
            windowAnim < target -> (windowAnim + ANIM_WINDOW).coerceAtMost(target)
            windowAnim > target -> (windowAnim - ANIM_WINDOW).coerceAtLeast(target)
            else -> windowAnim
        }
    }

    private fun renderChrome(context: GuiGraphics, gy: Int, mouseX: Int, mouseY: Int) {
        fill(context, guiX, gy, guiX + WIDTH, gy + CHROME_HEIGHT, colorChrome)
        val backHovered = mouseX in guiX..(guiX + 16) && mouseY in gy..(gy + CHROME_HEIGHT)
        text(context, "‹", guiX + 7, gy + 6, if (backHovered) accent else COLOR_TEXT_DIM)
        text(context, "SETTINGS", guiX + 18, gy + 6, COLOR_TEXT_DIM)
    }

    private fun renderAccentSection(context: GuiGraphics, startY: Int, mouseX: Int, mouseY: Int): Int {
        var y = startY
        val x = guiX + 12
        text(context, "ACCENT", x, y, COLOR_TEXT_FAINT)
        y += 12

        for (i in Theme.ACCENT_PRESETS.indices) {
            val sx = x + i * (SWATCH_SIZE + SWATCH_GAP)
            val color = Theme.ACCENT_PRESETS[i]
            val selected = color == Theme.accent
            val hAnim = animatedHover("swatch:$i", mouseX in sx..(sx + SWATCH_SIZE) && mouseY in y..(y + SWATCH_SIZE))
            fill(context, sx, y, sx + SWATCH_SIZE, y + SWATCH_SIZE, (0xFF shl 24) or color)
            if (selected) {
                drawBorder(context, sx - 2, y - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, COLOR_TEXT)
            } else if (hAnim > 0.02f) {
                drawBorder(context, sx - 2, y - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, scaleAlpha(COLOR_BORDER, hAnim))
            }
        }

        // "Custom" swatch, right after the presets -- always shows the
        // *current* accent (so it still looks selected/consistent even when
        // that colour isn't one of the fixed presets) and opens the same
        // SV-square + hue-slider popup ColorSettings use in the ClickGUI.
        val customIndex = Theme.ACCENT_PRESETS.size
        val customX = x + customIndex * (SWATCH_SIZE + SWATCH_GAP)
        val customSelected = Theme.accent !in Theme.ACCENT_PRESETS
        val customHover = animatedHover("swatch:custom", mouseX in customX..(customX + SWATCH_SIZE) && mouseY in y..(y + SWATCH_SIZE))
        fill(context, customX, y, customX + SWATCH_SIZE, y + SWATCH_SIZE, (0xFF shl 24) or Theme.accent)
        text(context, "+", customX + SWATCH_SIZE / 2 - 2, y + SWATCH_SIZE / 2 - 4, if (customSelected) COLOR_TEXT else COLOR_TEXT_DIM)
        if (customSelected) {
            drawBorder(context, customX - 2, y - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, COLOR_TEXT)
        } else if (customHover > 0.02f) {
            drawBorder(context, customX - 2, y - 2, SWATCH_SIZE + 4, SWATCH_SIZE + 4, scaleAlpha(COLOR_BORDER, customHover))
        }

        return y + SWATCH_SIZE + 8
    }

    private fun renderKeybindRow(context: GuiGraphics, y: Int, mouseX: Int, mouseY: Int): Int {
        val x = guiX + 12
        val w = WIDTH - 24
        text(context, "OPEN CLICKGUI", x, y + 5, COLOR_TEXT_DIM)

        val label = if (rebindingGuiKey) "..." else keyName(ClientSettings.guiKeybind)
        val chipW = font.width(label) + 10
        val chipX = x + w - chipW
        val chipY = y + 2
        fill(context, chipX, chipY, chipX + chipW, chipY + 12, colorFieldBg)
        text(context, label, chipX + 5, chipY + 2, if (rebindingGuiKey) accent else COLOR_TEXT)

        return y + ROW_HEIGHT
    }

    private fun renderGuiStyleRow(context: GuiGraphics, y: Int, mouseX: Int, mouseY: Int): Int {
        val x = guiX + 12
        val w = WIDTH - 24
        text(context, "GUI STYLE", x, y + 5, COLOR_TEXT_DIM)

        val label = ClientSettings.guiStyle
        val chipW = font.width(label) + 10
        val chipX = x + w - chipW
        val chipY = y + 2
        fill(context, chipX, chipY, chipX + chipW, chipY + 12, colorFieldBg)
        text(context, label, chipX + 5, chipY + 2, COLOR_TEXT)

        return y + ROW_HEIGHT
    }

    private fun renderReducedMotionRow(context: GuiGraphics, y: Int, mouseX: Int, mouseY: Int): Int {
        val x = guiX + 12
        val w = WIDTH - 24
        text(context, "REDUCED MOTION", x, y + 5, COLOR_TEXT_DIM)
        drawToggle(context, x + w - 24, y + 3, 24, 12, if (ClientSettings.reducedMotion) 1f else 0f)
        return y + ROW_HEIGHT
    }

    private fun renderNotificationPositionRow(context: GuiGraphics, y: Int, mouseX: Int, mouseY: Int): Int {
        val x = guiX + 12
        val w = WIDTH - 24
        text(context, "NOTIFICATIONS", x, y + 5, COLOR_TEXT_FAINT)
        val label = ClientSettings.notificationPosition
        val chipW = font.width(label) + 10
        val chipX = x + w - chipW
        val chipY = y + 2
        fill(context, chipX, chipY, chipX + chipW, chipY + 12, colorFieldBg)
        text(context, label, chipX + 5, chipY + 2, COLOR_TEXT)
        return y + ROW_HEIGHT
    }

    private fun renderNotificationDurationRow(context: GuiGraphics, y: Int, mouseX: Int, mouseY: Int): Int {
        val x = guiX + 12
        val w = WIDTH - 24
        text(context, "DURATION", x, y + 5, COLOR_TEXT_DIM)
        val label = "${ClientSettings.notificationDurationMs / 1000.0}s"
        text(context, label, x + w - font.width(label) - 24, y + 5, COLOR_TEXT)

        val minusX = x + w - 20
        val plusX = x + w - 8
        text(context, "-", minusX, y + 5, COLOR_TEXT_DIM)
        text(context, "+", plusX, y + 5, COLOR_TEXT_DIM)
        return y + ROW_HEIGHT
    }

    private fun renderResetButton(context: GuiGraphics, y: Int, mouseX: Int, mouseY: Int) {
        val x = guiX + 12
        val w = WIDTH - 24
        val h = 16
        val hAnim = animatedHover("reset", mouseX in x..(x + w) && mouseY in y..(y + h))
        fill(context, x, y, x + w, y + h, colorFieldBg)
        if (hAnim > 0.02f) fill(context, x, y, x + w, y + h, scaleAlpha(0x20FFFFFF.toInt(), hAnim))
        val label = "Reset to defaults"
        text(context, label, x + (w - font.width(label)) / 2, y + 4, COLOR_TEXT_DIM)
    }

    // --- helpers -------------------------------------------------------------

    private fun fill(context: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        context.fill(x0, y0, x1, y1, scaleAlpha(color, easeOutCubic(windowAnim)))
    }

    private fun text(context: GuiGraphics, str: String, x: Int, y: Int, color: Int) {
        context.drawString(font, str, x, y, scaleAlpha(color, easeOutCubic(windowAnim)), true)
    }

    private fun drawBorder(context: GuiGraphics, x: Int, y: Int, w: Int, h: Int, color: Int) {
        fill(context, x, y, x + w, y + 1, color)
        fill(context, x, y + h - 1, x + w, y + h, color)
        fill(context, x, y, x + 1, y + h, color)
        fill(context, x + w - 1, y, x + w, y + h, color)
    }

    private fun drawToggle(context: GuiGraphics, x: Int, y: Int, w: Int, h: Int, anim: Float) {
        fill(context, x, y, x + w, y + h, lerpColor(COLOR_TRACK_OFF, accent, anim))
        val knobD = h - 4
        val knobX = x + 2 + ((w - knobD - 4) * anim).toInt()
        fill(context, knobX, y + 2, knobX + knobD, y + h - 2, if (anim > 0.5f) 0xFF0B0D0F.toInt() else COLOR_TEXT_FAINT)
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int = Anim.lerpColor(from, to, t)

    private fun animatedHover(key: Any, hovered: Boolean): Float =
        Anim.advance(hoverAnim, key, if (hovered) 1f else 0f, ANIM_FAST)

    private fun easeOutCubic(t: Float): Float = Anim.easeOutCubic(t)

    private fun scaleAlpha(color: Int, scale: Float): Int = Anim.scaleAlpha(color, scale)

    private fun keyName(keyCode: Int): String {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "NONE"
        return GLFW.glfwGetKeyName(keyCode, 0)?.uppercase() ?: "KEY_$keyCode"
    }

    // --- Input ---------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (closing) return false
        val mouseX = click.x()
        val mouseY = click.y()

        // Popup takes priority, same pattern as ClickGuiScreen -- covers
        // both an explicit close (the X) and an outside click (which
        // closes it without consuming, so the click still falls through
        // to whatever it actually hit below).
        if (colorPicker.isOpen) {
            val consumed = colorPicker.mouseClicked(mouseX, mouseY)
            if (!colorPicker.isOpen && accentColorSetting != null) {
                accentColorSetting = null
                ConfigManager.save()
            }
            if (consumed) return true
        }

        if (mouseX in guiX.toDouble()..(guiX + 16).toDouble() && mouseY in guiY.toDouble()..(guiY + CHROME_HEIGHT).toDouble()) {
            minecraft?.setScreen(GuiOpener.clickGuiScreen())
            return true
        }

        var y = guiY + CHROME_HEIGHT + 8
        val x = guiX + 12
        val swatchesY = y + 12
        for (i in Theme.ACCENT_PRESETS.indices) {
            val sx = x + i * (SWATCH_SIZE + SWATCH_GAP)
            if (mouseX in sx.toDouble()..(sx + SWATCH_SIZE).toDouble() && mouseY in swatchesY.toDouble()..(swatchesY + SWATCH_SIZE).toDouble()) {
                Theme.accent = Theme.ACCENT_PRESETS[i]
                ConfigManager.save()
                return true
            }
        }

        val customIndex = Theme.ACCENT_PRESETS.size
        val customX = x + customIndex * (SWATCH_SIZE + SWATCH_GAP)
        if (mouseX in customX.toDouble()..(customX + SWATCH_SIZE).toDouble() && mouseY in swatchesY.toDouble()..(swatchesY + SWATCH_SIZE).toDouble()) {
            val setting = ColorSetting("Accent", "Accent colour.", (0xFF shl 24) or Theme.accent)
            accentColorSetting = setting
            colorPicker.open(setting, customX, swatchesY + SWATCH_SIZE + 4, guiX, guiY + CHROME_HEIGHT, guiX + WIDTH, guiY + HEIGHT)
            return true
        }
        y += 12 + SWATCH_SIZE + 8 + 6

        val w = WIDTH - 24
        val kbLabel = keyName(ClientSettings.guiKeybind)
        val chipW = font.width(kbLabel) + 10
        val chipX = x + w - chipW
        val chipY = y + 2
        if (mouseX in chipX.toDouble()..(chipX + chipW).toDouble() && mouseY in chipY.toDouble()..(chipY + 12).toDouble()) {
            rebindingGuiKey = true
            return true
        }
        y += ROW_HEIGHT

        // GUI style chip -- cycles Accordion <-> Meteor, same click-anywhere-on-chip pattern as the keybind chip above.
        val styleLabel = ClientSettings.guiStyle
        val styleChipW = font.width(styleLabel) + 10
        val styleChipX = x + w - styleChipW
        val styleChipY = y + 2
        if (mouseX in styleChipX.toDouble()..(styleChipX + styleChipW).toDouble() && mouseY in styleChipY.toDouble()..(styleChipY + 12).toDouble()) {
            val styles = ClientSettings.GUI_STYLES
            val i = styles.indexOf(ClientSettings.guiStyle)
            ClientSettings.guiStyle = styles[(i + 1) % styles.size]
            ConfigManager.save()
            return true
        }
        y += ROW_HEIGHT

        if (mouseX in (x + w - 24).toDouble()..(x + w).toDouble() && mouseY in (y + 3).toDouble()..(y + 15).toDouble()) {
            ClientSettings.reducedMotion = !ClientSettings.reducedMotion
            ConfigManager.save()
            return true
        }
        y += ROW_HEIGHT + 6

        // Notification position chip.
        val posLabel = ClientSettings.notificationPosition
        val posChipW = font.width(posLabel) + 10
        val posChipX = x + w - posChipW
        val posChipY = y + 2
        if (mouseX in posChipX.toDouble()..(posChipX + posChipW).toDouble() && mouseY in posChipY.toDouble()..(posChipY + 12).toDouble()) {
            val positions = ClientSettings.NOTIFICATION_POSITIONS
            val i = positions.indexOf(ClientSettings.notificationPosition)
            ClientSettings.notificationPosition = positions[(i + 1) % positions.size]
            ConfigManager.save()
            return true
        }
        y += ROW_HEIGHT

        // Notification duration -/+.
        val minusX = x + w - 20
        val plusX = x + w - 8
        if (mouseX in minusX.toDouble()..(minusX + 8).toDouble() && mouseY in y.toDouble()..(y + 10).toDouble()) {
            ClientSettings.notificationDurationMs = (ClientSettings.notificationDurationMs - 500).coerceAtLeast(1000)
            ConfigManager.save()
            return true
        }
        if (mouseX in plusX.toDouble()..(plusX + 8).toDouble() && mouseY in y.toDouble()..(y + 10).toDouble()) {
            ClientSettings.notificationDurationMs = (ClientSettings.notificationDurationMs + 500).coerceAtMost(15000)
            ConfigManager.save()
            return true
        }
        y += ROW_HEIGHT + 6

        val h = 16
        if (mouseX in x.toDouble()..(x + w).toDouble() && mouseY in y.toDouble()..(y + h).toDouble()) {
            Theme.reset()
            ClientSettings.reset()
            ConfigManager.save()
            return true
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (colorPicker.isOpen && colorPicker.mouseDragged(click.x(), click.y())) return true
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        if (colorPicker.mouseReleased()) {
            ConfigManager.save()
            return true
        }
        return super.mouseReleased(click)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (rebindingGuiKey) {
            ClientSettings.guiKeybind = input.key()
            rebindingGuiKey = false
            ConfigManager.save()
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (colorPicker.isOpen) {
                colorPicker.close()
                accentColorSetting = null
                ConfigManager.save()
                return true
            }
            onClose()
            return true
        }
        return super.keyPressed(input)
    }
}
