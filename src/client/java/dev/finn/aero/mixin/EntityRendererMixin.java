package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.esp.EspHighlightState;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EntityRenderer.getAndUpdateRenderState() is the one place every entity's
 * EntityRenderState comes from, after every renderer subclass (living,
 * player, etc.) has already filled in its own fields -- including
 * outlineColor, normally driven by the vanilla Glowing effect or a
 * scoreboard team. Overwriting it here, after the fact, is what lets ESP
 * modules force the same real, through-walls, entity-shaped outline
 * vanilla uses for spectral arrows, without needing a separate mixin per
 * entity renderer subclass.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "getAndUpdateRenderState", at = @At("RETURN"))
    private void aero$applyEspOutline(Entity entity, float tickDelta, CallbackInfoReturnable<EntityRenderState> cir) {
        Integer color = EspHighlightState.colorFor(entity);
        if (color != null) {
            cir.getReturnValue().outlineColor = color;
        }
    }
}
