package com.triger.triggerbot.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeInvoker {
	/** Sends the selected hotbar slot to the server right now (SpearSwap: spear in hand right after the hit). */
	@Invoker("ensureHasSentCarriedItem")
	void dls$sendCarriedItem();
}
