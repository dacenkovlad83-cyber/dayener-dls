package dls.launcher.ui;

import dls.launcher.GameInstaller;
import dls.launcher.GameLauncher;
import dls.launcher.LauncherConfig;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Main screen: title bar (greeting, daily gift, session timer), sidebar (Главная, Версии, Аккаунты,
 * Настройки) with the user card, and the pages.
 */
public final class MainPanel extends JPanel {
	private static final int CX = 152;
	private static final int CY = 52;
	private static final int CW = 812;
	private static final int CH = 536;
	private static final String[] PAGES = {"Главная", "Версии", "Аккаунты", "Настройки"};
	private static final Icons.Painter[] PAGE_ICONS = {Icons.HOME, Icons.VERSIONS, Icons.USER, Icons.SETTINGS};

	private final LauncherWindow window;
	private final LauncherConfig cfg;
	private final CardLayout cards = new CardLayout();
	private final JPanel pages = new JPanel(this.cards);
	private final List<NavItem> nav = new ArrayList<>();
	private final HomePage home;
	private final VersionsPage versions;
	private final AccountsPage accounts;
	private final SettingsPage settings;
	private final long openedAt = System.currentTimeMillis();
	private String page = PAGES[0];
	private volatile boolean running;

	public MainPanel(LauncherWindow window) {
		super(null);
		this.window = window;
		this.cfg = window.config();
		setBackground(Theme.BG);

		// Pages need the config, so they are created only now.
		this.home = new HomePage();
		this.versions = new VersionsPage();
		this.accounts = new AccountsPage();
		this.settings = new SettingsPage();

		JComponent bar = new JComponent() {
		};
		bar.setBounds(0, 0, 620, 50);
		window.dragBy(bar);
		add(bar);

		Chip gift = new Chip(Icons.GIFT, this::giftText, window::openGift);
		gift.setBounds(616, 12, 180, 30);
		add(gift);

		Chip clock = new Chip(Icons.CLOCK, this::clockText, null);
		clock.setBounds(804, 12, 96, 30);
		add(clock);

		JComponent min = window.windowButton(Icons.MINIMIZE, window::minimize);
		min.setBounds(906, 13, 30, 28);
		add(min);
		JComponent close = window.windowButton(Icons.CLOSE, () -> System.exit(0));
		close.setBounds(940, 13, 30, 28);
		add(close);

		for (int i = 0; i < PAGES.length; i++) {
			NavItem item = new NavItem(PAGES[i], PAGE_ICONS[i]);
			item.setBounds(10, 66 + i * 48, 128, 40);
			this.nav.add(item);
			add(item);
		}

		JComponent logout = new Clickable(() -> window.showWelcome());
		logout.setBounds(18, 548, 20, 20);
		logout.setToolTipText("Сменить ник");
		add(logout);

		this.pages.setOpaque(false);
		this.pages.setBounds(CX, CY, CW, CH);
		this.pages.add(this.home, PAGES[0]);
		this.pages.add(this.versions, PAGES[1]);
		this.pages.add(this.accounts, PAGES[2]);
		this.pages.add(this.settings, PAGES[3]);
		add(this.pages);

		new Timer(1000, e -> repaint(0, 0, getWidth(), 52)).start();
	}

	public void refresh() {
		this.home.refresh();
		this.versions.refresh();
		this.accounts.refresh();
		this.settings.refresh();
		repaint();
	}

	private void open(String name) {
		this.page = name;
		this.cards.show(this.pages, name);
		refresh();
	}

	// ---- title bar ---------------------------------------------------------------------------------

	/** "Подарок доступен!" or the time left until the next spin (24 hours after the last one). */
	private String giftText() {
		if (!this.cfg.giftOnCooldown()) {
			return "Подарок доступен!";
		}

		long s = this.cfg.giftLeftMs() / 1000;
		return String.format("Подарок через %02d:%02d", s / 3600, (s / 60) % 60);
	}

