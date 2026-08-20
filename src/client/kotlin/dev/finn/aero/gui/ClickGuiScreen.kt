package dev.finn.aero.gui

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.module.ModuleManager
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
import kotlin.math.roundToInt

/**
 * Aero's ClickGUI: a category rail + a scrolling module list, each module
 * expandable in place to reveal its settings. Everything is immediate-mode
 * (no widget objects), matching the rest of the codebase -- adding a new
 * Setting type means adding one branch to renderSettingRow/mouseClicked,
 * nowhere else.
 *
 * Visual language: dark Minecraft-panel base, Aero's cyan-to-green accent
 * spectrum reserved for state (enabled/selected/hover/focus), and short
 * lerp-based transitions on hover/toggle/expand so the interface reads as
 * alive without being flashy. See DESIGN.md for the full system this
 * implements.
 */
class ClickGuiScreen : Screen(Text.literal("Aero")) {

    private companion object {
        const val RAIL_X = 10
        const val RAIL_Y = 34
        const val RAIL_WIDTH = 96
        const val TAB_HEIGHT = 22
        const val TAB_GAP = 3

        const val SEARCH_X = 10
        const val SEARCH_Y = 10
        const val SEARCH_HEIGHT = 18

        const val PANEL_X = RAIL_X + RAIL_WIDTH + 10
        const val PANEL_WIDTH = 220
        const val PANEL_Y = 10
        const val ROW_HEIGHT = 22
        const val DESC_HEIGHT = 12
        const val SETTING_HEIGHT = 18
        const val ROW_GAP = 2

        // --- Base surfaces (Minecraft-panel derived, kept neutral) ---
        const val COLOR_BG = 0xC80B0B0F.toInt()
        const val COLOR_RAIL = 0xD0121216.toInt()
        const val COLOR_PANEL = 0xD8161619.toInt()
        const val COLOR_ROW = 0x901C1C21.toInt()
        const val COLOR_ROW_HOVER = 0xA0242429.toInt()
        const val COLOR_SETTING = 0x80202024.toInt()
        const val COLOR_SEARCH = 0xD0141417.toInt()

        // --- Aero accent spectrum (used only for state / feedback) ---
        const val ACCENT_BLUE = 0xFF0096FF.toInt()
        const val ACCENT_CYAN = 0xFF00C9FF.toInt()
        const val ACCENT_TEAL = 0xFF00ECC0.toInt()
        const val ACCENT_GREEN = 0xFF00F885.toInt()

        const val COLOR_TEXT = 0xFFEDEDF2.toInt()
        const val COLOR_TEXT_DIM = 0xFF9A9AA4.toInt()
        const val COLOR_TEXT_FAINT = 0xFF6A6A74.toInt()

        val PRESET_COLORS = intArrayOf(
            0xFFFF5555.toInt(), 0xFFFFAA00.toInt(), 0xFFFFFF55.toInt(),
            0xFF55FF55.toInt(), 0xFF55FFFF.toInt(), 0xFF5599FF.toInt(),
            0xFFAA55FF.toInt(), 0xFFFF55FF.toInt(), 0xFFFFFFFF.toInt(),
        )

        /** How quickly animated values approach their target, per frame at ~60fps. */
        const val ANIM_SPEED = 0.35f
    }

    private var selectedCategory: Category = Category.MOVEMENT
    private val expandedModules: MutableSet<Module> = mutableSetOf()

    private var searchQuery: String = ""
    private var searchFocused: Boolean = false

    private var keybindTarget: KeybindSetting? = null
    private var moduleKeybindTarget: Module? = null
    private var draggingSlider: SliderSetting? = null

    /** 0..1 animated "enabled/selected" progress per module, approached every frame. */
    private val toggleAnim: MutableMap<Any, Float> = mutableMapOf()
    private val hoverAnim: MutableMap<Any, Float> = mutableMapOf()

    private data class Row(val top: Int, val bottom: Int, val module: Module, val setting: Setting<*>?, val showDescription: Boolean)

