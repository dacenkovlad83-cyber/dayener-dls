package com.triger.triggerbot.mixin;

import com.triger.triggerbot.FreeLook;
import com.triger.triggerbot.Movement;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: the camera uses its own position and rotation instead of following the player.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	protected abstract void setPosition(Vec3 pos);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("HEAD"), cancellable = true)
	private void dls$freecam(float partialTick, CallbackInfo ci) {
		if (Movement.freecam()) {
			this.setRotation(Movement.camYaw(), Movement.camPitch());
			this.setPosition(Movement.camPos(partialTick));
			this.detached = true; // render our own player body
			ci.cancel();
		}
	}

	/** FreeLook: the camera uses its own angles, the player keeps its rotation. */
	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float dls$freeLookYaw(Entity entity, float partialTick) {
		return FreeLook.active() && entity == Minecraft.getInstance().player ? FreeLook.yaw() : entity.getViewYRot(partialTick);
	}

	@Redirect(method = "alignWithEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float dls$freeLookPitch(Entity entity, float partialTick) {
		return FreeLook.active() && entity == Minecraft.getInstance().player ? FreeLook.pitch() : entity.getViewXRot(partialTick);
	}
}
