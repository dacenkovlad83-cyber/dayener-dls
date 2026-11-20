package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Visuals → TargetESP: a spinning diamond of rounded corner brackets on the current target.
 *
 * <p>The target is the TriggerBot target, otherwise the living entity under the crosshair. After the
 * target is lost the marker fades out for a moment instead of disappearing at once.
 */
public final class TargetEsp {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "target_esp");
	private static final long FADE_NS = 350_000_000L;

	private static Entity last;
	private static long lostAt;

	private TargetEsp() {
	}

	public static void register(TriggerConfig config, TriggerBot bot) {
		HudElementRegistry.addFirst(ID, (graphics, tickCounter) -> {
			if (!config.targetEsp) {
				last = null;
				return;
			}

			Minecraft client = Minecraft.getInstance();

			if (client.player == null || client.level == null) {
				return;
			}

			Entity target = currentTarget(client, bot);
			float alpha = 1.0f;

			if (target != null) {
				last = target;
				lostAt = 0L;
			} else if (last != null) {
				if (lostAt == 0L) {
					lostAt = System.nanoTime();
				}

				alpha = 1.0f - (System.nanoTime() - lostAt) / (float) FADE_NS;

				if (alpha <= 0.0f || last.isRemoved()) {
					last = null;
					return;
				}

				target = last;
			} else {
				return;
			}

			float partial = tickCounter.getGameTimeDeltaPartialTick(true);

			if (config.targetEspMode == 1) {
				ghosts(graphics, client, config, target, partial, alpha);
			} else {
				draw(graphics, client, config, target, partial, alpha);
			}
		});
	}

	private static Entity currentTarget(Minecraft client, TriggerBot bot) {
		if (bot.getTarget() != null) {
			return bot.getTarget();
		}

		if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living && living.isAlive()) {
			return living;
		}

		return null;
	}

	private static void draw(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config, Entity target,
			float partialTick, float alpha) {
		Camera camera = client.gameRenderer.mainCamera();
		Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
		Vec3 cam = camera.position();
		Vec3 base = target.getPosition(partialTick);

		float[] center = project(matrix, cam, base.add(0.0, target.getBbHeight() * 0.5, 0.0), graphics);
		float[] top = project(matrix, cam, base.add(0.0, target.getBbHeight(), 0.0), graphics);
		float[] bottom = project(matrix, cam, base, graphics);

		if (center == null || top == null || bottom == null) {
			return;
		}

		float height = Math.abs(bottom[1] - top[1]);
		float size = Math.clamp(height * 0.55f, 14.0f, 140.0f) * config.targetEspSize;
		float angle = (float) ((System.nanoTime() / 1_000_000_000.0) * config.targetEspSpeed * Math.PI * 0.8);

		int a = Math.round(255 * Math.clamp(alpha, 0.0f, 1.0f));
		int main = config.targetEspAccent ? Theme.accent() : 0xFFFFFFFF;
		int color = (a << 24) | (main & 0x00FFFFFF);
		int glow = ((a / 4) << 24) | (Theme.accent() & 0x00FFFFFF);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(center[0], center[1]);
		pose.rotate((float) (Math.PI / 4.0) + angle);

		int half = Math.round(size / 2.0f);
		int len = Math.max(4, Math.round(size * 0.32f));
		int thick = Math.max(2, Math.round(size * 0.07f));

		brackets(graphics, half + 1, len + 2, thick + 2, glow);
		brackets(graphics, half, len, thick, color);

		pose.popMatrix();
	}

	// ---- "Призраки" -------------------------------------------------------------------------------

	public static final String[] MODES = {"Маркер", "Призраки"};
	private static final int GHOSTS = 3;
	private static final int TRAIL = 16;
	private static final double TRAIL_STEP = 0.034;
	private static final Matrix4f MATRIX = new Matrix4f();

	/**
	 * Ghosts: three glowing orbs fly around the target on tilted orbits, each followed by a fading tail of
	 * smaller orbs. Everything is projected from world space, so it follows distance and perspective.
	 */
	private static void ghosts(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config, Entity target,
			float partialTick, float alpha) {
		Camera camera = client.gameRenderer.mainCamera();
		Matrix4f matrix = camera.getViewRotationProjectionMatrix(MATRIX);
		Vec3 cam = camera.position();
		Vec3 base = target.getPosition(partialTick);
		double h = target.getBbHeight();
		double radius = (target.getBbWidth() * 0.75 + 0.35) * config.targetEspWidth;

		float[] top = project(matrix, cam, base.add(0.0, h, 0.0), graphics);
		float[] bottom = project(matrix, cam, base, graphics);

		if (top == null || bottom == null) {
			return;
		}

		// Pixels per block at the target's distance, used to size the orbs.
		float pxPerBlock = Math.abs(bottom[1] - top[1]) / (float) Math.max(0.1, h);
		float orb = Math.clamp(pxPerBlock * 0.22f, 4.0f, 36.0f) * config.targetEspSize;
		double time = System.nanoTime() / 1_000_000_000.0 * Math.max(0.1, config.targetEspSpeed) * 2.0;
		int core = config.targetEspAccent ? Theme.accent() : 0xFFFFFFFF;
		int glow = Theme.accent();
		float a = Math.clamp(alpha, 0.0f, 1.0f);
		double cx = base.x - cam.x;
		double cy = base.y - cam.y;
		double cz = base.z - cam.z;

		for (int g = 0; g < GHOSTS; g++) {
			double phase = g * (Math.PI * 2.0 / GHOSTS);

			// From the tail end to the head, so the head is drawn on top.
			for (int k = TRAIL - 1; k >= 0; k--) {
				double t = time - k * TRAIL_STEP;
				double angle = t + phase;
				double y = h * 0.5 + Math.sin(t * 1.3 + phase * 1.7) * h * 0.36;
				float[] p = projectRel(matrix, cx + Math.cos(angle) * radius, cy + y, cz + Math.sin(angle) * radius, graphics);

				if (p == null) {
					continue;
				}

				float fade = 1.0f - k / (float) TRAIL;
				float ease = fade * fade * (3.0f - 2.0f * fade); // smoothstep: soft tail
				float size = orb * (0.3f + 0.7f * ease);

				if (k == 0) {
					// Soft halo around the head: a few wide, very transparent circles.
					circle(graphics, p[0], p[1], size * 2.6f, Math.round(26 * a), glow);
					circle(graphics, p[0], p[1], size * 1.9f, Math.round(40 * a), glow);
					circle(graphics, p[0], p[1], size * 1.35f, Math.round(70 * a), glow);
				}

				circle(graphics, p[0], p[1], size, Math.round((k < 3 ? 245 : 170) * ease * a), k < 3 ? core : glow);
			}
		}
	}

	/** Same as {@link #project}, for a point already relative to the camera (no Vec3 allocations). */
	private static float[] projectRel(Matrix4f matrix, double x, double y, double z, GuiGraphicsExtractor graphics) {
		Vector4f v = new Vector4f((float) x, (float) y, (float) z, 1.0f);
		matrix.transform(v);

		if (v.w <= 0.05f) {
			return null;
		}

		return new float[] {(v.x / v.w * 0.5f + 0.5f) * graphics.guiWidth(), (0.5f - v.y / v.w * 0.5f) * graphics.guiHeight()};
	}

	/** Perfectly round, anti-aliased dot centred on (x, y), drawn in screen pixels. */
	private static void circle(GuiGraphicsExtractor graphics, float x, float y, float diameter, int alpha, int rgb) {
		if (alpha <= 0 || diameter < 0.5f) {
			return;
		}

		UiDraw.fill(graphics, x - diameter / 2.0f, y - diameter / 2.0f, diameter, diameter, diameter / 2.0f,
				(Math.min(255, alpha) << 24) | (rgb & 0x00FFFFFF));
	}

	/** Four rounded L-shaped corners of a square with half-size {@code half}. */
	private static void brackets(GuiGraphicsExtractor graphics, int half, int len, int thick, int color) {
		int r = thick / 2;

		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				int cornerX = sx < 0 ? -half : half - thick;
				int cornerY = sy < 0 ? -half : half - thick;
				int barX = sx < 0 ? -half : half - len;
				int barY = sy < 0 ? -half : half - len;

				UiDraw.fill(graphics, barX, cornerY, len, thick, r, color);
				UiDraw.fill(graphics, cornerX, barY + (sy < 0 ? thick : 0), thick, len - thick, r, color);
			}
		}
	}

	static float[] project(Matrix4f matrix, Vec3 cam, Vec3 pos, GuiGraphicsExtractor graphics) {
		Vector4f v = new Vector4f((float) (pos.x - cam.x), (float) (pos.y - cam.y), (float) (pos.z - cam.z), 1.0f);
		matrix.transform(v);

		if (v.w <= 0.05f) {
			return null;
		}

		float x = (v.x / v.w * 0.5f + 0.5f) * graphics.guiWidth();
		float y = (0.5f - v.y / v.w * 0.5f) * graphics.guiHeight();
		return new float[] {x, y};
	}
}
