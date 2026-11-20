package com.triger.triggerbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat → AutoTotem: puts a totem into the off hand when HP is low, or when an enemy with a mace or an end
 * crystal is nearby.
 * <ul>
 *     <li>Short random delay (30–120 ms), then — if you are running on the ground — you stop for exactly one
 *     tick and the swap is sent on that still tick. In the air it is sent at once.</li>
 *     <li>While a swap is in progress nothing new is scheduled (no double stops).</li>
 *     <li>Totems on cooldown are skipped; optionally plain totems (no custom name) are used first, so named /
 *     special server totems are saved.</li>
 *     <li>Golden hearts (absorption) can count towards HP or not.</li>
 * </ul>
 */
public final class AutoTotem {
	private static final int OFFHAND_BUTTON = 40;
	/** After a swap, give the server a moment before trying again. */
	private static final long AFTER_SWAP_MS = 250L;

	private long dueAt = -1L;
	private long quietUntil;

	public void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (!config.autoTotem || player == null || client.level == null || client.gameMode == null
				|| player.isCreative() || player.isSpectator() || !player.isAlive()) {
			this.dueAt = -1L;
			return;
		}

		if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING) || InvSync.busy()
				|| System.currentTimeMillis() < this.quietUntil) {
			this.dueAt = -1L;
			return;
		}

		Screen screen = client.gui.screen();

		// Another container (chest, furnace, ...) is open: our inventory slots are not addressable right now.
		if (screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)) {
			return;
		}

		if (!inDanger(client, player, config) || findTotem(player, config) < 0) {
			this.dueAt = -1L;
			return;
		}

		long now = System.currentTimeMillis();

		if (this.dueAt < 0L) {
			this.dueAt = now + ThreadLocalRandom.current().nextLong(30L, 121L);
			return;
		}

		if (now < this.dueAt) {
			return;
		}

		if (InvSync.run(client, () -> swapToOffhand(client, config))) {
			this.dueAt = -1L;
			this.quietUntil = now + AFTER_SWAP_MS;
		}
	}

	private static void swapToOffhand(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (player == null || client.gameMode == null || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return;
		}

		int slot = findTotem(player, config);

		if (slot < 0) {
			return;
		}

		int menuSlot = slot < Inventory.getSelectionSize() ? slot + 36 : slot;
		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, OFFHAND_BUTTON,
				ContainerInput.SWAP, player);
	}

	private static boolean inDanger(Minecraft client, LocalPlayer player, TriggerConfig config) {
		float health = player.getHealth() + (config.autoTotemAbsorption ? player.getAbsorptionAmount() : 0.0f);

		if (health <= config.autoTotemHealth) {
			return true;
		}

		if (AutoSwap.recentlySwapped()) {
			return false; // you just swapped by hand: don't undo it because of a mace / crystal nearby
		}

		double range = config.autoTotemRange;

		if (config.autoTotemMace) {
			for (Player other : client.level.players()) {
				if (other != player && !other.isSpectator() && !config.isFriend(other.getUUID())
						&& other.getMainHandItem().is(Items.MACE) && player.distanceTo(other) <= range) {
					return true;
				}
			}
		}

		if (config.autoTotemCrystals) {
			return !client.level.getEntitiesOfClass(EndCrystal.class, player.getBoundingBox().inflate(range)).isEmpty();
		}

		return false;
	}

	/**
	 * Best totem slot: never one on cooldown; with "plain first" a totem without a custom name wins, otherwise
	 * any. Main inventory before the hotbar, so the hotbar stays as it is when possible.
	 */
	private static int findTotem(LocalPlayer player, TriggerConfig config) {
		Inventory inventory = player.getInventory();
		int any = -1;

		for (int i = 0; i < 36; i++) {
			int slot = (i + Inventory.getSelectionSize()) % 36; // 9..35, then 0..8
			ItemStack stack = inventory.getItem(slot);

			if (!stack.is(Items.TOTEM_OF_UNDYING) || player.getCooldowns().isOnCooldown(stack)) {
				continue;
			}

			if (!config.autoTotemPlainFirst || !stack.has(DataComponents.CUSTOM_NAME)) {
				return slot;
			}

			if (any < 0) {
				any = slot;
			}
		}

		return any;
	}
}
