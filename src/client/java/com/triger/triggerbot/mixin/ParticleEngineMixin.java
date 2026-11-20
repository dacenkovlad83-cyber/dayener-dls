package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Visuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.item.component.FireworkExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * NoRender particles. Filtered where the world asks for a particle (ClientLevel), not inside the particle
 * engine: some vanilla code (firework sparks) uses the engine's result directly and crashes on null.
 */
@Mixin(ClientLevel.class)
public abstract class ParticleEngineMixin {
	/** Every addParticle / addAlwaysVisibleParticle variant ends here. */
	@Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
	private void dls$hideParticle(ParticleOptions options, boolean overrideLimiter, boolean alwaysShow, double x, double y,
			double z, double dx, double dy, double dz, CallbackInfo ci) {
		if (Visuals.hideParticle(options.getType())) {
			ci.cancel();
		}
	}

	/** Firework explosion (sparks, flash, crackle) is skipped as a whole. */
	@Inject(method = "createFireworks", at = @At("HEAD"), cancellable = true)
	private void dls$noFireworks(double x, double y, double z, double dx, double dy, double dz,
			List<FireworkExplosion> explosions, CallbackInfo ci) {
		if (Visuals.noFireworks()) {
			ci.cancel();
		}
	}
}
