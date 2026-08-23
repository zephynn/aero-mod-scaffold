package dev.finn.aero.module.impl

/**
 * Kotlin bridge for [dev.finn.aero.mixin.MinecraftClientAttackMixin] (Java,
 * same reasoning as XrayState -- can't see Kotlin module internals
 * directly). The mixin only ever reads these each time `doAttack()` runs,
 * so keeping this cheap matters, but it's not a hot path the way chunk
 * meshing is.
 *
 * The swap-*in* is intentionally left instant -- it has to be in place
 * before the attack for the crit setup to work at all, and it's already
 * timed by the player's own real click, so there's nothing artificial
 * about its timing to begin with. The swap-*back* is the part that was
 * previously landing on the exact same tick as the attack, every single
 * time, forever -- a zero-variance pattern no human reaction produces.
 * [pendingSwapBackTicks] turns that into a randomized few-tick delay
 * instead (picked fresh each attack in [scheduleSwapBack]), which
 * AutoAttributeSwap.onTick() counts down and executes the real swap-back
 * from, rather than the mixin doing it inline on return.
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

    /**
     * Whether to swap-and-arm on the first click after Primary, cancelling
     * that click's own attack, rather than swapping and hitting in the
     * same click. See the mixin's doc comment -- a same-tick swap-then-hit
     * is a much stronger anti-cheat tell than losing one click to the
     * switch.
     */
    @Volatile
    var delayFirstHit: Boolean = true

    /** True between an armed swap (waiting for the next click) and that click landing. */
    @Volatile
    var armed: Boolean = false

    /** Randomized swap-back delay range, in ticks (~50ms each). */
    @Volatile
    var minDelayTicks: Int = 1

    @Volatile
    var maxDelayTicks: Int = 4

    /** Ticks remaining until AutoAttributeSwap.onTick() should perform the swap-back. -1 means none pending. */
    @Volatile
    var pendingSwapBackTicks: Int = -1

    /** Called from the mixin right as the attack call returns -- picks a fresh random delay instead of swapping back inline. */
    fun scheduleSwapBack() {
        val min = minDelayTicks.coerceAtLeast(0)
        val max = maxDelayTicks.coerceAtLeast(min)
        pendingSwapBackTicks = (min..max).random()
    }

    fun cancelPendingSwapBack() {
        pendingSwapBackTicks = -1
    }
}
