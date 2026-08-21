package dev.finn.aero.module.impl.xray

import net.minecraft.block.Block
import net.minecraft.block.Blocks

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
        Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX,
        Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX,
        Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX,
        Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX,
        Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX,
        Blocks.BLACK_SHULKER_BOX, Blocks.SHULKER_BOX,
        // Cooking / crafting stations.
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
        Blocks.CRAFTING_TABLE, Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
        Blocks.BREWING_STAND, Blocks.SMITHING_TABLE, Blocks.STONECUTTER,
        Blocks.LOOM, Blocks.GRINDSTONE,
        // Beds.
        Blocks.WHITE_BED, Blocks.ORANGE_BED, Blocks.MAGENTA_BED, Blocks.LIGHT_BLUE_BED,
        Blocks.YELLOW_BED, Blocks.LIME_BED, Blocks.PINK_BED, Blocks.GRAY_BED,
        Blocks.LIGHT_GRAY_BED, Blocks.CYAN_BED, Blocks.PURPLE_BED, Blocks.BLUE_BED,
        Blocks.BROWN_BED, Blocks.GREEN_BED, Blocks.RED_BED, Blocks.BLACK_BED,
        // Redstone / misc.
        Blocks.HOPPER, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.REDSTONE_WIRE,
        Blocks.REDSTONE_TORCH, Blocks.LEVER,
        // Villager workstations.
        Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.COMPOSTER, Blocks.LECTERN,
    )
}
