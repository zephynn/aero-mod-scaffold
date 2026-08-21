package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.FullbrightState;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright. 26.x moved the lightmap onto the render-state extraction
 * pipeline: LightmapRenderStateExtractor#extract fills a plain mutable
 * LightmapRenderState once per frame, and Lightmap#render later bakes that
 * into the UBO that lightmap.fsh reads. Overwriting the state right after
 * it's filled is both simpler and more robust than the old approach of
 * intercepting individual Std140Builder#putFloat calls by ordinal.
 *
 * The field to override is AmbientColor: in this shader the lit colour
 * starts as `max(AmbientColor, nightVision)` and everything else (sky,
 * block light) is *added* on top, so forcing it to white makes every texel
 * fully lit regardless of actual light level -- a real fullbright, not a
 * gamma/brightness curve tweak (BrightnessFactor only reshapes light that
 * is already there, so it can never lift a zero-light texel).
 *
 * needsUpdate is forced for as long as Fullbright is on plus the first
 * frame after it goes off, so the lightmap texture is actually re-baked
 * on toggle instead of keeping whatever was last uploaded.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {

    @org.spongepowered.asm.mixin.Unique
    private boolean aero$wasActive = false;

    @Inject(method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V", at = @At("TAIL"))
    private void aero$forceFullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        boolean active = FullbrightState.INSTANCE.active;
        if (active) {
            state.ambientColor = LightmapRenderStateExtractor.WHITE;
            state.needsUpdate = true;
        } else if (this.aero$wasActive) {
            state.needsUpdate = true;
        }
        this.aero$wasActive = active;
    }
}
