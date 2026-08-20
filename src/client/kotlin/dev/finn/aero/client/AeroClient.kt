package dev.finn.aero.client

import dev.finn.aero.config.ConfigManager
import dev.finn.aero.module.ModuleManager
import dev.finn.aero.module.impl.Sprint
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
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

        LOGGER.info("Aero loaded with {} module(s).", ModuleManager.all().size)
    }

    private val wasDown = HashMap<Int, Boolean>()

    private fun pollKeybinds(client: MinecraftClient) {
        val window = client.window ?: return
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
