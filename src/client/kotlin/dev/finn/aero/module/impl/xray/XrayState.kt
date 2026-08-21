package dev.finn.aero.module.impl.xray

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.client.Minecraft

/**
 * Small Kotlin singleton bridge between [ChunkRendererRegionMixin] (Java,
 * can't see Kotlin module internals directly without a lot of interop
 * ceremony) and [XrayModule]. The mixin only ever reads [active] and calls
 * [isTarget] -- both cheap, since this runs inside the chunk mesh-building
 * hot path (still just a flag check + a Set.contains, not per-frame).
 */
object XrayState {
    @Volatile
    var active: Boolean = false

    /**
     * 0f = fully hidden (binary AIR-swap in [ChunkRendererRegionMixin]),
     * 1f = fully opaque/normal, anything in between drives a per-quad
     * alpha scale applied by the BlockModelRenderer mixin instead of the
     * AIR swap. Defaults to fully hidden to match the pre-existing binary
     * culling behaviour when nothing has set it yet.
     */
    @Volatile
    var terrainOpacity: Float = 0f

    @Volatile
    private var targets: Set<Block> = emptySet()

    fun isActive(): Boolean = active

    fun isTarget(state: BlockState): Boolean = state.block in targets

    /** Called by [XrayModule] whenever its resolved target block set changes. */
    fun setTargets(blocks: Set<Block>) {
        targets = blocks
    }

    /**
     * Forces every currently-loaded chunk to re-mesh so the mixin's
     * getBlockState swap actually takes visible effect. Verified via javap
     * against the 26.2 client jar: LevelRenderer's old no-arg `allChanged()`
     * is gone; `invalidateCompiledGeometry(level, options, camera, blockColors)`
     * is the replacement that discards and rebuilds every compiled section.
     */
    fun requestChunkReload() {
        val client = Minecraft.getInstance()
        client.execute {
            val level = client.level ?: return@execute
            client.levelRenderer.invalidateCompiledGeometry(
                level,
                client.options,
                client.gameRenderer.mainCamera(),
                client.blockColors,
            )
        }
    }
}
