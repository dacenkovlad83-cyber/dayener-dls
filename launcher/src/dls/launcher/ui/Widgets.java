package dls.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Custom-painted controls in the launcher style.
 */
public final class Widgets {
	private Widgets() {
	}

	/** Rounded button: accent (filled purple) or secondary (dark). */
	public static class Button extends JButton {
		private final boolean accent;
		private boolean hover;
		/** Optional little icon painter drawn before the text. */
		private Icons.Painter icon;

		public Button(String text, boolean accent) {
			super(text);
			this.accent = accent;
			setFont(Theme.semibold(15));
			setForeground(accent ? Color.WHITE : Theme.DIM);
			setFocusPainted(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setOpaque(false);
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
			});
		}

		public Button icon(Icons.Painter painter) {
			this.icon = painter;
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			Color fill = !isEnabled() ? Theme.CARD
					: this.accent ? (this.hover ? Theme.ACCENT_HOVER : Theme.ACCENT)
					: (this.hover ? Theme.CARD_HOVER : Theme.CARD);
			Theme.round(g2, 0, 0, w, h, 10, fill);

			if (!this.accent) {
				Theme.outline(g2, 0, 0, w, h, 10, Theme.BORDER);
			}

			g2.setFont(getFont());
			var fm = g2.getFontMetrics();
			String text = getText();
			int iconW = this.icon != null ? 18 + 8 : 0;
			int tx = (w - fm.stringWidth(text) - iconW) / 2;
			int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
			Color fg = !isEnabled() ? Theme.FAINT : this.accent ? Color.WHITE : (this.hover ? Theme.TEXT : Theme.DIM);

			if (this.icon != null) {
				this.icon.paint(g2, tx, h / 2 - 9, 18, fg);
			}

			g2.setColor(fg);
			g2.drawString(text, tx + iconW, ty);
			g2.dispose();
		}
	}

	/** Rounded card panel. */
	public static class Card extends JPanel {
		private final int radius;
		private Color fill = Theme.CARD;

		public Card(LayoutManager layout, int radius) {
			super(layout);
			this.radius = radius;
			setOpaque(false);
		}

		public Card fill(Color color) {
			this.fill = color;
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			Theme.round(g2, 0, 0, getWidth(), getHeight(), this.radius, this.fill);
			Theme.outline(g2, 0, 0, getWidth(), getHeight(), this.radius, Theme.BORDER);
			g2.dispose();
		}
	}

	/** Dark rounded text field with a placeholder. */
	public static class Field extends JTextField {
		private final String placeholder;

		public Field(String placeholder) {
			this.placeholder = placeholder;
			setOpaque(false);
			setFont(Theme.font(14));
			setForeground(Theme.TEXT);
			setCaretColor(Theme.ACCENT_HOVER);
			setSelectionColor(Theme.ACCENT_SOFT);
			setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			Theme.round(g2, 0, 0, getWidth(), getHeight(), 10, Theme.CARD);
			Theme.outline(g2, 0, 0, getWidth(), getHeight(), 10, hasFocus() ? Theme.ACCENT : Theme.BORDER);

			if (getText().isEmpty() && !hasFocus()) {
				g2.setColor(Theme.FAINT);
				g2.setFont(getFont());
				var fm = g2.getFontMetrics();
				g2.drawString(this.placeholder, 14, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
			}

			g2.dispose();
			super.paintComponent(g);
		}
	}

	/** Slider with a round knob; shows its value via {@code label}. */
	public static class Slider extends JComponent {
		private final int min;
		private final int max;
		private final int step;
		private final IntFunction<String> label;
		private final IntConsumer onChange;
		private int value;

		public Slider(int min, int max, int step, int value, IntFunction<String> label, IntConsumer onChange) {
			this.min = min;
			this.max = Math.max(min + step, max);
			this.step = step;
			this.value = value;
			this.label = label;
			this.onChange = onChange;
			setPreferredSize(new Dimension(300, 44));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			MouseAdapter drag = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					set(e.getX());
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					set(e.getX());
				}
			};
			addMouseListener(drag);
			addMouseMotionListener(drag);
		}

		private void set(int mouseX) {
			double t = Math.max(0, Math.min(1, (mouseX - 8) / (double) (getWidth() - 16)));
			int raw = this.min + (int) Math.round(t * (this.max - this.min) / this.step) * this.step;
			int v = Math.max(this.min, Math.min(this.max, raw));

			if (v != this.value) {
				this.value = v;
				this.onChange.accept(v);
				repaint();
			}
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			int w = getWidth();
			String text = this.label.apply(this.value);
			g2.setFont(Theme.semibold(14));
			g2.setColor(Theme.TEXT);
			g2.drawString(text, w - g2.getFontMetrics().stringWidth(text), 15);

			int trackY = 30;
			double t = (this.value - this.min) / (double) (this.max - this.min);
			int fx = 8 + (int) Math.round(t * (w - 16));
			Theme.round(g2, 0, trackY, w, 6, 3, Theme.BORDER);
			Theme.round(g2, 0, trackY, fx, 6, 3, Theme.ACCENT);
			g2.setColor(Color.WHITE);
			g2.fillOval(fx - 8, trackY - 5, 16, 16);
			g2.setColor(Theme.ACCENT);
			g2.setStroke(new BasicStroke(3f));
			g2.drawOval(fx - 6, trackY - 3, 12, 12);
			g2.dispose();
		}
	}

	/** iOS-like switch. */
	public static class Toggle extends JComponent {
		private boolean on;
		private final java.util.function.Consumer<Boolean> onChange;

		public Toggle(boolean on, java.util.function.Consumer<Boolean> onChange) {
			this.on = on;
			this.onChange = onChange;
			setPreferredSize(new Dimension(42, 24));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					Toggle.this.on = !Toggle.this.on;
					onChange.accept(Toggle.this.on);
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = Theme.smooth(g);
			Theme.round(g2, 0, 0, 42, 24, 12, this.on ? Theme.ACCENT : Theme.BORDER);
			g2.setColor(Color.WHITE);
			g2.fillOval(this.on ? 21 : 3, 3, 18, 18);
			g2.dispose();
		}
	}
}
