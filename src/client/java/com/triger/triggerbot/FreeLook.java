package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Visuals → FreeLook: look around with the mouse while the player keeps facing (and walking) forward.
 * With a key set it works while the key is held; without one, while the module is on.
 */
public final class FreeLook {
	private static boolean active;
	private static float yaw;
	private static float pitch;
	private static CameraType savedCamera;

	private FreeLook() {
	}

	public static boolean active() {
		return active;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	/** Mouse look (same sensitivity factor as {@code Entity.turn}). */
	public static void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch = Math.clamp(pitch + (float) dy * 0.15f, -90.0f, 90.0f);
	}

	public static void tick(Minecraft client, TriggerConfig config) {
		boolean want = false;

		if (config.freeLook && client.player != null && !config.freecam) {
			if (config.freeLookKey.isEmpty()) {
				want = true;
			} else if (client.gui.screen() == null) {
				want = Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(config.freeLookKey));
			} else {
				want = active; // keep the state while a screen is open
			}
		}

		if (want && !active) {
			active = true;
			yaw = client.player.getYRot();
			pitch = client.player.getXRot();
			savedCamera = client.options.getCameraType();

			if (config.freeLookThirdPerson && savedCamera.isFirstPerson()) {
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else if (!want && active) {
			active = false;

			if (savedCamera != null) {
				client.options.setCameraType(savedCamera);
				savedCamera = null;
			}
		}
	}
}
