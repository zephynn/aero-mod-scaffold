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
 * AutoAttributeSwap's original "Legacy" mechanism: swaps the selected
 * hotbar slot for the exact duration of Minecraft#startAttack() itself,
 * swap-in at HEAD and swap-back at RETURN, both instant. Kept available
 * as an explicit opt-in (AttributeSwapState.legacyMode) alongside the
 * newer MouseAttackSwapMixin default -- this mode is deliberately the
 * old same-tick swap-then-attack-then-swap-back shape, which is faster
 * and simpler but was the specific pattern that got the New mode built
 * in the first place (see MouseAttackSwapMixin's doc comment). Only
 * fires when legacyMode is on; the two mixins are mutually exclusive via
 * that flag, never both acting on the same click.
 */
@Mixin(Minecraft.class)
public class MinecraftClientAttackMixin {
    @Unique
    private boolean aero$didSwap = false;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void aero$beforeAttack(CallbackInfoReturnable<Boolean> cir) {
        aero$didSwap = false;
        if (!AttributeSwapState.INSTANCE.getActive() || !AttributeSwapState.INSTANCE.getLegacyMode()) return;

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
        if (!aero$didSwap || !AttributeSwapState.INSTANCE.getSwapBack()) {
            aero$didSwap = false;
            return;
        }
        aero$didSwap = false;

        Minecraft client = (Minecraft) (Object) this;
        LocalPlayer player = client.player;
        if (player == null || client.getConnection() == null) return;

        int primary = AttributeSwapState.INSTANCE.getPrimarySlot();
        player.getInventory().setSelectedSlot(primary);
        client.getConnection().send(new ServerboundSetCarriedItemPacket(primary));
    }
}
