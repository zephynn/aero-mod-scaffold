package dev.finn.aero.module.impl

/**
 * Kotlin bridge for [dev.finn.aero.mixin.MouseAttackSwapMixin] (Java, same
 * reasoning as XrayState -- can't see Kotlin module internals directly).
 * The mixin reads these on every raw left-click press/release, so keeping
 * this cheap matters.
 *
 * No ticks, countdowns, or scheduling live here any more -- the swap
 * happens on mouse-button-press and reverts on mouse-button-release, both
 * reacting to the raw input event rather than any synthetic timer. See
 * the mixin's doc comment for why that's the more defensible shape.
 */
object AttributeSwapState {
    @Volatile
    var active: Boolean = false

    /** Hotbar slot (0-8) the swap triggers from. */
    @Volatile
    var primarySlot: Int = 0

    /** Hotbar slot (0-8) swapped to while the button's held. */
    @Volatile
    var secondarySlot: Int = 1

    /** Whether to only trigger when the currently selected slot is [primarySlot]. */
    @Volatile
    var requireHoldingPrimary: Boolean = true

    /** Whether to switch back to [primarySlot] on release. */
    @Volatile
    var swapBack: Boolean = true

    /** True between our own press-triggered swap and the matching release, so the release handler knows it was us. */
    @Volatile
    var weSwapped: Boolean = false

    /**
     * When true, MinecraftClientAttackMixin's original same-tick
     * swap-in-startAttack/swap-back-on-return mechanism runs instead of
     * MouseAttackSwapMixin's press/release mechanism. The two mixins check
     * this flag and defer to each other so exactly one of them ever acts
     * on a given click.
     */
    @Volatile
    var legacyMode: Boolean = false
}
