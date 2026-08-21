package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.FreecamState;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeroes out the real WASD/space/sneak input while Freecam is active, so
 * the actual player entity stays put instead of drifting from held keys
 * while Freecam.kt moves the camera around independently.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void aero$cancelMovementWhileFreecam(CallbackInfo ci) {
        if (FreecamState.INSTANCE.active) {
            InputAccessor self = (InputAccessor) (Object) this;
            self.aero$setPlayerInput(Input.EMPTY);
            self.aero$setMovementVector(Vec2.ZERO);
        }
    }
}
