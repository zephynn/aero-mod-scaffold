package dev.finn.aero.module.impl

/**
 * Kotlin bridge for [dev.finn.aero.mixin.MinecraftClientAttackMixin] (Java,
 * same reasoning as XrayState -- can't see Kotlin module internals
 * directly). The mixin only ever reads these each time `doAttack()` runs,
 * so keeping this cheap matters, but it's not a hot path the way chunk
 * meshing is.
 */
object AttributeSwapState {
    @Volatile
    var active: Boolean = false

    /** Hotbar slot (0-8) the swap triggers from. */
    @Volatile
    var primarySlot: Int = 0

    /** Hotbar slot (0-8) swapped to for the duration of the attack. */
    @Volatile
    var secondarySlot: Int = 1

    /** Whether to only trigger when the currently selected slot is [primarySlot]. */
    @Volatile
    var requireHoldingPrimary: Boolean = true

    /** Whether to switch back to [primarySlot] once the attack call returns. */
    @Volatile
    var swapBack: Boolean = true
}
