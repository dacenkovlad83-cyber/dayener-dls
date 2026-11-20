package com.triger.triggerbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * Movement → NoPush: the local player is not pushed by other players / mobs, out of blocks, by water
 * currents, or pulled by someone's fishing rod — each can be switched separately.
 */
public final class NoPush {
	private NoPush() {
	}

	private static TriggerConfig on() {
		TriggerConfig c = TriggerBotClient.config();
		return c != null && c.noPush ? c : null;
	}

	private static boolean isMe(Object entity) {
		return entity != null && entity == Minecraft.getInstance().player;
	}

	public static boolean entities(Entity self, Entity other) {
		TriggerConfig c = on();
		return c != null && c.noPushEntities && (isMe(self) || isMe(other));
	}

	public static boolean blocks(Object self) {
		TriggerConfig c = on();
		return c != null && c.noPushBlocks && isMe(self);
	}

	public static boolean water(Object self) {
		TriggerConfig c = on();
		return c != null && c.noPushWater && isMe(self);
	}

	/** A velocity packet for us while someone's fishing hook is stuck in us = the rod pull. */
	public static boolean rodPull(int entityId) {
		TriggerConfig c = on();
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;

		if (c == null || !c.noPushRod || player == null || client.level == null || entityId != player.getId()
				|| !client.isSameThread()) {
			return false;
		}

		return !client.level.getEntitiesOfClass(FishingHook.class, player.getBoundingBox().inflate(64.0),
				hook -> hook.getHookedIn() == player).isEmpty();
	}
}
