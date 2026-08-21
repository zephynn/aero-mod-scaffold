package dev.finn.aero.module.impl.xray

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/**
 * Fixed block sets for X-Ray's "Ores" and "Base Finder" modes. Field names
 * pulled directly from Blocks.* (browsed via javap against the mapped
 * client jar rather than assumed) -- this build's registry names matched
 * vanilla-familiar expectations for every block here.
 */
object XrayPresets {
    val ORES: Set<Block> = setOf(
        Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
        Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
        Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
        Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
        Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
        Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
        Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
        Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
        Blocks.NETHER_QUARTZ_ORE,
        Blocks.NETHER_GOLD_ORE,
        Blocks.ANCIENT_DEBRIS,
    )

    val BASE_FINDER: Set<Block> = setOf(
        // Storage.
        Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL,
        // 26.x groups the 16 per-colour variants into a ColorCollection rather
        // than 16 separate Blocks.* constants -- asList() is the whole set.
        Blocks.SHULKER_BOX, *Blocks.DYED_SHULKER_BOX.asList().toTypedArray(),
        // Cooking / crafting stations.
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
        Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
        Blocks.BREWING_STAND, Blocks.SMITHING_TABLE, Blocks.STONECUTTER,
        Blocks.LOOM, Blocks.GRINDSTONE,
        // Beds.
        // Same ColorCollection grouping as shulker boxes.
        *Blocks.BED.asList().toTypedArray(),
        // Redstone / misc.
        Blocks.HOPPER, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.REDSTONE_WIRE,
        Blocks.REDSTONE_TORCH, Blocks.LEVER,
        // Villager workstations.
        Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.COMPOSTER, Blocks.LECTERN,
    )
}
