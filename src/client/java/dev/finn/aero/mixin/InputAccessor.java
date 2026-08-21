package dev.finn.aero.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * playerInput/movementVector are declared on Input, not KeyboardInput --
 * @Shadow only resolves members declared directly on the mixin's own
 * target class, so KeyboardInputMixin reaches these through this
 * accessor instead (any KeyboardInput instance is also an Input at
 * runtime, so the cast in KeyboardInputMixin is always valid).
 */
@Mixin(ClientInput.class)
public interface InputAccessor {
    @Accessor("keyPresses")
    void aero$setPlayerInput(Input input);

    @Accessor("moveVector")
    void aero$setMovementVector(Vec2 vector);
}
