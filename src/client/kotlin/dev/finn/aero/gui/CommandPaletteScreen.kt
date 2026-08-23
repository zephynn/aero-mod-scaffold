package dev.finn.aero.gui

import dev.finn.aero.config.Theme
import dev.finn.aero.module.Module
import dev.finn.aero.module.ModuleManager
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Fast fuzzy-search command palette, opened with Alt+RightShift from
 * gameplay (see `AeroClient.pollKeybinds`) -- independent of the full
 * ClickGUI. Type to filter, arrow keys to move selection, Enter to toggle
 * the selected module and close, Escape/click-outside to close without
 * acting. The search box is the same hand-rolled state-fields +
 * charTyped/keyPressed pattern [XrayCustomScreen]'s block search and
 * [ClickGuiScreen]'s rail search already use -- no new widget class.
 */
class CommandPaletteScreen : Screen(Component.literal("Aero Command Palette")) {

    private companion object {
        const val WIDTH = 280
        const val SEARCH_HEIGHT = 20
        const val ROW_HEIGHT = 16
        const val MAX_VISIBLE_ROWS = 8
        const val COLOR_DIM = 0x60000000.toInt()
        const val COLOR_TEXT = 0xFFE4E7EB.toInt()
        const val COLOR_TEXT_DIM = 0xFF83878E.toInt()
        const val COLOR_TEXT_FAINT = 0xFF4C4F55.toInt()
    }

    private var guiX = 0
    private var guiY = 0

    private var query = ""
    private var selectedIndex = 0

    private val colorBg get() = (0xE8 shl 24) or Theme.darkenedBackground(0.05f)
    private val colorFieldBg get() = (0xF0 shl 24) or Theme.darkenedBackground(0.22f)
    private val colorRowSelected get() = (0x40 shl 24) or Theme.accent
    private val accent get() = (0xFF shl 24) or Theme.accent

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        guiX = (width - WIDTH) / 2
        // Sits a bit above screen centre, like a command palette overlay rather than a centred dialog.
        guiY = (height * 0.28f).toInt()
    }

    /** Ranked matches: prefix match beats substring match beats no match (plain contains is all the rest of the codebase has today -- this scoring is new). */
    private fun matches(): List<Module> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val ql = q.lowercase()
        return ModuleManager.all()
            .mapNotNull { module ->
                val nl = module.name.lowercase()
                val score = when {
                    nl == ql -> 0
                    nl.startsWith(ql) -> 1
                    nl.contains(ql) -> 2
                    else -> return@mapNotNull null
                }
                module to score
            }
            .sortedWith(compareBy({ it.second }, { it.first.name }))
            .map { it.first }
    }

    private fun height(rowCount: Int): Int = SEARCH_HEIGHT + 8 + rowCount * ROW_HEIGHT + 8

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val results = matches().take(MAX_VISIBLE_ROWS)
        val h = height(results.size)

        context.fill(0, 0, width, height, COLOR_DIM)
        context.fill(guiX, guiY, guiX + WIDTH, guiY + h, colorBg)

        val sx = guiX + 6
        val sy = guiY + 6
        val sw = WIDTH - 12
        context.fill(sx, sy, sx + sw, sy + SEARCH_HEIGHT, colorFieldBg)
        val label = query.ifEmpty { "Search modules..." }
        context.drawString(font, label, sx + 6, sy + 6, if (query.isNotEmpty()) COLOR_TEXT else COLOR_TEXT_FAINT, true)

        var y = sy + SEARCH_HEIGHT + 8
        if (query.isNotEmpty() && results.isEmpty()) {
            context.drawString(font, "No matches", sx + 4, y + 3, COLOR_TEXT_FAINT, true)
        }
        for ((i, module) in results.withIndex()) {
            val selected = i == selectedIndex
            if (selected) context.fill(sx, y, sx + sw, y + ROW_HEIGHT, colorRowSelected)
            val nameColor = if (module.enabled) accent else COLOR_TEXT
            context.drawString(font, module.name, sx + 6, y + 4, nameColor, true)
            val stateLabel = if (module.enabled) "ON" else "OFF"
            context.drawString(font, stateLabel, sx + sw - font.width(stateLabel) - 4, y + 4, if (module.enabled) accent else COLOR_TEXT_FAINT, true)
            y += ROW_HEIGHT
        }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = click.x()
        val mouseY = click.y()
        val results = matches().take(MAX_VISIBLE_ROWS)
        val h = height(results.size)

        if (mouseX < guiX || mouseX > guiX + WIDTH || mouseY < guiY || mouseY > guiY + h) {
            // Click outside the palette -- close without acting.
            minecraft?.setScreen(null)
            return true
        }

        val sx = guiX + 6
        var y = guiY + 6 + SEARCH_HEIGHT + 8
        for ((i, module) in results.withIndex()) {
            if (mouseY >= y && mouseY < y + ROW_HEIGHT) {
                module.toggle()
                minecraft?.setScreen(null)
                return true
            }
            y += ROW_HEIGHT
        }

        return true
    }

    override fun charTyped(input: net.minecraft.client.input.CharacterEvent): Boolean {
        query += input.codepointAsString()
        selectedIndex = 0
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        when (input.key()) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (query.isNotEmpty()) query = query.dropLast(1)
                selectedIndex = 0
                return true
            }
            GLFW.GLFW_KEY_ESCAPE -> {
                minecraft?.setScreen(null)
                return true
            }
            GLFW.GLFW_KEY_DOWN -> {
                val count = matches().take(MAX_VISIBLE_ROWS).size
                if (count > 0) selectedIndex = (selectedIndex + 1).coerceAtMost(count - 1)
                return true
            }
            GLFW.GLFW_KEY_UP -> {
                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                return true
            }
            GLFW.GLFW_KEY_ENTER -> {
                val results = matches().take(MAX_VISIBLE_ROWS)
                results.getOrNull(selectedIndex)?.toggle()
                minecraft?.setScreen(null)
                return true
            }
        }
        return true
    }
}
