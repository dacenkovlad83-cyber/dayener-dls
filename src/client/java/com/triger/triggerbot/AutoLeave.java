package com.triger.triggerbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Utility → AutoLeave: leaves the world when health is low or a non-whitelisted player comes close.
 * Turns itself off after leaving, so rejoining does not kick you again straight away.
 */
public final class AutoLeave {
	private AutoLeave() {
	}

	/**
	 * @return {@code true} when the client just left the world
	 */
	public static boolean tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (!config.autoLeave || player == null || client.level == null || player.isSpectator()) {
			return false;
		}

		String reason = null;
		float health = player.getHealth();

		if (health > 0.0f && health <= config.autoLeaveHealth) {
			reason = String.format("мало HP (%.1f)", health);
		}

		if (reason == null && config.autoLeaveDistance > 0) {
			for (Player other : client.level.players()) {
				if (other == player || other.isSpectator() || config.isFriend(other.getUUID())) {
					continue;
				}

				if (player.distanceTo(other) <= config.autoLeaveDistance) {
					reason = "рядом игрок " + other.getName().getString();
					break;
				}
			}
		}

		if (reason == null) {
			return false;
		}

		config.autoLeave = false;
		config.save();
		TriggerBotClient.LOGGER.info("AutoLeave: {}", reason);
		client.disconnectFromWorld(Component.literal("AutoLeave: " + reason));
		return true;
	}
}
