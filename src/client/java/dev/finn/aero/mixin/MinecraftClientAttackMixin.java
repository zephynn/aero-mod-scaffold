package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.AttributeSwapState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AutoAttributeSwap: swaps the selected hotbar slot around a weapon-
 * attribute swap technique (mace/breach swaps, sword-to-mace, etc.) by
 * injecting into MinecraftClient#startAttack() -- the private method that
 * actually performs a left-click attack -- rather than reacting from a
 * tick loop. Criticals' first two attempts both learned this the hard
 * way: by the time a module's onTick() (which runs once per *tick*, not
 * per input poll) notices the attack was pressed, startAttack() has
 * frequently already run and returned for that same input.
 *
 * The slot switch goes out as a real UpdateSelectedSlotC2SPacket -- the
 * exact same packet vanilla sends when a player presses a number key or
 * scrolls the hotbar -- so the server is never told anything false about
 * what's held. But a *real* packet sent with zero-variance, same-tick
 * timing is still exactly what timing/behavior-based anti-cheat looks
 * for: a slot-change packet immediately followed by an attack using that
 * item, every single time, is a well-known "swap macro" signature no
 * human reaction reproduces.
 *
 * So this doesn't swap-and-hit in the same click any more. The click
 * while holding Primary Slot swaps to Secondary and arms -- but that
 * click's own attack is cancelled outright rather than executing in the
 * same tick as the swap. The real attack still fires from that single
 * click, though: AutoAttributeSwap.onTick() auto-fires it a randomized
 * few ticks later via MinecraftStartAttackInvoker (re-invoking this same
 * startAttack(), which is why the HEAD injection below treats an armed
 * attack on Secondary as a real attack to let through rather than
 * swapping again). No second physical click required -- just a real
 * multi-tick gap between the swap packet and the attack packet instead
 * of them landing in the same tick. Disable "Delay First Hit" on
 * AutoAttributeSwap to fall back to the old same-click swap+hit.
 */
@Mixin(Minecraft.class)
public class MinecraftClientAttackMixin {
    @Unique
    private boolean aero$didSwap = false;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void aero$beforeAttack(CallbackInfoReturnable<Boolean> cir) {
        aero$didSwap = false;
        if (!AttributeSwapState.INSTANCE.getActive()) return;

        Minecraft client = (Minecraft) (Object) this;
        LocalPlayer player = client.player;
        if (player == null || client.getConnection() == null) return;

        int primary = AttributeSwapState.INSTANCE.getPrimarySlot();
        int secondary = AttributeSwapState.INSTANCE.getSecondarySlot();
        int current = player.getInventory().getSelectedSlot();

        if (current == secondary) {
            // Already holding the secondary weapon -- either armed from a
            // previous click, or the player just happened to be holding
            // it already. Let the real attack proceed untouched; if we're
            // the ones who armed this, the RETURN handler needs to know
            // to schedule a swap-back once it lands.
            if (AttributeSwapState.INSTANCE.getArmed()) {
                AttributeSwapState.INSTANCE.setArmed(false);
                aero$didSwap = true;
            }
            return;
        }

        if (AttributeSwapState.INSTANCE.getRequireHoldingPrimary() && current != primary) return;
        if (current != primary) return;

        player.getInventory().setSelectedSlot(secondary);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(secondary));

        if (AttributeSwapState.INSTANCE.getDelayFirstHit()) {
            // Arm and swallow this click's own attack -- AutoAttributeSwap
            // fires the real attack itself a few ticks later (via
            // MinecraftStartAttackInvoker), so this doesn't cost the
            // player an actual second click, but the packets a server
            // sees still land a genuine tick or two apart instead of the
            // same tick as the swap.
            AttributeSwapState.INSTANCE.setArmed(true);
            AttributeSwapState.INSTANCE.scheduleAutoAttack();
            cir.setReturnValue(Boolean.FALSE);
            cir.cancel();
        } else {
            aero$didSwap = true;
        }
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void aero$afterAttack(CallbackInfoReturnable<Boolean> cir) {
        boolean didSwap = aero$didSwap;
        aero$didSwap = false;
        if (!didSwap || !AttributeSwapState.INSTANCE.getSwapBack()) return;

        // Don't swap back inline here -- see AttributeSwapState's doc comment.
        // A randomized few-tick delay, applied from AutoAttributeSwap.onTick(),
        // replaces what would otherwise be a same-tick, zero-variance revert.
        AttributeSwapState.INSTANCE.scheduleSwapBack();
    }
}