	private String clockText() {
		long s = (System.currentTimeMillis() - this.openedAt) / 1000;
		return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = Theme.smooth(g);
		g2.setColor(Theme.BG);
		g2.fillRect(0, 0, getWidth(), getHeight());

		Theme.logo(g2, 18, 15, 22);
		g2.setFont(Theme.bold(16));
		g2.setColor(Theme.TEXT);
		g2.drawString("dayener dls", 48, 32);
		g2.setFont(Theme.semibold(17));
		g2.drawString("С возвращением, " + this.cfg.nick + "!", 168, 33);

		// User card.
		Theme.round(g2, 10, 462, 128, 112, 12, Theme.CARD);
		Theme.outline(g2, 10, 462, 128, 112, 12, Theme.BORDER);
		Theme.logo(g2, 20, 472, 32);
		Icons.USER.paint(g2, 84, 474, 12, Theme.ACCENT_HOVER);
		g2.setFont(Theme.font(11));
		g2.setColor(Theme.ACCENT_HOVER);
		g2.drawString("Юзер", 98, 485);
		g2.setFont(Theme.semibold(13));
		g2.setColor(Theme.TEXT);
		g2.drawString(this.cfg.nick, 20, 526);
		int nw = g2.getFontMetrics().stringWidth(this.cfg.nick);
		g2.setFont(Theme.font(12));
		g2.setColor(Theme.FAINT);
		g2.drawString(" #" + this.cfg.tag, 20 + nw, 526);
		Icons.LOGOUT.paint(g2, 18, 548, 18, Theme.DIM);
		g2.setFont(Theme.font(11));
		g2.setColor(Theme.DIM);
		g2.drawString("Навсегда", 80, 561);

		g2.setFont(Theme.font(9));
		g2.setColor(Theme.FAINT);
		g2.drawString("©2026 dayener dls", 12, 592);
		g2.dispose();
	}

	// ---- play ------------------------------------------------------------------------------------------

	private void play() {
		if (this.running) {
			return;
		}

		this.running = true;
		this.home.busy(true);

		Thread thread = new Thread(() -> {
			try {
				GameInstaller.Installed game = GameInstaller.install(Path.of(this.cfg.gameDir), this.cfg.viaFabric,
						(stage, f) -> SwingUtilities.invokeLater(() -> this.home.progress(stage, f)));
				SwingUtilities.invokeLater(() -> this.home.progress("Запускаю игру…", 1.0));
				Process process = GameLauncher.start(this.cfg, game);
				long start = System.currentTimeMillis();
				SwingUtilities.invokeLater(() -> this.home.progress("Игра запущена — приятной игры!", 1.0));

				if (this.cfg.closeOnLaunch) {
					Thread.sleep(1500);
					System.exit(0);
				}

				int code = process.waitFor();
				this.cfg.playSeconds += (System.currentTimeMillis() - start) / 1000;
				this.cfg.save();
				String result = code == 0 ? "Игра закрыта" : "Игра закрылась с ошибкой (код " + code + "), смотри logs";
				SwingUtilities.invokeLater(() -> this.home.done(result, code != 0));
			} catch (Exception e) {
				String msg = e.getMessage() == null ? e.toString() : e.getMessage();
				SwingUtilities.invokeLater(() -> this.home.done("Ошибка: " + msg, true));
			} finally {
				this.running = false;
				SwingUtilities.invokeLater(() -> this.home.busy(false));
			}
		}, "dls-play");
		thread.setDaemon(true);
		thread.start();
	}

	private static void openFolder(String dir) {
		try {
			Path path = Path.of(dir);
			Files.createDirectories(path);
			Desktop.getDesktop().open(path.toFile());
		} catch (Exception ignored) {
			// nothing to open
		}
	}

	private static String formatMb(int mb) {
		return mb % 1024 == 0 ? (mb / 1024) + " ГБ" : String.format("%.1f ГБ", mb / 1024.0);
	}

