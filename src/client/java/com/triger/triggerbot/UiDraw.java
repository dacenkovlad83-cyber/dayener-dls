package com.triger.triggerbot;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Rounded, translucent shapes for the menu and the HUD.
 *
 * <p>Everything is drawn in real screen pixels (the pose is scaled down to one monitor pixel), so corners are
 * smooth at any GUI scale. Corners are not drawn row by row any more: each radius gets a tiny white
 * anti-aliased circle texture once (cached), and a rounded rectangle is just 4 tinted corner quads + 3 fills
 * (a ring: + 4 corner quads + 4 strips). That is a handful of draw elements instead of hundreds.
 */
public final class UiDraw {
	/** Biggest corner radius in screen pixels that gets its own texture. */
	private static final int MAX_RADIUS = 160;
	/** 4×4 sub-samples per pixel for the anti-aliased edge. */
	private static final int SS = 4;

	private static final Map<Integer, Identifier> DISKS = new HashMap<>();
	private static final Map<Long, Identifier> RINGS = new HashMap<>();

	/** Global alpha multiplier, set by the menu from the "opacity" setting. HUD uses 1.0. */
	private static float opacity = 1.0f;

	private UiDraw() {
	}

	public static void setOpacity(float value) {
		opacity = Math.clamp(value, 0.0f, 1.0f);
	}

	public static void resetOpacity() {
		opacity = 1.0f;
	}

	/** Pill: radius is half of the smaller side, i.e. fully rounded ends. */
	public static int pill(int w, int h) {
		return Math.min(w, h) / 2;
	}

	/** Filled rounded rectangle with smooth corners. */
	public static void fill(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		int c = applyOpacity(color);

		if (w <= 0 || h <= 0 || (c >>> 24) == 0) {
			return;
		}

		float d = density(graphics);
		begin(graphics, d);
		rounded(graphics, px(x, d), px(y, d), px(w, d), px(h, d), px(radius, d), c);
		graphics.pose().popMatrix();
	}

	/** Rounded rectangle with a border ring (one GUI pixel thick). */
	public static void box(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int fillColor,
			int borderColor) {
		ring(graphics, x, y, w, h, radius, 1.0f, fillColor, borderColor);
	}

	/** Rounded rectangle with a ring of {@code thickness} GUI pixels; the fill is only inside the ring. */
	public static void ring(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, float thickness,
			int fillColor, int borderColor) {
		if (w <= 0 || h <= 0) {
			return;
		}

		float d = density(graphics);
		begin(graphics, d);
		ringPx(graphics, px(x, d), px(y, d), px(w, d), px(h, d), px(radius, d), Math.max(1, Math.round(thickness * d)),
				applyOpacity(fillColor), applyOpacity(borderColor));
		graphics.pose().popMatrix();
	}

	/**
	 * HUD panel: near-black, very round card. The edge is a ring tinted with the menu colour and less
	 * transparent than the middle; the transparency comes from Interface → HUD → «Непрозрачность HUD».
	 */
	public static void hudPanel(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius) {
		if (w <= 0 || h <= 0) {
			return;
		}

		TriggerConfig config = TriggerBotClient.config();
		float k = config == null ? 0.9f : config.hudOpacity;
		int body = (Math.round(0xE0 * k) << 24) | 0x0E0E12;
		int rimA = Math.round(Math.min(255, 0xE0 * k + 50));
		int rim = (rimA << 24) | mix(0x1A1A20, Theme.accent() & 0xFFFFFF, 0.35f);
		ring(graphics, x, y, w, h, Math.min(radius, Math.min(w, h) / 2.0f), 1.0f, body, rim);
	}

	// ---- drawing in screen pixels -------------------------------------------------------------------

