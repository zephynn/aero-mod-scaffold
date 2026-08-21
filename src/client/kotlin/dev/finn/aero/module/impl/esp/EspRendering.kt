package dev.finn.aero.module.impl.esp

import net.minecraft.client.gui.GuiGraphicsExtractor
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * The drawing every ESP module needs: a 2D HUD tracer line, and (for
 * block-based targets, which can't use the entity glow-outline mixin) a
 * 3D world-space box outline.
 */
object EspRendering {
    /** [box] in absolute world coordinates; [camPos] is subtracted here so callers never have to think about it. */
    fun drawBox(matrices: PoseStack, buffer: VertexConsumer, camPos: Vec3, box: AABB, color: Int, width: Float = 2f) {
        val x0 = box.minX - camPos.x
        val y0 = box.minY - camPos.y
        val z0 = box.minZ - camPos.z
        val x1 = box.maxX - camPos.x
        val y1 = box.maxY - camPos.y
        val z1 = box.maxZ - camPos.z

        // Bottom face.
        segment(matrices, buffer, x0, y0, z0, x1, y0, z0, color, width)
        segment(matrices, buffer, x1, y0, z0, x1, y0, z1, color, width)
        segment(matrices, buffer, x1, y0, z1, x0, y0, z1, color, width)
        segment(matrices, buffer, x0, y0, z1, x0, y0, z0, color, width)
        // Top face.
        segment(matrices, buffer, x0, y1, z0, x1, y1, z0, color, width)
        segment(matrices, buffer, x1, y1, z0, x1, y1, z1, color, width)
        segment(matrices, buffer, x1, y1, z1, x0, y1, z1, color, width)
        segment(matrices, buffer, x0, y1, z1, x0, y1, z0, color, width)
        // Vertical edges.
        segment(matrices, buffer, x0, y0, z0, x0, y1, z0, color, width)
        segment(matrices, buffer, x1, y0, z0, x1, y1, z0, color, width)
        segment(matrices, buffer, x1, y0, z1, x1, y1, z1, color, width)
        segment(matrices, buffer, x0, y0, z1, x0, y1, z1, color, width)
    }

    /**
     * A filled box -- six quad faces -- for X-Ray's "Solid"/"Both"
     * highlight style, drawn into [EspRenderLayers.NO_DEPTH_QUADS]. [color]
     * carries the alpha (X-Ray's "Terrain Opacity" setting folds into it
     * before calling this) rather than taking a separate alpha parameter,
     * matching how [drawBox]'s own color already carries full ARGB.
     */
    fun drawSolidBox(matrices: PoseStack, buffer: VertexConsumer, camPos: Vec3, box: AABB, color: Int) {
        val x0 = (box.minX - camPos.x).toFloat()
        val y0 = (box.minY - camPos.y).toFloat()
        val z0 = (box.minZ - camPos.z).toFloat()
        val x1 = (box.maxX - camPos.x).toFloat()
        val y1 = (box.maxY - camPos.y).toFloat()
        val z1 = (box.maxZ - camPos.z).toFloat()
        val entry = matrices.last()

        fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float) {
            buffer.vertex(entry, ax, ay, az).color(color)
            buffer.vertex(entry, bx, by, bz).color(color)
            buffer.vertex(entry, cx, cy, cz).color(color)
            buffer.vertex(entry, dx, dy, dz).color(color)
        }

        // -Y / +Y
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1)
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0)
        // -X / +X
        quad(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1)
        quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0)
        // -Z / +Z
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0)
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1)
    }

    /**
     * A solid line in screen space, from ([x0],[y0]) to ([x1],[y1]) -- one thin
     * filled rectangle, rotated to match the line's angle via GuiGraphicsExtractor's own
     * 2D matrix stack, the same technique Meteor's tracer uses. GuiGraphicsExtractor has
     * no line primitive of its own, but fill() already respects whatever
     * transform is pushed onto getMatrices(), so a rotated 1px-tall rect reads
     * as a clean continuous line instead of a dashed row of squares.
     */
    fun drawScreenLine(context: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, thickness: Int = 1) {
        val dx = (x1 - x0).toFloat()
        val dy = (y1 - y0).toFloat()
        val length = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (length < 0.5f) return
        val angle = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()

        val matrices = context.matrices
        matrices.pushMatrix()
        matrices.translate(x0.toFloat(), y0.toFloat())
        matrices.rotate(angle)
        context.fill(0, -(thickness / 2), length.toInt(), -(thickness / 2) + thickness, color)
        matrices.popMatrix()
    }

    private fun segment(
        matrices: PoseStack,
        buffer: VertexConsumer,
        x1: Double,
        y1: Double,
        z1: Double,
        x2: Double,
        y2: Double,
        z2: Double,
        color: Int,
        width: Float,
    ) {
        val entry = matrices.last()
        val dx = (x2 - x1).toFloat()
        val dy = (y2 - y1).toFloat()
        val dz = (z2 - z1).toFloat()
        val length = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat().coerceAtLeast(0.0001f)
        val nx = dx / length
        val ny = dy / length
        val nz = dz / length

        buffer.vertex(entry, x1.toFloat(), y1.toFloat(), z1.toFloat()).color(color).normal(entry, nx, ny, nz).lineWidth(width)
        buffer.vertex(entry, x2.toFloat(), y2.toFloat(), z2.toFloat()).color(color).normal(entry, nx, ny, nz).lineWidth(width)
    }
}
