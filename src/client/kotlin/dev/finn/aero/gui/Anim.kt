package dev.finn.aero.gui

import dev.finn.aero.config.ClientSettings

/**
 * Small shared animation helpers, extracted out of [ClickGuiScreen] and
 * [SettingsScreen] (previously duplicated identically between the two).
 * Every immediate-mode screen in Aero -- and now [dev.finn.aero.notification.NotificationManager]
 * -- goes through these so easing/reduced-motion behaviour stays identical
 * everywhere instead of drifting between copies.
 */
object Anim {
    fun easeOutCubic(t: Float): Float {
        val tt = t.coerceIn(0f, 1f)
        val inv = 1f - tt
        return 1f - inv * inv * inv
    }

    fun scaleAlpha(color: Int, scale: Float): Int {
        val a = (color ushr 24) and 0xFF
        val newA = (a * scale.coerceIn(0f, 1f)).toInt().coerceIn(0, 0xFF)
        return (newA shl 24) or (color and 0x00FFFFFF)
    }

    fun lerpColor(from: Int, to: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        fun ch(v: Int, shift: Int) = (v ushr shift) and 0xFF
        val r = (ch(from, 16) + (ch(to, 16) - ch(from, 16)) * tt).toInt()
        val g = (ch(from, 8) + (ch(to, 8) - ch(from, 8)) * tt).toInt()
        val b = (ch(from, 0) + (ch(to, 0) - ch(from, 0)) * tt).toInt()
        val a = (ch(from, 24) + (ch(to, 24) - ch(from, 24)) * tt).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Steps [map]'s value for [key] toward [target] by [step] per call, snapping instantly if reduced motion is on. */
    fun advance(map: MutableMap<Any, Float>, key: Any, target: Float, step: Float): Float {
        if (ClientSettings.reducedMotion) {
            map[key] = target
            return target
        }
        val current = map[key] ?: target
        val next = when {
            current < target -> (current + step).coerceAtMost(target)
            current > target -> (current - step).coerceAtLeast(target)
            else -> current
        }
        map[key] = next
        return next
    }
}
