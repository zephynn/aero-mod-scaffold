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
 * Alternate "Meteor Client style" ClickGUI layout: a left category rail, a
 * flat scrollable module list (no accordion -- clicking a row *selects* it
 * instead of expanding inline), and a right-side panel showing the
 * selected module's settings. Switchable from the accordion [ClickGuiScreen]
 * via [ClientSettings.guiStyle] (see the gear-icon toggle in [SettingsScreen]).
 *
 * Per-setting-type rendering/click-handling is shared with [ClickGuiScreen]
 * through [SettingRow] -- this screen only owns the layout (rail + flat
 * list + side panel) around it, not a second copy of the BoolSetting/
 * SliderSetting/ModeSetting/KeybindSetting/ColorSetting logic.
 */
class MeteorGuiScreen : Screen(Component.literal("Aero")) {

    private companion object {
        const val MAX_GUI_WIDTH = 460
        const val MAX_GUI_HEIGHT = 260
        const val MIN_GUI_WIDTH = 360
        const val MIN_GUI_HEIGHT = 200
        const val CHROME_HEIGHT = 20

        const val SEARCH_HEIGHT = 18
        const val CAT_HEIGHT = 18
        const val ROW_HEIGHT = 18
        const val ROW_GAP = 2

        const val COLOR_DIM = 0x38000000.toInt()
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
    private val colorBg get() = (0xB2 shl 24) or Theme.BACKGROUND
    private val colorRail get() = (0xB2 shl 24) or Theme.darkenedBackground(0.10f)
    private val colorPanel get() = (0xB2 shl 24) or Theme.darkenedBackground(0.10f)
    private val colorChrome get() = (0xC0 shl 24) or Theme.darkenedBackground(0.20f)
    private val colorFieldBg get() = (0xC8 shl 24) or Theme.darkenedBackground(0.22f)
    private val colorRowSelected get() = (0x2A shl 24) or Theme.accent

    private var guiX = 0
    private var guiY = 0
    private var guiWidth = MAX_GUI_WIDTH
    private var guiHeight = MAX_GUI_HEIGHT
    private var railWidth = 0
    private var listWidth = 0
    private var panelWidth = 0

    private var selectedCategory: Category = Category.MOVEMENT
    private var showFavourites = false

    /** The module whose settings the side panel currently shows, or null if none/empty. */
    private var selectedModule: Module? = null

    private var searchFocused = false
    private var searchQuery = ""

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

    private var listScrollOffset = 0
    private var panelScrollOffset = 0

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        guiHeight = (height * 0.40f).toInt().coerceIn(MIN_GUI_HEIGHT, MAX_GUI_HEIGHT).coerceAtMost(height - 8)
        guiWidth = (guiHeight * 16 / 9).coerceIn(MIN_GUI_WIDTH, MAX_GUI_WIDTH).coerceAtMost(width - 8)
        railWidth = (guiWidth * 0.22f).toInt().coerceAtLeast(76)
        panelWidth = (guiWidth * 0.34f).toInt().coerceAtLeast(120)
        listWidth = guiWidth - railWidth - panelWidth

        guiX = (width - guiWidth) / 2
        guiY = (height - guiHeight) / 2
    }

    override fun onClose() {
        if (!closing) {
            closing = true
            return
        }
        super.onClose()
    }

    // --- Data ---------------------------------------------------------------

