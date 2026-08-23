package dev.finn.aero.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private Minecraft#startAttack() so AutoAttributeSwap can
 * fire the real, delayed second half of an armed swap itself -- a few
 * ticks after the player's one physical click -- instead of requiring an
 * actual second click. See MinecraftClientAttackMixin's doc comment: this
 * re-enters that same mixin's HEAD injection, which already knows how to
 * handle an armed, already-holding-secondary attack correctly.
 */
@Mixin(Minecraft.class)
public interface MinecraftStartAttackInvoker {
    @Invoker("startAttack")
    boolean aero$startAttack();
}
