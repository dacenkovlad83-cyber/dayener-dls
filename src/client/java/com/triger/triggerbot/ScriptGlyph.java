package com.triger.triggerbot;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A handwritten (script) capital "D" for the watermark logo. The game font has no script letters, so the D is
 * drawn from Bézier curves with a slanted calligraphy nib (thick down-strokes, thin hairlines), rendered once
 * per on-screen pixel size into a small anti-aliased texture and tinted when drawn.
 */
public final class ScriptGlyph {
	/** Strokes as cubic Béziers in a unit square (x right, y down): p0, c1, c2, p1. */
	private static final double[][] CURVES = {
			// main stem, slanted
			{0.50, 0.10, 0.47, 0.38, 0.42, 0.66, 0.31, 0.87},
			// small loop at the bottom left
			{0.31, 0.87, 0.24, 0.98, 0.07, 0.93, 0.12, 0.81},
			// base swinging to the right
			{0.12, 0.81, 0.18, 0.72, 0.40, 0.86, 0.57, 0.88},
			// right side of the bowl
			{0.57, 0.88, 0.88, 0.86, 0.98, 0.50, 0.84, 0.26},
			// top of the bowl back to the left
			{0.84, 0.26, 0.72, 0.05, 0.36, 0.03, 0.19, 0.16},
			// little curl at the top left
			{0.19, 0.16, 0.09, 0.24, 0.13, 0.35, 0.25, 0.30},
	};
	private static final int STEPS = 36;
	/** Nib angle: strokes going down at this angle are thickest. */
	private static final double NIB = Math.toRadians(35.0);
	private static final int SS = 3;

	private static final Map<Integer, Identifier> CACHE = new HashMap<>();
	private static double[][] segments;

	private ScriptGlyph() {
	}

	/** Draws the script D in a {@code size}×{@code size} GUI box at (x, y), tinted with {@code color}. */
	public static void drawD(GuiGraphicsExtractor graphics, float x, float y, float size, int color) {
		Matrix3x2fStack pose = graphics.pose();
		float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
		float d = Math.max(0.25f, (float) Minecraft.getInstance().getWindow().getGuiScale() * poseScale);
		int px = Math.max(6, Math.min(256, Math.round(size * d)));
		Identifier id = CACHE.computeIfAbsent(px, ScriptGlyph::render);

		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(size / px);
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, px, px, px, px, color);
		pose.popMatrix();
	}

	/** Polyline of every curve: {x1, y1, x2, y2, halfWidth}. */
	private static double[][] segments() {
		if (segments != null) {
			return segments;
		}

		List<double[]> list = new ArrayList<>();
		double base = 0.10;

		for (double[] c : CURVES) {
			double prevX = c[0];
			double prevY = c[1];

			for (int i = 1; i <= STEPS; i++) {
				double t = i / (double) STEPS;
				double u = 1.0 - t;
				double xx = u * u * u * c[0] + 3 * u * u * t * c[2] + 3 * u * t * t * c[4] + t * t * t * c[6];
				double yy = u * u * u * c[1] + 3 * u * u * t * c[3] + 3 * u * t * t * c[5] + t * t * t * c[7];
				double dx = xx - prevX;
				double dy = yy - prevY;
				double len = Math.max(1.0e-9, Math.sqrt(dx * dx + dy * dy));
				// Calligraphy: width depends on the stroke direction against the nib.
				double across = Math.abs((dx / len) * Math.sin(NIB) - (dy / len) * Math.cos(NIB));
				double half = base * (0.28 + 0.72 * across) / 2.0;
				list.add(new double[] {prevX, prevY, xx, yy, half});
				prevX = xx;
				prevY = yy;
			}
		}

		segments = list.toArray(new double[0][]);
		return segments;
	}

	private static Identifier render(int px) {
		double[][] segs = segments();
		NativeImage image = new NativeImage(px, px, false);

		for (int py = 0; py < px; py++) {
			for (int pxl = 0; pxl < px; pxl++) {
				int hits = 0;

				for (int sy = 0; sy < SS; sy++) {
					for (int sx = 0; sx < SS; sx++) {
						double ux = (pxl + (sx + 0.5) / SS) / px;
						double uy = (py + (sy + 0.5) / SS) / px;

						if (inside(segs, ux, uy)) {
							hits++;
						}
					}
				}

				int alpha = Math.round(255.0f * hits / (SS * SS));
				image.setPixel(pxl, py, (alpha << 24) | 0xFFFFFF);
			}
		}

		Identifier id = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "glyph/script_d_" + px);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "dls script D " + px, image));
		return id;
	}

	private static boolean inside(double[][] segs, double x, double y) {
		for (double[] s : segs) {
			double dx = s[2] - s[0];
			double dy = s[3] - s[1];
			double lenSq = dx * dx + dy * dy;
			double t = lenSq <= 0 ? 0 : Math.max(0, Math.min(1, ((x - s[0]) * dx + (y - s[1]) * dy) / lenSq));
			double cx = s[0] + t * dx - x;
			double cy = s[1] + t * dy - y;

			if (cx * cx + cy * cy <= s[4] * s[4]) {
				return true;
			}
		}

		return false;
	}
}
