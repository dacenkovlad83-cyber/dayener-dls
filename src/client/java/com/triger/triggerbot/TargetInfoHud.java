package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface → TargetHUD: head, name, HP and a health bar of the current target
 * (TriggerBot target, otherwise the entity under the crosshair). Stays for a moment after you look away.
 */
public final class TargetInfoHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "target_info_hud");
	private static final EquipmentSlot[] GEAR = {
			EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.OFFHAND
	};
	private static final int PAD = 7;
	private static final int HEAD = 24;
	private static final int BAR_H = 6;
	private static final long KEEP_MS = 2500L;

	private static HudDrag drag;
	private static LivingEntity last;
	private static long lastSeen;
	private static float shownHealth = -1.0f;

	private TargetInfoHud() {
	}

	public static void register(TriggerConfig config, TriggerBot bot) {
		drag = new HudDrag(() -> config.targetHud, () -> config.thX, () -> config.thY,
				v -> config.thX = v, v -> config.thY = v, () -> config.thScale);
		drag.register(config);

		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			if (config.targetHud) {
				render(graphics, Minecraft.getInstance(), config, bot);
			}
		});
	}

	public static void resetPosition() {
		drag.reset();
	}

	private static LivingEntity pick(Minecraft client, TriggerBot bot) {
		Entity target = bot.isEnabled() ? bot.getTarget() : null;

		if (!(target instanceof LivingEntity)) {
			target = client.crosshairPickEntity;
		}

		long now = System.currentTimeMillis();

		if (target instanceof LivingEntity living && living.isAlive() && living != client.player) {
			if (living != last) {
				shownHealth = -1.0f;
			}

			last = living;
			lastSeen = now;
			return living;
		}

		if (last != null && last.isAlive() && !last.isRemoved() && now - lastSeen < KEEP_MS) {
			return last;
		}

		last = null;
		return null;
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config, TriggerBot bot) {
		if (client.player == null) {
			return;
		}

		LivingEntity target = pick(client, bot);

		if (target == null) {
			if (!(client.gui.screen() instanceof ChatScreen)) {
				shownHealth = -1.0f;
				return;
			}

			target = client.player; // preview while the chat is open, so the HUD can be placed
		}

		Font font = client.font;
		String name = target.getName().getString();
		float max = Math.max(1.0f, target.getMaxHealth());
		float hp = Math.clamp(target.getHealth(), 0.0f, max);
		float absorb = target.getAbsorptionAmount();
		shownHealth = shownHealth < 0.0f ? hp : shownHealth + (hp - shownHealth) * 0.2f;

		String hpText = "HP: " + String.format("%.1f", hp + absorb);

		List<ItemStack> gear = new ArrayList<>();

		if (config.thItems) {
			for (EquipmentSlot slot : GEAR) {
				ItemStack stack = target.getItemBySlot(slot);

				if (!stack.isEmpty()) {
					gear.add(stack);
				}
			}
		}

		int textX = PAD + HEAD + 6;
		int textW = Math.max(font.width(name), font.width(hpText));
		int gearW = gear.isEmpty() ? 0 : gear.size() * 17 - 1;
		int width = Math.max(120, Math.max(textX + textW, PAD + gearW) + PAD + 4);
		int barY = PAD + HEAD + 6;
		int gearY = barY + BAR_H + 4;
		int height = (gear.isEmpty() ? barY + BAR_H : gearY + 16) + PAD;

		drag.width = width;
		drag.height = height;
		drag.place(graphics, graphics.guiWidth() / 2 + 12, graphics.guiHeight() / 2 + 12);
		drag.begin(graphics);

		HudStyle.panel(graphics, width, height);
		drawHead(graphics, client, target, PAD, PAD);

		graphics.text(font, name, textX, PAD + 2, HudStyle.TEXT, false);
		graphics.text(font, hpText, textX, PAD + 13, HudStyle.TEXT, false);

		int barW = width - 2 * PAD;
		HudStyle.bar(graphics, PAD, barY, barW, BAR_H, shownHealth / max, HudStyle.BAR_FILL);

		if (absorb > 0.0f) {
			int absW = Math.round(barW * Math.clamp(absorb / max, 0.0f, 1.0f));
			UiDraw.fill(graphics, PAD, barY, Math.max(absW, BAR_H), BAR_H, BAR_H / 2, HudStyle.ABSORB);
		}

		int x = PAD;

		for (ItemStack stack : gear) {
			graphics.item(stack, x, gearY);
			graphics.itemDecorations(font, stack, x, gearY);
			x += 17;
		}

		drag.end(graphics);
	}

	/** Player skin face, or the mob's spawn egg / first letter for other entities. */
	private static void drawHead(GuiGraphicsExtractor graphics, Minecraft client, LivingEntity target, int x, int y) {
		if (target instanceof AbstractClientPlayer player) {
			PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x, y, HEAD);
			return;
		}

		UiDraw.fill(graphics, x, y, HEAD, HEAD, 6, 0x30FFFFFF);
		Item egg = SpawnEggItem.byId(target.getType()).map(Holder::value).orElse(null);

		if (egg != null) {
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(x + (HEAD - 16) / 2.0f, y + (HEAD - 16) / 2.0f);
			graphics.item(new ItemStack(egg), 0, 0);
			pose.popMatrix();
			return;
		}

		String letter = target.getName().getString().isEmpty() ? "?" : target.getName().getString().substring(0, 1);
		graphics.text(client.font, letter, x + (HEAD - client.font.width(letter)) / 2, y + 8, HudStyle.TEXT, false);
	}
}
