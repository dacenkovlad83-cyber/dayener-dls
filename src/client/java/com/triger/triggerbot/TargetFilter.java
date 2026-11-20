package com.triger.triggerbot;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/**
 * Decides whether an entity under the crosshair is a valid target.
 */
public final class TargetFilter {
	private TargetFilter() {
	}

	public static boolean isValid(Minecraft client, Entity entity, TriggerConfig config) {
		if (entity == null || entity == client.player || entity.isRemoved()) {
			return false;
		}

		if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isDeadOrDying()) {
			return false;
		}

		if (entity instanceof Player player) {
			if (!config.attackPlayers) {
				return false;
			}

			if (player.isSpectator() || player.isCreative()) {
				return false;
			}

			return !config.isFriend(player.getUUID());
		}

		if (entity instanceof Monster) {
			return config.attackHostile;
		}

		if (entity instanceof Animal) {
			return config.attackPassive;
		}

		if (entity instanceof Mob) {
			// Non-monster mobs (villagers, golems, ...) count as passive.
			return config.attackPassive;
		}

		return false;
	}
}
