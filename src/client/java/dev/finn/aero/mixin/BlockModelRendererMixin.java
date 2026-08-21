package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.xray.AlphaScalingQuadOutput;
import dev.finn.aero.module.impl.xray.XrayState;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Real (continuously adjustable) terrain opacity for X-Ray. Found via
 * javap against the 26.2 client jar: ModelBlockRenderer#tesselateBlock is
 * called once per block during chunk meshing with the real BlockState and
 * the BlockQuadOutput the resulting quads get written into -- both already
 * parameters, so this only needs to swap that single output argument for a
 * decorating one when the block is a non-target block and X-Ray's
 * terrainOpacity is strictly between 0 and 1 (0 stays the existing binary
 * AIR-swap in ChunkRendererRegionMixin -- cheaper, and this method never
 * even runs for those blocks; 1 is fully opaque, so skip wrapping entirely
 * to avoid any overhead on the common case).
 *
 * 26.x replaced the old per-vertex VertexConsumer parameter with the
 * per-quad BlockQuadOutput, so the decorator now edits QuadInstance corner
 * colours rather than intercepting colour() calls.
 */
@Mixin(ModelBlockRenderer.class)
public class BlockModelRendererMixin {
    @ModifyVariable(
        method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1
    )
    private BlockQuadOutput aero$wrapForOpacity(
        BlockQuadOutput output,
        BlockQuadOutput unusedOutput,
        float x,
        float y,
        float z,
        net.minecraft.client.renderer.block.BlockAndTintGetter world,
        net.minecraft.core.BlockPos pos,
        BlockState state,
        net.minecraft.client.renderer.block.dispatch.BlockStateModel model,
        long seed
    ) {
        float opacity = XrayState.INSTANCE.getTerrainOpacity();
        if (XrayState.INSTANCE.isActive() && opacity > 0f && opacity < 1f && !XrayState.INSTANCE.isTarget(state)) {
            return new AlphaScalingQuadOutput(output, opacity);
        }
        return output;
    }
}