    private fun visibleModules(): List<Module> {
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            return ModuleManager.all().filter { it.name.contains(q, ignoreCase = true) }.sortedBy { it.name }
        }
        if (showFavourites) {
            return ModuleManager.all().filter { it.pinned }.sortedBy { it.name }
        }
        return ModuleManager.byCategory(selectedCategory)
    }

    private fun panelRows(): List<Row> {
        val module = selectedModule ?: return emptyList()
        var y = panelTop()
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

    private fun panelTop(): Int = guiY + CHROME_HEIGHT + 24
    private fun panelViewportTop(): Int = guiY + CHROME_HEIGHT + 22
    private fun panelViewportBottom(): Int = guiY + guiHeight - 4

    private fun listViewportTop(): Int = guiY + CHROME_HEIGHT + 22
    private fun listViewportBottom(): Int = guiY + guiHeight - 4

    // --- Render ---------------------------------------------------------------

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        advanceWindowAnim()
        if (windowAnim <= 0.001f && closing) {
            minecraft?.gui?.setScreen(null)
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
        renderSettingsPanel(context, gy, mouseX, mouseY)

        val listX = guiX + railWidth
        fill(context, listX, gy + CHROME_HEIGHT, listX + 1, gy + guiHeight, COLOR_BORDER)
        val panelX = guiX + railWidth + listWidth
        fill(context, panelX, gy + CHROME_HEIGHT, panelX + 1, gy + guiHeight, COLOR_BORDER)

        if (colorPicker.isOpen) {
            Scissor.clip(context, guiX, gy, guiX + guiWidth, gy + guiHeight) {
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

    private fun renderChrome(context: GuiGraphicsExtractor, gy: Int, mouseX: Int, mouseY: Int) {
        fill(context, guiX, gy, guiX + guiWidth, gy + CHROME_HEIGHT, colorChrome)
        text(context, "AERO", guiX + 8, gy + 6, COLOR_TEXT_DIM)

        val gearX = guiX + guiWidth - 16
        val gearY = gy + 5
        val gearHovered = mouseX in (gearX - 3)..(gearX + 11) && mouseY in (gearY - 3)..(gearY + 11)
        text(context, "⚙", gearX, gearY, if (gearHovered) accent else COLOR_TEXT_DIM)
    }

    private fun renderRail(context: GuiGraphicsExtractor, gy: Int, mouseX: Int, mouseY: Int) {
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
        text(context, searchLabel, sx + 5, sy + 5, if (searchQuery.isNotEmpty() || searchFocused) COLOR_TEXT else COLOR_TEXT_FAINT)

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

    private fun renderTab(context: GuiGraphicsExtractor, sx: Int, sw: Int, y: Int, label: String, selected: Boolean, hoverAnim: Float) {
        if (selected) {
            fill(context, sx, y, sx + sw, y + CAT_HEIGHT - 2, colorRowSelected)
        } else if (hoverAnim > 0.02f) {
            fill(context, sx, y, sx + sw, y + CAT_HEIGHT - 2, scaleAlpha(COLOR_ROW_HOVER, hoverAnim))
        }
        text(context, label, sx + 6, y + 4, if (selected) COLOR_TEXT else COLOR_TEXT_DIM)
    }

    private fun anyFavourites(): Boolean = ModuleManager.all().any { it.pinned }

    private fun renderModuleList(context: GuiGraphicsExtractor, gy: Int, mouseX: Int, mouseY: Int) {
        val listX = guiX + railWidth
        val headerY = gy + CHROME_HEIGHT + 6
        val headerText = when {
            searchQuery.isNotBlank() -> searchQuery.trim()
            showFavourites -> "Favourites"
            else -> prettyCategory(selectedCategory)
        }
        text(context, headerText.uppercase(), listX + 8, headerY, COLOR_TEXT_FAINT)

        val modules = visibleModules()
        val viewportTop = listViewportTop()
        val viewportBottom = listViewportBottom()
        val contentBottom = viewportTop + modules.size * (ROW_HEIGHT + ROW_GAP)
        val maxScroll = (contentBottom - viewportTop - (viewportBottom - viewportTop)).coerceAtLeast(0)
        listScrollOffset = listScrollOffset.coerceIn(0, maxScroll)

        Scissor.clip(context, listX, viewportTop, listX + listWidth, viewportBottom) {
            var y = viewportTop + 2 - listScrollOffset
            for (module in modules) {
                val top = y
                val bottom = y + ROW_HEIGHT
                y += ROW_HEIGHT + ROW_GAP
                if (bottom < viewportTop || top > viewportBottom) continue
                renderModuleRow(context, module, top, bottom, mouseX, mouseY)
            }
        }

        if (modules.isEmpty()) {
            val msg = if (showFavourites) "No pinned modules yet" else "No modules found"
            text(context, msg, listX + (listWidth - font.width(msg)) / 2, gy + guiHeight / 2, COLOR_TEXT_FAINT)
        }
    }

    private fun renderModuleRow(context: GuiGraphicsExtractor, module: Module, top: Int, bottom: Int, mouseX: Int, mouseY: Int) {
        val listX = guiX + railWidth
        val x0 = listX + 6
        val x1 = listX + listWidth - 6
        val selected = module == selectedModule
        val hovered = mouseX in x0..x1 && mouseY in top..bottom
        val hAnim = animatedHover(module, hovered)

        val anim = animatedFor(module)

        if (selected) {
            fill(context, x0, top, x1, bottom, colorRowSelected)
        } else if (hAnim > 0.02f) {
            fill(context, x0, top, x1, bottom, scaleAlpha(COLOR_ROW_HOVER, hAnim))
        }

        var rx = x1 - 3
        val toggleW = 16
        rx -= toggleW
        SettingRow.drawToggle(rowCtx(context), rx, top + (ROW_HEIGHT - 9) / 2, toggleW, 9, anim)

        if (module.keybind != GLFW.GLFW_KEY_UNKNOWN || module == moduleKeybindTarget) {
            rx -= 3
            val kbLabel = if (module == moduleKeybindTarget) "..." else SettingRow.keyName(module.keybind)
            val kbWidth = font.width(kbLabel) + 5
            rx -= kbWidth
            text(context, kbLabel, rx + 3, top + (ROW_HEIGHT - 8) / 2, COLOR_TEXT_FAINT)
            rx -= 2
        }

        rx -= 12
        val pinGlyph = if (module.pinned) "★" else "☆"
        text(context, pinGlyph, rx, top + (ROW_HEIGHT - 8) / 2, if (module.pinned) accent else COLOR_TEXT_FAINT)

        val nameLimit = rx - 6
        val textX = x0 + 6
        val textY = top + (ROW_HEIGHT - 8) / 2
        val nameColor = if (anim > 0.5f) COLOR_TEXT else COLOR_TEXT_DIM
        val name = clipToWidth(module.name, nameLimit - textX)
        text(context, name, textX, textY, nameColor)
    }

    private fun renderSettingsPanel(context: GuiGraphicsExtractor, gy: Int, mouseX: Int, mouseY: Int) {
        val panelX = guiX + railWidth + listWidth
        fill(context, panelX, gy + CHROME_HEIGHT, panelX + panelWidth, gy + guiHeight, colorPanel)

        val module = selectedModule
        val headerY = gy + CHROME_HEIGHT + 6
        if (module == null) {
            val msg = "Select a module"
            text(context, msg, panelX + (panelWidth - font.width(msg)) / 2, gy + guiHeight / 2, COLOR_TEXT_FAINT)
            return
        }
        text(context, module.name.uppercase(), panelX + 8, headerY, COLOR_TEXT_FAINT)

        val viewportTop = panelViewportTop()
        val viewportBottom = panelViewportBottom()
        val rows = panelRows()
        val contentBottom = rows.lastOrNull()?.bottom ?: viewportTop
        val maxScroll = (contentBottom - viewportTop - (viewportBottom - viewportTop)).coerceAtLeast(0)
        panelScrollOffset = panelScrollOffset.coerceIn(0, maxScroll)

        val px = panelX + 8
        val px1 = panelX + panelWidth - 6

        if (rows.isEmpty()) {
            val msg = "No settings"
            text(context, msg, panelX + (panelWidth - font.width(msg)) / 2, gy + guiHeight / 2, COLOR_TEXT_FAINT)
            return
        }

        Scissor.clip(context, panelX, viewportTop, panelX + panelWidth, viewportBottom) {
            for (row in rows) {
                val shifted = row.copy(top = row.top - panelScrollOffset, bottom = row.bottom - panelScrollOffset)
                if (shifted.bottom < viewportTop || shifted.top > viewportBottom) continue
                when {
                    shifted.isDropdownOption -> renderDropdownOptionRow(context, shifted, px, px1, mouseX, mouseY)
                    shifted.isEditListButton -> renderEditListRow(context, shifted, px, px1, mouseX, mouseY)
                    shifted.setting != null -> SettingRow.render(rowCtx(context), shifted.setting, shifted.top, px, px1)
                }
            }
        }
    }

    private fun renderDropdownOptionRow(context: GuiGraphicsExtractor, row: Row, x: Int, x1: Int, mouseX: Int, mouseY: Int) {
        val setting = row.setting as? ModeSetting ?: return
        val option = row.dropdownOption ?: return
        val ix = x + 8
        val selected = option == setting.value
        val hovered = mouseX in ix..x1 && mouseY in row.top..row.bottom
        if (selected) fill(context, ix, row.top, x1, row.bottom, colorRowSelected)
        else if (hovered) fill(context, ix, row.top, x1, row.bottom, COLOR_ROW_HOVER)
        text(context, option, ix + 4, row.top + 3, if (selected) COLOR_TEXT else COLOR_TEXT_DIM)
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

    // --- Input ---------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (closing) return false
        val mouseX = click.x()
        val mouseY = click.y()

        if (colorPicker.isOpen) {
            if (colorPicker.mouseClicked(mouseX, mouseY)) return true
        }

        val gearX = guiX + guiWidth - 16
        val gearY = guiY + 5
        if (mouseX in (gearX - 3).toDouble()..(gearX + 11).toDouble() && mouseY in (gearY - 3).toDouble()..(gearY + 11).toDouble()) {
            minecraft?.gui?.setScreen(SettingsScreen())
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
        val viewportTop = listViewportTop()
        val viewportBottom = listViewportBottom()
        if (mouseY >= viewportTop && mouseY <= viewportBottom) {
            val modules = visibleModules()
            var rowY = viewportTop + 2 - listScrollOffset
            for (module in modules) {
                val top = rowY
                val bottom = rowY + ROW_HEIGHT
                rowY += ROW_HEIGHT + ROW_GAP
                if (mouseX < x0 || mouseX > x1d || mouseY < top || mouseY > bottom) continue
                return handleModuleRowClick(module, mouseX, x1d.toInt())
            }
        }

        val panelX = guiX + railWidth + listWidth
        val panelViewportTop = panelViewportTop()
        val panelViewportBottom = panelViewportBottom()
        if (mouseX >= panelX && mouseY >= panelViewportTop && mouseY <= panelViewportBottom) {
            val contentMouseY = mouseY + panelScrollOffset
            for (row in panelRows()) {
                if (contentMouseY < row.top || contentMouseY > row.bottom) continue
                val px = panelX + 8
                val px1 = panelX + panelWidth - 6
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
                    return handlePanelSettingClick(row, mouseX, px, px1)
                }
            }
        }

        modeDropdown = null
        return super.mouseClicked(click, doubled)
    }

    private fun handleModuleRowClick(module: Module, mouseX: Double, x1: Int): Boolean {
        var rx = x1 - 3
        val toggleW = 16
        rx -= toggleW
        if (mouseX >= rx && mouseX <= rx + toggleW) {
            module.toggle()
            return true
        }

        if (module.keybind != GLFW.GLFW_KEY_UNKNOWN) {
            rx -= 3
            val kbLabel = SettingRow.keyName(module.keybind)
            val kbWidth = font.width(kbLabel) + 5
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

        selectedModule = module
        modeDropdown = null
        panelScrollOffset = 0
        return true
    }

    private fun handlePanelSettingClick(row: Row, mouseX: Double, px: Int, px1: Int): Boolean {
        val setting = row.setting ?: return false
        return SettingRow.handleClick(
            clickCtx,
            setting,
            openColorPicker = { cs ->
                val gy = guiY
                val screenBottom = row.bottom - panelScrollOffset
                val panelX = guiX + railWidth + listWidth
                colorPicker.open(cs, (panelX + panelWidth - ColorPickerPopup.WIDTH - 6).coerceAtLeast(panelX), screenBottom + 2, guiX, gy + CHROME_HEIGHT, guiX + guiWidth, gy + guiHeight)
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
        val listX = guiX + railWidth
        val panelX = listX + listWidth
        val viewportTop = listViewportTop()
        val viewportBottom = listViewportBottom()
        if (mouseX >= listX && mouseX < panelX && mouseY >= viewportTop && mouseY <= viewportBottom) {
            val modules = visibleModules()
            val contentBottom = viewportTop + modules.size * (ROW_HEIGHT + ROW_GAP)
            val maxScroll = (contentBottom - viewportTop - (viewportBottom - viewportTop)).coerceAtLeast(0)
            listScrollOffset = (listScrollOffset - (verticalAmount * 12).toInt()).coerceIn(0, maxScroll)
            return true
        }
        if (mouseX >= panelX && mouseY >= panelViewportTop() && mouseY <= panelViewportBottom()) {
            val rows = panelRows()
            val pvt = panelViewportTop()
            val contentBottom = rows.lastOrNull()?.bottom ?: pvt
            val maxScroll = (contentBottom - pvt - (panelViewportBottom() - pvt)).coerceAtLeast(0)
            panelScrollOffset = (panelScrollOffset - (verticalAmount * 12).toInt()).coerceIn(0, maxScroll)
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
        if (searchFocused) {
            searchQuery += input.codepointAsString()
            return true
        }
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
            onClose()
            return true
        }
        return super.keyPressed(input)
    }
}
