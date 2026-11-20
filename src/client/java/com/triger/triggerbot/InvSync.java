package com.triger.triggerbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Runs inventory actions the way servers with anti-cheat accept them.
 *
 * <p>On the ground while walking / sprinting: speed and sprint are cut at once, the player stands still for
 * exactly {@code stopTicks} ticks (1 by default), and the click is sent at the end of that still tick. In the
 * air (jumping, gliding) or when already standing, the click goes out immediately and nothing is stopped.
 * Each further step runs on its own tick (swap → use → swap back). Only one sequence at a time.
 *
 * <p>Must be requested from code that runs before {@code Movement.tick} in the client tick (see
 * TriggerBotClient), otherwise the stop would last one tick longer.
 */
public final class InvSync {
	private static Runnable[] steps;
	private static int index;
	private static int wait;

	private InvSync() {
	}

	public static boolean busy() {
		return steps != null;
	}

	/** One still tick on the ground if moving, then the steps one per tick. */
	public static boolean run(Minecraft client, Runnable... sequence) {
		return start(client, 1, sequence);
	}

	/** Same, but never stops the player (for steps that only change the selected hotbar slot). */
	public static boolean runNoStop(Minecraft client, Runnable... sequence) {
		return start(client, 0, sequence);
	}

	public static boolean isMoving(LocalPlayer player) {
		return player.isSprinting() || player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4
				|| player.input.hasForwardImpulse();
	}

	public static boolean isGrounded(LocalPlayer player) {
		return player.onGround() && !player.isFallFlying();
	}

	private static boolean start(Minecraft client, int stopTicks, Runnable... sequence) {
		LocalPlayer player = client.player;

		if (busy() || player == null || sequence.length == 0) {
			return false;
		}

		steps = sequence;
		index = 0;
		wait = 0;
		boolean grounded = isGrounded(player);

		if (stopTicks > 0 && grounded && isMoving(player)) {
			wait = stopTicks;
			Movement.stopNow(player);
			// Decremented once at the end of this tick, then covers the still tick(s) and every step tick.
			Movement.pauseTicks = Math.max(Movement.pauseTicks, stopTicks + sequence.length);
			return true;
		}

		if (stopTicks > 0 && grounded && sequence.length > 1) {
			Movement.pauseTicks = Math.max(Movement.pauseTicks, sequence.length);
		}

		step();
		return true;
	}

	/** Called once per client tick, before the modules. */
	public static void tick(Minecraft client) {
		if (steps == null) {
			return;
		}

		if (client.player == null || client.gameMode == null) {
			steps = null;
			return;
		}

		// The still tick has just been played: click now, at its end.
		if (wait > 0 && --wait > 0) {
			return;
		}

		step();
	}

	private static void step() {
		Runnable action = steps[index++];

		if (index >= steps.length) {
			steps = null;
		}

		action.run();
	}
}
