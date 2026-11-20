package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Visuals;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NoRender "bad effects": no Blindness / Darkness fog.
 */
@Mixin(MobEffectFogEnvironment.class)
public abstract class MobEffectFogEnvironmentMixin {
	@Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
	private void dls$noEffectFog(FogType fogType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Visuals.noBadEffects()) {
			cir.setReturnValue(false);
		}
	}
}
