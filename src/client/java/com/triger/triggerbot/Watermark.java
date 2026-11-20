package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface → Watermark: two rounded bars with client name, nick, FPS, session time, coordinates, ping,
 * server TPS and speed. While the chat is open the watermark can be dragged with the left mouse button.
 */
public final class Watermark {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "watermark");
	private static final int BAR_H = 18;
	/** Text next to the logo. */
	private static final String BRAND = "dayener";
	private static final int ROW_GAP = 4;
	private static final int BG = 0xC80D0F16;
	private static final int BORDER = 0x60343A52;
	private static final int TEXT = 0xFFE9ECF4;
	private static final int DIVIDER = 0x50FFFFFF;

	// Live stats.
	private static double tps = 20.0;
	private static long lastTimePacketNs;
	private static long lastGameTime = -1L;
	private static double bps;
	private static Vec3 lastPos;
	private static long sessionStart;

	// Dragging (only while the chat is open).
	private static boolean dragging;
	private static double grabX;
	private static double grabY;
	private static int width = 100;
	private static int height = 2 * BAR_H + ROW_GAP;

	private Watermark() {
	}

	public static void register(TriggerConfig config) {
		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			ElytraSwap.frame(Minecraft.getInstance()); // precise 75 ms timing, once per frame

			if (config.watermark) {
				render(graphics, Minecraft.getInstance(), config);
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}

			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
				if (!config.watermark || event.button() != 0 || !contains(config, event.x(), event.y())) {
					return true;
				}

				dragging = true;
				grabX = event.x() - config.wmX;
				grabY = event.y() - config.wmY;
				return false;
			});

			ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> {
				if (!dragging) {
					return true;
				}

				int w0 = Math.round(width * config.wmScale);
				int h0 = Math.round(height * config.wmScale);
				config.wmX = (int) Math.round(Math.clamp(event.x() - grabX, 0, Math.max(0, s.width - w0)));
				config.wmY = (int) Math.round(Math.clamp(event.y() - grabY, 0, Math.max(0, s.height - h0)));
				return false;
			});

			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
				if (!dragging) {
					return true;
				}

				dragging = false;
				config.save();
				return false;
			});
		});
	}

	private static boolean contains(TriggerConfig config, double x, double y) {
		float k = config.wmScale;
		return x >= config.wmX && x < config.wmX + width * k && y >= config.wmY && y < config.wmY + height * k;
	}

	// ---- stats ----------------------------------------------------------------------------------

	/** Called for every time-sync packet; TPS = game ticks passed / real seconds passed. */
	public static void onTimePacket(long gameTime) {
		long now = System.nanoTime();

		if (lastGameTime >= 0L && lastTimePacketNs != 0L && gameTime > lastGameTime) {
			double seconds = (now - lastTimePacketNs) / 1_000_000_000.0;

			if (seconds > 0.2) {
				double sample = Math.clamp((gameTime - lastGameTime) / seconds, 0.0, 20.0);
				tps = tps * 0.6 + sample * 0.4;
			}
		}

		lastGameTime = gameTime;
		lastTimePacketNs = now;
	}

	/** Once per client tick: speed in blocks per second and the session timer. */
	public static void tick(Minecraft client) {
		if (client.player == null) {
			lastPos = null;
			sessionStart = 0L;
			lastGameTime = -1L;
			tps = 20.0;
			return;
		}

		if (sessionStart == 0L) {
			sessionStart = System.currentTimeMillis();
		}

		Vec3 pos = client.player.position();

		if (lastPos != null) {
			double dx = pos.x - lastPos.x;
			double dz = pos.z - lastPos.z;
			double speed = Math.sqrt(dx * dx + dz * dz) * 20.0;
			bps = bps * 0.5 + speed * 0.5;
		}

		lastPos = pos;
	}

	// ---- render ---------------------------------------------------------------------------------

	private enum Icon {
		USER, FPS, CLOCK, COORDS, PING, TICKS, SPEED
	}

	private record Item(Icon icon, String text) {
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config) {
		if (client.player == null) {
			return;
		}

		Font font = client.font;
		List<Item> top = new ArrayList<>();
		List<Item> bottom = new ArrayList<>();

		if (config.wmNick) {
			String nick = config.wmNickText.isBlank() ? client.player.getName().getString() : config.wmNickText;
			top.add(new Item(Icon.USER, nick));
		}

		if (config.wmFps) {
			top.add(new Item(Icon.FPS, client.getFps() + " Fps"));
		}

		if (config.wmTime) {
			long seconds = sessionStart == 0L ? 0L : (System.currentTimeMillis() - sessionStart) / 1000L;
			top.add(new Item(Icon.CLOCK, String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)));
		}

		if (config.wmCoords) {
			bottom.add(new Item(Icon.COORDS, client.player.getBlockX() + " " + client.player.getBlockY() + " " + client.player.getBlockZ()));
		}

		if (config.wmPing) {
			bottom.add(new Item(Icon.PING, ping(client) + " Ping"));
		}

		if (config.wmTps) {
			bottom.add(new Item(Icon.TICKS, Math.round(tps) + " Ticks"));
		}

		if (config.wmBps) {
			bottom.add(new Item(Icon.SPEED, String.format("%.1f Bps", bps)));
		}

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(config.wmX, config.wmY);
		pose.scale(config.wmScale);

		int x = 0;
		int y = 0;

		// Row 1: logo "D" + client tag + items.
		int w1 = drawRow(graphics, font, x, y, top, true);
		int w2 = 0;

		if (!bottom.isEmpty()) {
			w2 = drawRow(graphics, font, x, y + BAR_H + ROW_GAP, bottom, false);
		}

		width = Math.max(w1, w2);
		height = bottom.isEmpty() ? BAR_H : 2 * BAR_H + ROW_GAP;

		if (dragging) {
			UiDraw.box(graphics, x - 2, y - 2, width + 4, height + 4, (BAR_H + 4) / 2, 0x00000000, Theme.accent());
		}

		pose.popMatrix();
	}

	private static int drawRow(GuiGraphicsExtractor graphics, Font font, int x, int y, List<Item> items, boolean logo) {
		int w = 8;

		if (logo) {
			w += 14 + 5 + font.width(BRAND) + 8;
		}

		for (int i = 0; i < items.size(); i++) {
			w += 10 + 3 + font.width(items.get(i).text());
			w += i < items.size() - 1 ? 11 : 6;
		}

		if (!logo && items.isEmpty()) {
			return 0;
		}

		UiDraw.hudPanel(graphics, x, y, w, BAR_H, BAR_H / 2);

		int cx = x + 6;

		if (logo) {
			logo(graphics, font, cx - 1, y + 2);
			cx += 14 + 5;
			graphics.text(font, BRAND, cx, y + 5, TEXT, false);
			cx += font.width(BRAND) + 4;

			if (!items.isEmpty()) {
				graphics.fill(cx, y + 5, cx + 1, y + BAR_H - 5, DIVIDER);
			}

			cx += 4;
		}

		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			icon(graphics, item.icon(), cx, y + 4, Theme.accent());
			cx += 10 + 3;
			graphics.text(font, item.text(), cx, y + 5, TEXT, false);
			cx += font.width(item.text());

			if (i < items.size() - 1) {
				cx += 5;
				graphics.fill(cx, y + 5, cx + 1, y + BAR_H - 5, DIVIDER);
				cx += 6;
			}
		}

		return w;
	}

	/**
	 * Logo: a 14x14 accent badge (lighter on top, like a soft gloss) with a handwritten script "D" in white
	 * over a darker accent shadow.
	 */
	private static void logo(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		int accent = Theme.accent();
		UiDraw.fill(graphics, x, y, 14, 14, 7, accent);
		UiDraw.fill(graphics, x + 2, y + 1, 10, 6, 3, 0x33FFFFFF);

		int shadow = 0xFF000000 | (((accent >> 16 & 0xFF) / 3) << 16) | (((accent >> 8 & 0xFF) / 3) << 8) | ((accent & 0xFF) / 3);
		ScriptGlyph.drawD(graphics, x + 1.6f, y + 1.6f, 11.5f, shadow);
		ScriptGlyph.drawD(graphics, x + 1.0f, y + 1.0f, 11.5f, 0xFFFFFFFF);
	}

	/** Tiny 10x10 pixel icons. */
	private static void icon(GuiGraphicsExtractor graphics, Icon icon, int x, int y, int c) {
		switch (icon) {
			case USER -> {
				UiDraw.fill(graphics, x + 3, y, 4, 4, 2, c);
				UiDraw.fill(graphics, x + 1, y + 5, 8, 5, 2, c);
			}
			case FPS -> {
				graphics.fill(x + 1, y + 6, x + 3, y + 10, c);
				graphics.fill(x + 4, y + 3, x + 6, y + 10, c);
				graphics.fill(x + 7, y, x + 9, y + 10, c);
			}
			case CLOCK -> {
				UiDraw.box(graphics, x, y, 10, 10, 5, 0x00000000, c);
				graphics.fill(x + 5, y + 2, x + 6, y + 6, c);
				graphics.fill(x + 5, y + 5, x + 8, y + 6, c);
			}
			case COORDS -> {
				UiDraw.box(graphics, x + 1, y + 1, 8, 8, 4, 0x00000000, c);
				graphics.fill(x + 4, y + 4, x + 6, y + 6, c);
			}
			case PING -> {
				graphics.fill(x, y + 7, x + 2, y + 10, c);
				graphics.fill(x + 3, y + 5, x + 5, y + 10, c);
				graphics.fill(x + 6, y + 2, x + 8, y + 10, c);
			}
			case TICKS -> {
				graphics.fill(x + 1, y + 1, x + 9, y + 2, c);
				graphics.fill(x + 1, y + 8, x + 9, y + 9, c);
				graphics.fill(x + 3, y + 2, x + 7, y + 4, c);
				graphics.fill(x + 4, y + 4, x + 6, y + 6, c);
				graphics.fill(x + 3, y + 6, x + 7, y + 8, c);
			}
			case SPEED -> {
				for (int i = 0; i < 4; i++) {
					graphics.fill(x + 1 + i, y + 1 + i, x + 3 + i, y + 2 + i, c);
					graphics.fill(x + 1 + i, y + 8 - i, x + 3 + i, y + 9 - i, c);
					graphics.fill(x + 5 + i, y + 1 + i, x + 6 + i, y + 2 + i, c);
					graphics.fill(x + 5 + i, y + 8 - i, x + 6 + i, y + 9 - i, c);
				}
			}
		}
	}

	private static int ping(Minecraft client) {
		if (client.getConnection() == null) {
			return 0;
		}

		PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}
}
