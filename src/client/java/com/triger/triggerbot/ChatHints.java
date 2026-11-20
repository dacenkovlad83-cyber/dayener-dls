package com.triger.triggerbot;

import com.triger.triggerbot.mixin.ChatScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chat: as soon as you type "." a list of our commands appears above the input line, filtered by what is
 * typed. Tab completes the first hint.
 */
public final class ChatHints {
	/** Usage, what Tab inserts, description. */
	private record Hint(String usage, String complete, String description) {
		String word() {
			int space = this.usage.indexOf(' ');
			return space < 0 ? this.usage : this.usage.substring(0, space);
		}
	}

	private static final Hint[] HINTS = {
			new Hint(".gps <x> <z>", ".gps ", "метка на координаты"),
			new Hint(".gps <x> <y> <z>", ".gps ", "метка с высотой"),
			new Hint(".gps off", ".gps off", "убрать метку"),
			new Hint(".keybind info", ".keybind info", "список биндов"),
			new Hint(".cfg dir", ".cfg dir", "открыть папку конфигов"),
			new Hint(".cfg save <имя>", ".cfg save ", "сохранить конфиг"),
			new Hint(".cfg load <имя>", ".cfg load ", "загрузить конфиг"),
			new Hint(".cfg list", ".cfg list", "список конфигов"),
			new Hint(".cfg delete <имя>", ".cfg delete ", "удалить конфиг"),
	};

	private static final int ROW = 11;
	private static final int PAD = 4;

	private ChatHints() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof ChatScreen chat)) {
				return;
			}

			EditBox input = ((ChatScreenAccessor) chat).dls$getInput();

			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
					draw(graphics, client, s.width, s.height, input.getValue()));

			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				if (event.key() != GLFW.GLFW_KEY_TAB) {
					return true;
				}

				List<Hint> hints = matches(input.getValue());

				if (hints.isEmpty() || hints.get(0).complete().equals(input.getValue())) {
					return true;
				}

				input.setValue(hints.get(0).complete());
				input.moveCursorToEnd(false);
				return false;
			});
		});
	}

	private static List<Hint> matches(String text) {
		List<Hint> list = new ArrayList<>();

		if (!text.startsWith(".")) {
			return list;
		}

		String typed = text.toLowerCase(Locale.ROOT);
		int space = typed.indexOf(' ');
		String word = space < 0 ? typed : typed.substring(0, space);

		for (Hint hint : HINTS) {
			boolean ok = space < 0 ? hint.word().startsWith(word) : hint.word().equals(word);

			// After the command word, keep only hints that still fit the typed words ("off" vs numbers).
			if (ok && space >= 0) {
				String rest = typed.substring(space + 1).trim();
				String hintRest = hint.usage().substring(hint.word().length()).trim();

				String restWord = rest.split("\\s+")[0];
				String hintWord = hintRest.split("\\s+")[0];
				boolean typedMore = rest.contains(" ");

				// Sub-command ("save", "off", ...) must match what is typed so far.
				if (!rest.isEmpty() && !hintWord.startsWith("<")
						&& (typedMore ? !hintWord.equals(restWord) : !hintWord.startsWith(restWord))) {
					ok = false;
				}

				// A sub-command without arguments is done once something more is typed after it.
				if (ok && typedMore && !hintRest.contains("<")) {
					ok = false;
				}

				// Coordinates are numbers.
				if (!rest.isEmpty() && hintWord.startsWith("<") && !Character.isDigit(rest.charAt(0))
						&& rest.charAt(0) != '-' && rest.charAt(0) != '~') {
					ok = false;
				}
			}

			if (ok) {
				list.add(hint);
			}
		}

		return list;
	}

	private static void draw(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, String text) {
		List<Hint> hints = matches(text);

		if (hints.isEmpty()) {
			return;
		}

		Font font = client.font;
		int maxUsage = 0;
		int maxDesc = 0;

		for (Hint hint : hints) {
			maxUsage = Math.max(maxUsage, font.width(hint.usage()));
			maxDesc = Math.max(maxDesc, font.width(hint.description()));
		}

		int w = PAD + maxUsage + 10 + maxDesc + PAD;
		int h = PAD + hints.size() * ROW - 2 + PAD;
		int x = 2;
		int y = height - 16 - h;

		HudStyle.panel(graphics, x, y, w, h, 6);

		for (int i = 0; i < hints.size(); i++) {
			Hint hint = hints.get(i);
			int ry = y + PAD + i * ROW;
			graphics.text(font, hint.usage(), x + PAD, ry, i == 0 ? Theme.accent() : HudStyle.TEXT, false);
			graphics.text(font, hint.description(), x + PAD + maxUsage + 10, ry, HudStyle.TEXT_DIM, false);
		}

		String tip = "Tab — дополнить";
		graphics.text(font, tip, x + w - font.width(tip), y - 10, HudStyle.TEXT_DIM, true);
	}
}
