package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface → ArmorHUD: your own armor (and optionally the items in both hands) with durability in percent.
 * Horizontal or vertical layout. While the chat is open it can be dragged with the left mouse button.
 */
public final class ArmorHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "armor_hud");
	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final int BG = 0xC80D0F16;
	private static final int BORDER = 0x60343A52;
	private static final int TEXT_DIM = 0xFF9AA0B0;
	private static final int PAD = 4;
	private static final int ICON = 16;

	private static boolean dragging;
	private static double grabX;
	private static double grabY;
	private static int width = 40;
	private static int height = 30;
	/** Where the HUD was actually drawn last frame (default position resolves at render time). */
	private static int drawX;
	private static int drawY;

	private ArmorHud() {
	}

	public static void register(TriggerConfig config) {
		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			if (config.armorHud) {
				render(graphics, Minecraft.getInstance(), config);
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}

			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (!config.armorHud || event.button() != 0 || !contains(config, event.x(), event.y())) {
					return true;
				}

				dragging = true;
				grabX = event.x() - drawX;
				grabY = event.y() - drawY;
				return false;
			});

			ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> {
				if (!dragging) {
					return true;
				}

				int w0 = Math.round(width * config.ahScale);
				int h0 = Math.round(height * config.ahScale);
				config.ahX = (int) Math.round(Math.clamp(event.x() - grabX, 0, Math.max(0, s.width - w0)));
				config.ahY = (int) Math.round(Math.clamp(event.y() - grabY, 0, Math.max(0, s.height - h0)));
				return false;
			});

			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
				if (!dragging) {
					return true;
				}

				dragging = false;
				config.save();
				return false;
			});
		});
	}

	/** Puts the HUD back to its default spot above the hotbar. */
	public static void resetPosition(TriggerConfig config) {
		config.ahX = -1;
		config.ahY = -1;
	}

	private static boolean contains(TriggerConfig config, double x, double y) {
		float k = config.ahScale;
		return x >= drawX && x < drawX + width * k && y >= drawY && y < drawY + height * k;
	}

	private static List<ItemStack> collect(TriggerConfig config, Player player) {
		List<ItemStack> items = new ArrayList<>();

		if (config.ahHands && !player.getMainHandItem().isEmpty()) {
			items.add(player.getMainHandItem());
		}

		for (EquipmentSlot slot : ARMOR) {
			ItemStack stack = player.getItemBySlot(slot);

			if (!stack.isEmpty()) {
				items.add(stack);
			}
		}

		if (config.ahHands && !player.getOffhandItem().isEmpty()) {
			items.add(player.getOffhandItem());
		}

		return items;
	}

	/** Durability text; empty for items that do not wear out. */
	private static String label(TriggerConfig config, ItemStack stack) {
		if (stack.isDamageableItem() && stack.getMaxDamage() > 0) {
			int left = stack.getMaxDamage() - stack.getDamageValue();

			if (config.ahPercent) {
				return Math.round(left * 100.0f / stack.getMaxDamage()) + "%";
			}

			return String.valueOf(left);
		}

		return ""; // stack size is already drawn by the vanilla item decorations
	}

	/** Green at full durability, through yellow, to red when almost broken. */
	private static int color(ItemStack stack) {
		if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return TEXT_DIM;
		}

		float frac = Math.clamp(1.0f - stack.getDamageValue() / (float) stack.getMaxDamage(), 0.0f, 1.0f);
		int rgb = Mth.hsvToArgb(frac / 3.0f, 0.75f, 1.0f, 0xFF);

		// Blink when the piece is about to break.
		if (frac <= 0.1f && (System.currentTimeMillis() / 400L) % 2L == 0L) {
			return 0xFFFFFFFF;
		}

		return rgb;
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config) {
		Player player = client.player;

		if (player == null) {
			return;
		}

		Font font = client.font;
		List<ItemStack> items = collect(config, player);
		boolean chatOpen = client.gui.screen() instanceof ChatScreen;

		if (items.isEmpty() && !chatOpen) {
			return;
		}

		List<String> labels = new ArrayList<>(items.size());
		int maxLabel = 0;

		for (ItemStack stack : items) {
			String text = label(config, stack);
			labels.add(text);
			maxLabel = Math.max(maxLabel, font.width(text));
		}

		int cellW;
		int cellH;

		if (items.isEmpty()) {
			width = font.width("ArmorHUD") + 2 * PAD + 4;
			height = 18;
		} else if (config.ahVertical) {
			cellW = ICON + (maxLabel > 0 ? 3 + maxLabel : 0);
			cellH = ICON + 2;
			width = cellW + 2 * PAD;
			height = items.size() * cellH - 2 + 2 * PAD;
		} else {
			cellW = Math.max(ICON + 2, maxLabel + 3);
			cellH = ICON + (maxLabel > 0 ? 1 + font.lineHeight : 0);
			width = items.size() * cellW + 2 * PAD - 2;
			height = cellH + 2 * PAD;
		}

		// Default position: centred above the hotbar, health and hunger.
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int scaledW = Math.round(width * config.ahScale);
		int scaledH = Math.round(height * config.ahScale);

		if (config.ahX < 0 || config.ahY < 0) {
			drawX = sw / 2 - scaledW / 2;
			drawY = sh - 62 - scaledH;
		} else {
			drawX = Math.clamp(config.ahX, 0, Math.max(0, sw - scaledW));
			drawY = Math.clamp(config.ahY, 0, Math.max(0, sh - scaledH));
		}

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(drawX, drawY);
		pose.scale(config.ahScale);

		if (config.ahBackground || items.isEmpty()) {
			HudStyle.panel(graphics, 0, 0, width, height, Math.min(HudStyle.RADIUS, height / 2));
		}

		if (items.isEmpty()) {
			graphics.text(font, "ArmorHUD", PAD + 2, 5, TEXT_DIM, false);
		} else if (config.ahVertical) {
			int y = PAD;

			for (int i = 0; i < items.size(); i++) {
				ItemStack stack = items.get(i);
				graphics.item(stack, PAD, y);
				graphics.itemDecorations(font, stack, PAD, y);

				if (!labels.get(i).isEmpty()) {
					graphics.text(font, labels.get(i), PAD + ICON + 3, y + 4, color(stack), true);
				}

				y += ICON + 2;
			}
		} else {
			int cell = Math.max(ICON + 2, maxLabel + 3);
			int x = PAD;

			for (int i = 0; i < items.size(); i++) {
				ItemStack stack = items.get(i);
				int iconX = x + (cell - 2 - ICON) / 2;
				graphics.item(stack, iconX, PAD);
				graphics.itemDecorations(font, stack, iconX, PAD);

				String text = labels.get(i);

				if (!text.isEmpty()) {
					int tx = x + (cell - 2 - font.width(text)) / 2;
					graphics.text(font, text, tx, PAD + ICON + 1, color(stack), true);
				}

				x += cell;
			}
		}

		if (dragging) {
			UiDraw.box(graphics, -2, -2, width + 4, height + 4, 6, 0x00000000, Theme.accent());
		}

		pose.popMatrix();
	}
}
