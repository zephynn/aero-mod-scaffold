package dev.finn.aero.module.impl.xray

import net.minecraft.util.math.Box
import net.minecraft.util.math.BlockPos

/** One group of nearby Base-Finder blocks, plus the individual positions that made it up. */
data class XrayCluster(val members: List<BlockPos>) {
    val box: Box by lazy {
        members.map { Box(it) }.reduce { a, b -> a.union(b) }
    }
    val center: net.minecraft.util.math.Vec3d get() = box.center
    val size: Int get() = members.size
}

/**
 * Simple single-pass greedy spatial grouping: each position joins the
 * first existing cluster that has a member within [radius] of it, or
 * starts a new cluster. Not as tight as a true union-find over all pairs,
 * but it's a pure function run only when the underlying block scan
 * re-runs (not every frame), and for base-finder-sized position counts the
 * difference is not visually meaningful.
 */
object XrayClustering {
    fun cluster(positions: List<BlockPos>, radius: Double, minClusterSize: Int): List<XrayCluster> {
        if (positions.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<BlockPos>>()
        val radiusSq = radius * radius

        for (pos in positions) {
            val target = clusters.firstOrNull { cluster ->
                cluster.any { member -> member.getSquaredDistance(pos) <= radiusSq }
            }
            if (target != null) {
                target.add(pos)
            } else {
                clusters.add(mutableListOf(pos))
            }
        }

        return clusters.filter { it.size >= minClusterSize }.map { XrayCluster(it) }
    }
}
