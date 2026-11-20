package com.triger.triggerbot.mixin;

import com.triger.triggerbot.LockSlot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LockSlot: clicks on locked hotbar slots (and number-key swaps into them) are ignored in inventories.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class LockSlotClickMixin {
	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void dls$lockedClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
		if (LockSlot.blocksClick(slot, button, input)) {
			ci.cancel();
		}
	}
}
