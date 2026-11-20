package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Movement;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AirStuck: while it is on, no movement / rotation packets are sent. Otherwise the server keeps getting
 * "in the air, not falling" every tick, which anti-cheats treat as flying and pull you back (rubber-band).
 * With no packets the server simply sees you standing where you froze.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerAirStuckMixin {
	@Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
	private void dls$airStuckNoPackets(CallbackInfo ci) {
		if (Movement.airStuck()) {
			ci.cancel();
		}
	}
}
