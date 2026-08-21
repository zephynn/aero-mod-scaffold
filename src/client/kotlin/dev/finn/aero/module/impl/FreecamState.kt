package dev.finn.aero.module.impl

import net.minecraft.world.phys.Vec3

/**
 * Bridge between Freecam.kt and the two client-side mixins that decouple
 * the render camera and movement input from the real player entity.
 *
 * Deliberately NOT in the dev.finn.aero.mixin package: Mixin treats every
 * class under a mixin config's declared "package" as owned by that config
 * and refuses to let anything outside reference it directly, even a plain
 * data holder like this one.
 */
object FreecamState {
    @JvmField
    var active: Boolean = false

    /** World-space position the camera should render from, or null when inactive. */
    @JvmField
    var position: Vec3? = null

    /** Blocks per second. CameraMixin reads this every frame, so changing it live is instant. */
    @JvmField
    var speed: Double = 8.0
}
