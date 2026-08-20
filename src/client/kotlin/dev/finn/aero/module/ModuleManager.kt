package dev.finn.aero.module

/**
 * Central registry of every module. Owns lookup by name/category and
 * fans out the tick/keybind hooks to whichever modules are enabled.
 */
object ModuleManager {
    private val modules: MutableList<Module> = mutableListOf()

    fun register(module: Module) {
        modules.add(module)
    }

    fun all(): List<Module> = modules

    fun byCategory(category: Category): List<Module> =
        modules.filter { it.category == category }

    fun byName(name: String): Module? =
        modules.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Called from ClientTickEvents.END_CLIENT_TICK. */
    fun onTick() {
        for (module in modules) {
            if (module.enabled) module.onTick()
        }
    }

    /** Called from a key-input event; matches the pressed key against every module's keybind. */
    fun onKeyPressed(keyCode: Int) {
        for (module in modules) {
            if (module.keybind == keyCode) module.toggle()
        }
    }
}
