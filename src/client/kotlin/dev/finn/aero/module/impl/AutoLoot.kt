package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.BoolSetting
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType

/**
 * The moment a chest/barrel/shulker/ender-chest screen opens, shift-clicks
 * every occupied container slot into your own inventory. ChestMenu covers
 * all of those container kinds -- their slot layout is always the
 * container's own slots first, then the player's 27 main + 9 hotbar slots
 * appended after, so `slots.size - 36` is the container's own slot count
 * regardless of container size.
 */
class AutoLoot : Module(
    name = "AutoLoot",
    description = "Automatically loots every item out of a chest the moment you open it.",
    category = Category.MISC,
) {
    private val closeAfter = register(BoolSetting("Close After", "Close the container once it's empty.", true))

    override fun onTick() {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        val menu = screen.menu as? ChestMenu ?: return

        val containerSlotCount = menu.slots.size - 36
        if (containerSlotCount <= 0) return

        var anyLooted = false
        for (i in 0 until containerSlotCount) {
            if (menu.slots[i].item.isEmpty) continue
            gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, player)
            anyLooted = true
        }

        if (closeAfter.value && anyLooted) {
            val stillHasItems = (0 until containerSlotCount).any { !menu.slots[it].item.isEmpty }
            if (!stillHasItems) player.closeContainer()
        }
    }
}
