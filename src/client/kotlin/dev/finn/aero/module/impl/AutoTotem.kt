package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.ContainerInput

/**
 * Swaps a totem of undying into the offhand once health drops below the
 * threshold, if the offhand doesn't already have one. This is exactly what
 * pressing vanilla's "swap item to offhand" key (F by default) does under
 * the hood -- `AbstractContainerMenu#clicked` special-cases button `40` on a
 * SWAP action to mean "swap with the offhand slot" -- so this just clicks
 * that slot programmatically instead of waiting for the player to notice
 * and react in time.
 */
class AutoTotem : Module(
    name = "AutoTotem",
    description = "Swaps a totem of undying into your offhand at low health.",
    category = Category.PLAYER,
) {
    private companion object {
        const val OFFHAND_SWAP_BUTTON = 40
    }

    private val healthThreshold = register(
        SliderSetting(
            name = "Health Threshold",
            description = "Swap a totem in once health drops to or below this.",
            default = 6.0,
            min = 1.0,
            max = 20.0,
            step = 1.0,
        ),
    )

    override fun onTick() {
        val player = mc.player ?: return
        val interactionManager = mc.gameMode ?: return

        if (player.offhandItem.item == Items.TOTEM_OF_UNDYING) return
        if (player.health > healthThreshold.value) return

        val mainStacks = player.inventory.nonEquipmentItems
        val totemIndex = mainStacks.indexOfFirst { it.item == Items.TOTEM_OF_UNDYING }
        if (totemIndex < 0) return

        // PlayerScreenHandler slot numbering: hotbar inventory indices 0-8
        // show up as screen slots 36-44; main storage indices 9-35 keep the
        // same numbers as their screen slots.
        val screenSlot = if (totemIndex < 9) 36 + totemIndex else totemIndex

        interactionManager.handleContainerInput(player.containerMenu.containerId, screenSlot, OFFHAND_SWAP_BUTTON, ContainerInput.SWAP, player)
    }
}
