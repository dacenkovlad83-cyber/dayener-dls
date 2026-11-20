package com.triger.triggerbot;

import com.triger.triggerbot.mixin.CooldownInstanceAccessor;
import com.triger.triggerbot.mixin.ItemCooldownsAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interface → HUD → Cooldowns: items from your inventory that are on cooldown (ender pearl, wind charge,
 * shield, golden apples on servers, ...), with the seconds left and a small progress bar.
 */
public final class CooldownHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "cooldown_hud");
	private static final int PAD = 6;
	private static final int ROW = 20;
	private static final int HEADER = 14;

	private static HudDrag drag;

	private CooldownHud() {
	}

	private record Entry(ItemStack stack, float secondsLeft, float progress) {
	}

	public static void register(TriggerConfig config) {
		drag = new HudDrag(() -> config.cooldownHud, () -> config.cdX, () -> config.cdY,
				v -> config.cdX = v, v -> config.cdY = v, () -> config.cdScale);
		drag.register(config);

		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			if (config.cooldownHud) {
				render(graphics, Minecraft.getInstance(), tickCounter.getGameTimeDeltaPartialTick(true));
			}
		});
	}

	public static void resetPosition() {
		drag.reset();
	}

	private static List<Entry> collect(LocalPlayer player, float partialTick) {
		ItemCooldowns cooldowns = player.getCooldowns();
		ItemCooldownsAccessor table = (ItemCooldownsAccessor) cooldowns;
		Map<Identifier, ?> active = table.dls$getCooldowns();
		List<Entry> list = new ArrayList<>();

		if (active.isEmpty()) {
			return list;
		}

		// One entry per cooldown group, using the first matching stack from the inventory as the icon.
		Map<Identifier, ItemStack> groups = new LinkedHashMap<>();
		Inventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			if (!stack.isEmpty()) {
				Identifier group = cooldowns.getCooldownGroup(stack);

				if (active.containsKey(group)) {
					groups.putIfAbsent(group, stack);
				}
			}
		}

		float now = table.dls$getTickCount() + partialTick;

		for (Map.Entry<Identifier, ItemStack> e : groups.entrySet()) {
			CooldownInstanceAccessor cd = (CooldownInstanceAccessor) active.get(e.getKey());
			float total = Math.max(1, cd.dls$getEndTime() - cd.dls$getStartTime());
			float left = Math.max(0.0f, cd.dls$getEndTime() - now);
			list.add(new Entry(e.getValue(), left / 20.0f, left / total));
		}

		list.sort((a, b) -> Float.compare(b.secondsLeft(), a.secondsLeft()));
		return list;
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, float partialTick) {
		LocalPlayer player = client.player;

		if (player == null) {
			return;
		}

		List<Entry> list = collect(player, partialTick);

		if (list.isEmpty() && !(client.gui.screen() instanceof ChatScreen)) {
			return;
		}

		Font font = client.font;
		int maxName = font.width("Cooldowns");
		int maxTime = 0;

		for (Entry e : list) {
			maxName = Math.max(maxName, font.width(e.stack().getHoverName().getString()));
			maxTime = Math.max(maxTime, font.width(String.format("%.1fs", e.secondsLeft())));
		}

		int width = Math.max(110, PAD + 16 + 5 + maxName + 10 + maxTime + PAD);
		int height = PAD + HEADER + (list.isEmpty() ? 0 : list.size() * ROW - 2) + PAD;
		drag.width = width;
		drag.height = height;
		drag.place(graphics, graphics.guiWidth() - Math.round(width * drag.scale()) - 6, graphics.guiHeight() / 2 + 40);
		drag.begin(graphics);

		HudStyle.panel(graphics, width, height);
		graphics.text(font, "Cooldowns", PAD, PAD, HudStyle.TEXT, false);
		String count = String.valueOf(list.size());
		graphics.text(font, count, width - PAD - font.width(count), PAD, HudStyle.TEXT_DIM, false);

		int y = PAD + HEADER;
		int textX = PAD + 21;

		for (Entry e : list) {
			graphics.item(e.stack(), PAD, y);
			graphics.text(font, e.stack().getHoverName().getString(), textX, y + 1, HudStyle.TEXT, false);

			String time = String.format("%.1fs", e.secondsLeft());
			graphics.text(font, time, width - PAD - font.width(time), y + 1, HudStyle.TEXT_DIM, false);

			HudStyle.bar(graphics, textX, y + 12, width - PAD - textX, 3, e.progress(), Theme.accent());
			y += ROW;
		}

		drag.end(graphics);
	}
}
