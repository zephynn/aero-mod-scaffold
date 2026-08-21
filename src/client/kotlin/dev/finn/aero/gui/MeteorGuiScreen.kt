package dev.finn.aero.gui

import dev.finn.aero.config.ClientSettings
import dev.finn.aero.config.Theme
import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.module.ModuleManager
import dev.finn.aero.module.impl.xray.XrayModule
import dev.finn.aero.setting.KeybindSetting
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * "Meteor Client style" ClickGUI: every [Category] rendered as its own
 * bordered column, side by side, all visible at once -- no rail, no
 * tab-switching, no accordion. Each column has a coloured header (name +
 * `[count]`) and a flat list of module rows directly underneath. Left-click
 * a row to toggle the module immediately; right-click opens a floating
 * settings panel beside that column (closes on click-outside or Escape).
 * Switchable from the accordion [ClickGuiScreen] via [ClientSettings.guiStyle].
 *
 * Per-setting-type rendering/click-handling is shared with [ClickGuiScreen]
 * through [SettingRow] -- this screen only owns the column layout and the
 * floating panel's positioning around it.
 */
class MeteorGuiScreen : Screen(Component.literal("Aero")) {

    private companion object {
        const val TOP_Y = 22
        const val COL_WIDTH = 132
        const val COL_GAP = 6
        const val COL_MARGIN = 8
        const val HEADER_HEIGHT = 16
        const val ROW_HEIGHT = 14
        const val ROW_GAP = 1

        const val PANEL_WIDTH = 160
        const val PANEL_GAP = 4

        const val COLOR_DIM = 0x20000000.toInt()
        const val COLOR_BORDER = 0x50FFFFFF.toInt()
        const val COLOR_ROW_HOVER = 0x20FFFFFF.toInt()
        const val COLOR_TRACK_OFF = 0x50FFFFFF.toInt()

        const val COLOR_TEXT = 0xFFE4E7EB.toInt()
        const val COLOR_TEXT_DIM = 0xFF83878E.toInt()
        const val COLOR_TEXT_FAINT = 0xFF4C4F55.toInt()

        const val ANIM_FAST = 0.55f
        const val ANIM_WINDOW = 0.45f
    }

    private val accent get() = (0xFF shl 24) or Theme.accent
    private val colorColumnBg get() = (0xA0 shl 24) or Theme.darkenedBackground(0.10f)
    private val colorHeaderBg get() = (0xE6 shl 24) or Theme.accent
    private val colorPanelBg get() = (0xE6 shl 24) or Theme.darkenedBackground(0.10f)
    private val colorChrome get() = (0xC0 shl 24) or Theme.darkenedBackground(0.20f)
    private val colorRowSelected get() = (0x2A shl 24) or Theme.accent

    /** Left edge of each column, keyed by category, recomputed in [init]. */
    private val columnX = LinkedHashMap<Category, Int>()

    /** Per-column scroll offset (pixels), keyed by category. */
    private val scrollOffset = HashMap<Category, Int>()

    /** Module whose floating settings panel is currently open, or null. */
    private var panelModule: Module? = null
    private var panelX = 0
    private var panelY = 0
    private var panelScrollOffset = 0

    private var keybindTarget: KeybindSetting? = null
    private var moduleKeybindTarget: Module? = null

    private var draggingSlider: SliderSetting? = null
    private var dragBarLeft = 0
    private var dragBarWidth = 0

    private val toggleAnim = HashMap<Any, Float>()
    private val hoverAnim = HashMap<Any, Float>()

    private var windowAnim = 0f
    private var closing = false

