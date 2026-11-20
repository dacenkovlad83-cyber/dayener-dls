package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Visuals → NameTags. Draws compact tags above players on the HUD layer.
 *
 * <p>The name is taken from the server's tab list (or the scoreboard team), so donor ranks and prefixes
 * come out exactly like on the server — including custom glyphs from the server resource pack, which the
 * font renders automatically. Vanilla player name tags are hidden while the module is on.
 */
public final class NameTags {
	private static final Identifier ID = Identifier.fromNamespaceAndPath(TriggerBotClient.MOD_ID, "nametags");
	private static final int BG = 0x90080A07;
	private static final int BORDER = 0x7042543A;
	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private NameTags() {
	}

	public static void register(TriggerConfig config) {
		HudElementRegistry.addFirst(ID, (graphics, tickCounter) -> {
			if (!config.nameTags) {
				return;
			}

			Minecraft client = Minecraft.getInstance();

			if (client.player == null || client.level == null) {
				return;
			}

			render(graphics, client, config, tickCounter.getGameTimeDeltaPartialTick(true));
		});
	}

	public static boolean hidesVanilla() {
		TriggerConfig config = TriggerBotClient.config();
		return config != null && config.nameTags;
	}

	private record Tag(Player player, float x, float y, double distance) {
	}

	private static void render(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config, float partialTick) {
		Camera camera = client.gameRenderer.mainCamera();
		Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
		Vec3 cameraPos = camera.position();
		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		double maxDistance = config.nameTagsRange;

		List<Tag> tags = new ArrayList<>();

		for (Player player : client.level.players()) {
			if (player == client.player || player.isSpectator() || !player.isAlive()) {
				continue;
			}

			double distance = client.player.distanceTo(player);

			if (distance > maxDistance) {
				continue;
			}

			Vec3 pos = player.getPosition(partialTick).add(0.0, player.getBbHeight() + 0.3, 0.0);
			Vector4f v = new Vector4f(
					(float) (pos.x - cameraPos.x),
					(float) (pos.y - cameraPos.y),
					(float) (pos.z - cameraPos.z),
					1.0f);
			matrix.transform(v);

			if (v.w <= 0.05f) {
				continue; // behind the camera
			}

			float sx = (v.x / v.w * 0.5f + 0.5f) * screenW;
			float sy = (0.5f - v.y / v.w * 0.5f) * screenH;

			if (sx < -100 || sx > screenW + 100 || sy < -60 || sy > screenH + 60) {
				continue;
			}

			tags.add(new Tag(player, sx, sy, distance));
		}

		// Far tags first, so the closest one ends up on top.
		tags.sort(Comparator.comparingDouble(Tag::distance).reversed());

		for (Tag tag : tags) {
			drawTag(graphics, client, config, tag);
		}
	}

	private static void drawTag(GuiGraphicsExtractor graphics, Minecraft client, TriggerConfig config, Tag tag) {
		Font font = client.font;
		Player player = tag.player();
		Component line = buildLine(client, config, player);

		int textW = font.width(line);
		int boxW = textW + 8;
		int boxH = 11;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(tag.x(), tag.y());
		pose.scale(config.nameTagsScale * 0.75f);

		UiDraw.hudPanel(graphics, -boxW / 2, -boxH, boxW, boxH, boxH / 2);
		graphics.text(font, line, -textW / 2, -boxH + 2, 0xFFFFFFFF, true);

		List<ItemStack> items = collectItems(config, player);

		if (!items.isEmpty()) {
			int rowW = items.size() * 16;
			int x = -rowW / 2;
			int y = -boxH - 18;

			for (ItemStack stack : items) {
				graphics.item(stack, x, y);
				graphics.itemDecorations(font, stack, x, y);
				x += 16;
			}
		}

		pose.popMatrix();
	}

	private static Component buildLine(Minecraft client, TriggerConfig config, Player player) {
		MutableComponent line = Component.empty();
		line.append(displayName(client, config, player));

		if (config.nameTagsHealth) {
			float health = player.getHealth() + player.getAbsorptionAmount();
			float ratio = health / Math.max(1.0f, player.getMaxHealth());
			ChatFormatting color = ratio > 0.66f ? ChatFormatting.GREEN : ratio > 0.33f ? ChatFormatting.YELLOW : ChatFormatting.RED;
			line.append(Component.literal(" " + Math.round(health)).withStyle(color));
		}

		if (config.isFriend(player.getUUID())) {
			line.append(Component.literal(" [F]").withStyle(ChatFormatting.AQUA));
		}

		return line;
	}

	/**
	 * Tab list name first (most servers put donor ranks there), then the team-formatted name, then the plain
	 * name. Custom prefix glyphs come from the server resource pack and render through the normal font.
	 */
	private static Component displayName(Minecraft client, TriggerConfig config, Player player) {
		if (!config.nameTagsPrefixes) {
			return Component.literal(player.getName().getString());
		}

		if (client.getConnection() != null) {
			PlayerInfo info = client.getConnection().getPlayerInfo(player.getUUID());

			if (info != null && info.getTabListDisplayName() != null) {
				return info.getTabListDisplayName();
			}
		}

		return player.getDisplayName();
	}

	private static List<ItemStack> collectItems(TriggerConfig config, Player player) {
		List<ItemStack> items = new ArrayList<>();

		if (config.nameTagsItems && !player.getMainHandItem().isEmpty()) {
			items.add(player.getMainHandItem());
		}

		if (config.nameTagsArmor) {
			for (EquipmentSlot slot : ARMOR) {
				ItemStack stack = player.getItemBySlot(slot);

				if (!stack.isEmpty()) {
					items.add(stack);
				}
			}
		}

		if (config.nameTagsItems && !player.getOffhandItem().isEmpty()) {
			items.add(player.getOffhandItem());
		}

		return items;
	}
}
