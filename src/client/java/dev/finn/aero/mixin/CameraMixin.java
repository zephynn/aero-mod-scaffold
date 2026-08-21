package dev.finn.aero.mixin;

import dev.finn.aero.module.impl.FreecamState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
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
 *
 * Movement is integrated here, once per *render frame* using real elapsed
 * time, rather than in Freecam.kt's onTick() (20/s game ticks). Camera.update
 * runs at the uncapped render rate, so a tick-rate position update left the
 * camera frozen between ticks -- a visible stair-step snap. Frame-rate,
 * wall-clock-time movement is what makes vanilla spectator mode feel smooth,
 * and it's what this replicates.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    /** -1 means "no previous frame yet" -- avoids one huge jump on the first frame after enabling. */
    private long aero$lastFrameNanos = -1;

    @Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void aero$overridePosition(Level area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        Vec3 pos = FreecamState.INSTANCE.position;
        if (!FreecamState.INSTANCE.active || pos == null) {
            aero$lastFrameNanos = -1;
            return;
        }

        long now = System.nanoTime();
        double deltaSeconds = aero$lastFrameNanos < 0 ? 0.0 : (now - aero$lastFrameNanos) / 1_000_000_000.0;
        aero$lastFrameNanos = now;
        // Guard against huge jumps after a stutter/breakpoint/alt-tab.
        deltaSeconds = Math.min(deltaSeconds, 0.1);

        long window = Minecraft.getInstance().getWindow().handle();
        double forward = pressed(window, GLFW.GLFW_KEY_W) - pressed(window, GLFW.GLFW_KEY_S);
        double strafe = pressed(window, GLFW.GLFW_KEY_D) - pressed(window, GLFW.GLFW_KEY_A);
        double vertical = pressed(window, GLFW.GLFW_KEY_SPACE) - pressed(window, GLFW.GLFW_KEY_LEFT_SHIFT);

        if (forward != 0.0 || strafe != 0.0 || vertical != 0.0) {
            double yaw = Math.toRadians(this.yRot());
            double pitch = Math.toRadians(this.xRot());

            // Full 3D look-direction vector: holding W while looking up/down
            // moves the camera up/down too, not just horizontally.
            Vec3 lookForward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)
            );
            // Horizontal-only forward, used to derive strafe so A/D never
            // tilt the camera up or down.
            Vec3 horizontalForward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
            Vec3 right = horizontalForward.cross(new Vec3(0.0, 1.0, 0.0));

            Vec3 direction = lookForward.scale(forward).add(right.scale(strafe));
            if (direction.lengthSqr() > 0.0) direction = direction.normalize();
            direction = direction.add(0.0, vertical, 0.0);

            pos = pos.add(direction.multiply(FreecamState.INSTANCE.speed * deltaSeconds));
            FreecamState.INSTANCE.position = pos;
        }

        this.setPosition(pos);
    }

    private static double pressed(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS ? 1.0 : 0.0;
    }
}
