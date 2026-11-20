package com.triger.triggerbot.mixin;

import com.triger.triggerbot.LockSlot;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LockSlot: Q does nothing on a locked hotbar slot.
 */
@Mixin(LocalPlayer.class)
public abstract class LockSlotDropMixin {
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void dls$lockedDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
		if (LockSlot.blocksDrop((LocalPlayer) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
