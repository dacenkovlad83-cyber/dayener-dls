package com.triger.triggerbot.mixin;

import com.triger.triggerbot.NoPush;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoPush blocks: you are not shoved out of a block you are standing inside of.
 */
@Mixin(LocalPlayer.class)
public abstract class NoPushBlocksMixin {
	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void dls$noBlockPush(double x, double z, CallbackInfo ci) {
		if (NoPush.blocks(this)) {
			ci.cancel();
		}
	}
}
