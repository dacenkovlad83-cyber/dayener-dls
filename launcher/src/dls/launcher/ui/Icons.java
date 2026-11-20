package dls.launcher.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Simple line icons drawn in code (s × s box at x, y).
 */
public final class Icons {
	/** Paints an icon in a square of {@code s} at (x, y). */
	@FunctionalInterface
	public interface Painter {
		void paint(Graphics2D g, double x, double y, double s, Color color);
	}

	private Icons() {
	}

	private static Graphics2D pen(Graphics2D g, double s, Color color) {
		Graphics2D p = (Graphics2D) g.create();
		p.setColor(color);
		p.setStroke(new BasicStroke((float) Math.max(1.4, s / 11), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		return p;
	}

	public static final Painter HOME = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		Path2D path = new Path2D.Double();
		path.moveTo(x + s * 0.12, y + s * 0.48);
		path.lineTo(x + s * 0.5, y + s * 0.14);
		path.lineTo(x + s * 0.88, y + s * 0.48);
		p.draw(path);
		p.draw(new RoundRectangle2D.Double(x + s * 0.22, y + s * 0.42, s * 0.56, s * 0.44, s * 0.12, s * 0.12));
		p.dispose();
	};

	public static final Painter VERSIONS = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		p.draw(new RoundRectangle2D.Double(x + s * 0.12, y + s * 0.18, s * 0.76, s * 0.24, s * 0.1, s * 0.1));
		p.draw(new RoundRectangle2D.Double(x + s * 0.12, y + s * 0.5, s * 0.76, s * 0.24, s * 0.1, s * 0.1));
		p.dispose();
	};

	public static final Painter USER = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		p.draw(new Ellipse2D.Double(x + s * 0.34, y + s * 0.12, s * 0.32, s * 0.32));
		p.draw(new Arc2D.Double(x + s * 0.16, y + s * 0.52, s * 0.68, s * 0.6, 0, 180, Arc2D.OPEN));
		p.dispose();
	};

	public static final Painter SETTINGS = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		double cx = x + s / 2;
		double cy = y + s / 2;

		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			p.draw(new Line2D.Double(cx + Math.cos(a) * s * 0.3, cy + Math.sin(a) * s * 0.3,
					cx + Math.cos(a) * s * 0.42, cy + Math.sin(a) * s * 0.42));
		}

		p.draw(new Ellipse2D.Double(cx - s * 0.28, cy - s * 0.28, s * 0.56, s * 0.56));
		p.draw(new Ellipse2D.Double(cx - s * 0.11, cy - s * 0.11, s * 0.22, s * 0.22));
		p.dispose();
	};

	public static final Painter PLAY = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		Path2D tri = new Path2D.Double();
		tri.moveTo(x + s * 0.28, y + s * 0.16);
		tri.lineTo(x + s * 0.84, y + s * 0.5);
		tri.lineTo(x + s * 0.28, y + s * 0.84);
		tri.closePath();
		p.fill(tri);
		p.dispose();
	};

	public static final Painter LOGIN = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		p.draw(new Line2D.Double(x + s * 0.1, y + s * 0.5, x + s * 0.6, y + s * 0.5));
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(x + s * 0.42, y + s * 0.32);
		arrow.lineTo(x + s * 0.6, y + s * 0.5);
		arrow.lineTo(x + s * 0.42, y + s * 0.68);
		p.draw(arrow);
		Path2D door = new Path2D.Double();
		door.moveTo(x + s * 0.55, y + s * 0.14);
		door.lineTo(x + s * 0.88, y + s * 0.14);
		door.lineTo(x + s * 0.88, y + s * 0.86);
		door.lineTo(x + s * 0.55, y + s * 0.86);
		p.draw(door);
		p.dispose();
	};

	public static final Painter GIFT = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		p.draw(new RoundRectangle2D.Double(x + s * 0.14, y + s * 0.38, s * 0.72, s * 0.5, s * 0.08, s * 0.08));
		p.draw(new Line2D.Double(x + s * 0.1, y + s * 0.38, x + s * 0.9, y + s * 0.38));
		p.draw(new Line2D.Double(x + s * 0.5, y + s * 0.38, x + s * 0.5, y + s * 0.88));
		p.draw(new Arc2D.Double(x + s * 0.26, y + s * 0.14, s * 0.24, s * 0.24, 0, 270, Arc2D.OPEN));
		p.draw(new Arc2D.Double(x + s * 0.5, y + s * 0.14, s * 0.24, s * 0.24, -90, 270, Arc2D.OPEN));
		p.dispose();
	};

	public static final Painter CLOCK = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		p.draw(new Ellipse2D.Double(x + s * 0.12, y + s * 0.12, s * 0.76, s * 0.76));
		p.draw(new Line2D.Double(x + s * 0.5, y + s * 0.5, x + s * 0.5, y + s * 0.28));
		p.draw(new Line2D.Double(x + s * 0.5, y + s * 0.5, x + s * 0.66, y + s * 0.58));
		p.dispose();
	};

	public static final Painter FOLDER = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		Path2D f = new Path2D.Double();
		f.moveTo(x + s * 0.12, y + s * 0.8);
		f.lineTo(x + s * 0.12, y + s * 0.22);
		f.lineTo(x + s * 0.4, y + s * 0.22);
		f.lineTo(x + s * 0.48, y + s * 0.32);
		f.lineTo(x + s * 0.88, y + s * 0.32);
		f.lineTo(x + s * 0.88, y + s * 0.8);
		f.closePath();
		p.draw(f);
		p.dispose();
	};

	public static final Painter LOGOUT = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		Path2D door = new Path2D.Double();
		door.moveTo(x + s * 0.45, y + s * 0.14);
		door.lineTo(x + s * 0.14, y + s * 0.14);
		door.lineTo(x + s * 0.14, y + s * 0.86);
		door.lineTo(x + s * 0.45, y + s * 0.86);
		p.draw(door);
		p.draw(new Line2D.Double(x + s * 0.36, y + s * 0.5, x + s * 0.88, y + s * 0.5));
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(x + s * 0.7, y + s * 0.32);
		arrow.lineTo(x + s * 0.88, y + s * 0.5);
		arrow.lineTo(x + s * 0.7, y + s * 0.68);
		p.draw(arrow);
		p.dispose();
	};

	public static final Painter CLOSE = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		p.draw(new Line2D.Double(x + s * 0.25, y + s * 0.25, x + s * 0.75, y + s * 0.75));
		p.draw(new Line2D.Double(x + s * 0.75, y + s * 0.25, x + s * 0.25, y + s * 0.75));
		p.dispose();
	};

	public static final Painter MINIMIZE = (g, x, y, s, c) -> {
		Graphics2D p = pen(g, s, c);
		p.draw(new Line2D.Double(x + s * 0.25, y + s * 0.55, x + s * 0.75, y + s * 0.55));
		p.dispose();
	};
}
