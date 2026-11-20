package com.triger.triggerbot.mixin;

import com.mojang.authlib.GameProfile;
import com.triger.triggerbot.AltManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Alt Manager: while an offline nick is active the game logs in with it. Chat signing keys of the
 * launcher account are not sent, because they would not match the offline nick.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "getUser", at = @At("HEAD"), cancellable = true)
	private void dls$altUser(CallbackInfoReturnable<User> cir) {
		User alt = AltManager.current();

		if (alt != null) {
			cir.setReturnValue(alt);
		}
	}

	@Inject(method = "getGameProfile", at = @At("HEAD"), cancellable = true)
	private void dls$altProfile(CallbackInfoReturnable<GameProfile> cir) {
		GameProfile alt = AltManager.currentProfile();

		if (alt != null) {
			cir.setReturnValue(alt);
		}
	}

	@Inject(method = "getProfileKeyPairManager", at = @At("HEAD"), cancellable = true)
	private void dls$altKeys(CallbackInfoReturnable<ProfileKeyPairManager> cir) {
		if (AltManager.current() != null) {
			cir.setReturnValue(ProfileKeyPairManager.EMPTY_KEY_MANAGER);
		}
	}
}
