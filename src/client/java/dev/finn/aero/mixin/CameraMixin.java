package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.FreecamState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides the render camera's position after vanilla has finished
 * pointing it at the player, whenever Freecam is active. The player entity
 * itself is never touched here -- see KeyboardInputMixin for how its
 * movement input gets cancelled so it doesn't drift on its own.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void aero$overridePosition(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        Vec3d freecamPos = FreecamState.INSTANCE.position;
        if (FreecamState.INSTANCE.active && freecamPos != null) {
            this.setPos(freecamPos);
        }
    }
}
