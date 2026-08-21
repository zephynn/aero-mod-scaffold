package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.FullbrightState;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * LightmapTextureManager.update() bakes the lightmap texture once per frame
 * by writing 7 floats + 2 vec3s into a UBO, consumed by lightmap.fsh. The
 * first float, AmbientLightFactor, is a per-frame constant (the current
 * dimension's ambientLight(), e.g. 0 in the Overworld) that the shader does
 * `mix(color, AmbientColor, AmbientLightFactor)` with -- forcing it to 1.0
 * replaces every texel with AmbientColor (white) uniformly, regardless of
 * actual block/sky light. That's a real, reliable fullbright for this
 * lighting model.
 *
 * The old approach -- overdriving the gamma option past 1.0 -- targets a
 * different UBO field (BrightnessFactor, 7th float) that only reshapes the
 * curve of light that's *already there*; a texel with zero actual light
 * multiplies out to zero no matter how high gamma goes, so it could never
 * have worked for this shader.
 */
@Mixin(LightTexture.class)
public abstract class LightmapTextureManagerMixin {

    @ModifyArg(
        method = "updateLightTexture(F)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
            ordinal = 0
        )
    )
    private float aero$forceAmbientLightFactor(float ambientLightFactor) {
        return FullbrightState.INSTANCE.active ? 1.0f : ambientLightFactor;
    }
}
