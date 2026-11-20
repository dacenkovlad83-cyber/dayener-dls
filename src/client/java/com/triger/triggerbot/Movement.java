package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Movement category: AutoSprint, InvMove, Freecam, AirStuck, plus the short movement pause used by AutoTotem.
 */
public final class Movement {
	/** Ticks during which player input is ignored (AutoTotem "pause"). */
	public static int pauseTicks;

	private static Vec3 camPos = Vec3.ZERO;
	private static Vec3 camPrev = Vec3.ZERO;
	private static float camYaw;
	private static float camPitch;

	private Movement() {
	}

	private static TriggerConfig cfg() {
		return TriggerBotClient.config();
	}

	// ---- state used by mixins -------------------------------------------------------------------

	public static boolean freecam() {
		TriggerConfig c = cfg();
		return c != null && c.freecam;
	}

	public static boolean airStuck() {
		TriggerConfig c = cfg();
		return c != null && c.airStuck;
	}

	/** Player movement input is replaced by "nothing pressed". */
	public static boolean blockInput() {
		LocalPlayer player = Minecraft.getInstance().player;
		boolean grounded = player != null && player.onGround() && !player.isFallFlying();
		return freecam() || airStuck() || (pauseTicks > 0 && grounded);
	}

	/** Kills horizontal speed and sprint right away (before an inventory click). */
	public static void stopNow(LocalPlayer player) {
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(0.0, motion.y, 0.0);
		player.setSprinting(false);
	}

	public static float camYaw() {
		return camYaw;
	}

	public static float camPitch() {
		return camPitch;
	}

	public static Vec3 camPos(float partialTick) {
		return camPrev.lerp(camPos, partialTick);
	}

	/** Mouse look while in freecam (same sensitivity factor as {@code Entity.turn}). */
	public static void turn(double dx, double dy) {
		camYaw += (float) dx * 0.15f;
		camPitch = Math.clamp(camPitch + (float) dy * 0.15f, -90.0f, 90.0f);
	}

	// ---- toggles --------------------------------------------------------------------------------

	public static void setFreecam(boolean on) {
		Minecraft client = Minecraft.getInstance();
		TriggerConfig c = cfg();

		if (on) {
			if (client.player == null) {
				return;
			}

			camPos = client.gameRenderer.mainCamera().position();
			camPrev = camPos;
			camYaw = client.player.getYRot();
			camPitch = client.player.getXRot();
		}

		c.freecam = on;
	}

	// ---- tick -----------------------------------------------------------------------------------

	public static void tick(Minecraft client, TriggerConfig c) {
		LocalPlayer player = client.player;

		if (player == null) {
			c.freecam = false;
			c.airStuck = false;
			pauseTicks = 0;
			return;
		}

		if (c.freecam) {
			moveFreecam(client.options, c);
		}

		if (c.autoSprint && !c.freecam && !c.airStuck && canSprint(player)) {
			player.setSprinting(true);
		}

		if (c.airStuck) {
			player.setDeltaMovement(Vec3.ZERO);
			player.setSprinting(false);
			player.resetFallDistance(); // no fall damage built up while frozen
		}

		if (pauseTicks > 0) {
			// Never brake while flying or in the air, only on the ground.
			if (player.onGround() && !player.isFallFlying()) {
				Vec3 motion = player.getDeltaMovement();
				player.setDeltaMovement(0.0, motion.y, 0.0);
				player.setSprinting(false);
			}

			pauseTicks--;
		}
	}

	private static boolean canSprint(LocalPlayer player) {
		return player.input.hasForwardImpulse()
				&& !player.isSprinting()
				&& !player.isShiftKeyDown()
				&& !player.isUsingItem()
				&& !player.horizontalCollision
				&& (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly);
	}

	private static void moveFreecam(Options options, TriggerConfig c) {
		camPrev = camPos;

		float forward = axis(options.keyUp, options.keyDown);
		float strafe = axis(options.keyLeft, options.keyRight);
		float vertical = axis(options.keyJump, options.keyShift);
		float boost = options.keySprint.isDown() ? 2.0f : 1.0f;

		double yaw = Math.toRadians(camYaw);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double speed = c.freecamSpeedXZ * boost;

		double dx = (-sin * forward + cos * strafe) * speed;
		double dz = (cos * forward + sin * strafe) * speed;
		double dy = vertical * c.freecamSpeedY * boost;

		camPos = camPos.add(dx, dy, dz);
	}

	private static float axis(KeyMapping positive, KeyMapping negative) {
		return (positive.isDown() ? 1.0f : 0.0f) - (negative.isDown() ? 1.0f : 0.0f);
	}

	// ---- InvMove --------------------------------------------------------------------------------

	/**
	 * Called right before the keyboard input is read. While an inventory (or our menu) is open, movement
	 * keys are refreshed from the real keyboard state, so the player keeps walking.
	 */
	public static void applyInvMove() {
		TriggerConfig c = cfg();
		Minecraft client = Minecraft.getInstance();
		Screen screen = client.gui.screen();

		if (c == null || !c.invMove || screen == null) {
			return;
		}

		boolean allowed = screen instanceof AbstractContainerScreen<?> || screen instanceof TriggerBotScreen;

		if (!allowed || screen.getFocused() instanceof EditBox
				|| (screen instanceof TriggerBotScreen menu && menu.isTyping())) {
			return;
		}

		Options o = client.options;
		Window window = client.getWindow();

		for (KeyMapping key : new KeyMapping[] {o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint}) {
			key.setDown(isPhysicallyDown(window, InputConstants.getKey(key.saveString())));
		}
	}

	public static boolean isPhysicallyDown(Window window, InputConstants.Key key) {
		if (key == null || key == InputConstants.UNKNOWN) {
			return false;
		}

		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
		}

		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(window, key.getValue());
		}

		return false;
	}
}
