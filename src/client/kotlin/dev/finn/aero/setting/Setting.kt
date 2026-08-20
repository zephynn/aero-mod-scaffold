package dev.finn.aero.setting

/**
 * Typed settings that a module exposes. The ClickGUI renders these
 * generically by switching on the sealed subtype -- add a new setting
 * type here and one new "when" branch in the GUI renderer, nowhere else.
 */
sealed class Setting<T>(
    val name: String,
    val description: String,
    var value: T,
) {
    /** Reset back to the default recorded at construction time. */
    private val default: T = value

    fun reset() {
        value = default
    }
}

class BoolSetting(
    name: String,
    description: String,
    default: Boolean,
) : Setting<Boolean>(name, description, default)

class SliderSetting(
    name: String,
    description: String,
    default: Double,
    val min: Double,
    val max: Double,
    val step: Double = 0.1,
) : Setting<Double>(name, description, default.coerceIn(min, max)) {
    fun set(newValue: Double) {
        value = newValue.coerceIn(min, max)
    }
}

/**
 * A setting that picks one value out of a fixed list of options, e.g.
 * ESP render mode: "Box" / "Outline" / "2D".
 */
class ModeSetting(
    name: String,
    description: String,
    val options: List<String>,
    default: String,
) : Setting<String>(name, description, default) {
    fun cycle() {
        val i = options.indexOf(value)
        value = options[(i + 1) % options.size]
    }
}

/**
 * Stores a GLFW key code (see org.lwjgl.glfw.GLFW.GLFW_KEY_*). -1 means unbound.
 * Kept as a plain Int setting so it can be serialized without extra glue.
 */
class KeybindSetting(
    name: String,
    description: String,
    default: Int,
) : Setting<Int>(name, description, default)

/** Packed ARGB color, e.g. 0xFFFF0000 for opaque red. */
class ColorSetting(
    name: String,
    description: String,
    default: Int,
) : Setting<Int>(name, description, default)