	// ---- pages -------------------------------------------------------------------------------------

	/** Главная: banner with Play, version and nick; launch status; statistics. */
	private final class HomePage extends JPanel {
		private final BufferedImage art;
		private final Widgets.Button playButton = new Widgets.Button("Играть", true).icon(Icons.PLAY);
		private String status = "Готов к запуску";
		private boolean statusBad;
		private double progress = -1;

		HomePage() {
			super(null);
			setOpaque(false);
			this.art = new BufferedImage(CW, 254, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = Theme.smooth(this.art.getGraphics());
			Theme.art(g, CW, 254, 42L);
			g.dispose();

			this.playButton.setBounds(24, 152, 124, 50);
			this.playButton.setFont(Theme.semibold(17));
			this.playButton.addActionListener(e -> play());
			add(this.playButton);

			Widgets.Button version = new Widgets.Button("Fabric " + GameInstaller.MC, false);
			version.setBounds(160, 152, 180, 50);
			version.setForeground(Theme.TEXT);
			version.addActionListener(e -> open(PAGES[1]));
			add(version);

			Widgets.Button folder = new Widgets.Button("Папка модов", false).icon(Icons.FOLDER);
			folder.setBounds(20, 470, 170, 40);
			folder.setFont(Theme.semibold(13));
			folder.addActionListener(e -> openFolder(Path.of(cfg.gameDir, "mods").toString()));
			add(folder);

			Widgets.Button logs = new Widgets.Button("Логи", false);
			logs.setBounds(200, 470, 100, 40);
			logs.setFont(Theme.semibold(13));
			logs.addActionListener(e -> openFolder(Path.of(cfg.gameDir, "logs").toString()));
			add(logs);
		}

		void refresh() {
			repaint();
		}

		void busy(boolean busy) {
			this.playButton.setEnabled(!busy);
			this.playButton.setText(busy ? "Запуск…" : "Играть");
		}

		void progress(String stage, double fraction) {
			this.status = stage;
			this.statusBad = false;
			this.progress = fraction;
			repaint();
		}

		void done(String text, boolean bad) {
			this.status = text;
			this.statusBad = bad;
			this.progress = -1;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);

			// Banner.
			var clip = new java.awt.geom.RoundRectangle2D.Double(0, 0, CW, 254, 28, 28);
			g2.setClip(clip);
			g2.drawImage(this.art, 0, 0, null);
			g2.setClip(null);
			Theme.outline(g2, 0, 0, CW, 254, 14, Theme.BORDER);

			g2.setFont(Theme.bold(34));
			g2.setColor(Color.WHITE);
			g2.drawString("Добро пожаловать", 24, 66);
			g2.drawString("в ", 24, 108);
			int vx = 24 + g2.getFontMetrics().stringWidth("в ");
			g2.setPaint(new GradientPaint(vx, 0, Theme.ACCENT_HOVER, vx + 200, 0, new Color(0xA79BFF)));
			g2.drawString("dayener dls", vx, 108);
			g2.setFont(Theme.font(13));
			g2.setColor(Theme.DIM);
			g2.drawString("Твоя игра — твои правила", 24, 134);

			// Nick chip.
			g2.setFont(Theme.font(12));
			String nickLabel = "Никнейм: ";
			int lw = g2.getFontMetrics().stringWidth(nickLabel);
			g2.setFont(Theme.semibold(12));
			int nw = g2.getFontMetrics().stringWidth(cfg.nick);
			Theme.round(g2, 24, 214, lw + nw + 22, 24, 7, Theme.alpha(Theme.CARD, 220));
			g2.setFont(Theme.font(12));
			g2.setColor(Theme.DIM);
			g2.drawString(nickLabel, 34, 230);
			g2.setFont(Theme.semibold(12));
			g2.setColor(Theme.TEXT);
			g2.drawString(cfg.nick, 34 + lw, 230);

			// Launch card.
			card(g2, 0, 270, 400, 266, "Запуск");
			g2.setFont(Theme.font(13));
			g2.setColor(this.statusBad ? Theme.BAD : Theme.TEXT);
			drawWrapped(g2, this.status, 20, 340, 360);

			if (this.progress >= 0) {
				Theme.round(g2, 20, 400, 360, 8, 4, Theme.BORDER);
				Theme.round(g2, 20, 400, Math.max(8, 360 * this.progress), 8, 4, Theme.ACCENT);
				g2.setFont(Theme.font(12));
				g2.setColor(Theme.DIM);
				g2.drawString(Math.round(this.progress * 100) + "%", 20, 428);
			} else {
				g2.setFont(Theme.font(12));
				g2.setColor(Theme.DIM);
				g2.drawString("Первый запуск скачает игру (~1 ГБ)", 20, 410);
			}

			// Stats card.
			card(g2, 412, 270, 400, 266, "Статистика");
			long s = cfg.playSeconds;
			stat(g2, 432, 340, "Время в игре", String.format("%d ч %02d мин", s / 3600, (s / 60) % 60));
			stat(g2, 432, 380, "Оперативная память", formatMb(cfg.ramMb));
			stat(g2, 432, 420, "Версия", "Fabric " + GameInstaller.MC);
			stat(g2, 432, 460, "Аккаунтов", String.valueOf(Math.max(1, cfg.accounts.size())));
			g2.dispose();
		}

