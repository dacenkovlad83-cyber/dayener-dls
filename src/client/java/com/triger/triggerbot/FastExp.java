package com.triger.triggerbot;

import com.triger.triggerbot.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

/**
 * Player → FastExp: throws experience bottles without the vanilla 4-tick delay while the use key is held.
 */
public final class FastExp {
	private FastExp() {
	}

	public static void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (!config.fastExp || player == null || client.gui.screen() != null || !client.options.keyUse.isDown()) {
			return;
		}

		if (!player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE) && !player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)) {
			return;
		}

		MinecraftAccessor accessor = (MinecraftAccessor) client;

		if (accessor.dls$getRightClickDelay() > config.fastExpDelay) {
			accessor.dls$setRightClickDelay(config.fastExpDelay);
		}
	}
}
