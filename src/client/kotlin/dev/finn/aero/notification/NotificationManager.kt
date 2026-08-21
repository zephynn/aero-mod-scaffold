package dev.finn.aero.notification

import dev.finn.aero.config.ClientSettings
import dev.finn.aero.gui.Anim
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.DeltaTracker

/**
 * Standalone toast/notification stack, reusable by any module (X-Ray uses
 * it, but nothing here is X-Ray-specific). Hooked into the same
 * HudRenderCallback AeroClient already wires ModuleManager through -- one
 * extra call, not a new event hook.
 */
object NotificationManager {
    private const val MAX_STACKED = 5
    private const val CARD_WIDTH = 160
    private const val CARD_PADDING = 6
    private const val CARD_GAP = 4
    private const val TITLE_HEIGHT = 10
    private const val MESSAGE_HEIGHT = 10
    private const val BAR_HEIGHT = 2
    private const val CARD_HEIGHT = CARD_PADDING * 2 + TITLE_HEIGHT + MESSAGE_HEIGHT + BAR_HEIGHT + 2
    private const val MARGIN = 8

    private const val ANIM_STEP = 0.5f

    private val queue: MutableList<Notification> = mutableListOf()
    private val anims: MutableMap<Any, Float> = HashMap()

    private val mc: Minecraft get() = Minecraft.getInstance()

    fun show(title: String, message: String, type: NotificationType = NotificationType.INFO, durationMs: Long = ClientSettings.notificationDurationMs) {
        val notification = Notification(title, message, type, durationMs, System.currentTimeMillis())
        queue.add(notification)
        // Cap the stack -- drop the oldest rather than let it grow unbounded
        // or silently swallow the newest (which would hide the very thing
        // that just happened).
        while (queue.size > MAX_STACKED) {
            queue.removeAt(0)
        }
    }

    fun onHudRender(context: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        val now = System.currentTimeMillis()
        // Expiry doesn't remove instantly -- flip into a "closing" state so
        // the loop below eases appearAnim back to 0 (a real slide-out,
        // mirroring the slide-in), and only drop it once that animation has
        // actually finished.
        for (notification in queue) {
            if (!notification.closing && notification.isExpired(now)) {
                notification.closing = true
            }
        }
        queue.removeAll { it.closing && it.appearAnim <= 0.001f }
        if (queue.isEmpty()) return

        val textRenderer = mc.font
        val screenW = mc.window.scaledWidth
        val screenH = mc.window.scaledHeight
        val position = ClientSettings.notificationPosition
        val top = position.startsWith("Top")
        val left = position.endsWith("Left")

        val baseX = if (left) MARGIN else screenW - MARGIN - CARD_WIDTH

        // Recompute each notification's target stack offset every frame --
        // cheap (at most MAX_STACKED entries) and keeps insert/remove reflow
        // correct without needing separate bookkeeping.
        var offset = 0
        for (notification in queue) {
            notification.targetY = offset.toFloat()
            offset += CARD_HEIGHT + CARD_GAP
        }

        for (notification in queue) {
            if (notification.currentY < 0f) notification.currentY = notification.targetY
            notification.currentY = Anim.advance(anims, notification to "y", notification.targetY, 6f)
            val appearTarget = if (notification.closing) 0f else 1f
            notification.appearAnim = Anim.advance(anims, notification to "appear", appearTarget, ANIM_STEP)

            val eased = Anim.easeOutCubic(notification.appearAnim)
            val slideIn = ((1f - eased) * (if (left) -CARD_WIDTH else CARD_WIDTH) * 0.3f).toInt()

            val y = (if (top) MARGIN else screenH - MARGIN - CARD_HEIGHT) +
                (if (top) notification.currentY.toInt() else -notification.currentY.toInt())
            val x = baseX + slideIn

            drawCard(context, textRenderer, notification, x, y, now, eased)
        }
    }

    private fun drawCard(
        context: GuiGraphicsExtractor,
        textRenderer: net.minecraft.client.gui.Font,
        notification: Notification,
        x: Int,
        y: Int,
        now: Long,
        eased: Float,
    ) {
        val bg = Anim.scaleAlpha(0xE0111214.toInt(), eased)
        val accent = Anim.scaleAlpha(notification.type.color, eased)
        val dimText = Anim.scaleAlpha(0xFF9BA0A6.toInt(), eased)

        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bg)
        context.fill(x, y, x + 2, y + CARD_HEIGHT, accent)

        val textX = x + CARD_PADDING + 3
        context.text(textRenderer, notification.title, textX, y + CARD_PADDING, accent, true)
        val clippedMessage = clipToWidth(textRenderer, notification.message, CARD_WIDTH - CARD_PADDING * 2 - 3)
        context.text(textRenderer, clippedMessage, textX, y + CARD_PADDING + TITLE_HEIGHT, dimText, true)

        val barY = y + CARD_HEIGHT - BAR_HEIGHT - 2
        val barX0 = x + CARD_PADDING
        val barX1 = x + CARD_WIDTH - CARD_PADDING
        context.fill(barX0, barY, barX1, barY + BAR_HEIGHT, Anim.scaleAlpha(0x40FFFFFF.toInt(), eased))
        val frac = notification.remainingFraction(now)
        val fillX = barX0 + ((barX1 - barX0) * frac).toInt()
        context.fill(barX0, barY, fillX, barY + BAR_HEIGHT, accent)
    }

    private fun clipToWidth(textRenderer: net.minecraft.client.gui.Font, str: String, maxWidth: Int): String {
        if (maxWidth <= 0) return ""
        if (textRenderer.width(str) <= maxWidth) return str
        return textRenderer.plainSubstrByWidth(str, (maxWidth - textRenderer.width("...")).coerceAtLeast(0)) + "..."
    }
}