		private void card(Graphics2D g, int x, int y, int w, int h, String title) {
			Theme.round(g, x, y, w, h, 14, Theme.PANEL);
			Theme.outline(g, x, y, w, h, 14, Theme.BORDER);
			g.setFont(Theme.semibold(17));
			g.setColor(Theme.TEXT);
			g.drawString(title, x + 20, y + 36);
		}

		private void stat(Graphics2D g, int x, int y, String label, String value) {
			g.setFont(Theme.font(13));
			g.setColor(Theme.DIM);
			g.drawString(label, x, y);
			g.setFont(Theme.semibold(13));
			g.setColor(Theme.TEXT);
			g.drawString(value, 792 - g.getFontMetrics().stringWidth(value), y);
			g.setColor(Theme.BORDER);
			g.fillRect(x, y + 12, 360, 1);
		}
	}

	/** Версии: the one supported build and a "check files" button. */
	private final class VersionsPage extends JPanel {
		private final Widgets.Button check = new Widgets.Button("Проверить файлы", false);

		VersionsPage() {
			super(null);
			setOpaque(false);
			this.check.setBounds(600, 96, 180, 40);
			this.check.setFont(Theme.semibold(13));
			this.check.addActionListener(e -> {
				open(PAGES[0]);
				play();
			});
			add(this.check);
		}

		void refresh() {
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			header(g2, "Версии", "Сборка, которую запускает лаунчер");

			Theme.round(g2, 0, 76, CW, 80, 14, Theme.PANEL);
			Theme.outline(g2, 0, 76, CW, 80, 14, Theme.BORDER);
			Theme.logo(g2, 20, 96, 40);
			g2.setFont(Theme.semibold(16));
			g2.setColor(Theme.TEXT);
			g2.drawString("Fabric " + GameInstaller.MC + " + dayener dls", 76, 112);

			boolean installed = Files.exists(Path.of(cfg.gameDir, "versions", GameInstaller.MC, GameInstaller.MC + ".jar"))
					&& Files.exists(Path.of(cfg.gameDir, "mods", GameInstaller.MOD_FILE));
			g2.setFont(Theme.font(12));
			g2.setColor(installed ? Theme.GOOD : Theme.DIM);
			g2.drawString(installed ? "● Установлено" : "○ Не установлено — скачается при запуске", 76, 134);

			g2.setFont(Theme.font(12));
			g2.setColor(Theme.DIM);
			g2.drawString("В сборке: Minecraft " + GameInstaller.MC + ", Fabric Loader, Fabric API, мод dayener dls"
					+ (cfg.viaFabric ? ", ViaFabric (сервера других версий)." : "."), 0, 186);
			g2.drawString("«Проверить файлы» докачает и починит всё, что повреждено или не хватает, и запустит игру.", 0, 206);
			g2.dispose();
		}
	}

