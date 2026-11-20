package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Movement → ElytraSwap: one key swaps elytra ↔ chestplate, another key launches a firework from the
 * inventory. Every action waits 75 ms and pauses movement for one tick. Nothing is ever dropped: when the
 * inventory is full the elytra goes to the free hotbar slot right / left of the selected one, then any free
 * slot, then the off hand.
 */
public final class ElytraSwap {
	private static final long DELAY_NS = 75_000_000L;
	private static final int MENU_CHEST = 6;
	private static final int MENU_OFFHAND = 45;
	private static final int ACTION_ELYTRA = 1;
	private static final int ACTION_FIREWORK = 2;

	private static boolean elytraWasDown;
	private static boolean fireworkWasDown;
	private static int pending;
	private static long dueAt;

	private ElytraSwap() {
	}

	/** Key polling, once per client tick. */
	public static void tick(Minecraft client, TriggerConfig config) {
		if (!config.elytraSwap || client.player == null || client.gameMode == null) {
			pending = 0;
			elytraWasDown = false;
			fireworkWasDown = false;
			return;
		}

		boolean inGame = client.gui.screen() == null;
		boolean elytraDown = inGame && isDown(client, config.elytraKey);
		boolean fireworkDown = inGame && isDown(client, config.fireworkKey);

		if (elytraDown && !elytraWasDown && pending == 0) {
			schedule(ACTION_ELYTRA);
		}

		if (fireworkDown && !fireworkWasDown && pending == 0) {
			schedule(ACTION_FIREWORK);
		}

		elytraWasDown = elytraDown;
		fireworkWasDown = fireworkDown;
		frame(client);
	}

	/** Called every frame as well, so the 75 ms delay is kept precisely (ticks are 50 ms apart). */
	public static void frame(Minecraft client) {
		if (pending == 0 || System.nanoTime() < dueAt) {
			return;
		}

		int action = pending;
		pending = 0;
		LocalPlayer player = client.player;

		if (player == null || client.gameMode == null || client.gui.screen() != null) {
			return;
		}

		boolean done = action == ACTION_ELYTRA ? swapElytra(client, player) : useFirework(client, player);

		// Pause only on the ground: in the air (gliding, jumping) it would kill your speed.
		if (done && player.onGround() && !player.isFallFlying()) {
			Movement.pauseTicks = 2; // one movement tick without input
		}
	}

	private static void schedule(int action) {
		pending = action;
		dueAt = System.nanoTime() + DELAY_NS;
	}

	private static boolean isDown(Minecraft client, String key) {
		return key != null && !key.isEmpty() && Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(key));
	}

	// ---- elytra ---------------------------------------------------------------------------------

	private static boolean swapElytra(Minecraft client, LocalPlayer player) {
		Inventory inventory = player.getInventory();
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);

		if (isElytra(chest)) {
			int chestplate = find(inventory, ElytraSwap::isChestplate);

			if (chestplate >= 0) {
				swapWithChest(client, player, chestplate);
				return true;
			}

			int free = findFree(inventory);

			if (free >= 0) {
				swapWithChest(client, player, free);
				return true;
			}

			if (player.getOffhandItem().isEmpty()) {
				click(client, player, MENU_CHEST);
				click(client, player, MENU_OFFHAND);
				return true;
			}

			message(player, "ElytraSwap: нет свободного места", ChatFormatting.RED);
			return false;
		}

		int elytra = find(inventory, ElytraSwap::isElytra);

		if (elytra < 0) {
			message(player, "ElytraSwap: элитр нет в инвентаре", ChatFormatting.RED);
			return false;
		}

		swapWithChest(client, player, elytra);
		return true;
	}

	/** Exchanges the chest slot with an inventory slot (may be empty). Never drops anything. */
	private static void swapWithChest(Minecraft client, LocalPlayer player, int slot) {
		if (slot < Inventory.getSelectionSize()) {
			// Hotbar: one SWAP click does the whole exchange.
			client.gameMode.handleContainerInput(player.inventoryMenu.containerId, MENU_CHEST, slot, ContainerInput.SWAP, player);
			return;
		}

		click(client, player, slot);
		click(client, player, MENU_CHEST);

		if (!player.inventoryMenu.getCarried().isEmpty()) {
			click(client, player, slot);
		}
	}

	private static void click(Minecraft client, LocalPlayer player, int menuSlot) {
		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, 0, ContainerInput.PICKUP, player);
	}

	/** Free slot: hotbar right of the selected one, then left of it, then the rest of the inventory. */
	private static int findFree(Inventory inventory) {
		int selected = inventory.getSelectedSlot();
		int size = Inventory.getSelectionSize();

		for (int i = selected + 1; i < size; i++) {
			if (inventory.getItem(i).isEmpty()) {
				return i;
			}
		}

		for (int i = selected - 1; i >= 0; i--) {
			if (inventory.getItem(i).isEmpty()) {
				return i;
			}
		}

		for (int i = size; i < 36; i++) {
			if (inventory.getItem(i).isEmpty()) {
				return i;
			}
		}

		return -1;
	}

	private static boolean isElytra(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(Items.ELYTRA) || stack.has(DataComponents.GLIDER));
	}

	private static boolean isChestplate(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !isElytra(stack);
	}

	// ---- firework -------------------------------------------------------------------------------

	private static boolean useFirework(Minecraft client, LocalPlayer player) {
		if (player.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
			client.gameMode.useItem(player, InteractionHand.OFF_HAND);
			return true;
		}

		Inventory inventory = player.getInventory();
		int slot = find(inventory, s -> s.is(Items.FIREWORK_ROCKET));

		if (slot < 0) {
			message(player, "ElytraSwap: фейерверков нет", ChatFormatting.RED);
			return false;
		}

		int selected = inventory.getSelectedSlot();

		if (slot < Inventory.getSelectionSize()) {
			inventory.setSelectedSlot(slot);
			client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			inventory.setSelectedSlot(selected);
			return true;
		}

		// From the main inventory: swap into the hand, use, swap back.
		int containerId = player.inventoryMenu.containerId;
		client.gameMode.handleContainerInput(containerId, slot, selected, ContainerInput.SWAP, player);
		client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		client.gameMode.handleContainerInput(containerId, slot, selected, ContainerInput.SWAP, player);
		return true;
	}

	// ---- helpers --------------------------------------------------------------------------------

	private interface StackTest {
		boolean test(ItemStack stack);
	}

	/** Inventory index (0–35), hotbar first. */
	private static int find(Inventory inventory, StackTest test) {
		for (int i = 0; i < 36; i++) {
			if (test.test(inventory.getItem(i))) {
				return i;
			}
		}

		return -1;
	}

	private static void message(LocalPlayer player, String text, ChatFormatting color) {
		player.sendOverlayMessage(Component.literal(text).withStyle(color));
	}
}
