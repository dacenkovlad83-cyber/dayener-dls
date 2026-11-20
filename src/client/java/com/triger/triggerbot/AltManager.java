package com.triger.triggerbot;

import com.mojang.authlib.GameProfile;
import com.triger.triggerbot.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.core.UUIDUtil;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Alt Manager: switches the nick used to join servers to an offline (no-password) account.
 * Works on servers without license check (online-mode=false). The launcher account is never touched,
 * "Основной аккаунт" switches back to it.
 */
public final class AltManager {
	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private static String cachedName;
	private static User cachedUser;

	private AltManager() {
	}

	public static boolean isValid(String name) {
		return name != null && VALID.matcher(name).matches();
	}

	/** The offline user to log in with, or {@code null} for the launcher account. */
	public static User current() {
		TriggerConfig config = TriggerBotClient.config();

		if (config == null || config.activeAlt.isEmpty()) {
			return null;
		}

		if (!config.activeAlt.equals(cachedName)) {
			cachedName = config.activeAlt;
			cachedUser = new User(cachedName, UUIDUtil.createOfflinePlayerUUID(cachedName), "0",
					Optional.empty(), Optional.empty());
		}

		return cachedUser;
	}

	public static GameProfile currentProfile() {
		User user = current();
		return user == null ? null : new GameProfile(user.getProfileId(), user.getName());
	}

	/** Name of the account the game was started with (bypasses our override). */
	public static String launcherName() {
		return ((MinecraftAccessor) Minecraft.getInstance()).dls$getRealUser().getName();
	}

	public static boolean add(String name) {
		TriggerConfig config = TriggerBotClient.config();

		if (!isValid(name)) {
			return false;
		}

		for (String alt : config.alts) {
			if (alt.equalsIgnoreCase(name)) {
				return false;
			}
		}

		config.alts.add(name);
		config.save();
		return true;
	}

	public static void remove(String name) {
		TriggerConfig config = TriggerBotClient.config();
		config.alts.remove(name);

		if (config.activeAlt.equals(name)) {
			config.activeAlt = "";
		}

		config.save();
	}

	/** {@code null} = back to the launcher account. */
	public static void use(String name) {
		TriggerConfig config = TriggerBotClient.config();
		config.activeAlt = name == null ? "" : name;
		config.save();
	}
}
