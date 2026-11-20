package com.triger.triggerbot;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Read-only switches for the Visuals category, used by the render mixins.
 */
public final class Visuals {
	private Visuals() {
	}

	private static TriggerConfig cfg() {
		return TriggerBotClient.config();
	}

	public static boolean fullbright() {
		TriggerConfig c = cfg();
		return c != null && c.fullbright;
	}

	private static boolean noRender() {
		TriggerConfig c = cfg();
		return c != null && c.noRender;
	}

	public static boolean noFire() {
		return noRender() && cfg().noRenderFire;
	}

	public static boolean noHurtShake() {
		return noRender() && cfg().noRenderHurtShake;
	}

	public static boolean noHandShake() {
		return noRender() && cfg().noRenderHandShake;
	}

	public static boolean noBadEffects() {
		return noRender() && cfg().noRenderBadEffects;
	}

	public static boolean noInBlock() {
		return noRender() && cfg().noRenderInBlock;
	}

	public static boolean noTotemPop() {
		return noRender() && cfg().noRenderTotem;
	}

	public static boolean noFireworks() {
		return noRender() && cfg().noRenderFireworks;
	}

	public static boolean noRain() {
		return noRender() && cfg().noRenderRain;
	}

	public static boolean noSnow() {
		return noRender() && cfg().noRenderSnow;
	}

	public static boolean noFog() {
		return noRender() && cfg().noRenderFog;
	}

	/** Particles hidden by NoRender (totem, fireworks, water / lava splashes, rain drops). */
	public static boolean hideParticle(ParticleType<?> type) {
		if (!noRender()) {
			return false;
		}

		TriggerConfig c = cfg();

		if (c.noRenderTotem && type == ParticleTypes.TOTEM_OF_UNDYING) {
			return true;
		}

		if (c.noRenderCrits && (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT)) {
			return true;
		}

		if (c.noRenderFireworks && (type == ParticleTypes.FIREWORK || type == ParticleTypes.FLASH)) {
			return true;
		}

		if (c.noRenderWaterSplash && (type == ParticleTypes.SPLASH || type == ParticleTypes.BUBBLE
				|| type == ParticleTypes.BUBBLE_POP || type == ParticleTypes.FISHING
				|| type == ParticleTypes.DRIPPING_WATER || type == ParticleTypes.FALLING_WATER)) {
			return true;
		}

		if (c.noRenderLavaSplash && (type == ParticleTypes.LAVA || type == ParticleTypes.DRIPPING_LAVA
				|| type == ParticleTypes.FALLING_LAVA || type == ParticleTypes.LANDING_LAVA
				|| type == ParticleTypes.DRIPPING_DRIPSTONE_LAVA || type == ParticleTypes.FALLING_DRIPSTONE_LAVA)) {
			return true;
		}

		return c.noRenderRain && type == ParticleTypes.RAIN;
	}
}
