package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.SliderSetting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BedItem
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult

/**
 * Places a bed next to a nearby target and immediately right-clicks it --
 * a bed used outside the Overworld (or a respawn anchor used outside the
 * Nether) explodes instead of letting anyone sleep in it, which is the
 * whole "BedAura" PvP trick. This only makes sense to enable somewhere
 * that's actually true; it doesn't check your current dimension itself.
 *
 * Placement geometry is intentionally simple rather than fully general: it
 * only tries the block directly beside the target's feet, clicking its
 * top face, and lets BedItem's own placement logic (which needs a second,
 * correctly oriented empty tile to lay the bed's other half in) decide
 * whether that spot actually works. When it doesn't, useItemOn just fails
 * silently -- nothing gets placed, and the module tries again next tick --
 * rather than anything breaking. It won't succeed in every position a
 * target can stand in, but it's a real, ordinary block placement + a real
 * block interaction, not anything synthetic.
 */
class BedAura : Module(
    name = "BedAura",
    description = "Places and detonates beds on nearby targets (Nether/End only).",
    category = Category.COMBAT,
) {
    private val range = register(
        SliderSetting("Range", "Max distance to a target, in blocks.", 4.0, 2.0, 6.0, 0.5),
    )
    private val attackPlayers = register(BoolSetting("Players", "Target other players.", true))
    private val attackHostiles = register(BoolSetting("Hostiles", "Target hostile mobs.", false))
    private val attackAnimals = register(BoolSetting("Animals", "Target passive/animal mobs.", false))

    /** Block a bed was just placed at, to trigger next tick once it's actually there. */
    private var pendingTrigger: BlockPos? = null

    override fun onDisable() {
        pendingTrigger = null
    }

    override fun onTick() {
        val player = mc.player ?: return
        val world = mc.level ?: return
        val gameMode = mc.gameMode ?: return

        val pending = pendingTrigger
        if (pending != null) {
            pendingTrigger = null
            if (world.getBlockState(pending).block is BedBlock) {
                val hit = BlockHitResult(AABB(pending).center, Direction.UP, pending, false)
                gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
            }
            return
        }

        val target = findTarget(world, player) ?: return
        val bedSlot = findBedSlot(player) ?: return
        val (footPos, supportPos) = findPlacementSpot(world, target.blockPosition()) ?: return

        val inventory = player.inventory
        val originalSlot = inventory.selectedSlot
        val needsSwap = bedSlot != originalSlot
        if (needsSwap) switchSlot(player, bedSlot)

        val hit = BlockHitResult(AABB(supportPos).center, Direction.UP, supportPos, false)
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        pendingTrigger = footPos

        if (needsSwap) switchSlot(player, originalSlot)
    }

    private fun findTarget(world: net.minecraft.client.multiplayer.ClientLevel, player: Player): LivingEntity? {
        val searchBox = player.boundingBox.inflate(range.value)
        return world.getEntities(player, searchBox) { entity ->
            entity is LivingEntity &&
                !entity.isDeadOrDying &&
                entity.health > 0f &&
                isValidTargetType(entity) &&
                player.distanceTo(entity) <= range.value
        }.minByOrNull { player.distanceTo(it) } as? LivingEntity
    }

    private fun isValidTargetType(entity: LivingEntity): Boolean = when (entity) {
        is Player -> attackPlayers.value && entity != mc.player
        is Monster -> attackHostiles.value
        is Animal -> attackAnimals.value
        else -> false
    }

    private fun findBedSlot(player: Player): Int? =
        (0..8).firstOrNull { player.inventory.getItem(it).item is BedItem }

    /** An empty block beside [targetPos] with solid ground under it, and the solid block below it to click against. */
    private fun findPlacementSpot(world: net.minecraft.client.multiplayer.ClientLevel, targetPos: BlockPos): Pair<BlockPos, BlockPos>? {
        for (dir in Direction.Plane.HORIZONTAL) {
            val footPos = targetPos.relative(dir)
            if (!world.getBlockState(footPos).isAir) continue
            val below = footPos.below()
            if (world.getBlockState(below).isAir) continue
            return footPos to below
        }
        return null
    }

    private fun switchSlot(player: Player, slot: Int) {
        val connection = mc.connection ?: return
        player.inventory.selectedSlot = slot
        connection.send(ServerboundSetCarriedItemPacket(slot))
    }
}