	/** Аккаунты: offline nicks, pick one or add / remove. */
	private final class AccountsPage extends JPanel {
		private final Widgets.Field field = new Widgets.Field("Новый ник (латиница, 3–16)");
		private final Widgets.Button add = new Widgets.Button("Добавить", true);
		private final List<JComponent> rows = new ArrayList<>();
		private String error = "";

		AccountsPage() {
			super(null);
			setOpaque(false);
			this.field.setBounds(0, 76, 640, 44);
			this.field.addActionListener(e -> addNick());
			add(this.field);
			this.add.setBounds(652, 76, 160, 44);
			this.add.addActionListener(e -> addNick());
			add(this.add);
		}

		private void addNick() {
			String nick = this.field.getText().trim();

			if (!LauncherConfig.validNick(nick)) {
				this.error = "Ник: 3–16 символов, латиница, цифры и _";
			} else {
				if (!cfg.accounts.contains(nick)) {
					cfg.accounts.add(nick);
				}

				cfg.nick = nick;
				cfg.save();
				this.field.setText("");
				this.error = "";
			}

			MainPanel.this.refresh();
		}

		void refresh() {
			if (LauncherConfig.validNick(cfg.nick) && !cfg.accounts.contains(cfg.nick)) {
				cfg.accounts.add(0, cfg.nick);
			}

			for (JComponent row : this.rows) {
				remove(row);
			}

			this.rows.clear();
			int y = 150;

			for (String nick : cfg.accounts) {
				if (y > CH - 50) {
					break;
				}

				boolean active = nick.equals(cfg.nick);
				Widgets.Button pick = new Widgets.Button(active ? "Активен" : "Выбрать", !active);
				pick.setFont(Theme.semibold(13));
				pick.setBounds(640, y + 8, 110, 34);
				pick.setEnabled(!active);
				pick.addActionListener(e -> {
					cfg.nick = nick;
					cfg.save();
					MainPanel.this.refresh();
				});

				Widgets.Button del = new Widgets.Button("×", false);
				del.setBounds(760, y + 8, 40, 34);
				del.setEnabled(cfg.accounts.size() > 1);
				del.addActionListener(e -> {
					cfg.accounts.remove(nick);

					if (nick.equals(cfg.nick)) {
						cfg.nick = cfg.accounts.isEmpty() ? "" : cfg.accounts.get(0);
					}

					cfg.save();
					MainPanel.this.refresh();
				});

				this.rows.add(pick);
				this.rows.add(del);
				add(pick);
				add(del);
				y += 58;
			}

			revalidate();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			header(g2, "Аккаунты", "Офлайн-ники для серверов без лицензии");

			if (!this.error.isEmpty()) {
				g2.setFont(Theme.font(12));
				g2.setColor(Theme.BAD);
				g2.drawString(this.error, 2, 140);
			}

			int y = 150;

			for (String nick : cfg.accounts) {
				if (y > CH - 50) {
					break;
				}

				boolean active = nick.equals(cfg.nick);
				Theme.round(g2, 0, y, CW, 50, 12, active ? Theme.ACCENT_SOFT : Theme.PANEL);
				Theme.outline(g2, 0, y, CW, 50, 12, active ? Theme.ACCENT : Theme.BORDER);
				Icons.USER.paint(g2, 16, y + 15, 20, active ? Theme.ACCENT_HOVER : Theme.DIM);
				g2.setFont(Theme.semibold(14));
				g2.setColor(Theme.TEXT);
				g2.drawString(nick, 48, y + 30);
				y += 58;
			}

			g2.dispose();
		}
	}

