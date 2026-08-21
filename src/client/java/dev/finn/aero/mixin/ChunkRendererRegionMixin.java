package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.xray.XrayState;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Real terrain culling for X-Ray: this fires during chunk (re)meshing --
 * SectionBuilder.build(...) walks blocks in a ChunkRendererRegion (a
 * BlockRenderView snapshot of the section plus its neighbours) to build the
 * mesh, calling getBlockState(BlockPos) per block. Swapping the returned
 * state to air here means non-target blocks are never even meshed, not
 * just hidden by a fake box drawn over them -- an actual X-Ray, not an
 * overlay illusion.
 *
 * Only fires during meshing, not every frame -- so toggling X-Ray or
 * changing its target block set has zero effect until whoever flips
 * XrayState.active also forces a chunk rebuild (see XrayModule / XrayState).
 */
@Mixin(RenderSectionRegion.class)
public class ChunkRendererRegionMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void aero$xrayCull(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState real = cir.getReturnValue();
        // Only binary-cull to AIR when terrain opacity is fully zero -- any
        // partial opacity is instead handled by BlockModelRendererMixin
        // scaling per-quad vertex alpha, so the real block still meshes
        // normally and can be rendered translucent.
        if (XrayState.INSTANCE.isActive() && XrayState.INSTANCE.getTerrainOpacity() <= 0f && !XrayState.INSTANCE.isTarget(real)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }
}
