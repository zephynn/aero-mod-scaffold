package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket

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
 * The swap-back itself is *not* done in the mixin any more: it's a
 * randomized few-tick delay (see AttributeSwapState) counted down here in
 * onTick and executed once it hits zero, instead of landing on the exact
 * same tick as the attack every single time -- that zero-variance timing
 * is a much stronger tell to behavior-based anti-cheat than the swap
 * itself, since no human reaction is ever that consistent.
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
    private val minDelay = register(
        SliderSetting("Min Delay", "Fastest allowed swap-back delay, in ticks (~50ms each).", 1.0, 0.0, 10.0, 1.0),
    )
    private val maxDelay = register(
        SliderSetting("Max Delay", "Slowest allowed swap-back delay, in ticks (~50ms each).", 4.0, 0.0, 10.0, 1.0),
    )

    override fun onEnable() {
        publish()
        AttributeSwapState.active = true
    }

    override fun onDisable() {
        AttributeSwapState.active = false
        // Don't leave the player stuck holding the secondary weapon if the
        // module is toggled off mid-countdown.
        if (AttributeSwapState.pendingSwapBackTicks >= 0) {
            AttributeSwapState.cancelPendingSwapBack()
            forceSwapToPrimary()
        }
    }

    override fun onTick() {
        // Settings are read live by the mixin every attack, but keep the
        // bridge in sync each tick too in case sliders are dragged live.
        publish()

        val pending = AttributeSwapState.pendingSwapBackTicks
        if (pending < 0) return

        if (pending == 0) {
            AttributeSwapState.pendingSwapBackTicks = -1
            forceSwapToPrimary()
        } else {
            AttributeSwapState.pendingSwapBackTicks = pending - 1
        }
    }

    private fun forceSwapToPrimary() {
        val player = mc.player ?: return
        val connection = mc.connection ?: return
        val primary = AttributeSwapState.primarySlot
        player.inventory.selectedSlot = primary
        connection.send(ServerboundSetCarriedItemPacket(primary))
    }

    private fun publish() {
        AttributeSwapState.primarySlot = (primarySlot.value.toInt() - 1).coerceIn(0, 8)
        AttributeSwapState.secondarySlot = (secondarySlot.value.toInt() - 1).coerceIn(0, 8)
        AttributeSwapState.requireHoldingPrimary = requireHoldingPrimary.value
        AttributeSwapState.swapBack = swapBack.value
        AttributeSwapState.minDelayTicks = minDelay.value.toInt()
        AttributeSwapState.maxDelayTicks = maxDelay.value.toInt().coerceAtLeast(minDelay.value.toInt())
    }
}
