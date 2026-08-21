package dev.finn.aero.client

import dev.finn.aero.config.ClientSettings
import dev.finn.aero.config.ConfigManager
import dev.finn.aero.gui.ClickGuiScreen
import dev.finn.aero.module.ModuleManager
import dev.finn.aero.module.impl.DeathWaypoint
import dev.finn.aero.module.impl.Freecam
import dev.finn.aero.module.impl.Fullbright
import dev.finn.aero.module.impl.Sprint
import dev.finn.aero.module.impl.esp.ChestEsp
import dev.finn.aero.module.impl.esp.EntityEsp
import dev.finn.aero.module.impl.esp.PlayerEsp
import dev.finn.aero.module.impl.xray.XrayModule
import dev.finn.aero.notification.NotificationManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Mod entrypoint. We use Fabric API's own event system (ClientTickEvents,
 * ClientLifecycleEvents, etc.) directly rather than rolling a custom
 * EventBus: Fabric's events are already a lightweight pub/sub system
 * (typed callbacks + phases), so a second bus on top of it would just be
 * indirection with no real benefit. ModuleManager plays the role of "the
 * bus" for module-specific hooks (tick/keybind), and subscribes itself to
 * Fabric's real events below.
 */
object AeroClient : ClientModInitializer {
    const val MOD_ID = "aero"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitializeClient() {
        LOGGER.info("Aero is loading...")

        // --- Register modules -----------------------------------------
        // Adding a new module is exactly this: write the class, add one
        // line here. Nothing else needs to change for it to tick, render,
        // save/load, and receive its keybind.
        ModuleManager.register(Sprint())
        ModuleManager.register(Fullbright())
        ModuleManager.register(Freecam())
        ModuleManager.register(EntityEsp())
        ModuleManager.register(PlayerEsp())
        ModuleManager.register(ChestEsp())
        ModuleManager.register(DeathWaypoint())
        ModuleManager.register(XrayModule())

        // Restore saved enabled-state/settings/keybinds from disk.
        ConfigManager.load()

        // --- Wire Fabric's real client events ---------------------------
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            ModuleManager.onTick()
        }

        // Poll keybinds every tick. Modules only own a raw GLFW key code
        // (not a full Fabric Keybinding) so they can be rebound freely
        // from the ClickGUI without touching the options screen.
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            pollKeybinds(client)
        }

        // Persist config on world/client shutdown so nothing is lost.
        ClientLifecycleEvents.CLIENT_STOPPING.register { client ->
            ConfigManager.save()
        }

        // 2D HUD overlays (waypoint compass, etc.) and 3D world-space
        // overlays (ESP boxes, waypoint beam) are separate Fabric API
        // events with different coordinate spaces -- fan both out through
        // ModuleManager the same way tick/keybinds already are.
        HudRenderCallback.EVENT.register { context, tickCounter ->
            ModuleManager.onHudRender(context, tickCounter)
            NotificationManager.onHudRender(context, tickCounter)
        }
        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            ModuleManager.onWorldRender(context)
        }

        LOGGER.info("Aero loaded with {} module(s).", ModuleManager.all().size)
    }

    private val wasDown = HashMap<Int, Boolean>()

    private fun pollKeybinds(client: MinecraftClient) {
        val window = client.window ?: return

        val guiKey = ClientSettings.guiKeybind
        val guiDown = org.lwjgl.glfw.GLFW.glfwGetKey(window.handle, guiKey) == org.lwjgl.glfw.GLFW.GLFW_PRESS
        if (guiDown && wasDown[guiKey] != true && client.currentScreen == null) {
            client.setScreen(ClickGuiScreen())
        }
        wasDown[guiKey] = guiDown

        // Don't let module keybinds fire while any screen (including our own
        // GUI, or an unrelated one like the inventory) is open.
        if (client.currentScreen != null) return

        for (module in ModuleManager.all()) {
            val key = module.keybind
            if (key < 0) continue

            val down = org.lwjgl.glfw.GLFW.glfwGetKey(window.handle, key) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            val previouslyDown = wasDown[key] ?: false

            if (down && !previouslyDown) {
                ModuleManager.onKeyPressed(key)
            }
            wasDown[key] = down
        }
    }
}
