package com.triger.triggerbot;

import net.minecraft.util.Mth;

/**
 * Menu colours derived from one hue (Interface → «Цвет меню»).
 */
public final class Theme {
	private Theme() {
	}

	private static float hue() {
		TriggerConfig config = TriggerBotClient.config();
		return (config == null ? 94.0f : config.guiHue) / 360.0f;
	}

	private static int hsv(float s, float v, int alpha) {
		return Mth.hsvToArgb(hue(), s, v, alpha);
	}

	/** The picked colour (hue + saturation + brightness from the colour picker). */
	public static int accent() {
		TriggerConfig config = TriggerBotClient.config();

		if (config == null) {
			return hsv(0.56f, 0.90f, 0xFF);
		}

		return hsv(config.guiSat, Math.max(0.08f, config.guiVal), 0xFF);
	}

	public static int accentDim() {
		return hsv(0.45f, 0.24f, 0xD0);
	}

	public static int border() {
		return hsv(0.31f, 0.33f, 0xFF);
	}

	public static int borderSoft() {
		return hsv(0.36f, 0.22f, 0xD0);
	}

	public static int rowActive() {
		return hsv(0.36f, 0.17f, 0xC8);
	}

	public static int rowHover() {
		return hsv(0.25f, 0.15f, 0xC0);
	}
}
