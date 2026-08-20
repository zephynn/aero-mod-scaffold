package dev.finn.aero.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * playerInput/movementVector are declared on Input, not KeyboardInput --
 * @Shadow only resolves members declared directly on the mixin's own
 * target class, so KeyboardInputMixin reaches these through this
 * accessor instead (any KeyboardInput instance is also an Input at
 * runtime, so the cast in KeyboardInputMixin is always valid).
 */
@Mixin(Input.class)
public interface InputAccessor {
    @Accessor("playerInput")
    void aero$setPlayerInput(PlayerInput input);

    @Accessor("movementVector")
    void aero$setMovementVector(Vec2f vector);
}
