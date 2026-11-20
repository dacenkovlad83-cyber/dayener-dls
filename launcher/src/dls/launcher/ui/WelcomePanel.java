package dls.launcher.ui;

import dls.launcher.LauncherConfig;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * First screen: "Привет, nick! 👋", nick / role / subscription rows and "Войти" / "Это не я".
 * On the first start (no nick yet) it asks for an offline nick instead.
 */
public final class WelcomePanel extends JPanel {
	private static final int ROWS_X = 330;
	private static final int ROWS_W = 320;

	private final LauncherWindow window;
	private final BufferedImage art;
	private final Widgets.Button enter = new Widgets.Button("Войти", true).icon(Icons.LOGIN);
	private final Widgets.Button notMe = new Widgets.Button("Это не я", false);
	private final Widgets.Field nickField = new Widgets.Field("Твой ник (латиница, 3–16)");
	private final Widgets.Button save = new Widgets.Button("Продолжить", true);
	private String error = "";
	private boolean asking;

	public WelcomePanel(LauncherWindow window) {
		super(null);
		this.window = window;
		setBackground(Theme.BG);

		this.art = new BufferedImage(LauncherWindow.W, 330, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = Theme.smooth(this.art.getGraphics());
		Theme.art(g, LauncherWindow.W, 330, 7L);
		g.dispose();

		JComponent bar = new JComponent() {
		};
		bar.setBounds(0, 0, LauncherWindow.W - 90, 56);
		window.dragBy(bar);
		add(bar);

		JComponent min = window.windowButton(Icons.MINIMIZE, window::minimize);
		min.setBounds(LauncherWindow.W - 82, 14, 32, 28);
		add(min);
		JComponent close = window.windowButton(Icons.CLOSE, () -> System.exit(0));
		close.setBounds(LauncherWindow.W - 46, 14, 32, 28);
		add(close);

		this.enter.setBounds(360, 487, 162, 48);
		this.enter.addActionListener(e -> window.showMain());
		add(this.enter);

		this.notMe.setBounds(534, 487, 92, 48);
		this.notMe.setFont(Theme.semibold(14));
		this.notMe.addActionListener(e -> {
			this.asking = true;
			this.nickField.setText("");
			refresh();
			this.nickField.requestFocusInWindow();
		});
		add(this.notMe);

		this.nickField.setBounds(ROWS_X, 400, ROWS_W, 44);
		this.nickField.addActionListener(e -> saveNick());
		add(this.nickField);

		this.save.setBounds(ROWS_X, 456, ROWS_W, 46);
		this.save.addActionListener(e -> saveNick());
		add(this.save);
	}

	private void saveNick() {
		String nick = this.nickField.getText().trim();

		if (!LauncherConfig.validNick(nick)) {
			this.error = "Ник: 3–16 символов, латиница, цифры и _";
			repaint();
			return;
		}

		LauncherConfig c = this.window.config();
		c.nick = nick;

		if (!c.accounts.contains(nick)) {
			c.accounts.add(nick);
		}

		c.save();
		this.asking = false;
		this.error = "";
		refresh();
	}

	public void refresh() {
		boolean noNick = !LauncherConfig.validNick(this.window.config().nick);
		this.asking = this.asking || noNick;
		this.nickField.setVisible(this.asking);
		this.save.setVisible(this.asking);
		this.enter.setVisible(!this.asking);
		this.notMe.setVisible(!this.asking);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Theme.smooth(g);
		int w = getWidth();
		g2.setColor(Theme.BG);
		g2.fillRect(0, 0, w, getHeight());

		// Art with a fade into the background.
		g2.drawImage(this.art, 0, 0, null);
		g2.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.BG, 120), 0, 330, Theme.alpha(Theme.BG, 120)));
		g2.fillRect(0, 0, w, 330);
		g2.setPaint(new GradientPaint(0, 190, Theme.alpha(Theme.BG, 0), 0, 330, Theme.BG));
		g2.fillRect(0, 190, w, 140);

		// Title bar.
		Theme.logo(g2, 24, 18, 22);
		g2.setFont(Theme.bold(16));
		g2.setColor(Theme.TEXT);
		g2.drawString("dayener dls", 54, 35);

		LauncherConfig c = this.window.config();
		String nick = LauncherConfig.validNick(c.nick) ? c.nick : "игрок";

		// Greeting with the waving hand.
		Font big = Theme.font(46);
		g2.setFont(big);
		String text = "Привет, " + nick + "! ";
		int tw = g2.getFontMetrics().stringWidth(text);
		Font emoji = new Font("Segoe UI Emoji", Font.PLAIN, 44);
		int ew = g2.getFontMetrics(emoji).stringWidth("👋");
		int tx = (w - tw - ew) / 2;
		g2.setPaint(new GradientPaint(tx, 0, new Color(0xD9D3FF), tx + tw, 0, Color.WHITE));
		g2.drawString(text, tx, 165);
		g2.setFont(emoji);
		g2.setColor(new Color(0xFFC83D));
		g2.drawString("👋", tx + tw, 165);

		if (this.asking) {
			g2.setFont(Theme.font(14));
			g2.setColor(Theme.DIM);
			center(g2, "Как тебя зовут в игре?", 385);

			if (!this.error.isEmpty()) {
				g2.setColor(Theme.BAD);
				center(g2, this.error, 528);
			}
		} else {
			row(g2, 380, "Никнейм", nick, "#" + c.tag, Theme.TEXT);
			row(g2, 412, "Роль", "Пользователь", null, Theme.ACCENT_HOVER);
			row(g2, 444, "Подписка", "Активна", null, Theme.TEXT);
		}

		g2.setFont(Theme.font(10));
		g2.setColor(Theme.FAINT);
		center(g2, "©2026 dayener dls", 586);
		g2.dispose();
	}

	private void row(Graphics2D g, int y, String label, String value, String suffix, Color valueColor) {
		g.setFont(Theme.font(13));
		g.setColor(Theme.TEXT);
		g.drawString(label, ROWS_X, y + 12);

		g.setFont(Theme.semibold(13));
		int sw = suffix == null ? 0 : g.getFontMetrics(Theme.font(13)).stringWidth(suffix);
		int vw = g.getFontMetrics().stringWidth(value);
		int right = ROWS_X + ROWS_W;
		g.setColor(valueColor);
		g.drawString(value, right - sw - vw, y + 12);

		if (suffix != null) {
			g.setFont(Theme.font(13));
			g.setColor(Theme.FAINT);
			g.drawString(suffix, right - sw, y + 12);
		}

		if (!label.equals("Подписка")) {
			g.setColor(Theme.BORDER);
			g.fillRect(ROWS_X, y + 25, ROWS_W, 1);
		}
	}

	private void center(Graphics2D g, String text, int y) {
		g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, y);
	}
}
