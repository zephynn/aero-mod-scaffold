package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * Raises how tall a block you can walk up onto without jumping, via the
 * real Attributes.STEP_HEIGHT attribute instance (the same one vanilla
 * uses for the Powder Snow boots / mob-specific step heights) rather than
 * a client-only override -- this is a legitimate, if unusually high,
 * attribute value.
 */
class StepAssist : Module(
    name = "StepAssist",
    description = "Walk up taller blocks without jumping.",
    category = Category.MOVEMENT,
) {
    private val height = register(
        SliderSetting(
            name = "Height",
            description = "Max step-up height, in blocks (vanilla is 0.6).",
            default = 1.0,
            min = 0.6,
            max = 4.0,
            step = 0.1,
        ),
    )

    private var original: Double? = null

    override fun onEnable() {
        val attribute = mc.player?.getAttribute(Attributes.STEP_HEIGHT) ?: return
        original = attribute.baseValue
        attribute.baseValue = height.value
    }

    override fun onDisable() {
        val attribute = mc.player?.getAttribute(Attributes.STEP_HEIGHT) ?: return
        original?.let { attribute.baseValue = it }
        original = null
    }

    override fun onTick() {
        mc.player?.getAttribute(Attributes.STEP_HEIGHT)?.baseValue = height.value
    }
}
