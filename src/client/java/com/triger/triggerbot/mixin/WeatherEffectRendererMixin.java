package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Visuals;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoRender rain / snow: the weather columns are dropped from the render state.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void dls$noWeather(ClientLevel level, float partialTick, Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
		if (Visuals.noRain()) {
			state.rainColumns.clear();
		}

		if (Visuals.noSnow()) {
			state.snowColumns.clear();
		}
	}
}
