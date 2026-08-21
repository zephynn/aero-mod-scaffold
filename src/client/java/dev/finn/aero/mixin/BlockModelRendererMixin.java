package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.xray.AlphaScalingVertexConsumer;
import dev.finn.aero.module.impl.xray.XrayState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Real (continuously adjustable) terrain opacity for X-Ray. Found via
 * javap against the mapped client jar: BlockModelRenderer#render(...) is
 * called once per block during chunk meshing with the real BlockState and
 * the VertexConsumer the resulting quads get written into -- both already
 * parameters, so this only needs to swap that single VertexConsumer
 * argument for a decorating one when the block is a non-target block and
 * X-Ray's terrainOpacity is strictly between 0 and 1 (0 stays the existing
 * binary AIR-swap in ChunkRendererRegionMixin -- cheaper, and this method
 * never even runs for those blocks; 1 is fully opaque, so skip wrapping
 * entirely to avoid any overhead on the common case).
 */
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
    @ModifyVariable(
        method = "render(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private VertexConsumer aero$wrapForOpacity(
        VertexConsumer consumer,
        net.minecraft.world.BlockRenderView world,
        java.util.List<net.minecraft.client.render.model.BlockModelPart> parts,
        BlockState state,
        net.minecraft.util.math.BlockPos pos,
        net.minecraft.client.util.math.MatrixStack matrices,
        VertexConsumer originalConsumer,
        boolean cull,
        int overlay
    ) {
        float opacity = XrayState.INSTANCE.getTerrainOpacity();
        if (XrayState.INSTANCE.isActive() && opacity > 0f && opacity < 1f && !XrayState.INSTANCE.isTarget(state)) {
            return new AlphaScalingVertexConsumer(consumer, opacity);
        }
        return consumer;
    }
}
