package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack

/**
 * Keeps your best armor piece per slot equipped. Ranks by material tier
 * inferred from the item's own registry name (e.g. "diamond_helmet")
 * rather than a numeric defense value: modern armor pieces carry their
 * protection via the material's own attribute-modifier layers rather than
 * a simple per-item getter, but which slot a piece goes in is still just
 * its Equippable data component.
 *
 * Equips via two real container clicks -- pick up the better piece, then
 * click the armor slot to swap it in -- the same manual swap a player
 * dragging armor into place would send, not a direct inventory edit.
 */
class AutoArmor : Module(
    name = "AutoArmor",
    description = "Automatically equips the best armor pieces in your inventory.",
    category = Category.PLAYER,
) {
    private val materialRank = listOf("leather", "golden", "chainmail", "iron", "diamond", "netherite")

    override fun onTick() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val menu = player.inventoryMenu

        for (equipSlot in listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            val armorMenuIndex = when (equipSlot) {
                EquipmentSlot.HEAD -> 5
                EquipmentSlot.CHEST -> 6
                EquipmentSlot.LEGS -> 7
                EquipmentSlot.FEET -> 8
                else -> continue
            }

            val currentScore = score(menu.slots[armorMenuIndex].item, equipSlot)

            var bestIndex = -1
            var bestScore = currentScore
            for (i in 9 until menu.slots.size) {
                val s = score(menu.slots[i].item, equipSlot)
                if (s > bestScore) {
                    bestScore = s
                    bestIndex = i
                }
            }

            if (bestIndex >= 0) {
                gameMode.handleContainerInput(menu.containerId, bestIndex, 0, ContainerInput.PICKUP, player)
                gameMode.handleContainerInput(menu.containerId, armorMenuIndex, 0, ContainerInput.PICKUP, player)
                if (!menu.carried.isEmpty) {
                    gameMode.handleContainerInput(menu.containerId, bestIndex, 0, ContainerInput.PICKUP, player)
                }
            }
        }
    }

    private fun score(stack: ItemStack, equipSlot: EquipmentSlot): Int {
        if (stack.isEmpty) return -1
        val equippable = stack.get(DataComponents.EQUIPPABLE) ?: return -1
        if (equippable.slot() != equipSlot) return -1

        val path = BuiltInRegistries.ITEM.getKey(stack.item).path
        val tier = materialRank.indexOfFirst { path.startsWith(it) }
        return tier + 1
    }
}