	/** Настройки: RAM, game folder, Java, close after launch. */
	private final class SettingsPage extends JPanel {
		private final Widgets.Field java = new Widgets.Field("Автоматически (Java, на которой запущен лаунчер)");

		SettingsPage() {
			super(null);
			setOpaque(false);
			int max = Math.max(2048, (LauncherConfig.systemRamMb() - 1024) / 512 * 512);
			Widgets.Slider ram = new Widgets.Slider(1024, max, 512, Math.min(cfg.ramMb, max),
					v -> formatMb(v) + " из " + formatMb(LauncherConfig.systemRamMb() / 512 * 512), v -> {
						cfg.ramMb = v;
						cfg.save();
					});
			ram.setBounds(20, 104, CW - 40, 44);
			add(ram);

			Widgets.Button change = new Widgets.Button("Изменить", false);
			change.setFont(Theme.semibold(13));
			change.setBounds(CW - 250, 214, 110, 38);
			change.addActionListener(e -> chooseFolder());
			add(change);

			Widgets.Button openDir = new Widgets.Button("Открыть", false).icon(Icons.FOLDER);
			openDir.setFont(Theme.semibold(13));
			openDir.setBounds(CW - 130, 214, 110, 38);
			openDir.addActionListener(e -> openFolder(cfg.gameDir));
			add(openDir);

			this.java.setBounds(20, 318, CW - 160, 40);
			this.java.setText(cfg.javaPath);
			this.java.addActionListener(e -> saveJava());
			this.java.addFocusListener(new java.awt.event.FocusAdapter() {
				@Override
				public void focusLost(java.awt.event.FocusEvent e) {
					saveJava();
				}
			});
			add(this.java);

			Widgets.Button browse = new Widgets.Button("Обзор", false);
			browse.setFont(Theme.semibold(13));
			browse.setBounds(CW - 130, 318, 110, 40);
			browse.addActionListener(e -> chooseJava());
			add(browse);

			Widgets.Toggle close = new Widgets.Toggle(cfg.closeOnLaunch, v -> {
				cfg.closeOnLaunch = v;
				cfg.save();
			});
			close.setBounds(CW - 62, 416, 42, 24);
			add(close);

			Widgets.Toggle via = new Widgets.Toggle(cfg.viaFabric, v -> {
				cfg.viaFabric = v;
				cfg.save();
			});
			via.setBounds(CW - 62, 492, 42, 24);
			add(via);
		}

		void refresh() {
			repaint();
		}

		private void saveJava() {
			cfg.javaPath = this.java.getText().trim();
			cfg.save();
		}

		private void chooseFolder() {
			JFileChooser chooser = new JFileChooser(cfg.gameDir);
			chooser.setDialogTitle("Папка для игры");
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				cfg.gameDir = chooser.getSelectedFile().getAbsolutePath();
				cfg.save();
				MainPanel.this.refresh();
			}
		}

		private void chooseJava() {
			JFileChooser chooser = new JFileChooser(System.getProperty("java.home"));
			chooser.setDialogTitle("Файл javaw.exe (Java 25+)");

			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				File file = chooser.getSelectedFile();
				this.java.setText(file.getAbsolutePath());
				saveJava();
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			header(g2, "Настройки", "Память, папка и Java для игры");

			section(g2, 76, 96, "Оперативная память", "Сколько памяти выделить игре");
			section(g2, 184, 96, "Папка игры", null);
			g2.setFont(Theme.font(12));
			g2.setColor(Theme.DIM);
			String dir = cfg.gameDir;

			while (g2.getFontMetrics().stringWidth(dir) > CW - 300 && dir.length() > 10) {
				dir = "…" + dir.substring(4);
			}

			g2.drawString(dir, 20, 240);
			section(g2, 288, 96, "Java", null);
			section(g2, 392, 72, "Закрывать лаунчер после запуска игры", "Игра продолжит работать сама по себе");
			section(g2, 476, 60, "ViaFabric — сервера других версий",
					"Сетевая игра → кнопка версии справа вверху: от 1.8 до новых");
			g2.dispose();
		}

