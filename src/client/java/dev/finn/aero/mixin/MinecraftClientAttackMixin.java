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
 * AutoAttributeSwap: swaps the selected hotbar slot for the exact duration
 * of MinecraftClient#doAttack() -- the private method that actually
 * performs a left-click attack -- rather than reacting to the attack
 * keypress from a tick loop. Criticals' first two attempts both learned
 * this the hard way: by the time a module's onTick() (which runs once per
 * *tick*, not per input poll) notices the attack was pressed, doAttack()
 * has frequently already run and returned for that same input. Injecting
 * directly into doAttack() itself brackets the swap deterministically
 * around the real attack call, every time, with no race.
 *
 * The slot switch goes out as a real UpdateSelectedSlotC2SPacket -- the
 * exact same packet vanilla sends when a player presses a number key or
 * scrolls the hotbar -- so the server is never told anything false about
 * what's held; it just genuinely sees the held item change, because it
 * genuinely did, in the same sequence a very fast manual player or an
 * OS-level macro could already produce.
 */
@Mixin(Minecraft.class)
public class MinecraftClientAttackMixin {
    @Unique
    private boolean aero$didSwap = false;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void aero$beforeAttack(CallbackInfoReturnable<Boolean> cir) {
        aero$didSwap = false;
        if (!AttributeSwapState.INSTANCE.getActive()) return;

        Minecraft client = (Minecraft) (Object) this;
        LocalPlayer player = client.player;
        if (player == null || client.getConnection() == null) return;

        int primary = AttributeSwapState.INSTANCE.getPrimarySlot();
        int secondary = AttributeSwapState.INSTANCE.getSecondarySlot();
        int current = player.getInventory().getSelectedSlot();

        if (AttributeSwapState.INSTANCE.getRequireHoldingPrimary() && current != primary) return;
        if (current == secondary) return;

        player.getInventory().setSelectedSlot(secondary);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(secondary));
        aero$didSwap = true;
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
