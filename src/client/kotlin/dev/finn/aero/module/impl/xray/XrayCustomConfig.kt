package dev.finn.aero.module.impl.xray

import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

/**
 * Persisted "Custom" mode block list. Kept as a small standalone object
 * (rather than folded into XrayModule) so [dev.finn.aero.config.ConfigManager]
 * can read/write it independently of module settings, under its own
 * top-level "xray" JSON key -- and so [dev.finn.aero.gui.XrayCustomScreen]
 * has one obvious place to mutate.
 */
object XrayCustomConfig {
    private val ids: MutableSet<String> = linkedSetOf()

    fun blockIds(): List<String> = ids.toList()

    fun setBlockIds(newIds: List<String>) {
        ids.clear()
        ids.addAll(newIds)
    }

    fun blocks(): Set<Block> =
        ids.mapNotNull { id -> Identifier.tryParse(id)?.let { Registries.BLOCK.get(it) } }.toSet()

    fun contains(block: Block): Boolean = Registries.BLOCK.getId(block).toString() in ids

    fun add(block: Block) {
        ids.add(Registries.BLOCK.getId(block).toString())
    }

    fun remove(block: Block) {
        ids.remove(Registries.BLOCK.getId(block).toString())
    }

    fun toggle(block: Block) {
        if (contains(block)) remove(block) else add(block)
    }

    fun resetToOres() {
        ids.clear()
        XrayPresets.ORES.forEach { add(it) }
    }

    fun clear() {
        ids.clear()
    }
}
