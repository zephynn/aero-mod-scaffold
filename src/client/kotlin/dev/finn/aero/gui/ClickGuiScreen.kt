package dev.finn.aero.gui

import dev.finn.aero.config.ClientSettings
import dev.finn.aero.config.Theme
import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.module.ModuleManager
import dev.finn.aero.module.impl.xray.XrayModule
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ColorSetting
import dev.finn.aero.setting.KeybindSetting
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.Setting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

/**
 * Aero's ClickGUI: a compact floating window -- a rail of categories on
 * the left, a full-width module list on the right. There's no separate
 * settings panel: clicking a module (the same click that used to just
 * "select" it) expands its settings inline, directly under the row, like
 * an accordion. That's what frees the list up to use the whole window
 * instead of being squeezed into a middle column.
 *
 * The window size is a *fraction* of the screen, not a fixed pixel size:
 * Minecraft's `width`/`height` here are already in scaled-GUI-space, so a
 * fixed pixel size would balloon to cover the whole screen at a high GUI
 * Scale (where scaled-space shrinks). Everything is recomputed in init(),
 * which the engine already calls on resize/scale changes.
 *
 * Every draw call goes through fill()/text() below instead of calling
 * DrawContext directly, so the open/close fade (windowAnim) applies
 * uniformly -- backgrounds, borders, AND text all fade together instead of
 * text snapping in ahead of the panel behind it.
 *
 * Rendering and input are both immediate-mode -- adding a new Setting
 * type still means one branch in renderSettingRow() and one in the
 * setting-row branch of mouseClicked(), nowhere else.
 */
class ClickGuiScreen : Screen(Text.literal("Aero")) {

    private companion object {
        // Kept as an exact 16:9 box -- guiWidth is always derived from
        // guiHeight (see init()) so the window's own shape stays widescreen
        // instead of the squarer panel this used to be.
        const val MAX_GUI_WIDTH = 400
        const val MAX_GUI_HEIGHT = 225
        const val MIN_GUI_WIDTH = 320
        const val MIN_GUI_HEIGHT = 180
        const val CHROME_HEIGHT = 20

        const val SEARCH_HEIGHT = 18
        const val CAT_HEIGHT = 18
        const val ROW_HEIGHT = 18
        const val SETTING_ROW_HEIGHT = 18
        const val SLIDER_ROW_HEIGHT = 24
        const val ROW_GAP = 2

        // Translucent palette -- the whole point is that the player's world
        // stays visible behind and through the GUI instead of getting
        // blotted out by a solid panel. Only small, text-bearing elements
        // (fields, chips) get near-opaque backing so they stay legible over
        // bright terrain. The base panel colour and the accent are both
        // user-configurable (Theme, edited from SettingsScreen) -- see the
        // colour properties below the companion object.
        const val COLOR_DIM = 0x38000000.toInt()
        const val COLOR_BORDER = 0x50FFFFFF.toInt()
        const val COLOR_ROW_HOVER = 0x20FFFFFF.toInt()
        const val COLOR_TRACK_OFF = 0x50FFFFFF.toInt()

        const val COLOR_TEXT = 0xFFE4E7EB.toInt()
        const val COLOR_TEXT_DIM = 0xFF83878E.toInt()
        const val COLOR_TEXT_FAINT = 0xFF4C4F55.toInt()

        const val DROPDOWN_ROW_HEIGHT = 14
        const val DROPDOWN_MAX_VISIBLE_ROWS = 6

        /** Micro-interaction step (per-frame): hover fades, toggle knobs, dropdown reveal. Snappy on purpose. */
        const val ANIM_FAST = 0.55f

        /** Window open/close step -- a hair slower than a toggle so the slide reads, not just a blink. */
        const val ANIM_WINDOW = 0.45f
    }

    // Theme-derived surface colours -- computed fresh each read so a change
    // made in SettingsScreen (which mutates Theme directly) shows up the
    // next time this screen is opened.
    private val accent get() = (0xFF shl 24) or Theme.accent
    private val colorBg get() = (0xB2 shl 24) or Theme.BACKGROUND
    private val colorRail get() = (0xB2 shl 24) or Theme.darkenedBackground(0.10f)
    private val colorChrome get() = (0xC0 shl 24) or Theme.darkenedBackground(0.20f)
    private val colorFieldBg get() = (0xC8 shl 24) or Theme.darkenedBackground(0.22f)
    private val colorRowSelected get() = (0x2A shl 24) or Theme.accent

