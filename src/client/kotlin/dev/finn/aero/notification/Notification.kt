package dev.finn.aero.notification

/**
 * One toast on screen. [createdAt] is a wall-clock (System.currentTimeMillis)
 * timestamp so the drain fraction and expiry are frame-rate independent.
 * [currentY]/[targetY] drive the reflow animation in [NotificationManager] --
 * eased toward the target each frame rather than snapped, so removals above
 * a notification slide the rest up instead of popping.
 */
data class Notification(
    val title: String,
    val message: String,
    val type: NotificationType,
    val durationMs: Long,
    val createdAt: Long,
) {
    /** 0 at spawn, eased toward 1 for the slide-in; driven back toward 0 for the slide-out once [closing] is set. */
    var appearAnim: Float = 0f

    /** True once this notification has expired and is animating out; removed from the queue once [appearAnim] reaches 0. */
    var closing: Boolean = false

    /** Current animated vertical offset within the stack; eased toward [targetY] each frame. */
    var currentY: Float = -1f
    var targetY: Float = 0f

    fun remainingFraction(now: Long): Float {
        val elapsed = now - createdAt
        return (1f - elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun isExpired(now: Long): Boolean = now - createdAt >= durationMs
}
