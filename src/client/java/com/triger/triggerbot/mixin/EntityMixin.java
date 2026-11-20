package com.triger.triggerbot.mixin;

import com.triger.triggerbot.FreeLook;
import com.triger.triggerbot.Movement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: mouse movement turns the free camera, not the player.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void dls$freecamTurn(double dx, double dy, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}

		if (Movement.freecam()) {
			Movement.turn(dx, dy);
			ci.cancel();
		} else if (FreeLook.active()) {
			FreeLook.turn(dx, dy);
			ci.cancel();
		}
	}
}
