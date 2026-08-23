package dev.finn.aero.gui

import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ColorSetting
import dev.finn.aero.setting.KeybindSetting
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.Setting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.client.gui.Font
import org.lwjgl.glfw.GLFW

/**
 * One list row a settings screen renders: either a module header
 * (`setting == null`) or one of its settings, indented underneath it.
 * Shared between [ClickGuiScreen]'s accordion layout and [MeteorGuiScreen]'s
 * side panel -- extracted out of `ClickGuiScreen` (see `SettingRow` below)
 * rather than duplicated.
 */
data class Row(
    val top: Int,
    val bottom: Int,
    val module: Module,
    val setting: Setting<*>?,
    val categoryLabel: String? = null,
    /** True for the special "Edit List..." row X-Ray's Custom mode gets instead of a generic Setting. */
    val isEditListButton: Boolean = false,
    /** True for one option row of an expanded ModeSetting's inline dropdown -- [setting] is the owning ModeSetting, [dropdownOption] is this row's option string. */
    val isDropdownOption: Boolean = false,
    val dropdownOption: String? = null,
)

/**
 * Rendering/click-handling for one setting row (BoolSetting/SliderSetting/
 * ModeSetting/KeybindSetting/ColorSetting), extracted out of
 * [ClickGuiScreen] so a second GUI layout ([MeteorGuiScreen]) can reuse the
 * exact same per-setting-type logic -- including [ColorPickerPopup] wiring
 * -- instead of reimplementing it. The owning screen supplies its own
 * fade-aware fill/text and whatever colour scheme it uses via [Ctx]; this
 * object holds no state of its own.
 */
object SettingRow {
    const val SETTING_ROW_HEIGHT = 18
    const val SLIDER_ROW_HEIGHT = 24

    private const val COLOR_TEXT_FAINT = 0xFF4C4F55.toInt()

    fun heightFor(setting: Setting<*>) = if (setting is SliderSetting) SLIDER_ROW_HEIGHT else SETTING_ROW_HEIGHT

    /** The bit of per-screen state [handleClick] needs -- no rendering, so it doesn't need a live [GuiGraphics] to construct. */
    interface ClickCtx {
        /** ModeSetting currently showing its expanded inline option list, or null. */
        var modeDropdown: ModeSetting?

        /** KeybindSetting currently listening for the next key press, or null. */
        var keybindTarget: KeybindSetting?
    }

    /** Everything a screen must supply so [render]/[handleClick] can draw/hit-test without knowing the screen's own fields. */
    interface Ctx : ClickCtx {
        val font: Font
        val accent: Int
        val colorText: Int
        val colorTextDim: Int
        val colorTrackOff: Int

        /** Fade-aware fill -- goes through the screen's own windowAnim/extra multiplier. */
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int, color: Int, extra: Float = 1f)

        /** Fade-aware text -- goes through the screen's own windowAnim/extra multiplier. */
        fun text(str: String, x: Int, y: Int, color: Int, extra: Float = 1f)
    }

    fun render(ctx: Ctx, setting: Setting<*>, top: Int, x: Int, x1: Int, extra: Float = 1f) {
        when (setting) {
            is BoolSetting -> {
                ctx.text(setting.name, x, top + 5, ctx.colorTextDim, extra)
                drawToggle(ctx, x1 - 22, top + 4, 22, 10, if (setting.value) 1f else 0f, extra)
            }
            is SliderSetting -> {
                ctx.text(setting.name, x, top + 3, ctx.colorTextDim, extra)
                val valueLabel = "%.2f".format(setting.value)
                ctx.text(valueLabel, x1 - ctx.font.width(valueLabel), top + 3, ctx.colorTextDim, extra)
                val barY = top + 15
                ctx.fill(x, barY, x1, barY + 2, ctx.colorTrackOff, extra)
                val frac = ((setting.value - setting.min) / (setting.max - setting.min)).coerceIn(0.0, 1.0)
                val fillX = x + (frac * (x1 - x)).toInt()
                ctx.fill(x, barY, fillX, barY + 2, ctx.accent, extra)
            }
            is ModeSetting -> {
                ctx.text(setting.name, x, top + 5, ctx.colorTextDim, extra)
                val label = (if (setting == ctx.modeDropdown) "▾ " else "▸ ") + setting.value
                ctx.text(label, x1 - ctx.font.width(label), top + 5, ctx.colorText, extra)
            }
            is KeybindSetting -> {
                ctx.text(setting.name, x, top + 5, ctx.colorTextDim, extra)
                val listening = setting == ctx.keybindTarget
                val label = if (listening) "..." else keyName(setting.value)
                ctx.text(label, x1 - ctx.font.width(label), top + 5, if (listening) ctx.accent else ctx.colorText, extra)
            }
            is ColorSetting -> {
                ctx.text(setting.name, x, top + 5, ctx.colorTextDim, extra)
                ctx.fill(x1 - 20, top + 3, x1, top + 13, setting.value, extra)
            }
        }
    }

    /**
     * Applies the click for a setting row. Mutates [Ctx.modeDropdown]/[Ctx.keybindTarget]
     * directly; delegates the two cases that need geometry/state the caller
     * owns (opening the colour picker anchored to this row, starting a
     * slider drag) back out via callbacks instead of taking on that state itself.
     */
    fun handleClick(
        ctx: ClickCtx,
        setting: Setting<*>,
        openColorPicker: (ColorSetting) -> Unit,
        startSliderDrag: (SliderSetting) -> Unit,
    ): Boolean {
        // Clicking any setting other than the ModeSetting whose dropdown is
        // currently open closes that dropdown -- an "outside click" close.
        if (setting !== ctx.modeDropdown) ctx.modeDropdown = null

        when (setting) {
            is BoolSetting -> setting.value = !setting.value
            is ModeSetting -> ctx.modeDropdown = if (ctx.modeDropdown == setting) null else setting
            is KeybindSetting -> ctx.keybindTarget = setting
            is ColorSetting -> openColorPicker(setting)
            is SliderSetting -> startSliderDrag(setting)
        }
        return true
    }

    fun drawToggle(ctx: Ctx, x: Int, y: Int, w: Int, h: Int, anim: Float, extra: Float = 1f) {
        ctx.fill(x, y, x + w, y + h, Anim.lerpColor(ctx.colorTrackOff, ctx.accent, anim), extra)
        val knobD = h - 4
        val knobX = x + 2 + ((w - knobD - 4) * anim).toInt()
        ctx.fill(knobX, y + 2, knobX + knobD, y + h - 2, if (anim > 0.5f) 0xFF0B0D0F.toInt() else COLOR_TEXT_FAINT, extra)
    }

    fun keyName(keyCode: Int): String {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return "NONE"
        return GLFW.glfwGetKeyName(keyCode, 0)?.uppercase() ?: "KEY_$keyCode"
    }
}
