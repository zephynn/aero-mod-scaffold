package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player

/**
 * Eats automatically once hunger drops to the threshold: switches to a
 * food item in the hotbar/inventory and right-clicks it, the same
 * `useItem()` a manual right-click sends -- eating still takes its real
 * ~1.6 second use duration, nothing about that is sped up. The one
 * wrinkle is that vanilla's own input loop normally cancels an in-progress
 * item use the instant it notices the use key isn't physically held down
 * (see SuppressUseItemReleaseMixin); AutoEatState tells that one check to
 * stand down for exactly the duration of an AutoEat-triggered eat, so the
 * use actually completes instead of getting cancelled a frame after it starts.
 */
class AutoEat : Module(
    name = "AutoEat",
    description = "Eats food automatically when hunger drops low.",
    category = Category.PLAYER,
) {
    private val threshold = register(
        SliderSetting("Hunger Threshold", "Eat once hunger drops to or below this.", 14.0, 0.0, 19.0, 1.0),
    )

    private var weTriggered = false
    private var originalSlot = -1

    override fun onDisable() {
        AutoEatState.suppressRelease = false
        weTriggered = false
    }

    override fun onTick() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return

        if (player.isUsingItem) {
            AutoEatState.suppressRelease = weTriggered
            return
        }

        if (weTriggered) {
            // Use just finished (consumed or otherwise ended) -- restore
            // whatever was selected before we started eating.
            weTriggered = false
            AutoEatState.suppressRelease = false
            if (originalSlot in 0..8) {
                switchSlot(player, originalSlot)
            }
            originalSlot = -1
            return
        }

        if (player.foodData.foodLevel > threshold.value) return

        val slot = findFoodSlot(player) ?: return
        originalSlot = player.inventory.selectedSlot
        if (slot != originalSlot) switchSlot(player, slot)

        gameMode.useItem(player, InteractionHand.MAIN_HAND)
        weTriggered = true
        AutoEatState.suppressRelease = true
    }

    private fun findFoodSlot(player: Player): Int? {
        for (i in 0..8) {
            if (player.inventory.getItem(i).get(DataComponents.FOOD) != null) return i
        }
        return null
    }

    private fun switchSlot(player: Player, slot: Int) {
        val connection = mc.connection ?: return
        player.inventory.selectedSlot = slot
        connection.send(ServerboundSetCarriedItemPacket(slot))
    }
}
