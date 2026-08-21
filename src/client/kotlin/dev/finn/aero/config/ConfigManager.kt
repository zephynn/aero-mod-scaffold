package dev.finn.aero.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.finn.aero.module.ModuleManager
import dev.finn.aero.setting.BoolSetting
import dev.finn.aero.setting.ColorSetting
import dev.finn.aero.setting.KeybindSetting
import dev.finn.aero.setting.ModeSetting
import dev.finn.aero.setting.SliderSetting
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Saves/loads every module's enabled state + settings as one JSON file
 * in <game dir>/config/aero.json using Gson, which Minecraft already
 * bundles -- no extra dependency needed.
 */
object ConfigManager {
    private val logger = LoggerFactory.getLogger("aero-config")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val configPath: Path by lazy {
        FabricLoader.getInstance().configDir.resolve("aero.json")
    }

    fun save() {
        val root = JsonObject()
        val modulesJson = JsonObject()

        for (module in ModuleManager.all()) {
            val moduleJson = JsonObject()
            moduleJson.addProperty("enabled", module.enabled)
            moduleJson.addProperty("keybind", module.keybind)
            moduleJson.addProperty("pinned", module.pinned)

            val settingsJson = JsonObject()
            for (setting in module.settings) {
                when (setting) {
                    is BoolSetting -> settingsJson.addProperty(setting.name, setting.value)
                    is SliderSetting -> settingsJson.addProperty(setting.name, setting.value)
                    is ModeSetting -> settingsJson.addProperty(setting.name, setting.value)
                    is KeybindSetting -> settingsJson.addProperty(setting.name, setting.value)
                    is ColorSetting -> settingsJson.addProperty(setting.name, setting.value)
                }
            }
            moduleJson.add("settings", settingsJson)

            modulesJson.add(module.name, moduleJson)
        }

        root.add("modules", modulesJson)

        val themeJson = JsonObject()
        themeJson.addProperty("accent", Theme.accent)
        root.add("theme", themeJson)

        val clientJson = JsonObject()
        clientJson.addProperty("guiKeybind", ClientSettings.guiKeybind)
        clientJson.addProperty("guiStyle", ClientSettings.guiStyle)
        clientJson.addProperty("reducedMotion", ClientSettings.reducedMotion)
        clientJson.addProperty("notificationPosition", ClientSettings.notificationPosition)
        clientJson.addProperty("notificationDurationMs", ClientSettings.notificationDurationMs)
        root.add("client", clientJson)

        val xrayJson = com.google.gson.JsonArray()
        for (id in dev.finn.aero.module.impl.xray.XrayCustomConfig.blockIds()) {
            xrayJson.add(id)
        }
        root.add("xray", xrayJson)

        try {
            Files.createDirectories(configPath.parent)
            Files.writeString(configPath, gson.toJson(root))
        } catch (e: IOException) {
            logger.error("Failed to save Aero config", e)
        }
    }

    fun load() {
        if (!Files.exists(configPath)) return

        try {
            val text = Files.readString(configPath)
            val root = JsonParser.parseString(text).asJsonObject

            root.getAsJsonObject("theme")?.let { themeJson ->
                themeJson.get("accent")?.asInt?.let { Theme.accent = it }
            }

            root.getAsJsonObject("client")?.let { clientJson ->
                clientJson.get("guiKeybind")?.asInt?.let { ClientSettings.guiKeybind = it }
                clientJson.get("guiStyle")?.asString?.let { ClientSettings.guiStyle = it }
                clientJson.get("reducedMotion")?.asBoolean?.let { ClientSettings.reducedMotion = it }
                clientJson.get("notificationPosition")?.asString?.let { ClientSettings.notificationPosition = it }
                clientJson.get("notificationDurationMs")?.asLong?.let { ClientSettings.notificationDurationMs = it }
            }

            root.getAsJsonArray("xray")?.let { xrayJson ->
                val ids = xrayJson.mapNotNull { it.asString }
                dev.finn.aero.module.impl.xray.XrayCustomConfig.setBlockIds(ids)
            }

            val modulesJson = root.getAsJsonObject("modules") ?: return

            for (module in ModuleManager.all()) {
                val moduleJson = modulesJson.getAsJsonObject(module.name) ?: continue

                moduleJson.get("keybind")?.asInt?.let { module.keybind = it }
                moduleJson.get("pinned")?.asBoolean?.let { module.pinned = it }

                val settingsJson = moduleJson.getAsJsonObject("settings")
                if (settingsJson != null) {
                    for (setting in module.settings) {
                        val element = settingsJson.get(setting.name) ?: continue
                        when (setting) {
                            is BoolSetting -> setting.value = element.asBoolean
                            is SliderSetting -> setting.set(element.asDouble)
                            is ModeSetting -> setting.value = element.asString
                            is KeybindSetting -> setting.value = element.asInt
                            is ColorSetting -> setting.value = element.asInt
                        }
                    }
                }

                // Enable last, after settings are restored, so onEnable() sees
                // the correct configured values.
                if (moduleJson.get("enabled")?.asBoolean == true) {
                    module.enable()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load Aero config, using defaults", e)
        }
    }
}
