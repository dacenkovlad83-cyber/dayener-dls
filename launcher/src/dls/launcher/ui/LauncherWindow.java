package dls.launcher.ui;

import dls.launcher.LauncherConfig;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The launcher window: borderless, 980×600, welcome screen first, then the main screen.
 */
public final class LauncherWindow extends JFrame {
	public static final int W = 980;
	public static final int H = 600;

	private final LauncherConfig config;
	private final CardLayout cards = new CardLayout();
	private final JPanel base = new JPanel(this.cards);
	private final WelcomePanel welcome;
	private final MainPanel main;
	private final GiftOverlay gift;

	public LauncherWindow(LauncherConfig config) {
		super("dayener dls");
		this.config = config;
		setUndecorated(true);
		setSize(W, H);
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		// Transparent window: the rounded shape is painted by RoundedRoot (smooth corners).
		setBackground(new Color(0, 0, 0, 0));
		setIconImages(appIcons());

		this.welcome = new WelcomePanel(this);
		this.main = new MainPanel(this);
		this.gift = new GiftOverlay(this);

		this.base.setBounds(0, 0, W, H);
		this.base.setOpaque(false);
		this.base.add(this.welcome, "welcome");
		this.base.add(this.main, "main");
		// Nothing below may be opaque, so every repaint goes through RoundedRoot and keeps the corners round.
		this.welcome.setOpaque(false);
		this.main.setOpaque(false);
		getRootPane().setOpaque(false);

		JLayeredPane layers = new RoundedRoot();
		layers.setPreferredSize(new Dimension(W, H));
		layers.add(this.base, JLayeredPane.DEFAULT_LAYER);
		this.gift.setBounds(0, 0, W, H);
		this.gift.setVisible(false);
		layers.add(this.gift, JLayeredPane.MODAL_LAYER);
		setContentPane(layers);

		showWelcome();
	}

	public LauncherConfig config() {
		return this.config;
	}

	public void showWelcome() {
		this.welcome.refresh();
		this.cards.show(this.base, "welcome");
	}

	public void showMain() {
		this.main.refresh();
		this.cards.show(this.base, "main");
	}

	public void openGift() {
		this.gift.open();
	}

	public void giftClosed() {
		this.main.refresh();
	}

	/** Window / taskbar icon in several sizes, so Windows picks a sharp one everywhere. */
	private static java.util.List<java.awt.Image> appIcons() {
		java.util.List<java.awt.Image> icons = new java.util.ArrayList<>();

		for (int size : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) {
			var img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = Theme.smooth(img.getGraphics());
			Theme.logo(g, 0, 0, size);
			g.dispose();
			icons.add(img);
		}

		return icons;
	}

	/** Lets the window be dragged by this component. */
	public void dragBy(JComponent component) {
		MouseAdapter drag = new MouseAdapter() {
			private Point start;

			@Override
			public void mousePressed(MouseEvent e) {
				this.start = e.getPoint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				Point p = getLocation();
				setLocation(p.x + e.getX() - this.start.x, p.y + e.getY() - this.start.y);
			}
		};
		component.addMouseListener(drag);
		component.addMouseMotionListener(drag);
	}

	/** Small square window button (minimize / close). */
	public JComponent windowButton(Icons.Painter icon, Runnable action) {
		JComponent b = new JComponent() {
			private boolean hover;

			{
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
						action.run();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = Theme.smooth(g);

				if (this.hover) {
					Theme.round(g2, 0, 0, getWidth(), getHeight(), 8, icon == Icons.CLOSE ? new Color(0x5A2A30) : Theme.CARD_HOVER);
				}

				icon.paint(g2, (getWidth() - 16) / 2.0, (getHeight() - 16) / 2.0, 16, this.hover ? Theme.TEXT : Theme.DIM);
				g2.dispose();
			}
		};
		return b;
	}

	public void minimize() {
		setState(ICONIFIED);
	}

	/** Corner radius of the window. */
	private static final int RADIUS = 18;

	/**
	 * Paints the whole window into an off-screen image, then keeps only a rounded rectangle of it using an
	 * anti-aliased mask, so the corners are round and smooth (a plain window shape would be jagged).
	 */
	private static final class RoundedRoot extends JLayeredPane {
		private java.awt.image.BufferedImage buffer;
		private java.awt.image.BufferedImage mask;

		RoundedRoot() {
			setOpaque(false);
		}

		@Override
		public void paint(Graphics g) {
			int w = getWidth();
			int h = getHeight();

			if (w <= 0 || h <= 0) {
				return;
			}

			// Draw at the screen's real pixel density (Windows display scaling), so text stays sharp.
			double scale = ((Graphics2D) g).getTransform().getScaleX();
			int pw = (int) Math.ceil(w * scale);
			int ph = (int) Math.ceil(h * scale);

			if (this.buffer == null || this.buffer.getWidth() != pw || this.buffer.getHeight() != ph) {
				this.buffer = new java.awt.image.BufferedImage(pw, ph, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
				this.mask = new java.awt.image.BufferedImage(pw, ph, java.awt.image.BufferedImage.TYPE_INT_ARGB_PRE);
				Graphics2D m = Theme.smooth(this.mask.getGraphics());
				m.setColor(Color.WHITE);
				m.fill(new java.awt.geom.RoundRectangle2D.Double(0, 0, pw, ph, RADIUS * 2 * scale, RADIUS * 2 * scale));
				m.dispose();
			}

			Graphics2D b = this.buffer.createGraphics();
			b.setComposite(java.awt.AlphaComposite.Clear);
			b.fillRect(0, 0, pw, ph);
			b.setComposite(java.awt.AlphaComposite.SrcOver);
			b.scale(scale, scale);
			b.setColor(Theme.BG);
			b.fillRect(0, 0, w, h);
			super.paint(b);
			b.setTransform(new java.awt.geom.AffineTransform());
			b.setComposite(java.awt.AlphaComposite.DstIn);
			b.drawImage(this.mask, 0, 0, null);
			b.setComposite(java.awt.AlphaComposite.SrcOver);
			b.setColor(Theme.BORDER);
			b.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			b.setStroke(new java.awt.BasicStroke((float) scale));
			b.draw(new java.awt.geom.RoundRectangle2D.Double(scale / 2, scale / 2, pw - scale, ph - scale,
					RADIUS * 2 * scale, RADIUS * 2 * scale));
			b.dispose();

			Graphics2D out = (Graphics2D) g.create();
			out.setComposite(java.awt.AlphaComposite.Src);
			out.scale(1 / scale, 1 / scale);
			out.drawImage(this.buffer, 0, 0, null);
			out.dispose();
		}
	}
}
