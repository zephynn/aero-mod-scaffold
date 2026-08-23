package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket

/**
 * Automates the weapon-attribute swap technique (mace/breach swaps,
 * sword-to-mace, etc.) by reacting to the raw left-mouse-button event
 * itself in [dev.finn.aero.mixin.MouseAttackSwapMixin] -- press swaps to
 * Secondary Slot, release swaps back to Primary Slot -- rather than
 * injecting into the attack method or scheduling anything on a timer.
 * That mixin's doc comment covers why: this module used to hook
 * `startAttack()` directly and schedule randomized-delay countdowns for
 * the swap-back (and, briefly, for auto-firing a cancelled attack), and
 * all of that turned out to be its own kind of engineered, code-driven
 * timing signature. Reacting to the physical press/release instead means
 * the timing is just real click-hold duration, the same as an actual
 * hardware macro bound to the mouse button would produce -- there's
 * nothing left here for this module to compute or fake.
 *
 * Slots are configured by hotbar position (1-9) rather than by item name:
 * there's no item-picker Setting type in this codebase yet (see
 * dev.finn.aero.setting.Setting), so "Primary"/"Secondary" here means
 * "whatever's in this hotbar slot" -- put your primary weapon in the
 * Primary Slot and your secondary (e.g. a mace) in the Secondary Slot.
 */
class AutoAttributeSwap : Module(
    name = "AutoAttributeSwap",
    description = "Swaps to a secondary weapon while the attack button is held, then swaps back on release.",
    category = Category.COMBAT,
) {
    private val mode = register(
        ModeSetting(
            "Mode",
            "New reacts to the raw mouse press/release (see MouseAttackSwapMixin). Legacy is the original same-tick swap-in-attack/swap-back-on-return mechanism.",
            listOf("New", "Legacy"),
            "New",
        ),
    )
    private val primarySlot = register(
        SliderSetting("Primary Slot", "Hotbar slot (1-9) you attack from normally.", 1.0, 1.0, 9.0, 1.0),
    )
    private val secondarySlot = register(
        SliderSetting("Secondary Slot", "Hotbar slot (1-9) swapped in while attack is held, e.g. a mace.", 2.0, 1.0, 9.0, 1.0),
    )
    private val requireHoldingPrimary = register(
        BoolSetting("Require Primary", "Only swap when Primary Slot is the one currently selected.", true),
    )
    private val swapBack = register(
        BoolSetting("Swap Back", "Switch back to Primary Slot on release.", true),
    )

    override fun onEnable() {
        publish()
        AttributeSwapState.active = true
    }

    override fun onDisable() {
        AttributeSwapState.active = false
        // Don't leave the player stuck holding the secondary weapon if the
        // module is toggled off mid-hold.
        if (AttributeSwapState.weSwapped) {
            AttributeSwapState.weSwapped = false
            forceSwapToPrimary()
        }
    }

    override fun onTick() {
        // Settings are read live by the mixin on every click, but keep the
        // bridge in sync each tick too in case sliders are dragged live.
        publish()
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
        AttributeSwapState.legacyMode = mode.value == "Legacy"
    }
}
