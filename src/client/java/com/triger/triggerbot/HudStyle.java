package com.triger.triggerbot;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared look of everything drawn on the game screen: near-black rounded cards with smooth corners
 * (transparency from the menu), white text and light-grey round bars.
 */
public final class HudStyle {
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFFA8A8A8;
	public static final int BAR_TRACK = 0xFF3A3A3A;
	public static final int BAR_FILL = 0xFFD2D2D2;
	public static final int ABSORB = 0xFFE8C547;
	public static final int BAD = 0xFFFF7070;
	/** Very round corners (clamped to half the height, so small plates become pills). */
	public static final int RADIUS = 12;

	private HudStyle() {
	}

	public static void panel(GuiGraphicsExtractor graphics, int w, int h) {
		UiDraw.hudPanel(graphics, 0, 0, w, h, RADIUS);
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius) {
		UiDraw.hudPanel(graphics, x, y, w, h, radius);
	}

	/** Fully round bar on a dark track. */
	public static void bar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float fraction, int fill) {
		UiDraw.fill(graphics, x, y, w, h, h / 2, BAR_TRACK);
		int fw = Math.round(w * Math.clamp(fraction, 0.0f, 1.0f));

		if (fw > 0) {
			UiDraw.fill(graphics, x, y, Math.max(fw, h), h, h / 2, fill);
		}
	}
}
