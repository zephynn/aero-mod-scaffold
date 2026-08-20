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

/**
 * The whole GUI: a category sidebar + a scrolling list of that category's
 * modules, each expandable to show its settings inline. Rendering and
 * input are both immediate-mode (no widget objects) -- everything switches
 * on the sealed Setting subtype, same pattern ConfigManager already uses.
 *
 * Layout, hit-testing, and rendering are kept in the same three loops
 * (buildRows / render / mouseClicked) on purpose: adding a new Setting
 * type means adding one branch to each of those, nowhere else.
 */
class ClickGuiScreen : Screen(Text.literal("Aero")) {

    private companion object {
        const val SIDEBAR_X = 12
        const val SIDEBAR_Y = 24
        const val SIDEBAR_WIDTH = 80
        const val TAB_HEIGHT = 20

        const val PANEL_X = SIDEBAR_X + SIDEBAR_WIDTH + 8
        const val PANEL_WIDTH = 170
        const val ROW_HEIGHT = 20
        const val SETTING_HEIGHT = 16

        const val COLOR_BG = 0xB0101014.toInt()
        const val COLOR_PANEL = 0xD0181820.toInt()
        const val COLOR_ROW = 0xB0202028.toInt()
        const val COLOR_ROW_ENABLED = 0xC02D6A4F.toInt()
        const val COLOR_ROW_HOVER = 0xD02A2A34.toInt()
        const val COLOR_SETTING = 0x902A2A32.toInt()
        const val COLOR_ACCENT = 0xFF4FD08A.toInt()
        const val COLOR_TEXT = 0xFFE0E0E8.toInt()
        const val COLOR_TEXT_DIM = 0xFFA0A0AC.toInt()

        val PRESET_COLORS = intArrayOf(
            0xFFFF5555.toInt(), 0xFFFFAA00.toInt(), 0xFFFFFF55.toInt(),
            0xFF55FF55.toInt(), 0xFF55FFFF.toInt(), 0xFF5599FF.toInt(),
            0xFFAA55FF.toInt(), 0xFFFF55FF.toInt(), 0xFFFFFFFF.toInt(),
        )
    }

    private var selectedCategory: Category = Category.MOVEMENT
    private val expandedModules: MutableSet<Module> = mutableSetOf()

    /** Non-null while the next key press should rebind this instead of doing anything else. */
    private var keybindTarget: KeybindSetting? = null
    private var moduleKeybindTarget: Module? = null

    /** Non-null while the mouse is held down dragging this slider. */
    private var draggingSlider: SliderSetting? = null

    /** One row's screen position, rebuilt every frame from the current category + expansion state. */
    private data class Row(val top: Int, val bottom: Int, val module: Module, val setting: Setting<*>?)

    private fun buildRows(): List<Row> {
        val rows = mutableListOf<Row>()
        var y = SIDEBAR_Y
        for (module in ModuleManager.byCategory(selectedCategory)) {
            rows.add(Row(y, y + ROW_HEIGHT, module, null))
            y += ROW_HEIGHT
            if (module in expandedModules) {
                for (setting in module.settings) {
                    rows.add(Row(y, y + SETTING_HEIGHT, module, setting))
                    y += SETTING_HEIGHT
                }
            }
        }
        return rows
    }

