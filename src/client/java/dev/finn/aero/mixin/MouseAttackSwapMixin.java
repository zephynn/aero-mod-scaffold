package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.AttributeSwapState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AutoAttributeSwap, take two: this reacts to the raw left-mouse-button
 * GLFW event itself -- MouseHandler#onButton(), which fires the instant
 * the OS reports the press, before Minecraft's own per-frame click
 * processing (handleKeybinds() -> startAttack()) ever runs -- rather than
 * injecting into the attack method. That's the actual shape of "bind
 * attack to also switch slots": a real keybind reacting to the raw input
 * event, with vanilla's own unmodified attack pipeline left to run
 * completely on its own afterward. There's no attack-cancelling,
 * artificial re-firing, or randomized countdown involved any more -- the
 * previous version's swap-arm-delay-refire machinery is exactly the kind
 * of engineered, code-driven timing pattern that's itself a tell. This
 * version's timing is just "whatever's between MouseHandler seeing your
 * physical press and Minecraft's next frame seeing the click," which is
 * the same gap a real player (or an actual hardware/OS macro bound to the
 * mouse button) produces on its own, for free.
 *
 * Swap-back is symmetric: it happens on the button's release event, not
 * on any timer. Hold to attack with Secondary, let go to go back to
 * Primary -- exactly the shape of a held modifier key, and it means the
 * "how long was Secondary held" duration is just real human click-hold
 * time, not anything synthesized.
 */
@Mixin(MouseHandler.class)
public class MouseAttackSwapMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void aero$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (!AttributeSwapState.INSTANCE.getActive() || AttributeSwapState.INSTANCE.getLegacyMode()) return;
        if (info.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        // Only in actual gameplay -- not while a screen (inventory, chat,
        // pause menu, ...) is open and left-click means something else.
        if (player == null || client.getConnection() == null || client.gui.screen() != null) return;

        int primary = AttributeSwapState.INSTANCE.getPrimarySlot();
        int secondary = AttributeSwapState.INSTANCE.getSecondarySlot();
        int current = player.getInventory().getSelectedSlot();

        if (action == GLFW.GLFW_PRESS) {
            if (AttributeSwapState.INSTANCE.getRequireHoldingPrimary() && current != primary) return;
            if (current != primary) return;

            player.getInventory().setSelectedSlot(secondary);
            client.getConnection().send(new ServerboundSetCarriedItemPacket(secondary));
            AttributeSwapState.INSTANCE.setWeSwapped(true);
        } else if (action == GLFW.GLFW_RELEASE) {
            if (!AttributeSwapState.INSTANCE.getWeSwapped()) return;
            AttributeSwapState.INSTANCE.setWeSwapped(false);
            if (!AttributeSwapState.INSTANCE.getSwapBack()) return;
            if (current != secondary) return;

            player.getInventory().setSelectedSlot(primary);
            client.getConnection().send(new ServerboundSetCarriedItemPacket(primary));
        }
    }
}
