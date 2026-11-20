package com.triger.triggerbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Core trigger bot logic. Called once per client tick.
 *
 * <p>Speed: hits as soon as the weapon is charged (a fresh random 92–97 % threshold and a small random wait
 * each time, so the timing is never the same). There is no CPS limit.
 *
 * <p>Crits: when a crit is wanted, the bot waits only while you are going up; the moment you start falling
 * (even after bumping your head in a 2-block room) it hits at once, without the random wait, with any charge
 * above the vanilla crit minimum. Sprinting cancels crits in vanilla, so sprint is dropped right before.
 */
public final class TriggerBot {
	/** Vanilla gives a crit only above this attack strength. */
	private static final float CRIT_CHARGE = 0.905f;

	private final TriggerConfig config;
	private boolean enabled;
	private Entity target;
	private boolean ready;
	private long maceNextAt;
	/** Charge needed for the next normal hit, extra random wait, and when the charge was reached. */
	private float threshold = 0.95f;
	private long jitterMs;
	private long chargedSince = -1L;

	public TriggerBot(TriggerConfig config) {
		this.config = config;
	}

	public void tick(Minecraft client) {
		target = null;
		ready = false;
		LocalPlayer player = client.player;

		if (!enabled || player == null || client.level == null || client.gameMode == null) {
			return;
		}

		// Never act while a menu (inventory, chat, our own screen, ...) is open.
		if (client.gui.screen() != null || client.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			return;
		}

		// Do not interrupt eating / drinking (AutoEat or by hand).
		if (player.isUsingItem()) {
			return;
		}

		Entity entity = pickTarget(client, player);

		if (entity == null || !TargetFilter.isValid(client, entity, config)) {
			return;
		}

		double maxDistance = config.range + 0.5;

		if (player.distanceToSqr(entity) > maxDistance * maxDistance) {
			return;
		}

		if (!config.throughWalls && !player.hasLineOfSight(entity)) {
			return;
		}

		target = entity;
		long now = System.currentTimeMillis();
		float charge = player.getAttackStrengthScale(0.5f);
		boolean crit = false;

		// Mace smash: the bonus comes from the fall, not from the charge — hit as soon as a smash is possible.
		if (config.maceNoCharge && player.getMainHandItem().is(Items.MACE) && MaceItem.canSmashAttack(player)) {
			ready = now >= maceNextAt;
		} else if (wantsCrit(player)) {
			crit = true;
			ready = critWindow(player) && charge >= CRIT_CHARGE;
		} else {
			if (charge < threshold) {
				chargedSince = -1L;
			} else if (chargedSince < 0L) {
				chargedSince = now;
			}

			ready = charge >= threshold && now - chargedSince >= jitterMs;
		}

		if (!ready) {
			return;
		}

		if (crit && player.isSprinting()) {
			// Vanilla: no crit while sprinting. Tell the server we stopped, right before the hit.
			player.setSprinting(false);
			player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
		}

		client.gameMode.attack(player, entity);
		player.swing(InteractionHand.MAIN_HAND);

		maceNextAt = now + 250L; // one smash per fall, not a burst
		rollTiming();
	}

	/** Entity under the crosshair; with "through walls" a ray that ignores blocks. */
	private Entity pickTarget(Minecraft client, LocalPlayer player) {
		// AimAssist silent: the server-side rotation is on a target even though the crosshair is not.
		Entity silent = AimAssist.silentTarget();

		if (silent != null) {
			return silent;
		}

		if (!config.throughWalls) {
			HitResult hit = client.hitResult;
			return hit != null && hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult e ? e.getEntity() : null;
		}

		double reach = config.range + 0.5;
		Vec3 eye = player.getEyePosition(1.0f);
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(reach));
		AABB area = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, area,
				e -> !e.isSpectator() && e.isPickable() && e != player, reach * reach);
		return hit == null ? null : hit.getEntity();
	}

	/** Should this hit wait for a crit? */
	private boolean wantsCrit(LocalPlayer player) {
		// Crits are impossible while blind (vanilla) or in water / on ladders / riding: just hit.
		if (player.hasEffect(MobEffects.BLINDNESS) || player.hasEffect(MobEffects.DARKNESS)
				|| player.isInWater() || player.onClimbable() || player.isPassenger() || player.getAbilities().flying) {
			return false;
		}

		return switch (config.critMode) {
			case OFF -> false;
			case ONLY_CRITS -> true;
			// Smart: on the ground hit normally; in the air (jumping) wait for the crit.
			case SMART -> !player.onGround();
		};
	}

	/** Falling right now (works with a low ceiling too: after the head bump you fall at once). */
	private static boolean critWindow(LocalPlayer player) {
		return !player.onGround() && (player.fallDistance > 0.0 || player.getDeltaMovement().y < -0.01);
	}

	/** New random timing for the next normal hit. */
	private void rollTiming() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		threshold = 0.92f + random.nextFloat() * 0.05f;
		jitterMs = config.fastRandomSpread <= 0 ? 0L : random.nextLong(config.fastRandomSpread + 1L);
		chargedSince = -1L;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;

		if (!enabled) {
			target = null;
			ready = false;
		} else {
			chargedSince = -1L;
		}
	}

	public Entity getTarget() {
		return target;
	}

	public boolean isReady() {
		return ready;
	}
}
