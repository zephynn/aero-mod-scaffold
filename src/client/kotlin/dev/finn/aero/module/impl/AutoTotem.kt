package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.ClickType

/**
 * Swaps a totem of undying into the offhand once health drops below the
 * threshold, if the offhand doesn't already have one. This is exactly what
 * pressing vanilla's "swap item to offhand" key (F by default) does under
 * the hood -- `AbstractContainerMenu#clicked` special-cases button `40` on a
 * SWAP action to mean "swap with the offhand slot" -- so this just clicks
 * that slot programmatically instead of waiting for the player to notice
 * and react in time.
 *
 * Legacy mode swaps the instant the threshold is crossed, every time,
 * with zero latency -- no human reacts to their own health bar that
 * consistently. New mode (default) waits out a short randomized reaction
 * delay first, the same "arm on trigger, act a few ticks later" shape
 * AutoAttributeSwap settled on, so the totem lands a beat after the
 * threshold crossing instead of the exact same tick, every time.
 */
class AutoTotem : Module(
    name = "AutoTotem",
    description = "Swaps a totem of undying into your offhand at low health.",
    category = Category.PLAYER,
) {
    private companion object {
        const val OFFHAND_SWAP_BUTTON = 40
    }

    private val mode = register(
        ModeSetting("Mode", "New waits out a short reaction delay before swapping. Legacy swaps instantly.", listOf("New", "Legacy"), "New"),
    )
    private val healthThreshold = register(
        SliderSetting(
            name = "Health Threshold",
            description = "Swap a totem in once health drops to or below this.",
            default = 6.0,
            min = 1.0,
            max = 20.0,
            step = 1.0,
        ),
    )
    private val minReactionTicks = register(
        SliderSetting("Min Reaction", "New mode only: fastest allowed reaction delay, in ticks (~50ms each).", 1.0, 0.0, 10.0, 1.0),
    )
    private val maxReactionTicks = register(
        SliderSetting("Max Reaction", "New mode only: slowest allowed reaction delay, in ticks (~50ms each).", 4.0, 0.0, 10.0, 1.0),
    )

    /** Ticks remaining before a New-mode swap fires, or -1 if none armed. */
    private var pendingTicks = -1

    override fun onDisable() {
        pendingTicks = -1
    }

    override fun onTick() {
        val player = mc.player ?: return

        val triggered = player.offhandItem.item != Items.TOTEM_OF_UNDYING && player.health <= healthThreshold.value
        if (!triggered) {
            pendingTicks = -1
            return
        }

        if (mode.value == "Legacy") {
            swapTotemIn()
            return
        }

        if (pendingTicks < 0) {
            val min = minReactionTicks.value.toInt()
            val max = maxReactionTicks.value.toInt().coerceAtLeast(min)
            pendingTicks = (min..max).random()
        }

        if (pendingTicks == 0) {
            pendingTicks = -1
            swapTotemIn()
        } else {
            pendingTicks--
        }
    }

    private fun swapTotemIn() {
        val player = mc.player ?: return
        val interactionManager = mc.gameMode ?: return

        val mainStacks = player.inventory.nonEquipmentItems
        val totemIndex = mainStacks.indexOfFirst { it.item == Items.TOTEM_OF_UNDYING }
        if (totemIndex < 0) return

        // PlayerScreenHandler slot numbering: hotbar inventory indices 0-8
        // show up as screen slots 36-44; main storage indices 9-35 keep the
        // same numbers as their screen slots.
        val screenSlot = if (totemIndex < 9) 36 + totemIndex else totemIndex

        interactionManager.handleInventoryMouseClick(player.containerMenu.containerId, screenSlot, OFFHAND_SWAP_BUTTON, ClickType.SWAP, player)
    }
}
