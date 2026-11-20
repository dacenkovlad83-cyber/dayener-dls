package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Movement;
import com.triger.triggerbot.Visuals;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.triger.triggerbot.TriggerBotClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NoRender "bad effects": the local player's Nausea wobble and Darkness pulsing are reported as 0.
 * Only the visual blend factor is changed — the effects themselves (and hasEffect checks) stay intact.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Shadow
	private int noJumpDelay;

	/** Player → NoJumpDelay: no 10-tick wait between jumps while holding space. */
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void dls$noJumpDelay(CallbackInfo ci) {
		if (TriggerBotClient.config() != null && TriggerBotClient.config().noJumpDelay
				&& (Object) this == Minecraft.getInstance().player) {
			this.noJumpDelay = 0;
		}
	}

	@Inject(method = "getEffectBlendFactor", at = @At("HEAD"), cancellable = true)
	private void dls$noBadEffectBlend(Holder<MobEffect> effect, float partialTick, CallbackInfoReturnable<Float> cir) {
		if (!Visuals.noBadEffects()) {
			return;
		}

		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}

		if (effect.equals(MobEffects.NAUSEA) || effect.equals(MobEffects.DARKNESS)) {
			cir.setReturnValue(0.0f);
		}
	}

	/** AirStuck: the local player does not move at all (no gravity, no input). */
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void dls$airStuck(Vec3 input, CallbackInfo ci) {
		if (Movement.airStuck() && (Object) this == Minecraft.getInstance().player) {
			((LivingEntity) (Object) this).setDeltaMovement(Vec3.ZERO);
			ci.cancel();
		}
	}
}
