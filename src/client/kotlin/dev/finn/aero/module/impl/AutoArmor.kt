package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.SliderSetting
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
 *
 * Legacy mode re-checks and re-equips every single tick -- continuously
 * optimal with zero latency, which no player manually managing their
 * inventory does. New mode (default) only re-checks on a randomized
 * interval, the way a player glancing at their armor bar every so often
 * (rather than watching it every 50ms) actually behaves.
 */
class AutoArmor : Module(
    name = "AutoArmor",
    description = "Automatically equips the best armor pieces in your inventory.",
    category = Category.PLAYER,
) {
    private val materialRank = listOf("leather", "golden", "chainmail", "iron", "diamond", "netherite")

    private val mode = register(
        ModeSetting("Mode", "New only re-checks every so often. Legacy re-checks and re-equips every tick.", listOf("New", "Legacy"), "New"),
    )
    private val minIntervalTicks = register(
        SliderSetting("Min Interval", "New mode only: fastest allowed re-check interval, in ticks (~50ms each).", 20.0, 1.0, 200.0, 5.0),
    )
    private val maxIntervalTicks = register(
        SliderSetting("Max Interval", "New mode only: slowest allowed re-check interval, in ticks (~50ms each).", 60.0, 1.0, 200.0, 5.0),
    )

    /** Ticks until the next New-mode re-check, or -1 if not yet armed (checks immediately on enable). */
    private var ticksUntilCheck = -1

    override fun onEnable() {
        ticksUntilCheck = -1
    }

    override fun onTick() {
        if (mode.value == "Legacy") {
            equipBest()
            return
        }

        if (ticksUntilCheck < 0) {
            equipBest()
            rearm()
            return
        }

        if (ticksUntilCheck == 0) {
            equipBest()
            rearm()
        } else {
            ticksUntilCheck--
        }
    }

    private fun rearm() {
        val min = minIntervalTicks.value.toInt()
        val max = maxIntervalTicks.value.toInt().coerceAtLeast(min)
        ticksUntilCheck = (min..max).random()
    }

    private fun equipBest() {
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