		private void section(Graphics2D g, int y, int h, String title, String sub) {
			Theme.round(g, 0, y, CW, h, 14, Theme.PANEL);
			Theme.outline(g, 0, y, CW, h, 14, Theme.BORDER);
			g.setFont(Theme.semibold(15));
			g.setColor(Theme.TEXT);
			g.drawString(title, 20, y + 26);

			if (sub != null) {
				g.setFont(Theme.font(12));
				g.setColor(Theme.DIM);
				g.drawString(sub, 20, y + 46);
			}
		}
	}

	private static void header(Graphics2D g, String title, String sub) {
		g.setFont(Theme.bold(26));
		g.setColor(Theme.TEXT);
		g.drawString(title, 0, 34);
		g.setFont(Theme.font(13));
		g.setColor(Theme.DIM);
		g.drawString(sub, 0, 58);
	}

	private static void drawWrapped(Graphics2D g, String text, int x, int y, int width) {
		var fm = g.getFontMetrics();
		StringBuilder line = new StringBuilder();
		int lineY = y;

		for (String word : text.split(" ")) {
			if (fm.stringWidth(line + word) > width && line.length() > 0) {
				g.drawString(line.toString(), x, lineY);
				line.setLength(0);
				lineY += fm.getHeight();
			}

			line.append(word).append(' ');
		}

		g.drawString(line.toString(), x, lineY);
	}

	// ---- small components -----------------------------------------------------------------------------

	/** Sidebar entry. */
	private final class NavItem extends JComponent {
		private final String name;
		private final Icons.Painter icon;
		private boolean hover;

		NavItem(String name, Icons.Painter icon) {
			this.name = name;
			this.icon = icon;
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					hover = false;
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					open(NavItem.this.name);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			boolean selected = this.name.equals(page);

			if (selected) {
				Theme.round(g2, 0, 0, getWidth(), getHeight(), 10, Theme.ACCENT);
			} else if (this.hover) {
				Theme.round(g2, 0, 0, getWidth(), getHeight(), 10, Theme.CARD);
			}

			Color c = selected ? Color.WHITE : this.hover ? Theme.TEXT : Theme.DIM;
			this.icon.paint(g2, 12, 11, 18, c);
			g2.setFont(Theme.semibold(14));
			g2.setColor(c);
			g2.drawString(this.name, 40, 25);
			g2.dispose();
		}
	}

	/** Rounded chip in the title bar (gift, clock). */
	private static final class Chip extends JComponent {
		private final Icons.Painter icon;
		private final java.util.function.Supplier<String> text;
		private boolean hover;

		Chip(Icons.Painter icon, java.util.function.Supplier<String> text, Runnable click) {
			this.icon = icon;
			this.text = text;

			if (click != null) {
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						hover = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e) {
						hover = false;
						repaint();
					}

					@Override
					public void mouseClicked(MouseEvent e) {
						click.run();
					}
				});
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			String t = this.text.get();
			boolean lit = t.endsWith("!");
			Theme.round(g2, 0, 0, getWidth(), getHeight(), 8, lit ? Theme.ACCENT_SOFT : (this.hover ? Theme.CARD_HOVER : Theme.CARD));
			Theme.outline(g2, 0, 0, getWidth(), getHeight(), 8, lit ? Theme.ACCENT : Theme.BORDER);
			Color c = lit ? Theme.ACCENT_HOVER : Theme.DIM;
			this.icon.paint(g2, 10, 7, 16, c);
			g2.setFont(Theme.font(12));
			g2.setColor(lit ? Theme.TEXT : Theme.DIM);
			g2.drawString(t, 32, 20);
			g2.dispose();
		}
	}

	/** Invisible clickable area over a painted icon. */
	private static final class Clickable extends JComponent {
		Clickable(Runnable action) {
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					action.run();
				}
			});
		}
	}
}
