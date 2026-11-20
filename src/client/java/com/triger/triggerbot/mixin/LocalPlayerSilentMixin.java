package com.triger.triggerbot.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.triger.triggerbot.AimAssist;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * AimAssist silent: while sending the movement packet the player's rotation is swapped for the silent aim
 * rotation, then put back. The server sees you aiming at the target; your camera does not move.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSilentMixin {
	@WrapMethod(method = "sendPosition")
	private void dls$silentRotation(Operation<Void> original) {
		if (!AimAssist.silentActive()) {
			original.call();
			return;
		}

		LocalPlayer self = (LocalPlayer) (Object) this;
		float yaw = self.getYRot();
		float pitch = self.getXRot();

		try {
			self.setYRot(AimAssist.silentYaw());
			self.setXRot(AimAssist.silentPitch());
			original.call();
		} finally {
			self.setYRot(yaw);
			self.setXRot(pitch);
		}
	}
}
