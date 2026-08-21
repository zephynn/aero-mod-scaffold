package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.SliderSetting

/**
 * Automates the precise item-switching timing behind weapon-attribute
 * swap techniques (mace/breach swaps, sword-to-mace, etc.) -- the actual
 * timing-critical work happens in [dev.finn.aero.mixin.MinecraftClientAttackMixin],
 * which brackets `MinecraftClient#doAttack()` itself rather than reacting
 * from a tick loop, so the swap is deterministically in place *before*
 * the attack call runs, not racing to catch up with it afterward.
 *
 * This doesn't create any new damage mechanic or fabricate anything the
 * server wouldn't otherwise see -- swapping the selected slot sends the
 * same real UpdateSelectedSlotC2SPacket a manual player pressing a number
 * key sends. It just does the swap-attack-swapback sequence with tick-
 * accurate timing a manual player would need a lot of practice (or a
 * macro) to reproduce reliably.
 *
 * Slots are configured by hotbar position (1-9) rather than by item name:
 * there's no item-picker Setting type in this codebase yet (see
 * dev.finn.aero.setting.Setting), so "Primary"/"Secondary" here means
 * "whatever's in this hotbar slot" -- put your primary weapon in the
 * Primary Slot and your secondary (e.g. a mace) in the Secondary Slot.
 */
class AutoAttributeSwap : Module(
    name = "AutoAttributeSwap",
    description = "Swaps to a secondary weapon for the exact duration of an attack, then swaps back.",
    category = Category.COMBAT,
) {
    private val primarySlot = register(
        SliderSetting("Primary Slot", "Hotbar slot (1-9) you attack from normally.", 1.0, 1.0, 9.0, 1.0),
    )
    private val secondarySlot = register(
        SliderSetting("Secondary Slot", "Hotbar slot (1-9) swapped in for the attack, e.g. a mace.", 2.0, 1.0, 9.0, 1.0),
    )
    private val requireHoldingPrimary = register(
        BoolSetting("Require Primary", "Only swap when Primary Slot is the one currently selected.", true),
    )
    private val swapBack = register(
        BoolSetting("Swap Back", "Switch back to Primary Slot immediately after the attack.", true),
    )

    override fun onEnable() {
        publish()
        AttributeSwapState.active = true
    }

    override fun onDisable() {
        AttributeSwapState.active = false
    }

    override fun onTick() {
        // Settings are read live by the mixin every attack, but keep the
        // bridge in sync each tick too in case sliders are dragged live.
        publish()
    }

    private fun publish() {
        AttributeSwapState.primarySlot = (primarySlot.value.toInt() - 1).coerceIn(0, 8)
        AttributeSwapState.secondarySlot = (secondarySlot.value.toInt() - 1).coerceIn(0, 8)
        AttributeSwapState.requireHoldingPrimary = requireHoldingPrimary.value
        AttributeSwapState.swapBack = swapBack.value
    }
}
