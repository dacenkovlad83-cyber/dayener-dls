package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper → HUD: a row of round chips, one per bound Helper item — the item icon, how many you have and the
 * key. A chip is dimmed when you have none of that item.
 */
public final class HelperHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "helper_hud");
	private static final int CHIP_H = 16;
	private static final int GAP = 4;
	private static final Map<Item, ItemStack> ICONS = new HashMap<>();

	private static HudDrag drag;

	private HelperHud() {
	}

	private record Chip(ItemStack icon, String key, int count) {
	}

	public static void register(TriggerConfig config) {
		drag = new HudDrag(() -> config.helperHud, () -> config.hhX, () -> config.hhY,
				v -> config.hhX = v, v -> config.hhY = v, () -> config.hhScale);
		drag.register(config);

		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			if (config.helperHud) {
				render(graphics, Minecraft.getInstance(), config);
			}
		});
	}

	public static void resetPosition() {
		drag.reset();
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (player == null) {
			return;
		}

		List<Chip> chips = new ArrayList<>();

		for (Helper.Entry entry : Helper.allEntries()) {
			String key = config.helperKeys.get(entry.id());

			if (key == null || key.isEmpty()) {
				continue;
			}

			ItemStack icon = ICONS.computeIfAbsent(entry.item(), ItemStack::new);
			chips.add(new Chip(icon, InputConstants.getKey(key).getDisplayName().getString().toUpperCase(),
					Helper.count(player, entry.item())));
		}

		if (chips.isEmpty() && !(client.gui.screen() instanceof ChatScreen)) {
			return;
		}

		Font font = client.font;
		List<Integer> widths = new ArrayList<>();
		int total = 0;

		for (Chip chip : chips) {
			String count = chip.count() > 1 ? "x" + chip.count() : "";
			int w = 4 + 12 + 3 + (count.isEmpty() ? 0 : font.width(count) + 3) + font.width(chip.key()) + 6;
			widths.add(w);
			total += w + GAP;
		}

		int width = chips.isEmpty() ? font.width("Helper HUD") + 12 : total - GAP;
		drag.width = width;
		drag.height = CHIP_H;
		drag.place(graphics, graphics.guiWidth() / 2 - Math.round(width * drag.scale()) / 2, graphics.guiHeight() - 80);
		drag.begin(graphics);

		if (chips.isEmpty()) {
			HudStyle.panel(graphics, 0, 0, width, CHIP_H, CHIP_H / 2);
			graphics.text(font, "Helper HUD", 6, 4, HudStyle.TEXT_DIM, false);
		}

		int x = 0;

		for (int i = 0; i < chips.size(); i++) {
			Chip chip = chips.get(i);
			int w = widths.get(i);
			boolean have = chip.count() > 0;

			HudStyle.panel(graphics, x, 0, w, CHIP_H, CHIP_H / 2);

			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(x + 4, 2);
			pose.scale(0.75f);
			graphics.item(chip.icon(), 0, 0);
			pose.popMatrix();

			int tx = x + 4 + 12 + 3;

			if (chip.count() > 1) {
				String count = "x" + chip.count();
				graphics.text(font, count, tx, 4, HudStyle.TEXT_DIM, false);
				tx += font.width(count) + 3;
			}

			graphics.text(font, chip.key(), tx, 4, have ? HudStyle.TEXT : 0xFF6A6A6A, false);
			x += w + GAP;
		}

		drag.end(graphics);
	}
}