    private val colorPicker = ColorPickerPopup()
    private var modeDropdown: ModeSetting? = null

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        columnX.clear()
        var x = COL_MARGIN
        for (category in Category.entries) {
            columnX[category] = x
            x += COL_WIDTH + COL_GAP
        }
    }

    override fun onClose() {
        if (!closing) {
            closing = true
            return
        }
        super.onClose()
    }

    // --- Data -------------------------------------------------------------

    private fun columnBottom(): Int = height - COL_MARGIN

    private fun columnHeaderY(): Int = TOP_Y

    private fun columnListTop(): Int = TOP_Y + HEADER_HEIGHT + 2

    private fun panelRows(module: Module): List<Row> {
        var y = panelHeaderBottom()
        val rows = mutableListOf<Row>()
        for (setting in module.settings) {
            val h = SettingRow.heightFor(setting)
            rows.add(Row(y, y + h, module, setting))
            y += h + ROW_GAP
            if (setting is ModeSetting && setting == modeDropdown) {
                for (option in setting.options) {
                    rows.add(Row(y, y + 14, module, setting, isDropdownOption = true, dropdownOption = option))
                    y += 14
                }
                y += ROW_GAP
            }
        }
        if (module is XrayModule && module.settings.any { it.name == "Mode" && it.value == "Custom" }) {
            rows.add(Row(y, y + SettingRow.SETTING_ROW_HEIGHT, module, null, isEditListButton = true))
            y += SettingRow.SETTING_ROW_HEIGHT + ROW_GAP
        }
        return rows
    }

    private fun panelHeaderBottom(): Int = panelY + 18
    private fun panelViewportBottom(): Int = (panelY + panelHeight(panelModule)).coerceAtMost(height - 4)
    private fun panelHeight(module: Module?): Int {
        if (module == null) return 40
        val rows = panelRowsUnclamped(module)
        val contentHeight = (rows.lastOrNull()?.bottom ?: panelHeaderBottom()) - panelY
        return (contentHeight + 6).coerceAtMost(height - 16)
    }

    /** [panelRows] but laid out from y=0 rather than the live [panelY] -- used only to measure total content height. */
    private fun panelRowsUnclamped(module: Module): List<Row> {
        var y = 18
        val rows = mutableListOf<Row>()
        for (setting in module.settings) {
            val h = SettingRow.heightFor(setting)
            rows.add(Row(y, y + h, module, setting))
            y += h + ROW_GAP
            if (setting is ModeSetting && setting == modeDropdown) {
                for (option in setting.options) {
                    rows.add(Row(y, y + 14, module, setting, isDropdownOption = true, dropdownOption = option))
                    y += 14
                }
                y += ROW_GAP
            }
        }
        if (module is XrayModule && module.settings.any { it.name == "Mode" && it.value == "Custom" }) {
            rows.add(Row(y, y + SettingRow.SETTING_ROW_HEIGHT, module, null, isEditListButton = true))
            y += SettingRow.SETTING_ROW_HEIGHT + ROW_GAP
        }
        return rows
    }

    // --- Render -------------------------------------------------------------

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        advanceWindowAnim()
        if (windowAnim <= 0.001f && closing) {
            minecraft?.gui?.setScreen(null)
            return
        }

        val eased = easeOutCubic(windowAnim)
        context.fill(0, 0, width, height, scaleAlpha(COLOR_DIM, eased))

        renderChrome(context, mouseX, mouseY)

        for (category in Category.entries) {
            renderColumn(context, category, mouseX, mouseY)
        }

        panelModule?.let { module ->
            renderPanel(context, module, mouseX, mouseY)
        }

        if (colorPicker.isOpen) {
            Scissor.clip(context, 0, 0, width, height) {
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

    private fun renderChrome(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        fill(context, 0, 0, width, TOP_Y - 2, colorChrome)
        text(context, "AERO", COL_MARGIN, 6, COLOR_TEXT_DIM)

        val gearX = width - 16
        val gearY = 5
        val gearHovered = mouseX in (gearX - 3)..(gearX + 11) && mouseY in (gearY - 3)..(gearY + 11)
        text(context, "⚙", gearX, gearY, if (gearHovered) accent else COLOR_TEXT_DIM)
    }

    private fun renderColumn(context: GuiGraphicsExtractor, category: Category, mouseX: Int, mouseY: Int) {
        val x0 = columnX[category] ?: return
        val x1 = x0 + COL_WIDTH
        if (x0 > width) return

        val headerY = columnHeaderY()
        val listTop = columnListTop()
        val listBottom = columnBottom()

        fill(context, x0, headerY, x1, listBottom, colorColumnBg)
        drawBorder(context, x0, headerY, COL_WIDTH, listBottom - headerY, COLOR_BORDER)

        // Header bar: accent-tinted, category name left, [count] right.
        fill(context, x0, headerY, x1, headerY + HEADER_HEIGHT, colorHeaderBg)
        val modules = ModuleManager.byCategory(category)
        val name = prettyCategory(category)
        text(context, name, x0 + 5, headerY + 4, COLOR_TEXT)
        val countLabel = "[${modules.size}]"
        text(context, countLabel, x1 - font.width(countLabel) - 5, headerY + 4, COLOR_TEXT)

        val contentBottom = listTop + modules.size * (ROW_HEIGHT + ROW_GAP)
        val maxScroll = (contentBottom - listTop - (listBottom - listTop)).coerceAtLeast(0)
        val off = (scrollOffset[category] ?: 0).coerceIn(0, maxScroll)
        scrollOffset[category] = off

        Scissor.clip(context, x0, listTop, x1, listBottom) {
            var y = listTop + 1 - off
            for (module in modules) {
                val top = y
                val bottom = y + ROW_HEIGHT
                y += ROW_HEIGHT + ROW_GAP
                if (bottom < listTop || top > listBottom) continue
                renderModuleRow(context, module, x0, x1, top, bottom, mouseX, mouseY)
            }
        }

        if (modules.isEmpty()) {
            val msg = "Empty"
            text(context, msg, x0 + (COL_WIDTH - font.width(msg)) / 2, (listTop + listBottom) / 2, COLOR_TEXT_FAINT)
        }
    }

    private fun renderModuleRow(context: GuiGraphicsExtractor, module: Module, x0: Int, x1: Int, top: Int, bottom: Int, mouseX: Int, mouseY: Int) {
        val rowX0 = x0 + 2
        val rowX1 = x1 - 2
        val hovered = mouseX in rowX0..rowX1 && mouseY in top..bottom
        val hAnim = animatedHover(module, hovered)
        val anim = animatedFor(module)

        if (module == panelModule) {
            fill(context, rowX0, top, rowX1, bottom, colorRowSelected)
        } else if (hAnim > 0.02f) {
            fill(context, rowX0, top, rowX1, bottom, scaleAlpha(COLOR_ROW_HOVER, hAnim))
        }

        val nameColor = if (anim > 0.5f) accent else COLOR_TEXT_DIM
        val name = clipToWidth(module.name, rowX1 - rowX0 - 6)
        text(context, name, rowX0 + 4, top + (ROW_HEIGHT - 8) / 2, nameColor)
    }

    private fun renderPanel(context: GuiGraphicsExtractor, module: Module, mouseX: Int, mouseY: Int) {
        val ph = panelHeight(module)
        panelY = panelY.coerceIn(TOP_Y, (height - ph - 4).coerceAtLeast(TOP_Y))
        val px1 = panelX + PANEL_WIDTH

        fill(context, panelX, panelY, px1, panelY + ph, colorPanelBg)
        drawBorder(context, panelX, panelY, PANEL_WIDTH, ph, COLOR_BORDER)
        text(context, module.name.uppercase(), panelX + 6, panelY + 5, COLOR_TEXT_FAINT)

        val viewportTop = panelHeaderBottom()
        val viewportBottom = panelViewportBottom()
        val rows = panelRows(module)
        val contentBottom = rows.lastOrNull()?.bottom ?: viewportTop
        val maxScroll = (contentBottom - viewportTop - (viewportBottom - viewportTop)).coerceAtLeast(0)
        panelScrollOffset = panelScrollOffset.coerceIn(0, maxScroll)

        if (rows.isEmpty()) {
            val msg = "No settings"
            text(context, msg, panelX + (PANEL_WIDTH - font.width(msg)) / 2, panelY + ph / 2, COLOR_TEXT_FAINT)
            return
        }

        val rx = panelX + 6
        val rx1 = px1 - 4

        Scissor.clip(context, panelX, viewportTop, px1, viewportBottom) {
            for (row in rows) {
                val shifted = row.copy(top = row.top - panelScrollOffset, bottom = row.bottom - panelScrollOffset)
                if (shifted.bottom < viewportTop || shifted.top > viewportBottom) continue
                when {
                    shifted.isDropdownOption -> renderDropdownOptionRow(context, shifted, rx, rx1, mouseX, mouseY)
                    shifted.isEditListButton -> renderEditListRow(context, shifted, rx, rx1, mouseX, mouseY)
                    shifted.setting != null -> SettingRow.render(rowCtx(context), shifted.setting, shifted.top, rx, rx1)
                }
            }
        }
    }

    private fun renderDropdownOptionRow(context: GuiGraphicsExtractor, row: Row, x: Int, x1: Int, mouseX: Int, mouseY: Int) {
        val setting = row.setting as? ModeSetting ?: return
        val option = row.dropdownOption ?: return
        val selected = option == setting.value
        val hovered = mouseX in x..x1 && mouseY in row.top..row.bottom
        if (selected) fill(context, x, row.top, x1, row.bottom, colorRowSelected)
        else if (hovered) fill(context, x, row.top, x1, row.bottom, COLOR_ROW_HOVER)
        text(context, option, x + 4, row.top + 3, if (selected) COLOR_TEXT else COLOR_TEXT_DIM)
    }

    private fun renderEditListRow(context: GuiGraphicsExtractor, row: Row, x: Int, x1: Int, mouseX: Int, mouseY: Int) {
        val hovered = mouseX in x..x1 && mouseY in row.top..row.bottom
        if (hovered) fill(context, x, row.top, x1, row.bottom, COLOR_ROW_HOVER)
        val label = "Edit List..."
        text(context, label, x + (x1 - x - font.width(label)) / 2, row.top + 5, accent)
    }

    // --- Small drawing helpers -------------------------------------------------

    private fun fill(context: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, extra: Float = 1f) {
        context.fill(x0, y0, x1, y1, scaleAlpha(color, easeOutCubic(windowAnim) * extra))
    }

    private fun text(context: GuiGraphicsExtractor, str: String, x: Int, y: Int, color: Int, extra: Float = 1f) {
        context.text(font, str, x, y, scaleAlpha(color, easeOutCubic(windowAnim) * extra), true)
    }

    private fun drawBorder(context: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        fill(context, x, y, x + w, y + 1, color)
        fill(context, x, y + h - 1, x + w, y + h, color)
        fill(context, x, y, x + 1, y + h, color)
        fill(context, x + w - 1, y, x + w, y + h, color)
    }

    private fun clipToWidth(str: String, maxWidth: Int): String {
        if (maxWidth <= 0) return ""
        if (font.width(str) <= maxWidth) return str
        return font.plainSubstrByWidth(str, (maxWidth - font.width("...")).coerceAtLeast(0)) + "..."
    }

    /** [SettingRow.Ctx] bound to this screen's own colours/state, wrapping [context] for the lifetime of one render call. */
    private fun rowCtx(context: GuiGraphicsExtractor): SettingRow.Ctx = object : SettingRow.Ctx {
        override val font get() = this@MeteorGuiScreen.font
        override val accent get() = this@MeteorGuiScreen.accent
        override val colorText = COLOR_TEXT
        override val colorTextDim = COLOR_TEXT_DIM
        override val colorTrackOff = COLOR_TRACK_OFF
        override var modeDropdown: ModeSetting?
            get() = this@MeteorGuiScreen.modeDropdown
            set(value) { this@MeteorGuiScreen.modeDropdown = value }
        override var keybindTarget: KeybindSetting?
            get() = this@MeteorGuiScreen.keybindTarget
            set(value) { this@MeteorGuiScreen.keybindTarget = value }
        override fun fill(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, extra: Float) =
            this@MeteorGuiScreen.fill(context, x0, y0, x1, y1, color, extra)
        override fun text(str: String, x: Int, y: Int, color: Int, extra: Float) =
            this@MeteorGuiScreen.text(context, str, x, y, color, extra)
    }

    private val clickCtx: SettingRow.ClickCtx = object : SettingRow.ClickCtx {
        override var modeDropdown: ModeSetting?
            get() = this@MeteorGuiScreen.modeDropdown
            set(value) { this@MeteorGuiScreen.modeDropdown = value }
        override var keybindTarget: KeybindSetting?
            get() = this@MeteorGuiScreen.keybindTarget
            set(value) { this@MeteorGuiScreen.keybindTarget = value }
    }

    private fun animatedFor(module: Module): Float = Anim.advance(toggleAnim, module, if (module.enabled) 1f else 0f, ANIM_FAST)

    private fun animatedHover(key: Any, hovered: Boolean): Float = Anim.advance(hoverAnim, key, if (hovered) 1f else 0f, ANIM_FAST)

    private fun easeOutCubic(t: Float): Float = Anim.easeOutCubic(t)

    private fun scaleAlpha(color: Int, scale: Float): Int = Anim.scaleAlpha(color, scale)

    private fun prettyCategory(category: Category): String =
        category.name.lowercase().replaceFirstChar { it.uppercase() }

    /** Opens the floating settings panel for [module], anchored beside its column -- flips to the left side if it'd clip off-screen to the right. */
    private fun openPanel(module: Module, columnX0: Int, columnX1: Int, anchorY: Int) {
        panelModule = module
        modeDropdown = null
        panelScrollOffset = 0
        val rightX = columnX1 + PANEL_GAP
        panelX = if (rightX + PANEL_WIDTH <= width) rightX else (columnX0 - PANEL_GAP - PANEL_WIDTH).coerceAtLeast(0)
        panelY = anchorY.coerceIn(TOP_Y, (height - panelHeight(module) - 4).coerceAtLeast(TOP_Y))
    }

    private fun closePanel() {
        panelModule = null
        modeDropdown = null
    }

    // --- Input ---------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (closing) return false
        val mouseX = click.x()
        val mouseY = click.y()

        if (colorPicker.isOpen) {
            if (colorPicker.mouseClicked(mouseX, mouseY)) return true
        }

        val gearX = width - 16
        val gearY = 5
        if (mouseX in (gearX - 3).toDouble()..(gearX + 11).toDouble() && mouseY in (gearY - 3).toDouble()..(gearY + 11).toDouble()) {
            minecraft?.gui?.setScreen(SettingsScreen())
            return true
        }

        val module = panelModule
        if (module != null) {
            val ph = panelHeight(module)
            if (mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY && mouseY <= panelY + ph) {
                val viewportTop = panelHeaderBottom()
                val viewportBottom = panelViewportBottom()
                if (mouseY >= viewportTop && mouseY <= viewportBottom) {
                    val contentMouseY = mouseY + panelScrollOffset
                    val rx = panelX + 6
                    val rx1 = panelX + PANEL_WIDTH - 4
                    for (row in panelRows(module)) {
                        if (contentMouseY < row.top || contentMouseY > row.bottom) continue
                        if (row.isDropdownOption) {
                            val dropdownSetting = row.setting as? ModeSetting
                            val option = row.dropdownOption
                            if (dropdownSetting != null && option != null) dropdownSetting.value = option
                            modeDropdown = null
                            return true
                        }
                        if (row.isEditListButton) {
                            modeDropdown = null
                            minecraft?.gui?.setScreen(XrayCustomScreen(row.module as XrayModule))
                            return true
                        }
                        if (row.setting != null) {
                            return handlePanelSettingClick(row, mouseX, rx, rx1)
                        }
                    }
                }
                return true
            }
            // Outside the panel -- close it, then fall through so the click
            // can still hit whatever's underneath (e.g. another module row).
            closePanel()
        }

        for (category in Category.entries) {
            val x0 = columnX[category] ?: continue
            val x1 = x0 + COL_WIDTH
            val listTop = columnListTop()
            val listBottom = columnBottom()
            if (mouseX < x0 || mouseX > x1 || mouseY < listTop || mouseY > listBottom) continue

            val modules = ModuleManager.byCategory(category)
            val off = scrollOffset[category] ?: 0
            var rowY = listTop + 1 - off
            for (m in modules) {
                val top = rowY
                val bottom = rowY + ROW_HEIGHT
                rowY += ROW_HEIGHT + ROW_GAP
                if (mouseY < top || mouseY > bottom) continue
                return handleModuleRowClick(m, click.button(), x0, x1, top)
            }
            return true
        }

        modeDropdown = null
        return super.mouseClicked(click, doubled)
    }

    private fun handleModuleRowClick(module: Module, button: Int, columnX0: Int, columnX1: Int, rowTop: Int): Boolean {
        if (button == 1) {
            openPanel(module, columnX0, columnX1, rowTop)
            return true
        }
        // Any other button (left-click) is a direct toggle.
        module.toggle()
        return true
    }

    private fun handlePanelSettingClick(row: Row, mouseX: Double, px: Int, px1: Int): Boolean {
        val setting = row.setting ?: return false
        return SettingRow.handleClick(
            clickCtx,
            setting,
            openColorPicker = { cs ->
                val screenBottom = row.bottom - panelScrollOffset
                val ph = panelHeight(panelModule)
                colorPicker.open(
                    cs,
                    (panelX + PANEL_WIDTH - ColorPickerPopup.WIDTH - 4).coerceAtLeast(panelX),
                    screenBottom + 2,
                    0, 0, width, height,
                )
            },
            startSliderDrag = { slider ->
                draggingSlider = slider
                dragBarLeft = px
                dragBarWidth = (px1 - px).coerceAtLeast(20)
                applySliderDrag(slider, mouseX, dragBarLeft, dragBarWidth)
            },
        )
    }

    override fun mouseDragged(click: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (colorPicker.isOpen && colorPicker.mouseDragged(click.x(), click.y())) return true
        draggingSlider?.let { slider ->
            applySliderDrag(slider, click.x(), dragBarLeft, dragBarWidth)
            return true
        }
        return super.mouseDragged(click, deltaX, deltaY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        colorPicker.mouseReleased()
        draggingSlider = null
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val module = panelModule
        if (module != null && mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH &&
            mouseY >= panelHeaderBottom() && mouseY <= panelViewportBottom()
        ) {
            val viewportTop = panelHeaderBottom()
            val viewportBottom = panelViewportBottom()
            val rows = panelRows(module)
            val contentBottom = rows.lastOrNull()?.bottom ?: viewportTop
            val maxScroll = (contentBottom - viewportTop - (viewportBottom - viewportTop)).coerceAtLeast(0)
            panelScrollOffset = (panelScrollOffset - (verticalAmount * 12).toInt()).coerceIn(0, maxScroll)
            return true
        }

        for (category in Category.entries) {
            val x0 = columnX[category] ?: continue
            val x1 = x0 + COL_WIDTH
            val listTop = columnListTop()
            val listBottom = columnBottom()
            if (mouseX < x0 || mouseX > x1 || mouseY < listTop || mouseY > listBottom) continue

            val modules = ModuleManager.byCategory(category)
            val contentBottom = listTop + modules.size * (ROW_HEIGHT + ROW_GAP)
            val maxScroll = (contentBottom - listTop - (listBottom - listTop)).coerceAtLeast(0)
            val off = scrollOffset[category] ?: 0
            scrollOffset[category] = (off - (verticalAmount * 12).toInt()).coerceIn(0, maxScroll)
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

    override fun charTyped(input: net.minecraft.client.input.CharacterEvent): Boolean {
        return super.charTyped(input)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
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
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (colorPicker.isOpen) {
                colorPicker.close()
                return true
            }
            if (modeDropdown != null) {
                modeDropdown = null
                return true
            }
            if (panelModule != null) {
                closePanel()
                return true
            }
            onClose()
            return true
        }
        return super.keyPressed(input)
    }
}
