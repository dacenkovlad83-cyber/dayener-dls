package com.triger.triggerbot.mixin;

import com.triger.triggerbot.NoPush;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoPush fishing rod: the pull velocity sent while someone's hook is stuck in you is ignored.
 */
@Mixin(ClientPacketListener.class)
public abstract class NoPushRodMixin {
	@Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
	private void dls$noRodPull(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		if (NoPush.rodPull(packet.id())) {
			ci.cancel();
		}
	}
}
