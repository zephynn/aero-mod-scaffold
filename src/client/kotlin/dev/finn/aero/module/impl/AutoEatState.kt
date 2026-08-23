package dev.finn.aero.module.impl

/**
 * Kotlin bridge for [dev.finn.aero.mixin.SuppressUseItemReleaseMixin].
 *
 * Vanilla's own per-frame input handling checks `!options.keyUse.isDown()`
 * while `player.isUsingItem()` and calls `releaseUsingItem()` the instant
 * it sees the key isn't physically held -- which would cancel an
 * AutoEat-triggered eat one frame after it starts, since nothing is
 * physically holding right-click. This flag tells that one call site to
 * skip itself while AutoEat is genuinely mid-eat, letting the item's use
 * duration tick down and complete normally instead.
 */
object AutoEatState {
    @Volatile
    var suppressRelease: Boolean = false
}
