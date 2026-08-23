package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.AutoEatState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft#handleKeybinds() has one call site that reads roughly:
 * `if (player.isUsingItem() && !options.keyUse.isDown()) gameMode.releaseUsingItem(player);`
 * -- a real player's item-use only continues while the key stays
 * physically held. AutoEat triggers an eat with a single useItem() call
 * and then never touches the key at all, so without this redirect that
 * check would cancel the eat on the very next frame. Redirecting the
 * call lets AutoEatState decide, per frame, whether this one release
 * should actually go through -- everywhere else that isn't mid-AutoEat,
 * this behaves completely unchanged.
 */
@Mixin(Minecraft.class)
public class SuppressUseItemReleaseMixin {
    @Redirect(
        method = "handleKeybinds",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V")
    )
    private void aero$maybeSuppressRelease(MultiPlayerGameMode gameMode, Player player) {
        if (AutoEatState.INSTANCE.getSuppressRelease()) return;
        gameMode.releaseUsingItem(player);
    }
}
