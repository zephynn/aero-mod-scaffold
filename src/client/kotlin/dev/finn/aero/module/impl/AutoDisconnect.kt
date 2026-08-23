package dev.finn.aero.module.impl

import dev.finn.aero.module.Category
import dev.finn.aero.module.Module
import dev.finn.aero.setting.SliderSetting

/** Disconnects the instant your health drops to or below a threshold -- a safety net for AFK/risky situations. */
class AutoDisconnect : Module(
    name = "AutoDisconnect",
    description = "Disconnects automatically if your health drops too low.",
    category = Category.MISC,
) {
    private val healthThreshold = register(
        SliderSetting("Health Threshold", "Disconnect once health drops to or below this.", 6.0, 0.0, 20.0, 1.0),
    )

    override fun onTick() {
        val player = mc.player ?: return
        if (player.health > healthThreshold.value) return

        mc.disconnectWithProgressScreen()
    }
}
