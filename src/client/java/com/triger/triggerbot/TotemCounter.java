package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Visuals → TotemCounter: totem icon with the number of totems left (inventory + off hand),
 * next to the crosshair or above the hotbar.
 */
public final class TotemCounter {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "totem_counter");
	/** Created on first draw: item components are not bound yet while the mod initialises. */
	private static ItemStack icon;
	public static final String[] POSITIONS = {"Возле прицела", "Над хотбаром"};

	private TotemCounter() {
	}

	public static void register(TriggerConfig config) {
		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			if (config.totemCounter) {
				render(graphics, Minecraft.getInstance(), config);
			}
		});
	}

	public static int count(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		int total = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			if (stack.is(Items.TOTEM_OF_UNDYING)) {
				total += stack.getCount();
			}
		}

		return total;
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (player == null) {
			return;
		}

		Font font = client.font;
		int count = count(player);
		String text = String.valueOf(count);
		int color = count == 0 ? HudStyle.BAD : count <= 2 ? 0xFFFFD25A : HudStyle.TEXT;

		int pad = 4;
		int w = pad + 16 + 3 + font.width(text) + pad + 1;
		int h = 16 + 2 * pad;
		float k = config.tcScale;
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int sw0 = Math.round(w * k);
		int sh0 = Math.round(h * k);

		int x;
		int y;

		if (config.tcPosition == 0) {
			// Right below and to the right of the crosshair.
			x = sw / 2 + 8;
			y = sh / 2 + 6;
		} else {
			// Centred above the hearts / hunger row.
			x = sw / 2 - sw0 / 2;
			y = sh - 52 - sh0;
		}

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(k);

		HudStyle.panel(graphics, 0, 0, w, h, h / 2); // pill
		if (icon == null) {
			icon = new ItemStack(Items.TOTEM_OF_UNDYING);
		}

		graphics.item(icon, pad, pad);
		graphics.text(font, text, pad + 16 + 3, pad + 4, color, false);

		pose.popMatrix();
	}
}
