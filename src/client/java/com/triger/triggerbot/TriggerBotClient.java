package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TriggerBotClient implements ClientModInitializer {
	public static final String MOD_ID = "triggerbot";
	public static final Identifier ID = Identifier.fromNamespaceAndPath(MOD_ID, "main");
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static TriggerConfig config;
	private static TriggerBot bot;
	private static final AutoEat AUTO_EAT = new AutoEat();
	private static final AutoTotem AUTO_TOTEM = new AutoTotem();

	private static KeyMapping toggleKey;
	private static KeyMapping screenKey;
	private static KeyMapping friendKey;

	private static boolean menuKeyPending;

	public static TriggerConfig config() {
		return config;
	}

	public static TriggerBot bot() {
		return bot;
	}

	public static KeyMapping toggleKey() {
		return toggleKey;
	}

	public static KeyMapping screenKey() {
		return screenKey;
	}

	/**
	 * Rebinds one of our keys from the in-game menu and persists it to options.txt
	 * (the same place the vanilla Controls screen writes to).
	 *
	 * @return {@code false} when another action already uses that key
	 */
	public static boolean rebind(KeyMapping keyMapping, InputConstants.Key key) {
		Minecraft client = Minecraft.getInstance();

		if (key == InputConstants.UNKNOWN) {
			return false;
		}

		if (client.options != null) {
			InputConstants.Key previous = InputConstants.getKey(keyMapping.saveString());
			keyMapping.setKey(key);
			String target = keyMapping.saveString();

			for (KeyMapping mapping : client.options.keyMappings) {
				// Refuse keys that are already taken, so e.g. the attack button cannot be stolen.
				if (mapping != keyMapping && target.equals(mapping.saveString())) {
					keyMapping.setKey(previous);
					return false;
				}
			}

			KeyMapping.resetMapping();
			client.options.save();
		}

		return true;
	}

	@Override
	public void onInitializeClient() {
		config = TriggerConfig.load();
		bot = new TriggerBot(config);
		bot.setEnabled(config.enabled);

		// Own section "dayener dls" in Options → Controls → Key Binds.
		KeyMapping.Category category = KeyMapping.Category.register(ID);

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.triggerbot.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				category));

		screenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.triggerbot.screen",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				category));

		friendKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.triggerbot.friend",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				category));

		migrateMenuKey();

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		TargetHud.register(config, bot);
		NameTags.register(config);
		TargetEsp.register(config, bot);
		Watermark.register(config);
		ArmorHud.register(config);
		TargetInfoHud.register(config, bot);
		EffectsHud.register(config);
		KeybindsHud.register(config);
		TotemCounter.register(config);
		CooldownHud.register(config);
		ShulkerPreview.register(config);
		AimAssist.register(config);
		HelperHud.register(config);
		Reconnect.register();
		Gps.register();
		ChatHints.register();
		ItemScroller.register(config);
	}

	/**
	 * Older builds opened the menu with J and used Right Shift for the HUD. Move existing users to the
	 * new default once, without overriding keys that were picked by hand afterwards.
	 */
	private static void migrateMenuKey() {
		if (config.menuKeyMigrated) {
			return;
		}

		InputConstants.Key oldDefault = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_J);

		if (screenKey.matches(oldDefault)) {
			screenKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_SHIFT));
			KeyMapping.resetMapping();
		}

		Minecraft client = Minecraft.getInstance();

		// Options are not guaranteed to exist yet during mod init, so persist on the first tick.
		if (client.options == null) {
			menuKeyPending = true;
			return;
		}

		client.options.save();
		config.menuKeyMigrated = true;
		config.save();
	}

	private void onTick(Minecraft client) {
		if (menuKeyPending) {
			menuKeyPending = false;
			migrateMenuKey();
		}

		while (toggleKey.consumeClick()) {
			toggle(client);
		}

		while (screenKey.consumeClick()) {
			openSettings(client);
		}

		while (friendKey.consumeClick()) {
			toggleFriend(client);
		}

		if (AutoLeave.tick(client, config)) {
			AUTO_EAT.stop(client);
			return;
		}

		InvSync.tick(client);
		SpearSwap.tick(client, config);
		Modules.tickBinds(client);
		// Everything that may ask for a short stop runs before Movement.tick, so a stop lasts exactly as
		// long as requested.
		AUTO_TOTEM.tick(client, config);
		AutoSwap.tick(client, config);
		Helper.tick(client, config);
		ClickPearl.tick(client, config);
		AutoTool.tick(client, config);
		ElytraSwap.tick(client, config);
		AUTO_EAT.tick(client, config);
		Movement.tick(client, config);
		Watermark.tick(client);
		FreeLook.tick(client, config);
		FastExp.tick(client, config);
		LockSlot.tick(client, config);
		bot.tick(client);
	}

	private void toggle(Minecraft client) {
		boolean enabled = !bot.isEnabled();
		bot.setEnabled(enabled);
		config.enabled = enabled;
		config.save();

		if (client.player != null) {
			client.player.sendOverlayMessage(Component.literal("TriggerBot: " + (enabled ? "ON" : "OFF"))
					.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
	}

	private void openSettings(Minecraft client) {
		if (client.gui.screen() == null) {
			client.gui.setScreen(new TriggerBotScreen());
		}
	}

	private void toggleFriend(Minecraft client) {
		if (client.player == null || !(client.hitResult instanceof EntityHitResult hitResult)) {
			return;
		}

		Entity entity = hitResult.getEntity();

		if (!(entity instanceof Player player) || player == client.player) {
			return;
		}

		String name = player.getName().getString();
		boolean removed = config.removeFriend(player.getUUID());

		if (!removed) {
			config.addFriend(player.getUUID(), name);
		}

		config.save();

		client.player.sendOverlayMessage(Component.literal((removed ? "Removed from" : "Added to") + " friends: " + name)
				.withStyle(removed ? ChatFormatting.RED : ChatFormatting.GREEN));
	}
}