    private fun modulesForCurrentView(): List<Module> {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            return ModuleManager.all().filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
        }
        return ModuleManager.byCategory(selectedCategory)
    }

    private fun buildRows(): List<Row> {
        val rows = mutableListOf<Row>()
        var y = PANEL_Y + 6
        for (module in modulesForCurrentView()) {
            val expanded = module in expandedModules
            rows.add(Row(y, y + ROW_HEIGHT, module, null, expanded))
            y += ROW_HEIGHT
            if (expanded) {
                if (module.description.isNotBlank()) {
                    rows.add(Row(y, y + DESC_HEIGHT, module, null, true))
                    y += DESC_HEIGHT
                }
                for (setting in module.settings) {
                    rows.add(Row(y, y + SETTING_HEIGHT, module, setting, false))
                    y += SETTING_HEIGHT + ROW_GAP
                }
                y += 4
            } else {
                y += ROW_GAP
            }
        }
        return rows
    }

    override fun shouldPause(): Boolean = false

    private fun approach(map: MutableMap<Any, Float>, key: Any, target: Float): Float {
        val current = map.getOrDefault(key, if (target > 0.5f) 0f else target)
        val next = current + (target - current) * ANIM_SPEED
        map[key] = next
        return next
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, COLOR_BG)

        renderSearchBar(context, mouseX, mouseY)
        if (searchQuery.isBlank()) renderCategoryRail(context, mouseX, mouseY)

        val rows = buildRows()
        val panelBottom = (rows.maxOfOrNull { it.bottom } ?: PANEL_Y + ROW_HEIGHT) + 6
        context.fill(PANEL_X, PANEL_Y, PANEL_X + PANEL_WIDTH, panelBottom, COLOR_PANEL)

        if (rows.isEmpty() && searchQuery.isNotBlank()) {
            context.drawTextWithShadow(textRenderer, "No results for \"$searchQuery\"", PANEL_X + 10, PANEL_Y + 10, COLOR_TEXT_DIM)
        }

        for (row in rows) {
            val hovered = mouseX in PANEL_X..(PANEL_X + PANEL_WIDTH) && mouseY in row.top..row.bottom
            when {
                row.setting != null -> renderSettingRow(context, row.setting, row.top, hovered)
                row.showDescription -> context.drawTextWithShadow(
                    textRenderer, row.module.description, PANEL_X + 12, row.top + 1, COLOR_TEXT_FAINT,
                )
                else -> renderModuleRow(context, row, hovered)
            }
        }
    }

    private fun renderSearchBar(context: DrawContext, mouseX: Int, mouseY: Int) {
        val width = RAIL_WIDTH
        val hovered = mouseX in SEARCH_X..(SEARCH_X + width) && mouseY in SEARCH_Y..(SEARCH_Y + SEARCH_HEIGHT)
        val focusT = approach(hoverAnim, "search", if (searchFocused) 1f else if (hovered) 0.4f else 0f)
        context.fill(SEARCH_X, SEARCH_Y, SEARCH_X + width, SEARCH_Y + SEARCH_HEIGHT, COLOR_SEARCH)
        if (focusT > 0.01f) {
            context.fill(SEARCH_X, SEARCH_Y + SEARCH_HEIGHT - 2, SEARCH_X + width, SEARCH_Y + SEARCH_HEIGHT, lerpColor(0x00000000, ACCENT_CYAN, focusT))
        }
        val label = searchQuery.ifEmpty { "Search modules..." }
        val color = if (searchQuery.isEmpty()) COLOR_TEXT_FAINT else COLOR_TEXT
        context.drawTextWithShadow(textRenderer, trimToWidth(label, width - 10), SEARCH_X + 6, SEARCH_Y + 5, color)
    }

    private fun renderCategoryRail(context: DrawContext, mouseX: Int, mouseY: Int) {
        var tabY = RAIL_Y
        for (category in Category.entries) {
            val hovered = mouseX in RAIL_X..(RAIL_X + RAIL_WIDTH) && mouseY in tabY..(tabY + TAB_HEIGHT)
            val selected = category == selectedCategory
            val t = approach(hoverAnim, category, if (selected) 1f else if (hovered) 0.5f else 0f)

            val bg = lerpColor(COLOR_ROW, COLOR_ROW_HOVER, t.coerceIn(0f, 1f))
            context.fill(RAIL_X, tabY, RAIL_X + RAIL_WIDTH, tabY + TAB_HEIGHT, bg)
            if (t > 0.01f) {
                val accent = lerpColor(0x00000000, ACCENT_BLUE, t)
                context.fill(RAIL_X, tabY, RAIL_X + 2, tabY + TAB_HEIGHT, accent)
            }
            val textColor = if (selected) COLOR_TEXT else COLOR_TEXT_DIM
            context.drawTextWithShadow(textRenderer, prettyCategory(category), RAIL_X + 8, tabY + 7, textColor)
            tabY += TAB_HEIGHT + TAB_GAP
        }
    }

    private fun renderModuleRow(context: DrawContext, row: Row, hovered: Boolean) {
        val module = row.module
        val t = approach(toggleAnim, module, if (module.enabled) 1f else 0f)
        val base = if (hovered) COLOR_ROW_HOVER else COLOR_ROW
        val bg = if (t > 0.01f) lerpColor(base, mixAccent(t), t * 0.55f) else base
        context.fill(PANEL_X + 6, row.top, PANEL_X + PANEL_WIDTH - 6, row.bottom - ROW_GAP, bg)

        val arrow = if (module in expandedModules) "−" else "+"
        context.drawTextWithShadow(textRenderer, "$arrow", PANEL_X + 10, row.top + 7, COLOR_TEXT_FAINT)
        context.drawTextWithShadow(textRenderer, module.name, PANEL_X + 22, row.top + 7, COLOR_TEXT)

        var rightEdge = PANEL_X + PANEL_WIDTH - 10
        if (module.keybind != GLFW.GLFW_KEY_UNKNOWN || module == moduleKeybindTarget) {
            val label = if (module == moduleKeybindTarget) "..." else keyName(module.keybind)
            val labelWidth = textRenderer.getWidth(label)
            rightEdge -= labelWidth
            context.drawTextWithShadow(textRenderer, label, rightEdge, row.top + 7, COLOR_TEXT_DIM)
            rightEdge -= 8
        }

        if (module.pinned) {
            context.drawTextWithShadow(textRenderer, "★", rightEdge - 8, row.top + 7, mixAccent(1f))
        }
    }

    private fun renderSettingRow(context: DrawContext, setting: Setting<*>, top: Int, hovered: Boolean) {
        val bg = if (hovered) 0x18FFFFFF else COLOR_SETTING
        context.fill(PANEL_X + 14, top, PANEL_X + PANEL_WIDTH - 10, top + SETTING_HEIGHT - 1, bg)

        when (setting) {
            is BoolSetting -> {
                context.drawTextWithShadow(textRenderer, setting.name, PANEL_X + 18, top + 5, COLOR_TEXT_DIM)
                val boxColor = if (setting.value) mixAccent(1f) else COLOR_TEXT_FAINT
                context.fill(PANEL_X + PANEL_WIDTH - 22, top + 4, PANEL_X + PANEL_WIDTH - 14, top + 12, boxColor)
            }
            is SliderSetting -> {
                val label = "${setting.name}: ${"%.2f".format(setting.value)}"
                context.drawTextWithShadow(textRenderer, label, PANEL_X + 18, top + 3, COLOR_TEXT_DIM)
                val barLeft = PANEL_X + 18
                val barRight = PANEL_X + PANEL_WIDTH - 16
                val fraction = ((setting.value - setting.min) / (setting.max - setting.min)).coerceIn(0.0, 1.0)
                val fillX = barLeft + (fraction * (barRight - barLeft)).toInt()
                context.fill(barLeft, top + 13, barRight, top + 15, COLOR_ROW)
                context.fill(barLeft, top + 13, fillX, top + 15, mixAccent(1f))
            }
            is ModeSetting -> {
                val label = "${setting.name}: ${setting.value}"
                context.drawTextWithShadow(textRenderer, label, PANEL_X + 18, top + 5, COLOR_TEXT_DIM)
            }
            is KeybindSetting -> {
                val listening = setting == keybindTarget
                val label = "${setting.name}: ${if (listening) "..." else keyName(setting.value)}"
                context.drawTextWithShadow(textRenderer, label, PANEL_X + 18, top + 5, COLOR_TEXT_DIM)
            }
            is ColorSetting -> {
                context.drawTextWithShadow(textRenderer, setting.name, PANEL_X + 18, top + 5, COLOR_TEXT_DIM)
                context.fill(PANEL_X + PANEL_WIDTH - 26, top + 4, PANEL_X + PANEL_WIDTH - 14, top + 12, setting.value)
            }
        }
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mouseX = click.x()
        val mouseY = click.y()

        if (mouseX in SEARCH_X.toDouble()..(SEARCH_X + RAIL_WIDTH).toDouble() &&
            mouseY in SEARCH_Y.toDouble()..(SEARCH_Y + SEARCH_HEIGHT).toDouble()
        ) {
            searchFocused = true
            return true
        }
        searchFocused = false

        if (searchQuery.isBlank()) {
            var tabY = RAIL_Y
            for (category in Category.entries) {
                if (mouseX in RAIL_X.toDouble()..(RAIL_X + RAIL_WIDTH).toDouble() &&
                    mouseY in tabY.toDouble()..(tabY + TAB_HEIGHT).toDouble()
                ) {
                    selectedCategory = category
                    return true
                }
                tabY += TAB_HEIGHT + TAB_GAP
            }
        }

        for (row in buildRows()) {
            if (mouseX < PANEL_X || mouseX > PANEL_X + PANEL_WIDTH) continue
            if (mouseY < row.top || mouseY > row.bottom) continue
            if (row.showDescription && row.setting == null) return true

            if (row.setting == null) {
                val keyLabel = keyName(row.module.keybind)
                val keyWidth = if (row.module.keybind != GLFW.GLFW_KEY_UNKNOWN) textRenderer.getWidth(keyLabel) + 8 else 0
                val pinLeft = PANEL_X + PANEL_WIDTH - 10 - keyWidth - 8
                when {
                    mouseX >= pinLeft && mouseX < pinLeft + 10 -> row.module.pinned = !row.module.pinned
                    keyWidth > 0 && mouseX >= pinLeft + 10 -> moduleKeybindTarget = row.module
                    mouseX < PANEL_X + 20 -> {
                        if (row.module in expandedModules) expandedModules.remove(row.module) else expandedModules.add(row.module)
                    }
                    else -> row.module.toggle()
                }
                return true
            }

            when (val setting = row.setting) {
                is BoolSetting -> setting.value = !setting.value
                is ModeSetting -> setting.cycle()
                is KeybindSetting -> keybindTarget = setting
                is ColorSetting -> {
                    val i = PRESET_COLORS.indexOf(setting.value)
                    setting.value = PRESET_COLORS[(i + 1).coerceAtLeast(0) % PRESET_COLORS.size]
                }
                is SliderSetting -> {
                    draggingSlider = setting
                    applySliderDrag(setting, mouseX)
                }
            }
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: Click, deltaX: Double, deltaY: Double): Boolean {
        draggingSlider?.let {
            applySliderDrag(it, click.x())
            return true
        }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: Click): Boolean {
        draggingSlider = null
        return super.mouseReleased(click)
    }

    private fun applySliderDrag(setting: SliderSetting, mouseX: Double) {
        val barLeft = PANEL_X + 18
        val barRight = PANEL_X + PANEL_WIDTH - 16
        val fraction = ((mouseX - barLeft) / (barRight - barLeft)).coerceIn(0.0, 1.0)
        val raw = setting.min + fraction * (setting.max - setting.min)
        val stepped = Math.round(raw / setting.step) * setting.step
        setting.set(stepped)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (searchFocused) {
            searchQuery += chr
            return true
        }
        return super.charTyped(chr, modifiers)
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
                GLFW.GLFW_KEY_BACKSPACE -> if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                GLFW.GLFW_KEY_ESCAPE -> {
                    searchQuery = ""
                    searchFocused = false
                }
                else -> {}
            }
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close()
            return true
        }
        return super.keyPressed(input)
    }

    private fun prettyCategory(category: Category): String =
        category.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun trimToWidth(text: String, maxWidth: Int): String {
        if (textRenderer.getWidth(text) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && textRenderer.getWidth("$s...") > maxWidth) s = s.dropLast(1)
        return "$s..."
    }

    private fun keyName(keyCode: Int): String {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "NONE"
        return GLFW.glfwGetKeyName(keyCode, 0)?.uppercase() ?: "KEY_$keyCode"
    }

    /** Blends between the Aero spectrum's blue and green ends by progress [0,1]. */
    private fun mixAccent(t: Float): Int = lerpColor(ACCENT_BLUE, ACCENT_GREEN, t.coerceIn(0f, 1f))

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val a1 = (from ushr 24) and 0xFF; val r1 = (from ushr 16) and 0xFF; val g1 = (from ushr 8) and 0xFF; val b1 = from and 0xFF
        val a2 = (to ushr 24) and 0xFF; val r2 = (to ushr 16) and 0xFF; val g2 = (to ushr 8) and 0xFF; val b2 = to and 0xFF
        val a = (a1 + (a2 - a1) * f).roundToInt().coerceIn(0, 255)
        val r = (r1 + (r2 - r1) * f).roundToInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * f).roundToInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * f).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
