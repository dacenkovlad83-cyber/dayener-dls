package com.triger.triggerbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Configs: named copies of all module settings in {@code config/dayener-configs/<name>.json}.
 * Personal things are never written to or read from a config: alts, active alt and the friend list.
 * A config file from a friend just goes into the folder and is loaded by name.
 */
public final class Configs {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Fields that stay yours when saving / loading a shared config. */
	private static final Set<String> PERSONAL = Set.of("alts", "activeAlt", "friends", "menuKeyMigrated");

	private Configs() {
	}

	public static Path dir() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("dayener-configs");

		try {
			Files.createDirectories(dir);
		} catch (IOException ignored) {
			// reported by the caller when reading / writing fails
		}

		return dir;
	}

	/** Allowed config names: letters (also Cyrillic), digits, "-" and "_", up to 32 chars. */
	public static String clean(String name) {
		String cleaned = name == null ? "" : name.trim().replaceAll("[^\\p{L}\\p{N}_-]", "");
		return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
	}

	private static Path file(String name) {
		return dir().resolve(name + ".json");
	}

	private static List<String> cached = List.of();
	private static long cachedAt;

	/** Config names; the folder is re-read at most twice a second (the menu asks every frame). */
	public static List<String> list() {
		long now = System.currentTimeMillis();

		if (now - cachedAt > 500L) {
			cached = readList();
			cachedAt = now;
		}

		return cached;
	}

	private static List<String> readList() {
		List<String> names = new ArrayList<>();

		try (Stream<Path> files = Files.list(dir())) {
			files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
					.map(p -> p.getFileName().toString())
					.map(n -> n.substring(0, n.length() - 5))
					.sorted(String.CASE_INSENSITIVE_ORDER)
					.forEach(names::add);
		} catch (IOException ignored) {
			// empty list
		}

		return names;
	}

	public static boolean exists(String name) {
		return Files.exists(file(name));
	}

	/** @return error text, or {@code null} on success */
	public static String save(String rawName) {
		String name = clean(rawName);

		if (name.isEmpty()) {
			return "Название: буквы, цифры, - и _";
		}

		JsonObject json = GSON.toJsonTree(TriggerBotClient.config()).getAsJsonObject();
		PERSONAL.forEach(json::remove);

		try (Writer writer = Files.newBufferedWriter(file(name))) {
			GSON.toJson(json, writer);
			cachedAt = 0L;
			return null;
		} catch (IOException exception) {
			TriggerBotClient.LOGGER.warn("Failed to save config {}", name, exception);
			return "Не удалось сохранить: " + exception.getMessage();
		}
	}

	/** @return error text, or {@code null} on success */
	public static String load(String rawName) {
		String name = clean(rawName);
		Path path = file(name);

		if (name.isEmpty() || !Files.exists(path)) {
			return "Конфиг «" + rawName + "» не найден (.cfg list)";
		}

		TriggerConfig loaded;

		try (Reader reader = Files.newBufferedReader(path)) {
			JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
			PERSONAL.forEach(json::remove);
			loaded = GSON.fromJson(json, TriggerConfig.class);
		} catch (Exception exception) {
			TriggerBotClient.LOGGER.warn("Failed to read config {}", name, exception);
			return "Файл повреждён: " + exception.getMessage();
		}

		if (loaded == null) {
			return "Файл пустой";
		}

		apply(loaded, TriggerBotClient.config());
		return null;
	}

	public static boolean delete(String rawName) {
		try {
			cachedAt = 0L;
			return Files.deleteIfExists(file(clean(rawName)));
		} catch (IOException exception) {
			return false;
		}
	}

	public static void openFolder() {
		Util.getPlatform().openPath(dir());
	}

	/**
	 * Copies every saved setting into the live config object (the menu and modules hold a reference to it),
	 * keeping the personal fields, then saves and applies the TriggerBot switch.
	 */
	private static void apply(TriggerConfig from, TriggerConfig to) {
		for (Field field : TriggerConfig.class.getDeclaredFields()) {
			int mods = field.getModifiers();

			if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || Modifier.isFinal(mods)
					|| PERSONAL.contains(field.getName())) {
				continue;
			}

			try {
				field.setAccessible(true);
				Object value = field.get(from);

				if (value != null) {
					field.set(to, value);
				}
			} catch (IllegalAccessException exception) {
				TriggerBotClient.LOGGER.warn("Config field {} skipped", field.getName(), exception);
			}
		}

		to.save();
		TriggerBotClient.bot().setEnabled(to.enabled);
	}
}
