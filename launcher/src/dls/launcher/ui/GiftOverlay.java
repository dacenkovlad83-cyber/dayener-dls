package dls.launcher.ui;

import dls.launcher.LauncherConfig;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily gift: once a day a roulette strip spins and stops on a prize — a promo code for the site (1 / 3 / 7 ₽)
 * or, very rarely, a 7-day subscription. The codes are test codes for now: real ones must be issued and
 * checked by the site's server, otherwise anyone could make them up.
 */
public final class GiftOverlay extends JComponent {
	private record Prize(String title, String sub, Color color, double weight) {
	}

	private static final Prize[] PRIZES = {
			new Prize("1 ₽", "промокод на сайт", new Color(0x5A6B8C), 60.0),
			new Prize("3 ₽", "промокод на сайт", new Color(0x3F8F6B), 28.0),
			new Prize("7 ₽", "промокод на сайт", new Color(0x8C5BD6), 11.7),
			new Prize("7 дней", "подписка", new Color(0xE0A33A), 0.3),
	};
	/** How often each prize shows up on the strip (only for the look, not the odds). */
	private static final double[] LOOK = {40, 30, 20, 10};

	private static final int PW = 580;
	private static final int PH = 390;
	private static final int CARD = 110;
	private static final int STEP = 120;
	private static final int REEL_W = 540;
	private static final int TARGET = 48;
	private static final long SPIN_MS = 5200;

	private final LauncherWindow window;
	private final SecureRandom random = new SecureRandom();
	private final List<Prize> strip = new ArrayList<>();
	private double offset;
	private double targetOffset;
	private long spinStart;
	private boolean spinning;
	private String resultTitle = "";
	private String resultCode = "";
	private boolean copied;
	private Timer timer;

