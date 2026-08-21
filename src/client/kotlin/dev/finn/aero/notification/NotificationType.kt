package dev.finn.aero.notification

import dev.finn.aero.config.Theme

/**
 * Semantic notification kind -- drives the accent stripe/title colour of
 * each toast. INFO defers to [Theme.accent] (so it matches whatever the
 * user picked for the rest of the UI); the other three are fixed colours
 * since "success"/"warning"/"error" need to stay recognisable regardless
 * of the chosen accent.
 */
enum class NotificationType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    ;

    val color: Int
        get() = when (this) {
            INFO -> (0xFF shl 24) or (Theme.accent and 0xFFFFFF)
            SUCCESS -> 0xFF4CD964.toInt()
            WARNING -> 0xFFFFB020.toInt()
            ERROR -> 0xFFE5484D.toInt()
        }
}
