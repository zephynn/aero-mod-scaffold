package dev.finn.aero.module.impl.esp

import net.minecraft.world.phys.Vec3
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Camera-relative angle-based perspective projection -- world position to
 * screen pixel -- without needing Minecraft's actual GL projection matrix.
 *
 * This exists because the first tracer attempt drew a straight 3D line
 * from the camera to each target, which is close to invisible from the
 * camera's own point of view: a line pointing almost exactly along your
 * look direction is maximally foreshortened, so it projects to nearly a
 * single point on screen whenever you're actually looking at the target
 * (which is, inconveniently, exactly when you'd be testing whether it
 * works). Meteor and every other ESP tool draw the tracer as a 2D HUD
 * line instead, from a fixed screen anchor to the target's *projected*
 * screen position -- this is what makes that possible.
 */
object EspProjection {
    /** Null if [target] is behind the camera or too far outside the view frustum to matter. */
    fun project(
        camPos: Vec3,
        yawDegrees: Float,
        pitchDegrees: Float,
        fovDegrees: Float,
        screenW: Int,
        screenH: Int,
        target: Vec3,
    ): Pair<Int, Int>? {
        val dx = target.x - camPos.x
        val dy = target.y - camPos.y
        val dz = target.z - camPos.z
        val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
        if (dist < 0.01) return null
        val tx = dx / dist
        val ty = dy / dist
        val tz = dz / dist

        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())

        // Standard Minecraft yaw/pitch -> direction convention.
        val fx = -sin(yaw) * cos(pitch)
        val fy = -sin(pitch)
        val fz = cos(yaw) * cos(pitch)

        // Horizontal right vector (roll is always 0 for the player camera).
        val rx = -cos(yaw)
        val rz = -sin(yaw)

        // up = right x forward
        val ux = -rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy

        val depth = tx * fx + ty * fy + tz * fz
        if (depth <= 0.05) return null

        val right = tx * rx + tz * rz
        val up = tx * ux + ty * uy + tz * uz

        val fovRad = Math.toRadians(fovDegrees.toDouble())
        val halfFovY = fovRad / 2.0
        val aspect = screenW.toDouble() / screenH.toDouble()
        val halfFovX = atan(tan(halfFovY) * aspect)

        val ndcX = (right / depth) / tan(halfFovX)
        val ndcY = (up / depth) / tan(halfFovY)
        if (ndcX < -2.5 || ndcX > 2.5 || ndcY < -2.5 || ndcY > 2.5) return null

        val screenX = (screenW / 2.0 + ndcX * screenW / 2.0).toInt()
        val screenY = (screenH / 2.0 - ndcY * screenH / 2.0).toInt()
        return screenX to screenY
    }
}
