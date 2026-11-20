package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Combat → AutoSwap: one key switches the off hand between two chosen items (e.g. totem ↔ player head).
 * If the off hand holds the first item it gets the second one, otherwise the first.
 */
public final class AutoSwap {
	/** Items that can be picked in the menu. */
	public static final Item[] ITEMS = {
			Items.TOTEM_OF_UNDYING, Items.PLAYER_HEAD, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.SHIELD
	};
	public static final String[] NAMES = {"Тотем", "Голова игрока", "Золотое яблоко", "Чарка", "Щит"};

	private static final int OFFHAND_BUTTON = 40;

	private static boolean wasDown;
	private static long lastSwapAt;
	/** Inventory slot used last time, so several copies are taken in turn. */
	private static int lastSlot = 35;

	private AutoSwap() {
	}

	public static void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (!config.autoSwap || player == null || client.gameMode == null || config.autoSwapKey.isEmpty()) {
			wasDown = false;
			return;
		}

		boolean down = client.gui.screen() == null
				&& Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(config.autoSwapKey));

		if (down && !wasDown) {
			swap(client, player, config);
		}

		wasDown = down;
	}

	private static void swap(Minecraft client, LocalPlayer player, TriggerConfig config) {
		Screen screen = client.gui.screen();

		if (screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)) {
			return;
		}

		if (pick(player, config) < 0) {
			player.sendOverlayMessage(Component.literal("AutoSwap: нечего свапать").withStyle(ChatFormatting.RED));
			return;
		}

		// Stop first, click on a still tick (servers drop inventory clicks sent while running).
		InvSync.run(client, () -> doSwap(client, config));
	}

	/**
	 * Which inventory slot goes into the off hand. Always swaps to something:
	 * <ol>
	 *     <li>the other chosen item (totem ↔ head), a copy that differs from the off hand first;</li>
	 *     <li>if there is none, another copy of the same item (2 totems / 2 heads: swaps between them).</li>
	 * </ol>
	 * Copies are taken in turn, so several heads / totems cycle.
	 */
	private static int pick(LocalPlayer player, TriggerConfig config) {
		Item first = ITEMS[config.autoSwapFirst];
		Item second = ITEMS[config.autoSwapSecond];
		ItemStack offhand = player.getOffhandItem();
		Item want = offhand.is(first) ? second : first;
		Item same = want == first ? second : first;
		Inventory inventory = player.getInventory();

		int slot = findNext(inventory, want, offhand);

		if (slot < 0 && !offhand.isEmpty()) {
			slot = findNext(inventory, same, offhand);
		}

		return slot;
	}

	private static void doSwap(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (player == null || client.gameMode == null) {
			return;
		}

		int slot = pick(player, config);

		if (slot < 0) {
			return;
		}

		String label = name(player.getInventory().getItem(slot).getItem());
		int menuSlot = slot < Inventory.getSelectionSize() ? slot + 36 : slot;
		client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, OFFHAND_BUTTON,
				ContainerInput.SWAP, player);
		lastSlot = slot;
		lastSwapAt = System.currentTimeMillis();
		player.sendOverlayMessage(Component.literal("AutoSwap: " + label).withStyle(ChatFormatting.GREEN));
	}

	/**
	 * Next slot (after the one used last time, wrapping around) holding {@code item}. A stack that is not an
	 * exact copy of the off hand is preferred; an exact copy is used only if nothing else exists.
	 */
	private static int findNext(Inventory inventory, Item item, ItemStack offhand) {
		int exactCopy = -1;

		for (int i = 1; i <= 36; i++) {
			int slot = (lastSlot + i) % 36;
			ItemStack stack = inventory.getItem(slot);

			if (!stack.is(item)) {
				continue;
			}

			if (!ItemStack.isSameItemSameComponents(stack, offhand)) {
				return slot;
			}

			if (exactCopy < 0) {
				exactCopy = slot;
			}
		}

		return exactCopy;
	}

	/** AutoTotem leaves a manual swap alone for a moment (it still reacts to low HP). */
	public static boolean recentlySwapped() {
		return System.currentTimeMillis() - lastSwapAt < 3000L;
	}

	private static String name(Item item) {
		for (int i = 0; i < ITEMS.length; i++) {
			if (ITEMS[i] == item) {
				return NAMES[i];
			}
		}

		return "?";
	}

	/** Main inventory first, so the hotbar stays untouched when possible. */
	private static int find(Inventory inventory, Item item) {
		for (int slot = Inventory.getSelectionSize(); slot < 36; slot++) {
			if (inventory.getItem(slot).is(item)) {
				return slot;
			}
		}

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (inventory.getItem(slot).is(item)) {
				return slot;
			}
		}

		return -1;
	}
}
