package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Visuals;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoRender fog: pushes the normal air fog (distance + weather/biome haze) far away.
 * Water / lava / powder snow fog is left alone.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void dls$noFog(FogData data, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker,
			CallbackInfo ci) {
		if (Visuals.noFog()) {
			data.environmentalStart = 1.0E6f;
			data.environmentalEnd = 1.0E6f + 1.0f;
			data.renderDistanceStart = 1.0E6f;
			data.renderDistanceEnd = 1.0E6f + 1.0f;
		}
	}
}
