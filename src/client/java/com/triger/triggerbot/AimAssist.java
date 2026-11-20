package com.triger.triggerbot;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Combat → AimAssist: smoothly turns the view onto a target (every frame, so it is not jerky).
 *
 * <ul>
 *     <li>Rotation: free (yaw + pitch), horizontal only, or instant snap.</li>
 *     <li>Inside the hitbox it does nothing (dead zone); outside it pulls with speed × correction.</li>
 *     <li>Slows down near the target, smooth entry after a new target, optional mouse sensitivity factor,
 *     optional "only while the mouse moves".</li>
 *     <li>Target: by angle / distance / health, within distance and FOV; after a hit it sticks to that
 *     target for the hold time; "last hit only" aims only at the one you hit last.</li>
 *     <li>Silent: the rotation is only sent to the server, your camera does not move.</li>
 * </ul>
 */
public final class AimAssist {
	public static final String[] MODES = {"Свободная", "Горизонталь", "Резкая"};
	public static final String[] PRIORITIES = {"Угол", "Дистанция", "Здоровье"};

	private static Entity target;
	private static long acquiredAt;
	private static Entity lastHit;
	private static long lastHitAt;
	private static long lastFrame;

	/** Silent rotation state. */
	private static boolean silent;
	private static float silentYaw;
	private static float silentPitch;

	/** "Only while the mouse moves": our own last rotation, to tell mouse movement apart. */
	private static float lastYaw = Float.NaN;
	private static float lastPitch = Float.NaN;
	private static long mouseMovedAt;

	private AimAssist() {
	}

	public static void register(TriggerConfig config) {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				lastHit = entity;
				lastHitAt = System.currentTimeMillis();
			}

