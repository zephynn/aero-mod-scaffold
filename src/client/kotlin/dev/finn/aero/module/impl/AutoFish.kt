package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.FishingRodItem

/**
 * Casts a fishing rod, watches the bobber for a bite, reels in, and casts
 * again. There's no client-visible "a fish is biting" flag -- that's
 * server-side-only state on FishingHook -- so this uses the same trick
 * every fishing bot does: the bobber's own Y position is genuinely synced
 * to the client for rendering the line, and a bite yanks it down hard and
 * fast, which is easy to tell apart from its normal gentle bobbing. A
 * right-click while the hook is already out reels it in (whether or not
 * anything actually bit), which is also what recasts on the next tick,
 * since "no hook out" is this module's only other trigger to cast.
 */
class AutoFish : Module(
    name = "AutoFish",
    description = "Casts, watches for a bite, reels in, and recasts automatically.",
    category = Category.PLAYER,
) {
    private val biteThreshold = register(
        SliderSetting("Bite Threshold", "How hard a downward yank has to be to count as a bite.", 0.15, 0.02, 0.5, 0.01),
    )

    private var lastBobberY: Double? = null

    override fun onDisable() {
        lastBobberY = null
    }

    override fun onTick() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return

        if (player.mainHandItem.item !is FishingRodItem) {
            lastBobberY = null
            return
        }

        val bobber = player.fishing
        if (bobber == null) {
            lastBobberY = null
            gameMode.useItem(player, InteractionHand.MAIN_HAND)
            return
        }

        val previous = lastBobberY
        lastBobberY = bobber.y
        if (previous == null) return

        val dy = bobber.y - previous
        if (dy <= -biteThreshold.value) {
            gameMode.useItem(player, InteractionHand.MAIN_HAND)
            lastBobberY = null
        }
    }
}
