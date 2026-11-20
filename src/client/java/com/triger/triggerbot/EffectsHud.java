package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interface → Effects: list of your active potion effects with level and time left.
 */
public final class EffectsHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "effects_hud");
	private static final String[] ROMAN = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
	private static final int TEXT = HudStyle.TEXT;
	private static final int TEXT_DIM = HudStyle.TEXT_DIM;
	private static final int BAD = HudStyle.BAD;
	private static final int PAD = 6;
	private static final int ROW = 18;
	private static final int HEADER = 14;

	private static HudDrag drag;

	private EffectsHud() {
	}

	public static void register(TriggerConfig config) {
		drag = new HudDrag(() -> config.effectsHud, () -> config.efX, () -> config.efY,
				v -> config.efX = v, v -> config.efY = v, () -> config.efScale);
		drag.register(config);

		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			if (config.effectsHud) {
				render(graphics, Minecraft.getInstance(), config);
			}
		});
	}

	public static void resetPosition() {
		drag.reset();
	}

	private static String level(int amplifier) {
		return amplifier >= 0 && amplifier < ROMAN.length ? ROMAN[amplifier] : String.valueOf(amplifier + 1);
	}

	private static Identifier sprite(Holder<MobEffect> effect) {
		return effect.unwrapKey().map(key -> key.identifier().withPrefix("mob_effect/")).orElse(null);
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config) {
		if (client.player == null) {
			return;
		}

		List<MobEffectInstance> effects = new ArrayList<>(client.player.getActiveEffects());
		effects.removeIf(e -> !e.showIcon() && !config.efHidden);
		effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
		boolean chatOpen = client.gui.screen() instanceof ChatScreen;

		if (effects.isEmpty() && !chatOpen) {
			return;
		}

		Font font = client.font;
		float tickRate = client.level != null ? client.level.tickRateManager().tickrate() : 20.0f;
		List<String> names = new ArrayList<>();
		List<String> times = new ArrayList<>();
		int maxName = font.width("Effects");
		int maxTime = 0;

		for (MobEffectInstance e : effects) {
			String lvl = level(e.getAmplifier());
			String name = e.getEffect().value().getDisplayName().getString() + (lvl.isEmpty() ? "" : " " + lvl);
			String time = MobEffectUtil.formatDuration(e, 1.0f, tickRate).getString();
			names.add(name);
			times.add(time);
			maxName = Math.max(maxName, font.width(name));
			maxTime = Math.max(maxTime, font.width(time));
		}

		int width = Math.max(110, PAD + 16 + 5 + maxName + 10 + maxTime + PAD);
		int height = PAD + HEADER + (effects.isEmpty() ? 0 : effects.size() * ROW - 2) + PAD;
		drag.width = width;
		drag.height = height;
		drag.place(graphics, graphics.guiWidth() - Math.round(width * drag.scale()) - 6, graphics.guiHeight() / 2 - 40);
		drag.begin(graphics);

		HudStyle.panel(graphics, width, height);
		graphics.text(font, "Effects", PAD, PAD, TEXT, false);
		String count = String.valueOf(effects.size());
		graphics.text(font, count, width - PAD - font.width(count), PAD, TEXT_DIM, false);

		int y = PAD + HEADER;

		for (int i = 0; i < effects.size(); i++) {
			MobEffectInstance e = effects.get(i);
			Identifier icon = sprite(e.getEffect());

			if (icon != null) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, PAD, y, 16, 16);
			}

			boolean bad = !e.getEffect().value().isBeneficial();
			graphics.text(font, names.get(i), PAD + 21, y + 4, bad ? BAD : TEXT, false);

			// Blink during the last 10 seconds, like the vanilla icons.
			boolean ending = !e.isInfiniteDuration() && e.endsWithin(200);
			int timeColor = ending && (System.currentTimeMillis() / 400L) % 2L == 0L ? TEXT : TEXT_DIM;
			String time = times.get(i);
			graphics.text(font, time, width - PAD - font.width(time), y + 4, timeColor, false);
			y += ROW;
		}

		drag.end(graphics);
	}
}
