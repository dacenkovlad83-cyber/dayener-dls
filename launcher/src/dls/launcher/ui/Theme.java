package dls.launcher.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

/**
 * Colours, fonts and shared painting for the launcher.
 */
public final class Theme {
	public static final Color BG = new Color(0x131317);
	public static final Color PANEL = new Color(0x18181D);
	public static final Color CARD = new Color(0x1E1E25);
	public static final Color CARD_HOVER = new Color(0x26262F);
	public static final Color BORDER = new Color(0x2B2B35);
	public static final Color TEXT = new Color(0xF2F2F7);
	public static final Color DIM = new Color(0x8C8C9A);
	public static final Color FAINT = new Color(0x55555F);
	public static final Color ACCENT = new Color(0x6D5DF6);
	public static final Color ACCENT_HOVER = new Color(0x7F71FF);
	public static final Color ACCENT_SOFT = new Color(0x2A2550);
	public static final Color GOOD = new Color(0x4CD08A);
	public static final Color BAD = new Color(0xFF6B6B);

	public static final String FAMILY = "Segoe UI";

	private Theme() {
	}

	public static Font font(int size) {
		return new Font(FAMILY, Font.PLAIN, size);
	}

	public static Font bold(int size) {
		return new Font(FAMILY, Font.BOLD, size);
	}

	public static Font semibold(int size) {
		Font f = new Font("Segoe UI Semibold", Font.PLAIN, size);
		return f.getFamily().equals("Dialog") ? bold(size) : f;
	}

	public static Graphics2D smooth(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		return g2;
	}

	public static void round(Graphics2D g, double x, double y, double w, double h, double r, Color fill) {
		g.setColor(fill);
		g.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
	}