	public GiftOverlay(LauncherWindow window) {
		this.window = window;
		setOpaque(false);
		fillStrip(null);

		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				click(e.getX(), e.getY());
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				setCursor(Cursor.getPredefinedCursor(overButton(e.getX(), e.getY()) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private int px() {
		return (LauncherWindow.W - PW) / 2;
	}

	private int py() {
		return (LauncherWindow.H - PH) / 2;
	}

	public void open() {
		LauncherConfig c = this.window.config();
		this.copied = false;

		if (!this.spinning) {
			// Still on the 24-hour cooldown: show the last prize.
			this.resultTitle = "";
			this.resultCode = "";

			if (spunToday() && !c.gifts.isEmpty()) {
				String[] last = c.gifts.get(c.gifts.size() - 1).split("\\|");

				if (last.length >= 3) {
					this.resultTitle = last[1];
					this.resultCode = last[2];
				}
			}
		}

		setVisible(true);

		if (this.timer == null) {
			this.timer = new Timer(15, e -> tick());
		}

		this.timer.start();
	}

	private void close() {
		if (this.spinning) {
			return;
		}

		setVisible(false);

		if (this.timer != null) {
			this.timer.stop();
		}

		this.window.giftClosed();
	}

	/** The roulette is on its 24-hour cooldown (counted from the last spin). */
	private boolean spunToday() {
		return this.window.config().giftOnCooldown();
	}

	// ---- spin ------------------------------------------------------------------------------------------

	private Prize roll() {
		double total = 0;

		for (Prize p : PRIZES) {
			total += p.weight();
		}

		double r = this.random.nextDouble() * total;

		for (Prize p : PRIZES) {
			r -= p.weight();

			if (r < 0) {
				return p;
			}
		}

		return PRIZES[0];
	}

	private void fillStrip(Prize atTarget) {
		this.strip.clear();

		for (int i = 0; i < TARGET + 6; i++) {
			double r = this.random.nextDouble() * 100;
			int k = 0;

			while (k < LOOK.length - 1 && r >= LOOK[k]) {
				r -= LOOK[k];
				k++;
			}

			this.strip.add(PRIZES[k]);
		}

		if (atTarget != null) {
			this.strip.set(TARGET, atTarget);
		}
	}

	private void spin() {
		if (this.spinning || spunToday()) {
			return;
		}

		Prize prize = roll();
		fillStrip(prize);
		this.offset = 0;
		int jitter = this.random.nextInt(CARD - 24) - (CARD - 24) / 2;
		this.targetOffset = TARGET * STEP + CARD / 2.0 - REEL_W / 2.0 + jitter;
		this.spinStart = System.currentTimeMillis();
		this.spinning = true;
		this.resultTitle = "";
		this.resultCode = "";

		// Saved right away, so closing the launcher mid-spin does not give a second try.
		LauncherConfig c = this.window.config();
		String code = code();
		String title = prize.title().equals("7 дней") ? "Подписка на 7 дней" : "Промокод на " + prize.title();
		c.lastGiftDay = LocalDate.now().toString();
		c.lastGiftAt = System.currentTimeMillis();
		c.gifts.add(c.lastGiftAt + "|" + title + "|" + code);
		c.save();
		this.pendingTitle = title;
		this.pendingCode = code;
	}

	private String pendingTitle = "";
	private String pendingCode = "";

	private String code() {
		String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
		StringBuilder sb = new StringBuilder("DLS-");

		for (int i = 0; i < 8; i++) {
			if (i == 4) {
				sb.append('-');
			}

			sb.append(alphabet.charAt(this.random.nextInt(alphabet.length())));
		}

		return sb.toString();
	}

	private void tick() {
		if (this.spinning) {
			double t = Math.min(1.0, (System.currentTimeMillis() - this.spinStart) / (double) SPIN_MS);
			double ease = 1 - Math.pow(1 - t, 4);
			this.offset = this.targetOffset * ease;

			if (t >= 1.0) {
				this.spinning = false;
				this.resultTitle = this.pendingTitle;
				this.resultCode = this.pendingCode;
			}
		}

		repaint();
	}

	// ---- input -----------------------------------------------------------------------------------------

	private java.awt.Rectangle spinButton() {
		return new java.awt.Rectangle(px() + (PW - 220) / 2, py() + 268, 220, 46);
	}

	private java.awt.Rectangle copyButton() {
		return new java.awt.Rectangle(px() + PW - 150, py() + 268, 120, 46);
	}

	private java.awt.Rectangle closeButton() {
		return new java.awt.Rectangle(px() + PW - 44, py() + 14, 30, 30);
	}

	private boolean overButton(int x, int y) {
		return closeButton().contains(x, y) || (canSpin() && spinButton().contains(x, y))
				|| (!this.resultCode.isEmpty() && copyButton().contains(x, y));
	}

	private boolean canSpin() {
		return !this.spinning && !spunToday();
	}

	private void click(int x, int y) {
		if (closeButton().contains(x, y) || !new java.awt.Rectangle(px(), py(), PW, PH).contains(x, y)) {
			close();
		} else if (canSpin() && spinButton().contains(x, y)) {
			spin();
		} else if (!this.resultCode.isEmpty() && copyButton().contains(x, y)) {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(this.resultCode), null);
			this.copied = true;
		}

		repaint();
	}

	// ---- painting --------------------------------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Theme.smooth(g);
		g2.setColor(new Color(0, 0, 0, 170));
		g2.fillRect(0, 0, getWidth(), getHeight());

		int x = px();
		int y = py();
		Theme.round(g2, x, y, PW, PH, 18, Theme.PANEL);
		Theme.outline(g2, x, y, PW, PH, 18, Theme.BORDER);
		Icons.CLOSE.paint(g2, closeButton().x + 7, closeButton().y + 7, 16, Theme.DIM);

		Icons.GIFT.paint(g2, x + 24, y + 22, 26, Theme.ACCENT_HOVER);
		g2.setFont(Theme.bold(22));
		g2.setColor(Theme.TEXT);
		g2.drawString("Ежедневный подарок", x + 60, y + 44);
		g2.setFont(Theme.font(12));
		g2.setColor(Theme.DIM);
		g2.drawString("Крути раз в 24 часа: промокоды на сайт и шанс на подписку", x + 24, y + 70);

		// Reel.
		int rx = x + (PW - REEL_W) / 2;
		int ry = y + 92;
		Theme.round(g2, rx, ry, REEL_W, 140, 14, Theme.BG);
		var clip = new RoundRectangle2D.Double(rx, ry, REEL_W, 140, 28, 28);
		g2.setClip(clip);

		for (int i = 0; i < this.strip.size(); i++) {
			double cx = rx + 15 + i * STEP - this.offset;

			if (cx + CARD < rx || cx > rx + REEL_W) {
				continue;
			}

			prizeCard(g2, (int) Math.round(cx), ry + 15, this.strip.get(i));
		}

		// Edge fades.
		g2.setPaint(new GradientPaint(rx, 0, Theme.BG, rx + 70, 0, Theme.alpha(Theme.BG, 0)));
		g2.fillRect(rx, ry, 70, 140);
		g2.setPaint(new GradientPaint(rx + REEL_W - 70, 0, Theme.alpha(Theme.BG, 0), rx + REEL_W, 0, Theme.BG));
		g2.fillRect(rx + REEL_W - 70, ry, 70, 140);
		g2.setClip(null);

		// Centre marker.
		int mx = rx + REEL_W / 2;
		g2.setColor(Theme.ACCENT_HOVER);
		g2.fillRect(mx - 1, ry + 6, 2, 128);
		Path2D tri = new Path2D.Double();
		tri.moveTo(mx - 8, ry);
		tri.lineTo(mx + 8, ry);
		tri.lineTo(mx, ry + 10);
		tri.closePath();
		g2.fill(tri);

		// Result / button.
		if (!this.resultCode.isEmpty()) {
			g2.setFont(Theme.semibold(15));
			g2.setColor(Theme.TEXT);
			g2.drawString(this.resultTitle, x + 30, y + 262);
			Theme.round(g2, x + 30, y + 268, PW - 200, 46, 10, Theme.CARD);
			Theme.outline(g2, x + 30, y + 268, PW - 200, 46, 10, Theme.ACCENT);
			g2.setFont(new java.awt.Font("Consolas", java.awt.Font.BOLD, 18));
			g2.setColor(Theme.ACCENT_HOVER);
			g2.drawString(this.resultCode, x + 46, y + 298);

			var cb = copyButton();
			Theme.round(g2, cb.x, cb.y, cb.width, cb.height, 10, Theme.ACCENT);
			g2.setFont(Theme.semibold(14));
			g2.setColor(Color.WHITE);
			String copy = this.copied ? "Скопировано" : "Копировать";
			g2.drawString(copy, cb.x + (cb.width - g2.getFontMetrics().stringWidth(copy)) / 2, cb.y + 29);

			g2.setFont(Theme.font(11));
			g2.setColor(Theme.FAINT);
			g2.drawString("Тестовый код — подключим к сайту позже. Следующий подарок через " + untilTomorrow(), x + 30, y + 336);
		} else {
			var sb = spinButton();
			boolean can = canSpin();
			Theme.round(g2, sb.x, sb.y, sb.width, sb.height, 10, can ? Theme.ACCENT : Theme.CARD);
			g2.setFont(Theme.semibold(15));
			g2.setColor(can ? Color.WHITE : Theme.DIM);
			String label = this.spinning ? "Крутится…" : can ? "Крутить" : "Через " + untilTomorrow();
			g2.drawString(label, sb.x + (sb.width - g2.getFontMetrics().stringWidth(label)) / 2, sb.y + 29);
		}

		g2.setFont(Theme.font(11));
		g2.setColor(Theme.FAINT);
		String odds = "Шансы: 1 ₽ — 60%  ·  3 ₽ — 28%  ·  7 ₽ — 11.7%  ·  подписка 7 дней — 0.3%";
		g2.drawString(odds, x + (PW - g2.getFontMetrics().stringWidth(odds)) / 2, y + PH - 24);
		g2.dispose();
	}

	private void prizeCard(Graphics2D g, int x, int y, Prize p) {
		g.setPaint(new GradientPaint(x, y, p.color().brighter(), x, y + CARD, p.color().darker().darker()));
		g.fill(new RoundRectangle2D.Double(x, y, CARD, CARD, 20, 20));
		Theme.outline(g, x, y, CARD, CARD, 10, Theme.alpha(Color.WHITE, 40));
		g.setFont(Theme.bold(p.title().length() > 4 ? 22 : 30));
		g.setColor(Color.WHITE);
		g.drawString(p.title(), x + (CARD - g.getFontMetrics().stringWidth(p.title())) / 2, y + 62);
		g.setFont(Theme.font(11));
		g.setColor(Theme.alpha(Color.WHITE, 200));
		g.drawString(p.sub(), x + (CARD - g.getFontMetrics().stringWidth(p.sub())) / 2, y + 86);
	}

	/** Time left until the next spin (24 hours after the last one). */
	private String untilTomorrow() {
		return LauncherConfig.formatLeft(this.window.config().giftLeftMs());
	}
}
