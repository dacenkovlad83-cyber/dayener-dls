package com.triger.triggerbot.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.triger.triggerbot.TriggerBotClient;
import com.triger.triggerbot.TriggerConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Visuals → CustomHands: which hands are drawn, and View Model offsets for the right and left hand.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	private static HumanoidArm dls$arm(AbstractClientPlayer player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void dls$hideHand(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
			ItemStack stack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		TriggerConfig c = TriggerBotClient.config();

		if (c == null || !c.customHands || c.handsMode == 0) {
			return;
		}

		HumanoidArm arm = dls$arm(player, hand);

		if ((c.handsMode == 1 && arm != HumanoidArm.RIGHT) || (c.handsMode == 2 && arm != HumanoidArm.LEFT)) {
			ci.cancel();
		}
	}

	@Inject(method = "submitArmWithItem",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
	private void dls$viewModel(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
			ItemStack stack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		TriggerConfig c = TriggerBotClient.config();

		if (c == null || !c.customHands) {
			return;
		}

		if (dls$arm(player, hand) == HumanoidArm.RIGHT) {
			poseStack.translate(c.rightHandX, c.rightHandY, c.rightHandZ);
			poseStack.scale(c.rightHandScale, c.rightHandScale, c.rightHandScale);
		} else {
			poseStack.translate(c.leftHandX, c.leftHandY, c.leftHandZ);
			poseStack.scale(c.leftHandScale, c.leftHandScale, c.leftHandScale);
		}
	}
}
