package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * One place that knows how to switch every module, used by the menu and by module key binds.
 */
public final class Modules {
	private static final Map<String, Boolean> WAS_DOWN = new HashMap<>();

	private Modules() {
	}

	public static boolean isOn(String name) {
		TriggerConfig c = TriggerBotClient.config();

		return switch (name) {
			case "TriggerBot" -> TriggerBotClient.bot().isEnabled();
			case "AutoTotem" -> c.autoTotem;
			case "AutoEat" -> c.autoEat;
			case "AutoLeave" -> c.autoLeave;
			case "Fullbright" -> c.fullbright;
			case "NameTags" -> c.nameTags;
			case "NoRender" -> c.noRender;
			case "AutoSprint" -> c.autoSprint;
			case "InvMove" -> c.invMove;
			case "Freecam" -> c.freecam;
			case "AirStuck" -> c.airStuck;
			case "TriggerBotHUD" -> c.showHud;
			case "TargetESP" -> c.targetEsp;
			case "CustomHands" -> c.customHands;
			case "ItemScroller" -> c.itemScroller;
			case "NoJumpDelay" -> c.noJumpDelay;
			case "Watermark" -> c.watermark;
			case "ArmorHUD" -> c.armorHud;
			case "TargetHUD" -> c.targetHud;
			case "Effects" -> c.effectsHud;
			case "Keybinds" -> c.keybindsHud;
			case "AutoSwap" -> c.autoSwap;
			case "FreeLook" -> c.freeLook;
			case "FastExp" -> c.fastExp;
			case "Helper" -> c.helper;
			case "AimAssist" -> c.aimAssist;
			case "NoPush" -> c.noPush;
			case "ClickPearl" -> c.clickPearl;
			case "ShulkerPreview" -> c.shulkerPreview;
			case "Cooldowns" -> c.cooldownHud;
			case "AutoTool" -> c.autoTool;
			case "LockSlot" -> c.lockSlot;
			case "SpearSwap" -> c.spearSwap;
			case "TotemCounter" -> c.totemCounter;
			case "ElytraSwap" -> c.elytraSwap;
			default -> false;
		};
	}

	public static void toggle(String name) {
		TriggerConfig c = TriggerBotClient.config();
		boolean on = !isOn(name);

		switch (name) {
			case "TriggerBot" -> {
				TriggerBotClient.bot().setEnabled(on);
				c.enabled = on;
			}
			case "AutoTotem" -> c.autoTotem = on;
			case "AutoEat" -> c.autoEat = on;
			case "AutoLeave" -> c.autoLeave = on;
			case "Fullbright" -> c.fullbright = on;
			case "NameTags" -> c.nameTags = on;
			case "NoRender" -> c.noRender = on;
			case "AutoSprint" -> c.autoSprint = on;
			case "InvMove" -> c.invMove = on;
			case "Freecam" -> Movement.setFreecam(on);
			case "AirStuck" -> c.airStuck = on;
			case "TriggerBotHUD" -> c.showHud = on;
			case "TargetESP" -> c.targetEsp = on;
			case "CustomHands" -> c.customHands = on;
			case "ItemScroller" -> c.itemScroller = on;
			case "NoJumpDelay" -> c.noJumpDelay = on;
			case "Watermark" -> c.watermark = on;
			case "ArmorHUD" -> c.armorHud = on;
			case "TargetHUD" -> c.targetHud = on;
			case "Effects" -> c.effectsHud = on;
			case "Keybinds" -> c.keybindsHud = on;
			case "AutoSwap" -> c.autoSwap = on;
			case "FreeLook" -> c.freeLook = on;
			case "FastExp" -> c.fastExp = on;
			case "Helper" -> c.helper = on;
			case "AimAssist" -> c.aimAssist = on;
			case "NoPush" -> c.noPush = on;
			case "ClickPearl" -> c.clickPearl = on;
			case "ShulkerPreview" -> c.shulkerPreview = on;
			case "Cooldowns" -> c.cooldownHud = on;
			case "AutoTool" -> c.autoTool = on;
			case "LockSlot" -> c.lockSlot = on;
			case "SpearSwap" -> c.spearSwap = on;
			case "TotemCounter" -> c.totemCounter = on;
			case "ElytraSwap" -> c.elytraSwap = on;
			default -> {
				return;
			}
		}

		c.save();
	}

	/** Polls the module key binds once per tick (only while no screen is open). */
	public static void tickBinds(Minecraft client) {
		TriggerConfig c = TriggerBotClient.config();
		boolean inGame = client.player != null && client.gui.screen() == null;

		for (Map.Entry<String, String> entry : c.moduleBinds.entrySet()) {
			String name = entry.getKey();
			boolean down = inGame && Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(entry.getValue()));
			boolean wasDown = WAS_DOWN.getOrDefault(name, false);
			WAS_DOWN.put(name, down);

			if (down && !wasDown) {
				toggle(name);
				boolean on = isOn(name);
				client.player.sendOverlayMessage(Component.literal(name + ": " + (on ? "ON" : "OFF"))
						.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			}
		}
	}
}