    override fun shouldPause(): Boolean = false

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, COLOR_BG)

        // --- Sidebar: one tab per category ---------------------------------
        var tabY = SIDEBAR_Y
        for (category in Category.entries) {
            val hovered = mouseX in SIDEBAR_X..(SIDEBAR_X + SIDEBAR_WIDTH) && mouseY in tabY..(tabY + TAB_HEIGHT)
            val selected = category == selectedCategory
            context.fill(
                SIDEBAR_X, tabY, SIDEBAR_X + SIDEBAR_WIDTH, tabY + TAB_HEIGHT - 2,
                when {
                    selected -> COLOR_ROW_ENABLED
                    hovered -> COLOR_ROW_HOVER
                    else -> COLOR_ROW
                },
            )
            context.drawTextWithShadow(
                textRenderer, category.name, SIDEBAR_X + 6, tabY + 6, COLOR_TEXT,
            )
            tabY += TAB_HEIGHT
        }

        // --- Module list for the selected category --------------------------
        val panelBottom = buildRows().maxOfOrNull { it.bottom }?.plus(4) ?: (SIDEBAR_Y + ROW_HEIGHT)
        context.fill(PANEL_X, SIDEBAR_Y, PANEL_X + PANEL_WIDTH, panelBottom, COLOR_PANEL)

        for (row in buildRows()) {
            val hovered = mouseX in PANEL_X..(PANEL_X + PANEL_WIDTH) && mouseY in row.top..row.bottom
            if (row.setting == null) {
                renderModuleRow(context, row, hovered)
            } else {
                renderSettingRow(context, row.setting, row.top, hovered)
            }
        }
    }

    private fun renderModuleRow(context: DrawContext, row: Row, hovered: Boolean) {
        val module = row.module
        context.fill(
            PANEL_X + 1, row.top, PANEL_X + PANEL_WIDTH - 1, row.bottom - 1,
            when {
                module.enabled -> COLOR_ROW_ENABLED
                hovered -> COLOR_ROW_HOVER
                else -> COLOR_ROW
            },
        )
        val arrow = if (module in expandedModules) "-" else "+"
        context.drawTextWithShadow(textRenderer, "$arrow ${module.name}", PANEL_X + 6, row.top + 6, COLOR_TEXT)

        if (module.keybind != GLFW.GLFW_KEY_UNKNOWN || module == moduleKeybindTarget) {
            val label = if (module == moduleKeybindTarget) "..." else keyName(module.keybind)
            val labelWidth = textRenderer.getWidth(label)
            context.drawTextWithShadow(
                textRenderer, label, PANEL_X + PANEL_WIDTH - labelWidth - 6, row.top + 6, COLOR_TEXT_DIM,
            )
        }
    }

    private fun renderSettingRow(context: DrawContext, setting: Setting<*>, top: Int, hovered: Boolean) {
        context.fill(PANEL_X + 8, top, PANEL_X + PANEL_WIDTH - 4, top + SETTING_HEIGHT - 1, COLOR_SETTING)

        when (setting) {
            is BoolSetting -> {
                context.drawTextWithShadow(textRenderer, setting.name, PANEL_X + 12, top + 4, COLOR_TEXT_DIM)
                val boxColor = if (setting.value) COLOR_ACCENT else COLOR_TEXT_DIM
                context.fill(PANEL_X + PANEL_WIDTH - 16, top + 3, PANEL_X + PANEL_WIDTH - 6, top + 13, boxColor)
            }
            is SliderSetting -> {
                val label = "${setting.name}: ${"%.2f".format(setting.value)}"
                context.drawTextWithShadow(textRenderer, label, PANEL_X + 12, top + 4, COLOR_TEXT_DIM)
                val barLeft = PANEL_X + 12
                val barRight = PANEL_X + PANEL_WIDTH - 12
                val fraction = ((setting.value - setting.min) / (setting.max - setting.min)).coerceIn(0.0, 1.0)
                val fillX = barLeft + (fraction * (barRight - barLeft)).toInt()
                context.fill(barLeft, top + 13, barRight, top + 15, COLOR_ROW)
                context.fill(barLeft, top + 13, fillX, top + 15, COLOR_ACCENT)
            }
            is ModeSetting -> {
                val label = "${setting.name}: ${setting.value}"
                context.drawTextWithShadow(textRenderer, label, PANEL_X + 12, top + 4, COLOR_TEXT_DIM)
            }
            is KeybindSetting -> {
                val listening = setting == keybindTarget
                val label = "${setting.name}: ${if (listening) "..." else keyName(setting.value)}"
                context.drawTextWithShadow(textRenderer, label, PANEL_X + 12, top + 4, COLOR_TEXT_DIM)
            }
            is ColorSetting -> {
                context.drawTextWithShadow(textRenderer, setting.name, PANEL_X + 12, top + 4, COLOR_TEXT_DIM)
                context.fill(PANEL_X + PANEL_WIDTH - 20, top + 3, PANEL_X + PANEL_WIDTH - 6, top + 13, setting.value)
            }
        }
        if (hovered) {
            context.fill(PANEL_X + 8, top, PANEL_X + PANEL_WIDTH - 4, top + SETTING_HEIGHT - 1, 0x30FFFFFF)
        }
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mouseX = click.x()
        val mouseY = click.y()

        // Sidebar tab click.
        var tabY = SIDEBAR_Y
        for (category in Category.entries) {
            if (mouseX in SIDEBAR_X.toDouble()..(SIDEBAR_X + SIDEBAR_WIDTH).toDouble() &&
                mouseY in tabY.toDouble()..(tabY + TAB_HEIGHT).toDouble()
            ) {
                selectedCategory = category
                return true
            }
            tabY += TAB_HEIGHT
        }

        for (row in buildRows()) {
            if (mouseX < PANEL_X || mouseX > PANEL_X + PANEL_WIDTH) continue
            if (mouseY < row.top || mouseY > row.bottom) continue

            if (row.setting == null) {
                // Right-hand keybind label vs. the rest of the row toggles the module.
                val label = keyName(row.module.keybind)
                val labelWidth = textRenderer.getWidth(label)
                val labelLeft = PANEL_X + PANEL_WIDTH - labelWidth - 8
                if (mouseX >= labelLeft) {
                    moduleKeybindTarget = row.module
                } else {
                    if (row.module in expandedModules) expandedModules.remove(row.module) else expandedModules.add(row.module)
                    // Clicking directly on the name (not the arrow) also toggles enable.
                    if (mouseX > PANEL_X + 16) row.module.toggle()
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
        val barLeft = PANEL_X + 12
        val barRight = PANEL_X + PANEL_WIDTH - 12
        val fraction = ((mouseX - barLeft) / (barRight - barLeft)).coerceIn(0.0, 1.0)
        val raw = setting.min + fraction * (setting.max - setting.min)
        val stepped = Math.round(raw / setting.step) * setting.step
        setting.set(stepped)
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
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
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
