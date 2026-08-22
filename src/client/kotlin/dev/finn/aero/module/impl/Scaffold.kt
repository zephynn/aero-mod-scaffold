package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import kotlin.math.floor

/**
 * Places a block directly beneath your feet whenever you're standing over
 * air, so walking off an edge just bridges instead of falling. Swaps to
 * whatever hotbar slot holds a block for the one placement and swaps back
 * immediately after -- the same swap-and-restore shape AutoAttributeSwap
 * uses for weapon swaps -- and places against a real neighbouring solid
 * block (BlockHitResult + useItemOn), so it's a normal block-placement
 * packet, not a direct world edit.
 *
 * Two things make this work at a sprint or mid-jump rather than only at a
 * dead walk: the XZ target is projected one tick ahead from the current
 * velocity (so the block lands where you're about to be, not where you
 * already are), and the Y target is pinned to the layer you were last
 * standing on ([groundY]) instead of your live (jump-arced) feet height --
 * otherwise a jump would aim the placement at thin air until you fell back
 * down to it.
 */
class Scaffold : Module(
    name = "Scaffold",
    description = "Automatically places blocks beneath you while walking, sprinting, or jumping.",
    category = Category.MOVEMENT,
) {
    /** Y of the last block layer the player was actually standing on -- kept steady through a jump's rise and fall. */
    private var groundY: Int? = null

    override fun onDisable() {
        groundY = null
    }

    override fun onTick() {
        val player = mc.player ?: return
        val world = mc.level ?: return
        val gameMode = mc.gameMode ?: return

        if (player.isSpectator || player.abilities.flying) return

        if (player.onGround()) groundY = floor(player.y - 0.05).toInt()
        val layerY = groundY ?: floor(player.y - 0.05).toInt()

        val vel = player.deltaMovement
        val predictedX = player.x + vel.x
        val predictedZ = player.z + vel.z

        val targetPos = BlockPos(floor(predictedX).toInt(), layerY, floor(predictedZ).toInt())
        if (!world.getBlockState(targetPos).isAir) return

        val slot = findBlockSlot(player) ?: return
        val support = findSupportFace(world, targetPos) ?: return

        val inventory = player.inventory
        val originalSlot = inventory.selectedSlot
        val needsSwap = slot != originalSlot
        if (needsSwap) inventory.selectedSlot = slot

        val (supportPos, face) = support
        val hitLocation = AABB(supportPos).center
        val hitResult = BlockHitResult(hitLocation, face, supportPos, false)
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)

        if (needsSwap) inventory.selectedSlot = originalSlot
    }

    private fun findBlockSlot(player: Player): Int? {
        for (i in 0..8) {
            if (player.inventory.getItem(i).item is BlockItem) return i
        }
        return null
    }

    /** A solid block neighbouring [targetPos], and the face of it facing [targetPos] -- what useItemOn needs to click "into" the empty spot. */
    private fun findSupportFace(world: ClientLevel, targetPos: BlockPos): Pair<BlockPos, Direction>? {
        for (dir in Direction.values()) {
            val neighbor = targetPos.relative(dir)
            if (!world.getBlockState(neighbor).isAir) return neighbor to dir.opposite
        }
        return null
    }
}
