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
 *
 * The real *attack* is delayed the same way now too: a same-tick
 * swap-then-attack is its own tell independent of the swap-back, so the
 * mixin arms and cancels that first click's attack, then
 * [pendingAutoAttackTicks] (via [scheduleAutoAttack]) has
 * AutoAttributeSwap.onTick() fire the real attack itself a few ticks
 * later -- via MinecraftStartAttackInvoker re-invoking the same
 * startAttack() the mixin hooks -- instead of requiring the player to
 * physically click a second time.
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

    /** True between an armed swap and the (auto-fired) attack that follows it landing. */
    @Volatile
    var armed: Boolean = false

    /** Randomized delay range, in ticks, between arming and auto-firing the real attack. */
    @Volatile
    var minAttackDelayTicks: Int = 1

    @Volatile
    var maxAttackDelayTicks: Int = 3

    /** Ticks remaining until AutoAttributeSwap.onTick() should fire the real, delayed attack. -1 means none pending. */
    @Volatile
    var pendingAutoAttackTicks: Int = -1

    /** Called from the mixin right after arming -- picks a fresh random delay before the real attack auto-fires. */
    fun scheduleAutoAttack() {
        val min = minAttackDelayTicks.coerceAtLeast(0)
        val max = maxAttackDelayTicks.coerceAtLeast(min)
        pendingAutoAttackTicks = (min..max).random()
    }

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

    fun cancelPendingAutoAttack() {
        pendingAutoAttackTicks = -1
    }
}
