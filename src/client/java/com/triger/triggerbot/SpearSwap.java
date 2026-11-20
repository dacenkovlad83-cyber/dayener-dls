package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import com.triger.triggerbot.mixin.MultiPlayerGameModeInvoker;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Combat → SpearSwap (attribute swap). On the key:
 * <ol>
 *     <li>a left click (hit) with the item in your hand,</li>
 *     <li>in the same tick the hotbar switches to the spear (sent to the server at once),</li>
 *     <li>after a few more ticks the previous slot is selected again.</li>
 * </ol>
 */
public final class SpearSwap {
	private static boolean wasDown;
	private static int previousSlot = -1;
	/** Ticks until the spear is taken; -1 = idle. */
	private static int spearIn = -1;
	/** Ticks until the old slot comes back; -1 = idle. */
	private static int backIn = -1;

	private SpearSwap() {
	}

	public static void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (player == null || client.gameMode == null) {
			wasDown = false;
			spearIn = -1;
			backIn = -1;
			return;
		}

		advance(player, config);

		if (!config.spearSwap || config.spearSwapKey.isEmpty()) {
			wasDown = false;
			return;
		}

		boolean down = client.gui.screen() == null
				&& Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(config.spearSwapKey));

		if (down && !wasDown && spearIn < 0 && backIn < 0) {
			start(client, player, config);
		}

		wasDown = down;
	}

	private static void start(Minecraft client, LocalPlayer player, TriggerConfig config) {
		if (findSpear(player.getInventory()) < 0) {
			player.sendOverlayMessage(Component.literal("SpearSwap: нет копья в хотбаре").withStyle(ChatFormatting.RED));
			return;
		}

		Inventory inventory = player.getInventory();
		previousSlot = inventory.getSelectedSlot();

		if (client.hitResult instanceof EntityHitResult hit && hit.getEntity().isAlive()) {
			// Hit with the current item, then the spear in the very same tick, and tell the server at once.
			client.gameMode.attack(player, hit.getEntity());
			player.swing(InteractionHand.MAIN_HAND);
			takeSpear(client, player, config);
			return;
		}

		// Nothing to hit: a normal left click, spear on the next tick.
		KeyMapping.click(InputConstants.getKey(client.options.keyAttack.saveString()));
		spearIn = 1;
	}

	private static void takeSpear(Minecraft client, LocalPlayer player, TriggerConfig config) {
		int spear = findSpear(player.getInventory());

		if (spear < 0) {
			return;
		}

		player.getInventory().setSelectedSlot(spear);
		((MultiPlayerGameModeInvoker) client.gameMode).dls$sendCarriedItem();
		backIn = Math.max(1, config.spearSwapBack);
	}

	private static void advance(LocalPlayer player, TriggerConfig config) {
		Inventory inventory = player.getInventory();

		if (spearIn >= 0 && --spearIn <= 0) {
			spearIn = -1;
			takeSpear(Minecraft.getInstance(), player, config);
			return;
		}

		if (backIn >= 0 && --backIn <= 0) {
			backIn = -1;

			if (previousSlot >= 0 && isSpear(player.getMainHandItem())) {
				inventory.setSelectedSlot(previousSlot);
			}

			previousSlot = -1;
		}
	}

	public static boolean isSpear(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_spear");
	}

	/** Best spear in the hotbar (netherite > diamond > iron > ...). */
	private static int findSpear(Inventory inventory) {
		int best = -1;
		int bestRank = -1;

		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			ItemStack stack = inventory.getItem(i);

			if (isSpear(stack)) {
				int rank = rank(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());

				if (rank > bestRank) {
					best = i;
					bestRank = rank;
				}
			}
		}

		return best;
	}

	private static int rank(String path) {
		String[] order = {"wooden", "golden", "stone", "copper", "iron", "diamond", "netherite"};

		for (int i = order.length - 1; i >= 0; i--) {
			if (path.startsWith(order[i])) {
				return i;
			}
		}

		return 0;
	}
}
