package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB

/**
 * Automatically mines the nearest breakable block within range, over and
 * over. Rather than an instant "break everything in radius" exploit, this
 * drives Minecraft's own start/continue/stop-mining sequence one target at
 * a time -- the same three calls a real left-click hold goes through -- so
 * it plays out as progressive mining (respecting block hardness and tool
 * speed in survival, instant in creative) and re-targets to the next
 * nearest block whenever the current one breaks or falls out of range.
 */
class Nuker : Module(
    name = "Nuker",
    description = "Automatically mines the nearest breakable block within range.",
    category = Category.MISC,
) {
    private val range = register(
        SliderSetting(
            name = "Range",
            description = "Max distance to a block, in blocks.",
            default = 4.0,
            min = 2.0,
            max = 6.0,
            step = 0.5,
        ),
    )

    private var targetPos: BlockPos? = null
    private var targetDir: Direction? = null

    override fun onDisable() {
        stopMining()
    }

    override fun onTick() {
        val player = mc.player ?: return
        val world = mc.level ?: return

        val current = targetPos
        if (current != null && (!isBreakable(world, current) || player.position().distanceTo(AABB(current).center) > range.value)) {
            stopMining()
        }

        val pos = targetPos
        if (pos == null) {
            val next = findTarget(world, player) ?: return
            targetPos = next
            targetDir = Direction.UP
            mc.gameMode?.startDestroyBlock(next, Direction.UP)
        } else {
            mc.gameMode?.continueDestroyBlock(pos, targetDir ?: Direction.UP)
            if (world.getBlockState(pos).isAir) {
                targetPos = null
                targetDir = null
            }
        }
    }

    private fun stopMining() {
        if (targetPos != null) mc.gameMode?.stopDestroyBlock()
        targetPos = null
        targetDir = null
    }

    private fun isBreakable(world: ClientLevel, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        if (state.isAir) return false
        return state.getDestroySpeed(world, pos) >= 0f
    }

    private fun findTarget(world: ClientLevel, player: net.minecraft.world.entity.player.Player): BlockPos? {
        val eye = player.eyePosition
        val base = player.blockPosition()
        val r = range.value.toInt() + 1

        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE
        for (x in -r..r) {
            for (y in -r..r) {
                for (z in -r..r) {
                    val pos = base.offset(x, y, z)
                    if (!isBreakable(world, pos)) continue
                    val dist = eye.distanceTo(AABB(pos).center)
                    if (dist > range.value) continue
                    if (dist < bestDist) {
                        bestDist = dist
                        best = pos
                    }
                }
            }
        }
        return best
    }
}
