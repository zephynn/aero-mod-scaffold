package dev.finn.aero.gui

import dev.finn.aero.config.ConfigManager
import dev.finn.aero.config.Theme
import dev.finn.aero.module.impl.xray.XrayCustomConfig
import dev.finn.aero.module.impl.xray.XrayModule
import net.minecraft.world.level.block.Block
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * X-Ray's "Custom" mode block-list editor -- a searchable add/remove list
 * over the full block registry, opened from a special-cased "Edit List..."
 * button in [ClickGuiScreen] (rather than a new generic Setting type, per
 * the plan). Same immediate-mode style as the other two screens.
 */
class XrayCustomScreen(private val module: XrayModule) : Screen(Component.literal("X-Ray Custom Blocks")) {

    private companion object {
        const val WIDTH = 260
        const val HEIGHT = 220
        const val CHROME_HEIGHT = 20
        const val SEARCH_HEIGHT = 16
        const val ROW_HEIGHT = 14
        const val COLOR_DIM = 0x38000000.toInt()
        const val COLOR_TEXT = 0xFFE4E7EB.toInt()
        const val COLOR_TEXT_DIM = 0xFF83878E.toInt()
    }

    private var guiX = 0
    private var guiY = 0
    private var searchQuery = ""
    private var searchFocused = true
    private var scrollOffset = 0