	public static void outline(Graphics2D g, double x, double y, double w, double h, double r, Color color) {
		g.setColor(color);
		g.setStroke(new BasicStroke(1f));
		g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, r * 2, r * 2));
	}

	public static Color alpha(Color c, int a) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
	}

	private static java.awt.image.BufferedImage logoSource;
	private static boolean logoLoaded;
	private static final java.util.Map<Integer, java.awt.image.BufferedImage> LOGO_CACHE = new java.util.HashMap<>();

	/** The launcher picture (resources/logo.png), or null if it is missing. */
	private static java.awt.image.BufferedImage logoSource() {
		if (!logoLoaded) {
			logoLoaded = true;

			try (var in = Theme.class.getResourceAsStream("/logo.png")) {
				if (in != null) {
					java.awt.image.BufferedImage full = javax.imageio.ImageIO.read(in);
					// The picture has a wide dark border around the "D": crop to the middle 80 % so the letter
					// fills small icons.
					int side = Math.round(Math.min(full.getWidth(), full.getHeight()) * 0.8f);
					int ox = (full.getWidth() - side) / 2;
					int oy = (full.getHeight() - side) / 2;
					logoSource = full.getSubimage(ox, oy, side, side);
				}
			} catch (java.io.IOException ignored) {
				// fall back to the drawn logo
			}
		}

		return logoSource;
	}

	/**
	 * The picture scaled to {@code size}×{@code size} pixels. Scaled down in halves with bilinear filtering,
	 * so small icons stay smooth instead of grainy. Cached per size.
	 */
	public static java.awt.image.BufferedImage logoImage(int size) {
		java.awt.image.BufferedImage src = logoSource();

		if (src == null || size <= 0) {
			return null;
		}

		return LOGO_CACHE.computeIfAbsent(size, s -> {
			java.awt.image.BufferedImage img = src;
			int w = img.getWidth();

			while (w / 2 >= s) {
				w /= 2;
				img = scaled(img, w);
			}

			return scaled(img, s);
		});
	}

	private static java.awt.image.BufferedImage scaled(java.awt.image.BufferedImage img, int size) {
		var out = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(img, 0, 0, size, size, null);
		g.dispose();
		return out;
	}

	/** Logo: the launcher picture with rounded corners (or a drawn "d" if the picture is missing). */
	public static void logo(Graphics2D g, int x, int y, int size) {
		double scale = g.getTransform().getScaleX();
		java.awt.image.BufferedImage img = logoImage((int) Math.ceil(size * Math.max(1.0, scale)));

		if (img != null) {
			Graphics2D c = (Graphics2D) g.create();
			c.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			c.clip(new RoundRectangle2D.Double(x, y, size, size, size * 0.5, size * 0.5));
			c.drawImage(img, x, y, size, size, null);
			c.dispose();
			g.setColor(alpha(ACCENT, 90));
			g.setStroke(new BasicStroke(1f));
			g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, size - 1, size - 1, size * 0.5, size * 0.5));
			return;
		}

		g.setPaint(new GradientPaint(x, y, ACCENT_HOVER, x + size, y + size, ACCENT));
		g.fill(new RoundRectangle2D.Double(x, y, size, size, size * 0.6, size * 0.6));
		g.setColor(Color.WHITE);
		g.setFont(bold((int) (size * 0.72)));
		var fm = g.getFontMetrics();
		g.drawString("d", x + (size - fm.stringWidth("d")) / 2f, y + (size + fm.getAscent() - fm.getDescent()) / 2f - 1);
	}

	/**
	 * Banner art drawn in code: a deep purple night gradient, scattered pixel blocks, glowing amethyst-like
	 * crystals and a soft glow. Same seed → same picture.
	 */
	public static void art(Graphics2D g, int w, int h, long seed) {
		Random rnd = new Random(seed);
		g.setPaint(new GradientPaint(0, 0, new Color(0x1B1530), w, h, new Color(0x0E0D16)));
		g.fillRect(0, 0, w, h);

		// Pixel blocks in the background.
		int cell = 22;

		for (int y = 0; y < h; y += cell) {
			for (int x = 0; x < w; x += cell) {
				if (rnd.nextFloat() < 0.35f) {
					int shade = 20 + rnd.nextInt(28);
					g.setColor(new Color(shade + 8, shade, shade + 26));
					g.fillRect(x, y, cell - 1, cell - 1);
				}
			}
		}

		// Glow on the right.
		float cx = w * 0.72f;
		float cy = h * 0.55f;
		g.setPaint(new RadialGradientPaint(cx, cy, Math.max(w, h) * 0.55f, new float[] {0f, 1f},
				new Color[] {alpha(ACCENT, 120), alpha(ACCENT, 0)}));
		g.fillRect(0, 0, w, h);

		// Crystals.
		for (int i = 0; i < 9; i++) {
			int bx = (int) (w * (0.48 + rnd.nextFloat() * 0.5));
			int by = (int) (h * (0.55 + rnd.nextFloat() * 0.5));
			int ch = 30 + rnd.nextInt(90);
			int cw = 10 + rnd.nextInt(18);
			double tilt = (rnd.nextDouble() - 0.5) * 0.8;
			crystal(g, bx, by, cw, ch, tilt, rnd);
		}

		// Dark vignette at the left so text stays readable.
		g.setPaint(new GradientPaint(0, 0, alpha(new Color(0x0E0D16), 230), w * 0.6f, 0, alpha(new Color(0x0E0D16), 0)));
		g.fillRect(0, 0, w, h);
	}

	private static void crystal(Graphics2D g, int x, int y, int w, int h, double tilt, Random rnd) {
		Graphics2D c = (Graphics2D) g.create();
		c.rotate(tilt, x, y);
		Polygon left = new Polygon(new int[] {x - w / 2, x, x, x - w / 2}, new int[] {y, y + 4, y - h, y - h + w / 2}, 4);
		Polygon right = new Polygon(new int[] {x, x + w / 2, x + w / 2, x}, new int[] {y + 4, y, y - h + w / 2, y - h}, 4);
		int base = 150 + rnd.nextInt(60);
		c.setColor(new Color(base - 60, base - 90, 255, 220));
		c.fill(left);
		c.setColor(new Color(base - 20, base - 40, 255, 235));
		c.fill(right);
		c.setComposite(AlphaComposite.SrcOver.derive(0.35f));
		c.setColor(Color.WHITE);
		c.fillRect(x - 1, y - h + w / 2, 2, h - w / 2);
		c.dispose();
	}
}
