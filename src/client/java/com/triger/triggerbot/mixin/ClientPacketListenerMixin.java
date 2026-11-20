package com.triger.triggerbot.mixin;

import com.triger.triggerbot.Watermark;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Watermark TPS: measures how many game ticks the server advances per real second.
 * TAIL only runs on the main thread (the network-thread call bails out early).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void dls$tps(ClientboundSetTimePacket packet, CallbackInfo ci) {
		Watermark.onTimePacket(packet.gameTime());
	}
}
