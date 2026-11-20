package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Visuals;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoRender "totem pop": no big totem animation over the screen.
 */
@Mixin(GameRenderer.class)
public abstract class NoRenderTotemMixin {
	@Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
	private void dls$noTotemPop(ItemStack stack, CallbackInfo ci) {
		if (Visuals.noTotemPop()) {
			ci.cancel();
		}
	}
}
