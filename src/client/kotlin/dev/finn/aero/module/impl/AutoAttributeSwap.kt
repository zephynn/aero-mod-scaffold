package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.mixin.MinecraftStartAttackInvoker
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
 * key sends. But with Delay First Hit on, the swap packet and the attack
 * packet are no longer the same tick: the click swaps and arms, that
 * click's own attack is cancelled, and this module auto-fires the real
 * attack itself (via MinecraftStartAttackInvoker) a randomized few ticks
 * later -- one physical click still does the whole thing, but the two
 * packets a server sees now land a genuine tick or two apart instead of
 * together, every time, which is one of the more well-known things
 * behavior-based anti-cheat watches for.
 *
 * The swap-back is delayed the same way, for the same reason: a
 * randomized few-tick countdown (see AttributeSwapState), counted down
 * here in onTick and executed once it hits zero, instead of landing on
 * the exact same tick as the attack every single time.
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
    private val delayFirstHit = register(
        BoolSetting("Delay First Hit", "Cancel the click's own attack and auto-fire it a few ticks later instead of the same tick as the swap.", true),
    )
    private val minAttackDelay = register(
        SliderSetting("Min Attack Delay", "Fastest allowed delay between the swap and the auto-fired attack, in ticks (~50ms each).", 1.0, 0.0, 10.0, 1.0),
    )
    private val maxAttackDelay = register(
        SliderSetting("Max Attack Delay", "Slowest allowed delay between the swap and the auto-fired attack, in ticks (~50ms each).", 3.0, 0.0, 10.0, 1.0),
    )
    private val minSwapBackDelay = register(
        SliderSetting("Min Swap Back Delay", "Fastest allowed swap-back delay, in ticks (~50ms each).", 1.0, 0.0, 10.0, 1.0),
    )
    private val maxSwapBackDelay = register(
        SliderSetting("Max Swap Back Delay", "Slowest allowed swap-back delay, in ticks (~50ms each).", 4.0, 0.0, 10.0, 1.0),
    )

    override fun onEnable() {
        publish()
        AttributeSwapState.active = true
    }

    override fun onDisable() {
        AttributeSwapState.active = false
        // Don't leave the player stuck holding the secondary weapon (or an
        // attack that never fires) if the module is toggled off mid-countdown.
        if (AttributeSwapState.pendingSwapBackTicks >= 0 || AttributeSwapState.pendingAutoAttackTicks >= 0 || AttributeSwapState.armed) {
            AttributeSwapState.cancelPendingSwapBack()
            AttributeSwapState.cancelPendingAutoAttack()
            AttributeSwapState.armed = false
            forceSwapToPrimary()
        }
    }

    override fun onTick() {
        // Settings are read live by the mixin every attack, but keep the
        // bridge in sync each tick too in case sliders are dragged live.
        publish()

        val pendingAttack = AttributeSwapState.pendingAutoAttackTicks
        if (pendingAttack >= 0) {
            if (pendingAttack == 0) {
                AttributeSwapState.pendingAutoAttackTicks = -1
                fireQueuedAttack()
            } else {
                AttributeSwapState.pendingAutoAttackTicks = pendingAttack - 1
            }
        }

        val pendingSwapBack = AttributeSwapState.pendingSwapBackTicks
        if (pendingSwapBack < 0) return

        if (pendingSwapBack == 0) {
            AttributeSwapState.pendingSwapBackTicks = -1
            forceSwapToPrimary()
        } else {
            AttributeSwapState.pendingSwapBackTicks = pendingSwapBack - 1
        }
    }

    /** Re-invokes the same startAttack() the mixin hooks -- it already knows how to treat an armed, already-holding-secondary attack as real. */
    private fun fireQueuedAttack() {
        (mc as? MinecraftStartAttackInvoker)?.`aero$startAttack`()
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
        AttributeSwapState.delayFirstHit = delayFirstHit.value
        AttributeSwapState.minAttackDelayTicks = minAttackDelay.value.toInt()
        AttributeSwapState.maxAttackDelayTicks = maxAttackDelay.value.toInt().coerceAtLeast(minAttackDelay.value.toInt())
        AttributeSwapState.minDelayTicks = minSwapBackDelay.value.toInt()
        AttributeSwapState.maxDelayTicks = maxSwapBackDelay.value.toInt().coerceAtLeast(minSwapBackDelay.value.toInt())
    }
}
