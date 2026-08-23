package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.FullbrightState;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright, 1.21.11-shape: LightTexture#getBrightness is the static
 * per-texel gamma curve that turns a light level into a brightness
 * fraction -- forcing it to 1.0f makes every texel maximally lit
 * regardless of actual light level, a real fullbright rather than a
 * gamma/brightness slider tweak (which can only reshape light that's
 * already there, never lift a zero-light texel).
 *
 * LightTexture#updateLightTexture(float) only actually rebuilds the GPU
 * texture when its private `updateLightTexture` dirty flag is set --
 * otherwise it's a no-op that reuses last frame's texture. Toggling
 * Fullbright doesn't set that flag on its own, so this forces it for as
 * long as Fullbright is on plus the first frame after it goes off (so the
 * texture actually gets rebuilt back to normal on toggle-off too).
 */
@Mixin(LightTexture.class)
public abstract class LightmapExtractorMixin {
    @Shadow
    private boolean updateLightTexture;

    @Unique
    private boolean aero$wasActive = false;

    @Inject(method = "updateLightTexture(F)V", at = @At("HEAD"))
    private void aero$forceRebuild(float partialTick, CallbackInfo ci) {
        boolean active = FullbrightState.INSTANCE.active;
        if (active || this.aero$wasActive) {
            this.updateLightTexture = true;
        }
        this.aero$wasActive = active;
    }

    @Inject(
        method = "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void aero$forceBrightness(DimensionType dimensionType, int light, CallbackInfoReturnable<Float> cir) {
        if (FullbrightState.INSTANCE.active) {
            cir.setReturnValue(1.0f);
        }
    }
}
