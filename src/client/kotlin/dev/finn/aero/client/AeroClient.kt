package dev.finn.aero.client

import dev.finn.aero.config.ClientSettings
import dev.finn.aero.config.ConfigManager
import dev.finn.aero.gui.CommandPaletteScreen
import dev.finn.aero.gui.GuiOpener
import dev.finn.aero.module.ModuleManager
import dev.finn.aero.module.impl.AntiKnockback
import dev.finn.aero.module.impl.AutoAttributeSwap
import dev.finn.aero.module.impl.AutoTotem
import dev.finn.aero.module.impl.Criticals
import dev.finn.aero.module.impl.DeathWaypoint
import dev.finn.aero.module.impl.Fly
import dev.finn.aero.module.impl.Freecam
import dev.finn.aero.module.impl.Fullbright
import dev.finn.aero.module.impl.HudInfo
import dev.finn.aero.module.impl.Jesus
import dev.finn.aero.module.impl.KillAura
import dev.finn.aero.module.impl.NoFall
import dev.finn.aero.module.impl.Nuker
import dev.finn.aero.module.impl.Scaffold
import dev.finn.aero.module.impl.Speed
import dev.finn.aero.module.impl.Spider
import dev.finn.aero.module.impl.Sprint
import dev.finn.aero.module.impl.esp.ChestEsp
import dev.finn.aero.module.impl.esp.EntityEsp
import dev.finn.aero.module.impl.esp.PlayerEsp
import dev.finn.aero.module.impl.xray.XrayModule
import dev.finn.aero.notification.NotificationManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.resources.Identifier
import net.minecraft.client.Minecraft
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
        ModuleManager.register(KillAura())
        ModuleManager.register(Criticals())
        ModuleManager.register(AutoTotem())
        ModuleManager.register(Speed())
        ModuleManager.register(NoFall())
        ModuleManager.register(AutoAttributeSwap())
        ModuleManager.register(Fly())
        ModuleManager.register(Nuker())
        ModuleManager.register(AntiKnockback())
        ModuleManager.register(HudInfo())
        ModuleManager.register(Scaffold())
        ModuleManager.register(Jesus())
        ModuleManager.register(Spider())

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
        // 26.x replaced HudRenderCallback with named, orderable HUD elements,
        // and WorldRenderEvents with the render-state extraction pipeline's
        // LevelRenderEvents -- COLLECT_SUBMITS is where custom world geometry
        // gets submitted now.
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(MOD_ID, "overlays"),
            HudElement { context, tickCounter ->
                ModuleManager.onHudRender(context, tickCounter)
                NotificationManager.onHudRender(context, tickCounter)
            },
        )
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            ModuleManager.onWorldRender(context)
        }

        LOGGER.info("Aero loaded with {} module(s).", ModuleManager.all().size)
    }

    private val wasDown = HashMap<Int, Boolean>()

    /** Synthetic key into [wasDown] for the Alt+RightShift combo's rising-edge state -- distinct from any real GLFW keycode (which are all >= -1, GLFW_KEY_UNKNOWN). */
    private const val PALETTE_WAS_DOWN_KEY = -1000

    private fun pollKeybinds(client: Minecraft) {
        val window = client.window ?: return

        val guiKey = ClientSettings.guiKeybind
        val guiDown = org.lwjgl.glfw.GLFW.glfwGetKey(window.handle(), guiKey) == org.lwjgl.glfw.GLFW.GLFW_PRESS
        if (guiDown && wasDown[guiKey] != true && client.gui.screen() == null) {
            client.gui.setScreen(GuiOpener.clickGuiScreen())
        }
        wasDown[guiKey] = guiDown

        // Independent of the single-keycode guiKeybind system above: a fixed
        // Alt+RightShift combo that opens the command palette, gated the
        // same way (only fires from gameplay, not while any screen is open).
        // Not user-rebindable, so this is its own raw polling block rather
        // than going through Module.keybind/ClientSettings.guiKeybind.
        val altDown = org.lwjgl.glfw.GLFW.glfwGetKey(window.handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
            org.lwjgl.glfw.GLFW.glfwGetKey(window.handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
        val paletteKey = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT
        val paletteDown = altDown && org.lwjgl.glfw.GLFW.glfwGetKey(window.handle(), paletteKey) == org.lwjgl.glfw.GLFW.GLFW_PRESS
        if (paletteDown && wasDown[PALETTE_WAS_DOWN_KEY] != true && client.gui.screen() == null) {
            client.gui.setScreen(CommandPaletteScreen())
        }
        wasDown[PALETTE_WAS_DOWN_KEY] = paletteDown

        // Don't let module keybinds fire while any screen (including our own
        // GUI, or an unrelated one like the inventory) is open.
        if (client.gui.screen() != null) return

        for (module in ModuleManager.all()) {
            val key = module.keybind
            if (key < 0) continue

            val down = org.lwjgl.glfw.GLFW.glfwGetKey(window.handle(), key) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            val previouslyDown = wasDown[key] ?: false

            if (down && !previouslyDown) {
                ModuleManager.onKeyPressed(key)
            }
            wasDown[key] = down
        }
    }
}