	private static void rounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
			return;
		}

		int r = Math.min(MAX_RADIUS, clampRadius(radius, w, h));

		if (r <= 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}

		corners(g, disk(r), x, y, w, h, r, color);

		if (w > 2 * r) {
			g.fill(x + r, y, x + w - r, y + r, color);
			g.fill(x + r, y + h - r, x + w - r, y + h, color);
		}

		if (h > 2 * r) {
			g.fill(x, y + r, x + w, y + h - r, color);
		}
	}

	private static void ringPx(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int t, int fill, int border) {
		if (w <= 2 * t || h <= 2 * t) {
			rounded(g, x, y, w, h, radius, border);
			return;
		}

		int r = Math.min(MAX_RADIUS, Math.max(t, clampRadius(radius, w, h)));
		rounded(g, x + t, y + t, w - 2 * t, h - 2 * t, r - t, fill);

		if ((border >>> 24) == 0) {
			return;
		}

		corners(g, annulus(r, t), x, y, w, h, r, border);

		if (w > 2 * r) {
			g.fill(x + r, y, x + w - r, y + t, border);
			g.fill(x + r, y + h - t, x + w - r, y + h, border);
		}

		if (h > 2 * r) {
			g.fill(x, y + r, x + t, y + h - r, border);
			g.fill(x + w - t, y + r, x + w, y + h - r, border);
		}
	}

	/** The four quarters of a 2r×2r circle texture into the four corners. */
	private static void corners(GuiGraphicsExtractor g, Identifier tex, int x, int y, int w, int h, int r, int color) {
		int s = 2 * r;
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, r, r, s, s, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y, r, 0, r, r, s, s, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y + h - r, 0, r, r, r, s, s, color);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x + w - r, y + h - r, r, r, r, r, s, s, color);
	}

	// ---- cached circle textures ---------------------------------------------------------------------

	/** White anti-aliased disk of radius {@code r}, made once. */
	private static Identifier disk(int r) {
		return DISKS.computeIfAbsent(r, key -> upload("round/disk_" + r, r, 0));
	}

	/** White anti-aliased ring (outer radius {@code r}, {@code t} pixels thick), made once. */
	private static Identifier annulus(int r, int t) {
		return RINGS.computeIfAbsent(((long) r << 16) | t, key -> upload("round/ring_" + r + "_" + t, r, t));
	}

	/** {@code t == 0}: filled disk; otherwise a ring {@code t} thick. */
	private static Identifier upload(String path, int r, int t) {
		int size = 2 * r;
		NativeImage image = new NativeImage(size, size, false);
		double outer = r;
		double inner = t > 0 ? Math.max(0.0, r - t) : -1.0;

		for (int py = 0; py < size; py++) {
			for (int pxl = 0; pxl < size; pxl++) {
				int hits = 0;

				for (int sy = 0; sy < SS; sy++) {
					for (int sx = 0; sx < SS; sx++) {
						double dx = pxl + (sx + 0.5) / SS - r;
						double dy = py + (sy + 0.5) / SS - r;
						double dist = Math.sqrt(dx * dx + dy * dy);

						if (dist <= outer && dist >= inner) {
							hits++;
						}
					}
				}

				int alpha = Math.round(255.0f * hits / (SS * SS));
				image.setPixel(pxl, py, (alpha << 24) | 0xFFFFFF);
			}
		}

		Identifier id = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, path);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "dls " + path, image));
		return id;
	}

	// ---- helpers ------------------------------------------------------------------------------------

	/** Screen pixels per current GUI unit (GUI scale × any pose scale, e.g. HUD size sliders, menu scale). */
	private static float density(GuiGraphicsExtractor graphics) {
		Matrix3x2fStack pose = graphics.pose();
		float poseScale = (float) Math.sqrt(pose.m00() * pose.m00() + pose.m01() * pose.m01());
		float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
		return Math.max(0.25f, gui * poseScale);
	}

	private static void begin(GuiGraphicsExtractor graphics, float d) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1.0f / d);
	}

	private static int px(float v, float d) {
		return Math.round(v * d);
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int clampRadius(int radius, int w, int h) {
		return Math.max(0, Math.min(radius, Math.min(w, h) / 2));
	}

	private static int applyOpacity(int color) {
		if (opacity >= 1.0f) {
			return color;
		}

		int alpha = Math.round((color >>> 24) * opacity);
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