    private var guiX = 0
    private var guiY = 0
    private var guiWidth = MAX_GUI_WIDTH
    private var guiHeight = MAX_GUI_HEIGHT
    private var railWidth = 0
    private var listWidth = 0

    private var selectedCategory: Category = Category.MOVEMENT
    private var showFavourites = false

    /** Modules whose settings dropdown is currently open. */
    private val expandedModules: MutableSet<Module> = mutableSetOf()

    private var searchFocused = false
    private var searchQuery = ""

    /** Non-null while the next key press should rebind this instead of doing anything else. */
    private var keybindTarget: KeybindSetting? = null
    private var moduleKeybindTarget: Module? = null

    /** Non-null while the mouse is held down dragging this slider, with the bar geometry it started on. */
    private var draggingSlider: SliderSetting? = null
    private var dragBarLeft = 0
    private var dragBarWidth = 0

    /** Per-module 0..1 toggle-animation progress, eased toward enabled/disabled each frame. */
    private val toggleAnim = HashMap<Any, Float>()

    /** 0..1 hover-fade progress for module rows and tabs, keyed by identity. */
    private val hoverAnim = HashMap<Any, Float>()

    /** 0..1 reveal progress for each module's settings dropdown, keyed by module. */
    private val expandAnim = HashMap<Any, Float>()

    /** 0 = fully closed, 1 = fully open. Drives the open/close slide+fade. Read by fill()/text(). */
    private var windowAnim = 0f
    private var closing = false

    /** Photoshop-style popup opened when a ColorSetting row is clicked, replacing the old preset-cycling behaviour. */
    private val colorPicker = ColorPickerPopup()

    /** ModeSetting currently showing its expanded option dropdown, plus the anchor geometry it was opened with. */
    private var modeDropdown: ModeSetting? = null
    private var modeDropdownX = 0
    private var modeDropdownY = 0
    private var modeDropdownW = 0
    private var modeDropdownScroll = 0

    override fun shouldPause(): Boolean = false

    override fun init() {
        // Cap the window to a small fraction of the *scaled* screen so it
        // never overwhelms the view, however small scaled-space gets at a
        // high GUI Scale -- then clamp to a sane minimum so it's still
        // usable at the other extreme.
        guiHeight = (height * 0.34f).toInt().coerceIn(MIN_GUI_HEIGHT, MAX_GUI_HEIGHT).coerceAtMost(height - 8)
        guiWidth = (guiHeight * 16 / 9).coerceIn(MIN_GUI_WIDTH, MAX_GUI_WIDTH).coerceAtMost(width - 8)
        railWidth = (guiWidth * 0.30f).toInt().coerceAtLeast(76)
        listWidth = guiWidth - railWidth

        guiX = (width - guiWidth) / 2
        guiY = (height - guiHeight) / 2
    }

    override fun close() {
        if (!closing) {
            closing = true
            return
        }
        super.close()
    }

    // --- Row model, rebuilt every frame from current category/search/expand state ---

    /** One list row: a module header (setting == null) or one of its settings, indented underneath it. */
    private data class Row(
        val top: Int,
        val bottom: Int,
        val module: Module,
        val setting: Setting<*>?,
        val categoryLabel: String?,
        /** True for the special "Edit List..." row X-Ray's Custom mode gets instead of a generic Setting -- see class kdoc. */
        val isEditListButton: Boolean = false,
    )