    private val colorChrome get() = (0xD0 shl 24) or Theme.darkenedBackground(0.20f)
    private val colorFieldBg get() = (0xC8 shl 24) or Theme.darkenedBackground(0.22f)
    private val colorBg get() = (0xC0 shl 24) or Theme.darkenedBackground(0f)
    private val accent get() = (0xFF shl 24) or Theme.accent

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        guiX = (width - WIDTH) / 2
        guiY = (height - HEIGHT) / 2
    }

    private fun filteredBlocks(): List<Block> {
        val q = searchQuery.trim().lowercase()
        val all = BuiltInRegistries.BLOCK.iterator().asSequence().toList()
        val matching = if (q.isEmpty()) all else all.filter { BuiltInRegistries.BLOCK.getKey(it).path.lowercase().contains(q) }
        // Selected-first, then alphabetical -- keeps the current list visible while searching to add more.
        return matching.sortedWith(compareByDescending<Block> { XrayCustomConfig.contains(it) }.thenBy { BuiltInRegistries.BLOCK.getKey(it).path })
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, COLOR_DIM)
        context.fill(guiX, guiY, guiX + WIDTH, guiY + HEIGHT, colorBg)
        context.fill(guiX, guiY, guiX + WIDTH, guiY + CHROME_HEIGHT, colorChrome)

        val backHovered = mouseX in guiX..(guiX + 16) && mouseY in guiY..(guiY + CHROME_HEIGHT)
        context.text(font, "‹", guiX + 7, guiY + 6, if (backHovered) accent else COLOR_TEXT_DIM, true)
        context.text(font, "X-RAY CUSTOM LIST", guiX + 18, guiY + 6, COLOR_TEXT_DIM, true)

        val sx = guiX + 8
        var sy = guiY + CHROME_HEIGHT + 6
        val sw = WIDTH - 16
        context.fill(sx, sy, sx + sw, sy + SEARCH_HEIGHT, colorFieldBg)
        val searchLabel = when {
            searchQuery.isNotEmpty() -> searchQuery
            else -> "Search blocks..."
        }
        context.text(font, searchLabel, sx + 4, sy + 4, if (searchQuery.isNotEmpty()) COLOR_TEXT else COLOR_TEXT_DIM, true)
        sy += SEARCH_HEIGHT + 4

        val listTop = sy
        val listBottom = guiY + HEIGHT - 26
        val blocks = filteredBlocks()
        val visibleRows = (listBottom - listTop) / ROW_HEIGHT
        scrollOffset = scrollOffset.coerceIn(0, (blocks.size - visibleRows).coerceAtLeast(0))

        var rowY = listTop
        for (i in scrollOffset until (scrollOffset + visibleRows).coerceAtMost(blocks.size)) {
            val block = blocks[i]
            val selected = XrayCustomConfig.contains(block)
            val hovered = mouseX in sx..(sx + sw) && mouseY in rowY..(rowY + ROW_HEIGHT)
            if (selected) context.fill(sx, rowY, sx + sw, rowY + ROW_HEIGHT, (0x30 shl 24) or Theme.accent)
            else if (hovered) context.fill(sx, rowY, sx + sw, rowY + ROW_HEIGHT, 0x20FFFFFF.toInt())

            val name = BuiltInRegistries.BLOCK.getKey(block).path
            context.text(font, name, sx + 4, rowY + 3, if (selected) COLOR_TEXT else COLOR_TEXT_DIM, true)
            val mark = if (selected) "✓" else "+"
            context.text(font, mark, sx + sw - 10, rowY + 3, if (selected) accent else COLOR_TEXT_DIM, true)
            rowY += ROW_HEIGHT
        }

        if (blocks.isEmpty()) {
            context.text(font, "No matches", sx + (sw - font.width("No matches")) / 2, (listTop + listBottom) / 2, COLOR_TEXT_DIM, true)
        }

        val resetY = guiY + HEIGHT - 20
        val resetHovered = mouseX in sx..(sx + sw) && mouseY in resetY..(resetY + 16)
        context.fill(sx, resetY, sx + sw, resetY + 16, colorFieldBg)
        if (resetHovered) context.fill(sx, resetY, sx + sw, resetY + 16, 0x20FFFFFF.toInt())
        val resetLabel = "Reset to Default (Ores)"
        context.text(font, resetLabel, sx + (sw - font.width(resetLabel)) / 2, resetY + 4, COLOR_TEXT_DIM, true)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mouseX = click.x().toInt()
        val mouseY = click.y().toInt()

        if (mouseX in guiX..(guiX + 16) && mouseY in guiY..(guiY + CHROME_HEIGHT)) {
            minecraft?.gui?.setScreen(ClickGuiScreen())
            return true
        }

        val sx = guiX + 8
        var sy = guiY + CHROME_HEIGHT + 6
        val sw = WIDTH - 16
        if (mouseX in sx..(sx + sw) && mouseY in sy..(sy + SEARCH_HEIGHT)) {
            searchFocused = true
            return true
        }
        searchFocused = false
        sy += SEARCH_HEIGHT + 4

        val listTop = sy
        val listBottom = guiY + HEIGHT - 26
        val blocks = filteredBlocks()
        val visibleRows = (listBottom - listTop) / ROW_HEIGHT

        var rowY = listTop
        for (i in scrollOffset until (scrollOffset + visibleRows).coerceAtMost(blocks.size)) {
            if (mouseX in sx..(sx + sw) && mouseY in rowY..(rowY + ROW_HEIGHT)) {
                XrayCustomConfig.toggle(blocks[i])
                module.markTargetsDirty()
                ConfigManager.save()
                return true
            }
            rowY += ROW_HEIGHT
        }

        val resetY = guiY + HEIGHT - 20
        if (mouseX in sx..(sx + sw) && mouseY in resetY..(resetY + 16)) {
            XrayCustomConfig.resetToOres()
            module.markTargetsDirty()
            ConfigManager.save()
            return true
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scrollOffset -= verticalAmount.toInt()
        return true
    }

    override fun charTyped(input: net.minecraft.client.input.CharacterEvent): Boolean {
        if (searchFocused) {
            searchQuery += input.codepointAsString()
            scrollOffset = 0
            return true
        }
        return super.charTyped(input)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (searchFocused) {
            when (input.key()) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1)
                    return true
                }
                GLFW.GLFW_KEY_ESCAPE -> {
                    minecraft?.gui?.setScreen(ClickGuiScreen())
                    return true
                }
            }
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft?.gui?.setScreen(ClickGuiScreen())
            return true
        }
        return super.keyPressed(input)
    }
}
