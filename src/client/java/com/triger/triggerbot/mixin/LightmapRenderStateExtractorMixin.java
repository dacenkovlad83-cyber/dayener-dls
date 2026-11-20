package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Visuals;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright (white ambient light everywhere) and NoRender "bad effects" (no Darkness pulsing).
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void dls$lightmap(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (Visuals.fullbright()) {
			state.nightVisionEffectIntensity = 1.0f;
			state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
			state.brightness = 1.0f;
			state.darknessEffectScale = 0.0f;
		} else if (Visuals.noBadEffects()) {
			state.darknessEffectScale = 0.0f;
		}
	}
}