    private fun visibleModules(): List<Pair<Module, String?>> {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            return ModuleManager.all()
                .filter { it.name.contains(q, ignoreCase = true) }
                .sortedBy { it.name }
                .map { it to it.category.name }
        }
        if (showFavourites) {
            return ModuleManager.all().filter { it.pinned }.sortedBy { it.name }.map { it to it.category.name }
        }
        return ModuleManager.byCategory(selectedCategory).map { it to null }
    }

    private fun settingRowHeight(setting: Setting<*>) = if (setting is SliderSetting) SLIDER_ROW_HEIGHT else SETTING_ROW_HEIGHT

    private fun buildRows(): List<Row> {
        var y = guiY + CHROME_HEIGHT + 24
        val rows = mutableListOf<Row>()
        for ((module, label) in visibleModules()) {
            rows.add(Row(y, y + ROW_HEIGHT, module, null, label))
            y += ROW_HEIGHT + ROW_GAP
            if (module in expandedModules) {
                for (setting in module.settings) {
                    val h = settingRowHeight(setting)
                    rows.add(Row(y, y + h, module, setting, null))
                    y += h + ROW_GAP
                }
                if (module is XrayModule && module.settings.any { it.name == "Mode" && it.value == "Custom" }) {
                    rows.add(Row(y, y + SETTING_ROW_HEIGHT, module, null, null, isEditListButton = true))
                    y += SETTING_ROW_HEIGHT + ROW_GAP
                }
            }
        }
        return rows
    }

    // --- Render -----------------------------------------------------------

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        advanceWindowAnim()
        if (windowAnim <= 0.001f && closing) {
            client?.setScreen(null)
            return
        }

        val eased = easeOutCubic(windowAnim)
        val slide = ((1f - eased) * 6).toInt()
        context.fill(0, 0, width, height, scaleAlpha(COLOR_DIM, eased))

        val gy = guiY + slide
        fill(context, guiX, gy, guiX + guiWidth, gy + guiHeight, colorBg)
        renderChrome(context, gy, mouseX, mouseY)

        renderRail(context, gy, mouseX, mouseY)
        renderModuleList(context, gy, mouseX, mouseY)

        val listX = guiX + railWidth
        fill(context, listX, gy + CHROME_HEIGHT, listX + 1, gy + guiHeight, COLOR_BORDER)

        // Popups render last, on top of everything else, each clipped to
        // the panel bounds so neither can spill outside the GUI window.
        renderModeDropdown(context, gy, mouseX, mouseY)
        if (colorPicker.isOpen) {
            Scissor.clip(context, guiX, gy, guiX + guiWidth, gy + guiHeight) {
                colorPicker.render(context, mouseX, mouseY)
            }
        }
    }

    private fun renderModeDropdown(context: DrawContext, gy: Int, mouseX: Int, mouseY: Int) {
        val setting = modeDropdown ?: return
        val panelTop = gy + CHROME_HEIGHT
        val panelBottom = gy + guiHeight

        val visibleRows = ((panelBottom - modeDropdownY) / DROPDOWN_ROW_HEIGHT).coerceIn(1, DROPDOWN_MAX_VISIBLE_ROWS)
        val listHeight = visibleRows * DROPDOWN_ROW_HEIGHT
        modeDropdownScroll = modeDropdownScroll.coerceIn(0, (setting.options.size - visibleRows).coerceAtLeast(0))

        Scissor.clip(context, guiX, panelTop, guiX + guiWidth, panelBottom) {
            context.fill(modeDropdownX, modeDropdownY, modeDropdownX + modeDropdownW, modeDropdownY + listHeight, colorFieldBg)
            drawBorder(context, modeDropdownX, modeDropdownY, modeDropdownW, listHeight, COLOR_BORDER)

            var oy = modeDropdownY
            for (i in modeDropdownScroll until (modeDropdownScroll + visibleRows).coerceAtMost(setting.options.size)) {
                val option = setting.options[i]
                val selected = option == setting.value
                val hovered = mouseX in modeDropdownX..(modeDropdownX + modeDropdownW) && mouseY in oy..(oy + DROPDOWN_ROW_HEIGHT)
                if (selected) context.fill(modeDropdownX, oy, modeDropdownX + modeDropdownW, oy + DROPDOWN_ROW_HEIGHT, colorRowSelected)
                else if (hovered) context.fill(modeDropdownX, oy, modeDropdownX + modeDropdownW, oy + DROPDOWN_ROW_HEIGHT, COLOR_ROW_HOVER)
                context.drawTextWithShadow(textRenderer, option, modeDropdownX + 4, oy + 3, if (selected) COLOR_TEXT else COLOR_TEXT_DIM)
                oy += DROPDOWN_ROW_HEIGHT
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

    private fun renderChrome(context: DrawContext, gy: Int, mouseX: Int, mouseY: Int) {
        fill(context, guiX, gy, guiX + guiWidth, gy + CHROME_HEIGHT, colorChrome)
        text(context, "AERO", guiX + 8, gy + 6, COLOR_TEXT_DIM)

        val gearX = guiX + guiWidth - 16
        val gearY = gy + 5
        val gearHovered = mouseX in (gearX - 3)..(gearX + 11) && mouseY in (gearY - 3)..(gearY + 11)
        text(context, "⚙", gearX, gearY, if (gearHovered) accent else COLOR_TEXT_DIM)
    }

    private fun renderRail(context: DrawContext, gy: Int, mouseX: Int, mouseY: Int) {
        val railX = guiX
        val railTop = gy + CHROME_HEIGHT
        fill(context, railX, railTop, railX + railWidth, gy + guiHeight, colorRail)

        val sx = railX + 6
        val sy = railTop + 6
        val sw = railWidth - 12
        fill(context, sx, sy, sx + sw, sy + SEARCH_HEIGHT, colorFieldBg)
        if (searchFocused) drawBorder(context, sx, sy, sw, SEARCH_HEIGHT, accent)
        val searchLabel = when {
            searchQuery.isNotEmpty() -> searchQuery
            searchFocused -> "|"
            else -> "Search..."
        }
        text(
            context, searchLabel, sx + 5, sy + 5,
            if (searchQuery.isNotEmpty() || searchFocused) COLOR_TEXT else COLOR_TEXT_FAINT,
        )

        var y = sy + SEARCH_HEIGHT + 6

        if (anyFavourites()) {
            val selected = showFavourites && searchQuery.isBlank()
            val hAnim = animatedHover("tab:favourites", mouseX in railX..(railX + railWidth) && mouseY in y..(y + CAT_HEIGHT))
            renderTab(context, sx, sw, y, "Favourites", selected, hAnim)
            y += CAT_HEIGHT
        }

        for (category in Category.entries) {
            val selected = category == selectedCategory && !showFavourites && searchQuery.isBlank()
            val hAnim = animatedHover(category, mouseX in railX..(railX + railWidth) && mouseY in y..(y + CAT_HEIGHT))
            renderTab(context, sx, sw, y, prettyCategory(category), selected, hAnim)
            y += CAT_HEIGHT
        }
    }

    private fun renderTab(context: DrawContext, sx: Int, sw: Int, y: Int, label: String, selected: Boolean, hoverAnim: Float) {
        if (selected) {
            fill(context, sx, y, sx + sw, y + CAT_HEIGHT - 2, colorRowSelected)
        } else if (hoverAnim > 0.02f) {
            fill(context, sx, y, sx + sw, y + CAT_HEIGHT - 2, scaleAlpha(COLOR_ROW_HOVER, hoverAnim))
        }
        text(context, label, sx + 6, y + 4, if (selected) COLOR_TEXT else COLOR_TEXT_DIM)
    }

    private fun anyFavourites(): Boolean = ModuleManager.all().any { it.pinned }

    private fun renderModuleList(context: DrawContext, gy: Int, mouseX: Int, mouseY: Int) {
        val listX = guiX + railWidth
        val headerY = gy + CHROME_HEIGHT + 6
        val headerText = when {
            searchQuery.isNotBlank() -> searchQuery.trim()
            showFavourites -> "Favourites"
            else -> prettyCategory(selectedCategory)
        }
        text(context, headerText.uppercase(), listX + 8, headerY, COLOR_TEXT_FAINT)

        for (row in buildRows()) {
            when {
                row.isEditListButton -> renderEditListRow(context, row, mouseX, mouseY)
                row.setting == null -> renderModuleHeaderRow(context, row, mouseX, mouseY)
                else -> renderSettingRow(context, row, mouseX, mouseY)
            }
        }

        if (visibleModules().isEmpty()) {
            val msg = if (showFavourites) "No pinned modules yet" else "No modules found"
            text(context, msg, listX + (listWidth - textRenderer.getWidth(msg)) / 2, gy + guiHeight / 2, COLOR_TEXT_FAINT)
        }
    }

    private fun renderModuleHeaderRow(context: DrawContext, row: Row, mouseX: Int, mouseY: Int) {
        val module = row.module
        val listX = guiX + railWidth
        val x0 = listX + 6
        val x1 = listX + listWidth - 6
        val expanded = module in expandedModules
        val hovered = mouseX in x0..x1 && mouseY in row.top..row.bottom
        val hAnim = animatedHover(module, hovered)

        val anim = animatedFor(module)

        if (expanded) {
            fill(context, x0, row.top, x1, row.bottom, colorRowSelected)
        } else if (hAnim > 0.02f) {
            fill(context, x0, row.top, x1, row.bottom, scaleAlpha(COLOR_ROW_HOVER, hAnim))
        }

        // Right-hand meta first, right to left, so we know how much room is
        // left for the name (and an optional category tag) before drawing it.
        var rx = x1 - 3
        val toggleW = 16
        rx -= toggleW
        drawToggle(context, rx, row.top + (ROW_HEIGHT - 9) / 2, toggleW, 9, anim)

        if (module.keybind != GLFW.GLFW_KEY_UNKNOWN || module == moduleKeybindTarget) {
            rx -= 3
            val kbLabel = if (module == moduleKeybindTarget) "..." else keyName(module.keybind)
            val kbWidth = textRenderer.getWidth(kbLabel) + 5
            rx -= kbWidth
            text(context, kbLabel, rx + 3, row.top + (ROW_HEIGHT - 8) / 2, COLOR_TEXT_FAINT)
            rx -= 2
        }

        rx -= 12
        val pinGlyph = if (module.pinned) "★" else "☆"
        text(context, pinGlyph, rx, row.top + (ROW_HEIGHT - 8) / 2, if (module.pinned) accent else COLOR_TEXT_FAINT)

        val nameLimit = rx - 6
        val textX = x0 + 6
        val textY = row.top + (ROW_HEIGHT - 8) / 2

        if (module.settings.isNotEmpty()) {
            val arrow = if (expanded) "▾" else "▸"
            text(context, arrow, textX, textY, COLOR_TEXT_FAINT)
        }
        val nameX = if (module.settings.isNotEmpty()) textX + 7 else textX
        val nameColor = if (anim > 0.5f) COLOR_TEXT else COLOR_TEXT_DIM
        val name = clipToWidth(module.name, nameLimit - nameX)
        text(context, name, nameX, textY, nameColor)

        if (row.categoryLabel != null) {
            val tagX = nameX + textRenderer.getWidth(name) + 5
            if (tagX < nameLimit) text(context, row.categoryLabel, tagX, textY, COLOR_TEXT_FAINT)
        }
    }

    /** Special-cased row for X-Ray's Custom mode -- opens [XrayCustomScreen] rather than being a generic Setting. */
    private fun renderEditListRow(context: DrawContext, row: Row, mouseX: Int, mouseY: Int) {
        val listX = guiX + railWidth
        val x = listX + 20
        val x1 = listX + listWidth - 6
        val extra = animatedExpand(row.module)
        if (extra <= 0.02f) return

        val hovered = mouseX in x..x1 && mouseY in row.top..row.bottom
        if (hovered) fill(context, x, row.top, x1, row.bottom, COLOR_ROW_HOVER, extra)
        val label = "Edit List..."
        text(context, label, x + (x1 - x - textRenderer.getWidth(label)) / 2, row.top + 5, accent, extra)
    }

    private fun renderSettingRow(context: DrawContext, row: Row, mouseX: Int, mouseY: Int) {
        val setting = row.setting ?: return
        val listX = guiX + railWidth
        val x = listX + 20
        val x1 = listX + listWidth - 6
        val w = (x1 - x).coerceAtLeast(20)
        val extra = animatedExpand(row.module)
        if (extra <= 0.02f) return

        when (setting) {
            is BoolSetting -> {
                text(context, setting.name, x, row.top + 5, COLOR_TEXT_DIM, extra)
                drawToggle(context, x1 - 22, row.top + 4, 22, 10, if (setting.value) 1f else 0f, extra)
            }
            is SliderSetting -> {
                text(context, setting.name, x, row.top + 3, COLOR_TEXT_DIM, extra)
                val valueLabel = "%.2f".format(setting.value)
                text(context, valueLabel, x1 - textRenderer.getWidth(valueLabel), row.top + 3, COLOR_TEXT_DIM, extra)
                val barY = row.top + 15
                fill(context, x, barY, x1, barY + 2, COLOR_TRACK_OFF, extra)
                val frac = ((setting.value - setting.min) / (setting.max - setting.min)).coerceIn(0.0, 1.0)
                val fillX = x + (frac * (x1 - x)).toInt()
                fill(context, x, barY, fillX, barY + 2, accent, extra)
            }
            is ModeSetting -> {
                text(context, setting.name, x, row.top + 5, COLOR_TEXT_DIM, extra)
                val label = setting.value
                text(context, label, x1 - textRenderer.getWidth(label), row.top + 5, COLOR_TEXT, extra)
            }
            is KeybindSetting -> {
                text(context, setting.name, x, row.top + 5, COLOR_TEXT_DIM, extra)
                val listening = setting == keybindTarget
                val label = if (listening) "..." else keyName(setting.value)
                text(context, label, x1 - textRenderer.getWidth(label), row.top + 5, if (listening) accent else COLOR_TEXT, extra)
            }
            is ColorSetting -> {
                text(context, setting.name, x, row.top + 5, COLOR_TEXT_DIM, extra)
                fill(context, x1 - 20, row.top + 3, x1, row.top + 13, setting.value, extra)
            }
        }
    }

    // --- Small drawing helpers ---------------------------------------------

    /** Every background/box fill in the GUI goes through here so it fades with windowAnim (and, optionally, a dropdown's own reveal). */
    private fun fill(context: DrawContext, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, extra: Float = 1f) {
        context.fill(x0, y0, x1, y1, scaleAlpha(color, easeOutCubic(windowAnim) * extra))
    }

    /** Every label/value in the GUI goes through here so text fades in lockstep with its background. */
    private fun text(context: DrawContext, str: String, x: Int, y: Int, color: Int, extra: Float = 1f) {
        context.drawTextWithShadow(textRenderer, str, x, y, scaleAlpha(color, easeOutCubic(windowAnim) * extra))
    }

    private fun drawToggle(context: DrawContext, x: Int, y: Int, w: Int, h: Int, anim: Float, extra: Float = 1f) {
        fill(context, x, y, x + w, y + h, lerpColor(COLOR_TRACK_OFF, accent, anim), extra)
        val knobD = h - 4
        val knobX = x + 2 + ((w - knobD - 4) * anim).toInt()
        fill(context, knobX, y + 2, knobX + knobD, y + h - 2, if (anim > 0.5f) 0xFF0B0D0F.toInt() else COLOR_TEXT_FAINT, extra)
    }

    private fun drawBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        fill(context, x, y, x + w, y + 1, color)
        fill(context, x, y + h - 1, x + w, y + h, color)
        fill(context, x, y, x + 1, y + h, color)
        fill(context, x + w - 1, y, x + w, y + h, color)
    }

    private fun clipToWidth(str: String, maxWidth: Int): String {
        if (maxWidth <= 0) return ""
        if (textRenderer.getWidth(str) <= maxWidth) return str
        return textRenderer.trimToWidth(str, (maxWidth - textRenderer.getWidth("...")).coerceAtLeast(0)) + "..."
    }

    private fun animatedFor(module: Module): Float = Anim.advance(toggleAnim, module, if (module.enabled) 1f else 0f, ANIM_FAST)

    private fun animatedHover(key: Any, hovered: Boolean): Float = Anim.advance(hoverAnim, key, if (hovered) 1f else 0f, ANIM_FAST)

    private fun animatedExpand(module: Module): Float = Anim.advance(expandAnim, module, if (module in expandedModules) 1f else 0f, ANIM_FAST)

    private fun easeOutCubic(t: Float): Float = Anim.easeOutCubic(t)

    private fun scaleAlpha(color: Int, scale: Float): Int = Anim.scaleAlpha(color, scale)

    private fun lerpColor(from: Int, to: Int, t: Float): Int = Anim.lerpColor(from, to, t)

    private fun prettyCategory(category: Category): String =
        category.name.lowercase().replaceFirstChar { it.uppercase() }

    // --- Input ---------------------------------------------------------------

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (closing) return false
        val mouseX = click.x()
        val mouseY = click.y()

        // Popups take priority over everything underneath them -- an
        // outside click just closes them (falling through to normal
        // handling below) rather than being swallowed.
        if (colorPicker.isOpen) {
            if (colorPicker.mouseClicked(mouseX, mouseY)) return true
        }
        if (modeDropdown != null) {
            if (handleModeDropdownClick(mouseX, mouseY)) return true
        }

        val gearX = guiX + guiWidth - 16
        val gearY = guiY + 5
        if (mouseX in (gearX - 3).toDouble()..(gearX + 11).toDouble() && mouseY in (gearY - 3).toDouble()..(gearY + 11).toDouble()) {
            client?.setScreen(SettingsScreen())
            return true
        }

        val railX = guiX
        val railTop = guiY + CHROME_HEIGHT
        val sx = railX + 6
        val sy = railTop + 6
        val sw = railWidth - 12

        if (mouseX in sx.toDouble()..(sx + sw).toDouble() && mouseY in sy.toDouble()..(sy + SEARCH_HEIGHT).toDouble()) {
            searchFocused = true
            return true
        }
        searchFocused = false

        var y = sy + SEARCH_HEIGHT + 6

        if (anyFavourites()) {
            if (mouseX in sx.toDouble()..(sx + sw).toDouble() && mouseY in y.toDouble()..(y + CAT_HEIGHT).toDouble()) {
                showFavourites = true
                searchQuery = ""
                return true
            }
            y += CAT_HEIGHT
        }

        for (category in Category.entries) {
            if (mouseX in sx.toDouble()..(sx + sw).toDouble() && mouseY in y.toDouble()..(y + CAT_HEIGHT).toDouble()) {
                selectedCategory = category
                showFavourites = false
                searchQuery = ""
                return true
            }
            y += CAT_HEIGHT
        }

        val listX = guiX + railWidth
        val x0 = (listX + 6).toDouble()
        val x1d = (listX + listWidth - 6).toDouble()
        for (row in buildRows()) {
            if (mouseX < x0 || mouseX > x1d || mouseY < row.top || mouseY > row.bottom) continue

            if (row.isEditListButton) {
                client?.setScreen(XrayCustomScreen(row.module as XrayModule))
                return true
            }
            if (row.setting == null) {
                return handleHeaderClick(row.module, mouseX, x1d.toInt())
            }
            return handleSettingClick(row, mouseX)
        }

        return super.mouseClicked(click, doubled)
    }

    /** True if the click hit the open dropdown (an option, or inside it) -- consumed either way; a click fully outside closes it without consuming. */
    private fun handleModeDropdownClick(mouseX: Double, mouseY: Double): Boolean {
        val setting = modeDropdown ?: return false
        val gy = guiY
        val panelBottom = gy + guiHeight
        val visibleRows = ((panelBottom - modeDropdownY) / DROPDOWN_ROW_HEIGHT).coerceIn(1, DROPDOWN_MAX_VISIBLE_ROWS)
        val listHeight = visibleRows * DROPDOWN_ROW_HEIGHT

        if (mouseX >= modeDropdownX && mouseX <= modeDropdownX + modeDropdownW && mouseY >= modeDropdownY && mouseY <= modeDropdownY + listHeight) {
            val row = ((mouseY - modeDropdownY) / DROPDOWN_ROW_HEIGHT).toInt() + modeDropdownScroll
            if (row in setting.options.indices) {
                setting.value = setting.options[row]
            }
            modeDropdown = null
            return true
        }

        modeDropdown = null
        return false
    }

    private fun handleHeaderClick(module: Module, mouseX: Double, x1: Int): Boolean {
        var rx = x1 - 3
        val toggleW = 16
        rx -= toggleW
        if (mouseX >= rx && mouseX <= rx + toggleW) {
            module.toggle()
            return true
        }

        if (module.keybind != GLFW.GLFW_KEY_UNKNOWN) {
            rx -= 3
            val kbLabel = keyName(module.keybind)
            val kbWidth = textRenderer.getWidth(kbLabel) + 5
            rx -= kbWidth
            if (mouseX >= rx && mouseX <= rx + kbWidth) {
                moduleKeybindTarget = module
                return true
            }
            rx -= 2
        }

        rx -= 12
        if (mouseX >= rx && mouseX <= rx + 10) {
            module.pinned = !module.pinned
            if (showFavourites && !anyFavourites()) showFavourites = false
            return true
        }

        if (module.settings.isNotEmpty()) {
            if (module in expandedModules) expandedModules.remove(module) else expandedModules.add(module)
        }
        return true
    }

    private fun handleSettingClick(row: Row, mouseX: Double): Boolean {
        val setting = row.setting ?: return false
        val listX = guiX + railWidth
        val x = listX + 20
        val x1 = listX + listWidth - 6

        when (setting) {
            is BoolSetting -> setting.value = !setting.value
            is ModeSetting -> {
                if (modeDropdown == setting) {
                    modeDropdown = null
                } else {
                    modeDropdown = setting
                    modeDropdownX = x
                    modeDropdownY = row.bottom + 2
                    modeDropdownW = (x1 - x).coerceAtLeast(60)
                    modeDropdownScroll = 0
                }
            }
            is KeybindSetting -> keybindTarget = setting
            is ColorSetting -> {
                val gy = guiY
                colorPicker.open(setting, x1 - ColorPickerPopup.WIDTH, row.bottom + 2, guiX, gy + CHROME_HEIGHT, guiX + guiWidth, gy + guiHeight)
            }
            is SliderSetting -> {
                draggingSlider = setting
                dragBarLeft = x
                dragBarWidth = (x1 - x).coerceAtLeast(20)
                applySliderDrag(setting, mouseX, dragBarLeft, dragBarWidth)
            }
        }
        return true
    }

    override fun mouseDragged(click: Click, deltaX: Double, deltaY: Double): Boolean {
        if (colorPicker.isOpen && colorPicker.mouseDragged(click.x(), click.y())) return true
        draggingSlider?.let { slider ->
            applySliderDrag(slider, click.x(), dragBarLeft, dragBarWidth)
            return true
        }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: Click): Boolean {
        colorPicker.mouseReleased()
        draggingSlider = null
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val setting = modeDropdown
        if (setting != null && mouseX >= modeDropdownX && mouseX <= modeDropdownX + modeDropdownW) {
            modeDropdownScroll -= verticalAmount.toInt()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun applySliderDrag(setting: SliderSetting, mouseX: Double, barLeft: Int, barWidth: Int) {
        val fraction = ((mouseX - barLeft) / barWidth).coerceIn(0.0, 1.0)
        val raw = setting.min + fraction * (setting.max - setting.min)
        val stepped = Math.round(raw / setting.step) * setting.step
        setting.set(stepped)
    }

    override fun charTyped(input: net.minecraft.client.input.CharInput): Boolean {
        if (searchFocused) {
            searchQuery += input.asString()
            return true
        }
        return super.charTyped(input)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        keybindTarget?.let {
            it.value = input.key()
            keybindTarget = null
            return true
        }
        moduleKeybindTarget?.let {
            it.keybind = input.key()
            moduleKeybindTarget = null
            return true
        }
        if (searchFocused) {
            when (input.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    searchFocused = false
                    searchQuery = ""
                    return true
                }
                GLFW.GLFW_KEY_ENTER -> {
                    searchFocused = false
                    return true
                }
            }
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (colorPicker.isOpen) {
                colorPicker.close()
                return true
            }
            if (modeDropdown != null) {
                modeDropdown = null
                return true
            }
            close()
            return true
        }
        return super.keyPressed(input)
    }

    private fun keyName(keyCode: Int): String {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "NONE"
        return GLFW.glfwGetKeyName(keyCode, 0)?.uppercase() ?: "KEY_$keyCode"
    }
}
