package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Chat command {@code .gps <x> <z>} / {@code .gps <x> <y> <z>}: puts a waypoint into the world. A card in the
 * TargetHUD style sits on the spot (or on the screen edge when it is behind you) and shows how far is left.
 * {@code .gps off} removes it; it also disappears when you arrive.
 */
public final class Gps {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "gps");
	private static final double ARRIVE = 3.0;
	private static final int PAD = 4;
	/** Base size; multiplied by Interface → HUD → «Размер GPS». */
	private static final float BASE_SCALE = 0.85f;

	private static Vec3 target;
	private static boolean hasY;

	private Gps() {
	}

	public static void register() {
		HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
			if (target != null) {
				render(graphics, Minecraft.getInstance(), tickCounter.getGameTimeDeltaPartialTick(true));
			}
		});
	}

	// ---- command ----------------------------------------------------------------------------------

	/** {@code args} = everything after ".gps". */
	public static void command(String[] args) {
		if (args.length == 1 && (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("clear")
				|| args[0].equalsIgnoreCase("stop"))) {
			target = null;
			print(Component.literal("Метка убрана").withStyle(ChatFormatting.GRAY));
			return;
		}

		try {
			if (args.length == 2) {
				target = new Vec3(Double.parseDouble(args[0]) + 0.5, 0.0, Double.parseDouble(args[1]) + 0.5);
				hasY = false;
			} else if (args.length == 3) {
				target = new Vec3(Double.parseDouble(args[0]) + 0.5, Double.parseDouble(args[1]),
						Double.parseDouble(args[2]) + 0.5);
				hasY = true;
			} else {
				usage();
				return;
			}
		} catch (NumberFormatException exception) {
			usage();
			return;
		}

		print(Component.literal("Метка поставлена: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(coords()).withStyle(ChatFormatting.WHITE)));
	}

	private static void usage() {
		print(Component.literal("Использование: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(".gps <x> <z>").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(".gps <x> <y> <z>").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(", ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(".gps off").withStyle(ChatFormatting.WHITE)));
	}

	private static String coords() {
		int x = Mth.floor(target.x);
		int z = Mth.floor(target.z);
		return hasY ? x + " " + Mth.floor(target.y) + " " + z : x + " " + z;
	}

	private static void print(Component component) {
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[GPS] ").withStyle(ChatFormatting.DARK_AQUA).append(component));
		}
	}

	// ---- render -----------------------------------------------------------------------------------

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, float partialTick) {
		LocalPlayer player = client.player;

		if (player == null || client.level == null) {
			return;
		}

		Vec3 eye = player.getEyePosition(partialTick);
		// Without Y the marker floats at eye height, so it is always visible on the horizon.
		Vec3 point = hasY ? target : new Vec3(target.x, eye.y, target.z);
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		double distance = hasY ? Math.sqrt(dx * dx + dz * dz + Math.pow(target.y - player.getY(), 2))
				: Math.sqrt(dx * dx + dz * dz);

		if (distance <= ARRIVE) {
			target = null;
			print(Component.literal("Вы пришли!").withStyle(ChatFormatting.GREEN));
			return;
		}

		Font font = client.font;
		TriggerConfig config = TriggerBotClient.config();
		float scale = BASE_SCALE * (config == null ? 1.0f : config.gpsScale);
		String left = formatDistance(distance);
		// Compact pill: dot + distance, drawn at 85 % size.
		int innerW = 6 + 4 + font.width(left);
		int width = Math.round((innerW + PAD * 2) * scale);
		int height = Math.round((font.lineHeight + PAD * 2 - 1) * scale);

		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		Camera camera = client.gameRenderer.mainCamera();
		Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
		float[] p = TargetEsp.project(matrix, camera.position(), point, graphics);

		float px;
		float py;
		boolean onScreen = p != null && p[0] >= 0 && p[0] <= sw && p[1] >= 0 && p[1] <= sh;

		if (onScreen) {
			px = p[0];
			py = p[1];
		} else {
			// Behind you / off screen: stick to the left or right edge, in the direction to turn.
			float yawTo = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
			float diff = Mth.wrapDegrees(yawTo - player.getYRot());
			px = diff < 0 ? width / 2.0f + 6 : sw - width / 2.0f - 6;
			py = sh / 2.0f;
		}

		int x = Math.round(Math.clamp(px - width / 2.0f, 4, sw - width - 4));
		int y = Math.round(Math.clamp(py - height - 5, 4, sh - height - 4));

		// Small pin dot on the exact spot.
		if (onScreen) {
			UiDraw.fill(graphics, Math.round(px) - 2, Math.round(py) - 2, 4, 4, 2, Theme.accent());
		}

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale);

		int w0 = innerW + PAD * 2;
		int h0 = font.lineHeight + PAD * 2 - 1;
		HudStyle.panel(graphics, 0, 0, w0, h0, h0 / 2);
		UiDraw.fill(graphics, PAD, (h0 - 6) / 2, 6, 6, 3, Theme.accent());
		graphics.text(font, left, PAD + 10, PAD, HudStyle.TEXT, false);
		pose.popMatrix();

		if (!onScreen) {
			String arrow = px < sw / 2.0f ? "<" : ">";
			graphics.text(font, arrow, px < sw / 2.0f ? x - 6 : x + width + 2, y + height / 2 - 4, HudStyle.TEXT, true);
		}
	}

	private static String formatDistance(double blocks) {
		if (blocks >= 1000.0) {
			return String.format("%.1f км", blocks / 1000.0);
		}

		return Math.round(blocks) + " м";
	}
}
