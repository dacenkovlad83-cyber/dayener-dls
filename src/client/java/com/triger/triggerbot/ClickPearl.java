package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Player → ClickPearl: the key throws an ender pearl from anywhere in the inventory and puts the slot back
 * (same way as Helper: from the hotbar without stopping, from the inventory with a short stop on the ground).
 */
public final class ClickPearl {
	private static boolean wasDown;

	private ClickPearl() {
	}

	public static void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;
		boolean down = config.clickPearl && player != null && client.gameMode != null && client.gui.screen() == null
				&& !config.clickPearlKey.isEmpty()
				&& Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(config.clickPearlKey));

		if (down && !wasDown) {
			if (player.getCooldowns().isOnCooldown(new ItemStack(Items.ENDER_PEARL))) {
				player.sendOverlayMessage(Component.literal("ClickPearl: перка на КД").withStyle(ChatFormatting.GRAY));
			} else if (!Helper.use(client, player, Items.ENDER_PEARL) && !InvSync.busy()) {
				player.sendOverlayMessage(Component.literal("ClickPearl: нет эндер-жемчуга").withStyle(ChatFormatting.RED));
			}
		}

		wasDown = down;
	}
}
