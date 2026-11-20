package com.triger.triggerbot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Semi transparent HUD overlay and the world-space target highlight.
 */
public final class TargetHud {
	private static final int PANEL_BG = 0x99101409;
	private static final int PANEL_BORDER = 0xCC3A482E;
	private static final int TEXT_ON = 0xFFEDEFEA;
	private static final int TEXT_OFF = 0xB3C3CBC0;
	private static final int WAIT = 0xFFB6A24A;
	private static final int OFF_DOT = 0xB35B6557;

	private TargetHud() {
	}

	public static void register(TriggerConfig config, TriggerBot bot) {
		HudElementRegistry.addLast(TriggerBotClient.ID, (extractor, tickCounter) -> {
			if (!config.showHud) {
				return;
			}

			Minecraft client = Minecraft.getInstance();

			if (client.player == null) {
				return;
			}

			boolean on = bot.isEnabled();
			int x = 6;
			int barW = 100;
			int barH = 16;
			int y = extractor.guiHeight() - barH - 6;

			UiDraw.hudPanel(extractor, x, y, barW, barH, barH / 2);
			UiDraw.fill(extractor, x + 6, y + 6, 5, 5, 2, on ? Theme.accent() : OFF_DOT);
			extractor.text(client.font, Component.literal("TriggerBot"), x + 15, y + 4, on ? TEXT_ON : TEXT_OFF);

			String status = on ? "ON" : "OFF";
			extractor.text(client.font, Component.literal(status),
					x + barW - 8 - client.font.width(status), y + 4, on ? Theme.accent() : TEXT_OFF);

			Entity target = bot.getTarget();

			if (on && target != null) {
				String name = target.getName().getString();
				int lineW = client.font.width(name) + 20;
				int lineY = y - 18;

				UiDraw.hudPanel(extractor, x, lineY, lineW, 14, 7);
				UiDraw.fill(extractor, x + 6, lineY + 5, 4, 4, 2, bot.isReady() ? Theme.accent() : WAIT);
				extractor.text(client.font, Component.literal(name), x + 13, lineY + 3, TEXT_ON);
			}
		});

		LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
			Entity target = bot.getTarget();

			if (!config.showHighlight || !bot.isEnabled() || target == null) {
				return;
			}

			Vec3 camera = context.levelState().cameraRenderState.pos;
			AABB box = target.getBoundingBox().inflate(0.03).move(-camera.x, -camera.y, -camera.z);

			int color = bot.isReady()
					? ARGB.colorFromFloat(0.25f, 0.2f, 1.0f, 0.35f)
					: ARGB.colorFromFloat(0.15f, 1.0f, 0.85f, 0.2f);

			PoseStack poseStack = context.poseStack();
			poseStack.pushPose();
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
					(pose, buffer) -> drawFilledBox(pose, buffer, box, color));
			poseStack.popPose();
		});
	}

	private static void drawFilledBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
		float minX = (float) box.minX;
		float minY = (float) box.minY;
		float minZ = (float) box.minZ;
		float maxX = (float) box.maxX;
		float maxY = (float) box.maxY;
		float maxZ = (float) box.maxZ;

		// Front
		buffer.addVertex(pose, minX, minY, minZ).setColor(color);
		buffer.addVertex(pose, maxX, minY, minZ).setColor(color);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(color);
		buffer.addVertex(pose, minX, maxY, minZ).setColor(color);
		// Back
		buffer.addVertex(pose, maxX, minY, maxZ).setColor(color);
		buffer.addVertex(pose, minX, minY, maxZ).setColor(color);
		buffer.addVertex(pose, minX, maxY, maxZ).setColor(color);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(color);
		// Left
		buffer.addVertex(pose, minX, minY, maxZ).setColor(color);
		buffer.addVertex(pose, minX, minY, minZ).setColor(color);
		buffer.addVertex(pose, minX, maxY, minZ).setColor(color);
		buffer.addVertex(pose, minX, maxY, maxZ).setColor(color);
		// Right
		buffer.addVertex(pose, maxX, minY, minZ).setColor(color);
		buffer.addVertex(pose, maxX, minY, maxZ).setColor(color);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(color);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(color);
		// Top
		buffer.addVertex(pose, minX, maxY, minZ).setColor(color);
		buffer.addVertex(pose, maxX, maxY, minZ).setColor(color);
		buffer.addVertex(pose, maxX, maxY, maxZ).setColor(color);
		buffer.addVertex(pose, minX, maxY, maxZ).setColor(color);
		// Bottom
		buffer.addVertex(pose, minX, minY, minZ).setColor(color);
		buffer.addVertex(pose, minX, minY, maxZ).setColor(color);
		buffer.addVertex(pose, maxX, minY, maxZ).setColor(color);
		buffer.addVertex(pose, maxX, minY, minZ).setColor(color);
	}
}
