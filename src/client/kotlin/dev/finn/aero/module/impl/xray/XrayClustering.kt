package dev.finn.aero.module.impl.xray

import net.minecraft.world.phys.AABB
import net.minecraft.core.BlockPos

/** One group of nearby Base-Finder blocks, plus the individual positions that made it up. */
data class XrayCluster(val members: List<BlockPos>) {
    val box: AABB by lazy {
        members.map { AABB(it) }.reduce { a, b -> a.minmax(b) }
    }
    val center: net.minecraft.world.phys.Vec3 get() = box.center
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
                cluster.any { member -> member.distSqr(pos) <= radiusSq }
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
