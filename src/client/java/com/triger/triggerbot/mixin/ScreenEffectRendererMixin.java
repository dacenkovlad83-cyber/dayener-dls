package com.triger.triggerbot.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.triger.triggerbot.Visuals;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoRender: fire overlay and the block texture drawn over the screen while the camera is inside a block.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
	private static void dls$noFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		if (Visuals.noFire()) {
			ci.cancel();
		}
	}

	@Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
	private static void dls$noInBlock(TextureAtlasSprite sprite, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (Visuals.noInBlock()) {
			ci.cancel();
		}
	}
}
