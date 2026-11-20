package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Position + drag handling for one HUD element. While the chat is open the element can be moved with the
 * left mouse button. A stored position of -1/-1 means "use the default spot".
 */
public final class HudDrag {
	private final BooleanSupplier enabled;
	private final IntSupplier getX;
	private final IntSupplier getY;
	private final IntConsumer setX;
	private final IntConsumer setY;
	private final DoubleSupplier scale;

	private boolean dragging;
	private double grabX;
	private double grabY;

	/** Unscaled size of the element, updated every frame by the renderer. */
	public int width = 40;
	public int height = 20;
	/** Where the element was drawn last frame (screen coordinates). */
	public int drawX;
	public int drawY;

	public HudDrag(BooleanSupplier enabled, IntSupplier getX, IntSupplier getY, IntConsumer setX, IntConsumer setY,
			DoubleSupplier scale) {
		this.enabled = enabled;
		this.getX = getX;
		this.getY = getY;
		this.setX = setX;
		this.setY = setY;
		this.scale = scale;
	}

	public float scale() {
		return (float) this.scale.getAsDouble();
	}

	public boolean isDragging() {
		return this.dragging;
	}

	public void register(TriggerConfig config) {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}

			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (!this.enabled.getAsBoolean() || event.button() != 0 || !contains(event.x(), event.y())) {
					return true;
				}

				this.dragging = true;
				this.grabX = event.x() - this.drawX;
				this.grabY = event.y() - this.drawY;
				return false;
			});

			ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> {
				if (!this.dragging) {
					return true;
				}

				int w0 = Math.round(this.width * scale());
				int h0 = Math.round(this.height * scale());
				this.setX.accept((int) Math.round(Math.clamp(event.x() - this.grabX, 0, Math.max(0, s.width - w0))));
				this.setY.accept((int) Math.round(Math.clamp(event.y() - this.grabY, 0, Math.max(0, s.height - h0))));
				return false;
			});

			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
				if (!this.dragging) {
					return true;
				}

				this.dragging = false;
				config.save();
				return false;
			});
		});
	}

	private boolean contains(double x, double y) {
		float k = scale();
		return x >= this.drawX && x < this.drawX + this.width * k && y >= this.drawY && y < this.drawY + this.height * k;
	}

	/** Resolves the on-screen position for this frame (call after {@link #width}/{@link #height} are set). */
	public void place(GuiGraphicsExtractor graphics, int defaultX, int defaultY) {
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int scaledW = Math.round(this.width * scale());
		int scaledH = Math.round(this.height * scale());
		int x = this.getX.getAsInt();
		int y = this.getY.getAsInt();

		if (x < 0 || y < 0) {
			x = defaultX;
			y = defaultY;
		}

		this.drawX = Math.clamp(x, 0, Math.max(0, sw - scaledW));
		this.drawY = Math.clamp(y, 0, Math.max(0, sh - scaledH));
	}

	/** Pushes the translate + scale for drawing in element-local coordinates. Pair with {@link #end}. */
	public void begin(GuiGraphicsExtractor graphics) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(this.drawX, this.drawY);
		pose.scale(scale());
	}

	public void end(GuiGraphicsExtractor graphics) {
		if (this.dragging) {
			UiDraw.box(graphics, -2, -2, this.width + 4, this.height + 4, 6, 0x00000000, Theme.accent());
		}

		graphics.pose().popMatrix();
	}

	public void reset() {
		this.setX.accept(-1);
		this.setY.accept(-1);
	}
}
