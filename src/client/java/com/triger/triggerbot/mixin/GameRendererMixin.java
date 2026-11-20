package com.triger.triggerbot.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.triger.triggerbot.Movement;
import com.triger.triggerbot.Visuals;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoRender: hurt camera shake, and view bobbing of the hands only (camera bobbing stays as in options).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Unique
	private boolean dls$inHandRender;

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void dls$noHurtShake(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
		if (Visuals.noHurtShake()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void dls$handStart(CameraRenderState camera, float partialTick, Matrix4fc projection, CallbackInfo ci) {
		if (Movement.freecam()) {
			ci.cancel(); // no floating hands in freecam
			return;
		}

		this.dls$inHandRender = true;
	}

	@Inject(method = "renderItemInHand", at = @At("RETURN"))
	private void dls$handEnd(CameraRenderState camera, float partialTick, Matrix4fc projection, CallbackInfo ci) {
		this.dls$inHandRender = false;
	}

	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void dls$noHandShake(CameraRenderState camera, PoseStack poseStack, CallbackInfo ci) {
		if (this.dls$inHandRender && Visuals.noHandShake()) {
			ci.cancel();
		}
	}
}
