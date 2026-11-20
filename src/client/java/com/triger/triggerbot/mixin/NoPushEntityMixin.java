package com.triger.triggerbot.mixin;

import com.triger.triggerbot.NoPush;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NoPush: entity collisions do not push us, and (optionally) water currents do not move us.
 */
@Mixin(Entity.class)
public abstract class NoPushEntityMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void dls$noEntityPush(Entity other, CallbackInfo ci) {
		if (NoPush.entities((Entity) (Object) this, other)) {
			ci.cancel();
		}
	}

	@Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
	private void dls$noWaterPush(CallbackInfoReturnable<Boolean> cir) {
		if (NoPush.water(this)) {
			cir.setReturnValue(false);
		}
	}
}
