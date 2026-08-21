package dev.finn.aero.config

/**
 * The one colour the whole Aero UI is actually built from: an accent used
 * for every "on"/selected/active state. The panel's base colour is fixed
 * (not user-configurable -- background switching turned out to be more
 * confusing than useful) and every surface (rail, chrome bar, fields) is
 * still a darkened shade of it. Screens read [accent] directly rather than
 * caching their own copy, so a change in the Settings screen applies
 * everywhere immediately.
 */
object Theme {
    const val BACKGROUND = 0x18191C
    const val DEFAULT_ACCENT = 0x3FB6D6

    /** RGB, no alpha. */
    var accent: Int = DEFAULT_ACCENT

    /** Quick-pick accent swatches -- Aero's original spectrum, one accent at a time instead of all at once. */
    val ACCENT_PRESETS = intArrayOf(
        0x0096FF, 0x00B1FF, 0x00C9FF, 0x00DCF0, 0x00ECC0, 0x00F885, 0x8EFF40,
    )

    fun reset() {
        accent = DEFAULT_ACCENT
    }

    /** [BACKGROUND], darkened by [amount] (0..1) -- used for rail/chrome/field surfaces layered on the panel. */
    fun darkenedBackground(amount: Float): Int {
        val r = (BACKGROUND ushr 16) and 0xFF
        val g = (BACKGROUND ushr 8) and 0xFF
        val b = BACKGROUND and 0xFF
        fun d(c: Int) = (c * (1f - amount)).toInt().coerceIn(0, 255)
        return (d(r) shl 16) or (d(g) shl 8) or d(b)
    }
}