			return InteractionResult.PASS;
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> frame(Minecraft.getInstance(), config));
	}

	// ---- state used by the silent-rotation mixin and TriggerBot ----------------------------------

	public static boolean silentActive() {
		return silent;
	}

	public static float silentYaw() {
		return silentYaw;
	}

	public static float silentPitch() {
		return silentPitch;
	}

	/** Target the silent rotation is pointing into (for TriggerBot), or null. */
	public static Entity silentTarget() {
		if (!silent || target == null || !target.isAlive()) {
			return null;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && looksAt(player, target, silentYaw, silentPitch, TriggerBotClient.config().aimRange + 0.5)
				? target : null;
	}

	// ---- per frame -------------------------------------------------------------------------------

	private static void frame(Minecraft client, TriggerConfig c) {
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000.0f);
		lastFrame = now;
		LocalPlayer player = client.player;

		if (!c.aimAssist || player == null || client.level == null || client.gui.screen() != null
				|| !conditionsOk(client, player, c)) {
			release(player, dt, c);
			return;
		}

		trackMouse(player);

		if (c.aimOnlyMouse && System.currentTimeMillis() - mouseMovedAt > 150L) {
			release(player, dt, c);
			return;
		}

		Entity found = pick(client, player, c);

		if (found == null) {
			release(player, dt, c);
			return;
		}

		if (found != target) {
			target = found;
			acquiredAt = System.currentTimeMillis();
		}

		if (c.aimSilent && !silent) {
			silent = true;
			silentYaw = player.getYRot();
			silentPitch = player.getXRot();
		} else if (!c.aimSilent && silent) {
			silent = false;
		}

		float curYaw = silent ? silentYaw : player.getYRot();
		float curPitch = silent ? silentPitch : player.getXRot();
		double range = c.aimRange + 0.5;

		// Dead zone: the crosshair (or the silent rotation) is already inside the hitbox.
		if (looksAt(player, target, curYaw, curPitch, range)) {
			rememberOwn(player);
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 point = aimPoint(target, eye, c);
		Vec3 d = point.subtract(eye);
		float wantYaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0f;
		float wantPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));

		float dYaw = Mth.wrapDegrees(wantYaw - curYaw);
		float dPitch = c.aimMode == 1 ? 0.0f : wantPitch - curPitch;
		float total = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);

		if (total < 0.01f) {
			return;
		}

		float stepYaw;
		float stepPitch;

		if (c.aimMode == 2) {
			stepYaw = dYaw;
			stepPitch = dPitch;
		} else {
			// Degrees per second: speed × correction (0.5 = ×1).
			float degPerSec = c.aimSpeed * 12.0f * (c.aimCorrection * 2.0f);

			if (c.aimSlowdown && total < c.aimSlowAngle) {
				degPerSec *= Math.max(0.15f, total / c.aimSlowAngle);
			}

			if (c.aimSmoothEntry) {
				degPerSec *= Math.min(1.0f, 0.15f + (System.currentTimeMillis() - acquiredAt) / 400.0f);
			}

			if (c.aimUseSens) {
				degPerSec *= 0.5f + client.options.sensitivity().get().floatValue();
			}

			float max = degPerSec * dt;
			float k = Math.min(1.0f, max / total);
			stepYaw = dYaw * k;
			stepPitch = dPitch * k;
		}

		if (silent) {
			silentYaw += stepYaw;
			silentPitch = Mth.clamp(silentPitch + stepPitch, -90.0f, 90.0f);
		} else {
			float newPitch = Mth.clamp(player.getXRot() + stepPitch, -90.0f, 90.0f);
			player.setYRot(player.getYRot() + stepYaw);
			player.setXRot(newPitch);
			player.yRotO += stepYaw;
			player.xRotO = Mth.clamp(player.xRotO + stepPitch, -90.0f, 90.0f);
			rememberOwn(player);
		}
	}

	/** No target: a silent rotation glides back to the camera, then the override ends. */
	private static void release(LocalPlayer player, float dt, TriggerConfig c) {
		target = null;

		if (!silent) {
			return;
		}

		if (player == null) {
			silent = false;
			return;
		}

		float dYaw = Mth.wrapDegrees(player.getYRot() - silentYaw);
		float dPitch = player.getXRot() - silentPitch;
		float total = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);

		if (total < 1.0f || c.aimMode == 2) {
			silent = false;
			return;
		}

		float k = Math.min(1.0f, c.aimSpeed * 18.0f * dt / total);
		silentYaw += dYaw * k;
		silentPitch += dPitch * k;
	}

	private static void trackMouse(LocalPlayer player) {
		if (!Float.isNaN(lastYaw) && (Math.abs(player.getYRot() - lastYaw) > 0.01f || Math.abs(player.getXRot() - lastPitch) > 0.01f)) {
			mouseMovedAt = System.currentTimeMillis();
		}

		rememberOwn(player);
	}

	private static void rememberOwn(LocalPlayer player) {
		lastYaw = player.getYRot();
		lastPitch = player.getXRot();
	}

	// ---- target ----------------------------------------------------------------------------------

	private static boolean conditionsOk(Minecraft client, LocalPlayer player, TriggerConfig c) {
		if (c.aimOnlyWeapon) {
			String path = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getPath();

			if (!(path.endsWith("_sword") || path.endsWith("_axe") || path.endsWith("_spear") || path.equals("mace")
					|| path.equals("trident"))) {
				return false;
			}
		}

		return !c.aimOnlyLmb || client.options.keyAttack.isDown();
	}

	private static Entity pick(Minecraft client, LocalPlayer player, TriggerConfig c) {
		double range = c.aimRange + 0.5;
		long now = System.currentTimeMillis();
		boolean holding = lastHit != null && lastHit.isAlive() && !lastHit.isRemoved()
				&& now - lastHitAt < (long) (c.aimHold * 1000.0f);

		if (holding && valid(player, lastHit, c, range)) {
			return lastHit;
		}

		if (c.aimLastHitOnly) {
			return null; // only the one you hit last (and it is not available)
		}

		List<LivingEntity> list = client.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
				e -> valid(player, e, c, range));

		Comparator<LivingEntity> order = switch (c.aimPriority) {
			case 1 -> Comparator.comparingDouble(e -> player.distanceToSqr(e));
			case 2 -> Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount());
			default -> Comparator.comparingDouble(e -> angleTo(player, e));
		};

		Optional<LivingEntity> best = list.stream().min(order);
		return best.orElse(null);
	}

	private static boolean valid(LocalPlayer player, Entity e, TriggerConfig c, double range) {
		if (e == player || !(e instanceof LivingEntity living) || !living.isAlive() || living.isDeadOrDying()
				|| e.isSpectator() || e.isInvisible()) {
			return false;
		}

		if (e instanceof Player p) {
			if (!c.aimPlayers || p.isCreative() || c.isFriend(p.getUUID())) {
				return false;
			}
		} else if (e instanceof Monster) {
			if (!c.aimHostile) {
				return false;
			}
		} else if (!(e instanceof Mob) || !c.aimPassive) {
			return false;
		}

		if (closestDistance(player, e) > range) {
			return false;
		}

		if (c.aimFov < 360.0f && angleTo(player, e) > c.aimFov / 2.0f) {
			return false;
		}

		return !c.aimVisible || player.hasLineOfSight(e);
	}

	/** Angle (degrees) between where you look and the target's centre. */
	private static double angleTo(LocalPlayer player, Entity e) {
		Vec3 look = player.getViewVector(1.0f);
		Vec3 to = e.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
		return Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1.0, 1.0)));
	}

	private static double closestDistance(LocalPlayer player, Entity e) {
		Vec3 eye = player.getEyePosition();
		AABB box = e.getBoundingBox();
		double x = Mth.clamp(eye.x, box.minX, box.maxX);
		double y = Mth.clamp(eye.y, box.minY, box.maxY);
		double z = Mth.clamp(eye.z, box.minZ, box.maxZ);
		return eye.distanceTo(new Vec3(x, y, z));
	}

	/**
	 * Where to aim: the hitbox centre horizontally; vertically your eye height clamped into the hitbox.
	 * "Свобода вниз" moves the lower limit: − keeps the aim higher, + lets it go lower.
	 */
	private static Vec3 aimPoint(Entity e, Vec3 eye, TriggerConfig c) {
		AABB box = e.getBoundingBox();
		double h = box.getYsize();
		double lower = box.minY + h * Mth.clamp(0.25 - c.aimFreeDown * 0.25, 0.0, 0.5);
		double upper = box.maxY - h * 0.12;
		double y = Mth.clamp(eye.y, Math.min(lower, upper), upper);
		return new Vec3((box.minX + box.maxX) / 2.0, y, (box.minZ + box.maxZ) / 2.0);
	}

	/** Does a ray with this yaw / pitch hit the entity's hitbox within range? */
	private static boolean looksAt(LocalPlayer player, Entity e, float yaw, float pitch, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
		AABB box = e.getBoundingBox().inflate(e.getPickRadius());
		return box.contains(eye) || box.clip(eye, eye.add(dir.scale(range))).isPresent();
	}
}
