package dev.finn.aero.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.finn.aero.module.impl.xray.AlphaScalingVertexConsumer;
import dev.finn.aero.module.impl.xray.XrayState;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.world.level.block.state.BlockState;
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
@Mixin(ModelBlockRenderer.class)
public class BlockModelRendererMixin {
    @ModifyVariable(
        method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private VertexConsumer aero$wrapForOpacity(
        VertexConsumer consumer,
        net.minecraft.world.level.BlockAndTintGetter world,
        java.util.List<net.minecraft.client.renderer.block.model.BlockModelPart> parts,
        BlockState state,
        net.minecraft.core.BlockPos pos,
        com.mojang.blaze3d.vertex.PoseStack matrices,
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
