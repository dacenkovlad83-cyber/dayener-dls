package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interface → Keybinds: on-screen list of module binds (green = module is on).
 * Also handles the chat command {@code .keybind info} (alias {@code .kaybind}, {@code .bind}).
 */
public final class KeybindsHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "keybinds_hud");
	private static final int TEXT = HudStyle.TEXT;
	private static final int TEXT_DIM = HudStyle.TEXT_DIM;
	private static final int PAD = 6;
	private static final int ROW = 11;
	private static final int HEADER = 14;

	private static HudDrag drag;

	private KeybindsHud() {
	}

	/** One module with its key. */
	public record Entry(String module, String key, boolean toggleable) {
	}

	public static void register(TriggerConfig config) {
		drag = new HudDrag(() -> config.keybindsHud, () -> config.kbX, () -> config.kbY,
				v -> config.kbX = v, v -> config.kbY = v, () -> config.kbScale);
		drag.register(config);

		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			if (config.keybindsHud) {
				render(graphics, Minecraft.getInstance(), config);
			}
		});

		ClientSendMessageEvents.ALLOW_CHAT.register(KeybindsHud::onChat);
	}

	public static void resetPosition() {
		drag.reset();
	}

	/** All binds: the vanilla-mapped ones first, then module binds from the config, sorted by name. */
	public static List<Entry> entries(TriggerConfig config) {
		List<Entry> list = new ArrayList<>();
		addMapping(list, "TriggerBot", TriggerBotClient.toggleKey(), true);
		addMapping(list, "ClickGUI", TriggerBotClient.screenKey(), false);

		List<Map.Entry<String, String>> binds = new ArrayList<>(config.moduleBinds.entrySet());
		binds.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));

		for (Map.Entry<String, String> bind : binds) {
			if (bind.getValue() == null || bind.getValue().isEmpty()) {
				continue;
			}

			list.add(new Entry(bind.getKey(), keyName(bind.getValue()), true));
		}

		return list;
	}

	private static void addMapping(List<Entry> list, String name, KeyMapping mapping, boolean toggleable) {
		if (mapping != null && !mapping.isUnbound()) {
			list.add(new Entry(name, mapping.getTranslatedKeyMessage().getString(), toggleable));
		}
	}

	private static String keyName(String saved) {
		try {
			return InputConstants.getKey(saved).getDisplayName().getString();
		} catch (RuntimeException exception) {
			return saved;
		}
	}

	// --------------------------------------------------------------------------------------------
	// HUD
	// --------------------------------------------------------------------------------------------

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config) {
		if (client.player == null) {
			return;
		}

		// Only binds of modules that are switched on right now.
		List<Entry> list = entries(config);
		list.removeIf(e -> !e.toggleable() || !Modules.isOn(e.module()));

		if (list.isEmpty() && !(client.gui.screen() instanceof ChatScreen)) {
			return;
		}

		Font font = client.font;
		String title = "Keybinds";
		int maxName = font.width(title);
		int maxKey = 0;

		for (Entry e : list) {
			maxName = Math.max(maxName, font.width(e.module()));
			maxKey = Math.max(maxKey, font.width("[" + e.key() + "]"));
		}

		int width = Math.max(110, PAD + maxName + 12 + maxKey + PAD);
		int height = PAD + HEADER + list.size() * ROW + PAD - 2;
		drag.width = width;
		drag.height = height;
		drag.place(graphics, 6, 60);
		drag.begin(graphics);

		HudStyle.panel(graphics, width, height);
		graphics.text(font, title, PAD, PAD, TEXT, false);
		String count = String.valueOf(list.size());
		graphics.text(font, count, width - PAD - font.width(count), PAD, TEXT_DIM, false);

		int y = PAD + HEADER;

		for (Entry e : list) {
			String key = "[" + e.key() + "]";
			graphics.text(font, e.module(), PAD, y, TEXT, false);
			graphics.text(font, key, width - PAD - font.width(key), y, TEXT_DIM, false);
			y += ROW;
		}

		drag.end(graphics);
	}

	// --------------------------------------------------------------------------------------------
	// Chat command
	// --------------------------------------------------------------------------------------------

	/** @return {@code false} to keep our own commands from being sent to the server */
	private static boolean onChat(String message) {
		String text = message.trim();

		if (!text.startsWith(".")) {
			return true;
		}

		String[] parts = text.substring(1).trim().split("\\s+");
		String cmd = parts[0].toLowerCase(Locale.ROOT);

		if (cmd.equals("gps")) {
			Gps.command(java.util.Arrays.copyOfRange(parts, 1, parts.length));
			return false;
		}

		if (cmd.equals("cfg") || cmd.equals("config")) {
			configCommand(parts);
			return false;
		}

		if (!cmd.equals("keybind") && !cmd.equals("kaybind") && !cmd.equals("keybinds") && !cmd.equals("bind")
				&& !cmd.equals("binds")) {
			return true;
		}

		String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";

		if (sub.equals("info") || sub.equals("list") || (cmd.endsWith("s") && sub.isEmpty())) {
			printInfo();
		} else {
			print(Component.literal("Использование: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(".keybind info").withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" — список биндов").withStyle(ChatFormatting.GRAY)));
		}

		return false;
	}

	/** .cfg dir | save &lt;name&gt; | load &lt;name&gt; | list | delete &lt;name&gt; */
	private static void configCommand(String[] parts) {
		String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";
		String name = parts.length > 2 ? parts[2] : "";

		switch (sub) {
			case "dir", "folder", "open" -> {
				Configs.openFolder();
				print(Component.literal("Открыта папка конфигов").withStyle(ChatFormatting.GRAY));
			}
			case "save" -> {
				String error = name.isEmpty() ? "Укажи название: .cfg save <название>" : Configs.save(name);
				print(error == null
						? Component.literal("Конфиг сохранён: ").withStyle(ChatFormatting.GREEN)
								.append(Component.literal(Configs.clean(name)).withStyle(ChatFormatting.WHITE))
						: Component.literal(error).withStyle(ChatFormatting.RED));
			}
			case "load" -> {
				String error = name.isEmpty() ? "Укажи название: .cfg load <название>" : Configs.load(name);
				print(error == null
						? Component.literal("Конфиг загружен: ").withStyle(ChatFormatting.GREEN)
								.append(Component.literal(Configs.clean(name)).withStyle(ChatFormatting.WHITE))
						: Component.literal(error).withStyle(ChatFormatting.RED));
			}
			case "delete", "remove", "del" -> print(name.isEmpty() || !Configs.delete(name)
					? Component.literal("Конфиг не найден").withStyle(ChatFormatting.RED)
					: Component.literal("Конфиг удалён: " + Configs.clean(name)).withStyle(ChatFormatting.GRAY));
			case "list" -> {
				List<String> names = Configs.list();
				print(names.isEmpty()
						? Component.literal("Конфигов нет. .cfg save <название>").withStyle(ChatFormatting.GRAY)
						: Component.literal("Конфиги: ").withStyle(ChatFormatting.AQUA)
								.append(Component.literal(String.join(", ", names)).withStyle(ChatFormatting.WHITE)));
			}
			default -> print(Component.literal(".cfg dir · .cfg save <имя> · .cfg load <имя> · .cfg list · .cfg delete <имя>")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	private static void printInfo() {
		List<Entry> list = entries(TriggerBotClient.config());

		if (list.isEmpty()) {
			print(Component.literal("Биндов нет. Назначь их в меню (поле «Бинд» у модуля).")
					.withStyle(ChatFormatting.GRAY));
			return;
		}

		print(Component.literal("Бинды (" + list.size() + "):").withStyle(ChatFormatting.AQUA));

		for (Entry e : list) {
			MutableComponent line = Component.literal(" " + e.module()).withStyle(ChatFormatting.WHITE)
					.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(e.key()).withStyle(ChatFormatting.YELLOW));

			if (e.toggleable()) {
				boolean on = Modules.isOn(e.module());
				line.append(Component.literal(on ? "  ON" : "  OFF")
						.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			}

			print(line);
		}
	}

	private static void print(Component component) {
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[dls] ").withStyle(ChatFormatting.DARK_AQUA).append(component));
		}
	}
}
