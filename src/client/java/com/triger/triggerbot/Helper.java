package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Player → Helper: one key per server item (snowball, god's aura, trap, ...). The key finds the item in the
 * inventory, stops you, takes it into the hand, uses it and puts everything back — one step per tick.
 */
public final class Helper {
	/** Config key, menu label and the item that stands for it on the server. */
	public record Entry(String id, String label, Item item) {
	}

	public static final Entry[] ENTRIES = {
			new Entry("snowball", "Снежок заморозка", Items.SNOWBALL),
			new Entry("godsAura", "Божья аура", Items.PHANTOM_MEMBRANE),
			new Entry("trap", "Трапка", Items.NETHERITE_SCRAP),
			new Entry("plast", "Пласт", Items.DRIED_KELP),
			new Entry("clearDust", "Явная пыль", Items.SUGAR),
			new Entry("fireCharge", "Огненный заряд", Items.FIRE_CHARGE),
			new Entry("disorientation", "Дезориентация", Items.ENDER_EYE)
	};

	private static final Map<String, Boolean> WAS_DOWN = new HashMap<>();

	/** Every Helper bind with its icon item: the server items, the wind charge + mace combo and the chorus. */
	public static Entry[] allEntries() {
		Entry[] all = java.util.Arrays.copyOf(ENTRIES, ENTRIES.length + 2);
		all[ENTRIES.length] = new Entry(WIND_MACE, "Заряд ветра + булава", Items.MACE);
		all[ENTRIES.length + 1] = new Entry(CHORUS, "Хорус (удерживать)", Items.CHORUS_FRUIT);
		return all;
	}

	/** How many of {@code item} you have (inventory + off hand). */
	public static int count(LocalPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				total += inventory.getItem(i).getCount();
			}
		}

		return total;
	}

	private Helper() {
	}

	public static void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;
		boolean inGame = config.helper && player != null && client.gameMode != null && client.gui.screen() == null;

		for (Entry entry : ENTRIES) {
			String key = config.helperKeys.get(entry.id());
			boolean down = inGame && key != null && !key.isEmpty()
					&& Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(key));
			boolean was = WAS_DOWN.getOrDefault(entry.id(), false);
			WAS_DOWN.put(entry.id(), down);

			if (down && !was && !InvSync.busy()) {
				use(client, player, entry.item());
			}
		}

		String comboKey = config.helperKeys.get(WIND_MACE);
		boolean comboDown = inGame && comboKey != null && !comboKey.isEmpty()
				&& Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(comboKey));
		boolean comboWas = WAS_DOWN.getOrDefault(WIND_MACE, false);
		WAS_DOWN.put(WIND_MACE, comboDown);

		if (comboDown && !comboWas && !InvSync.busy()) {
			windMace(client, player);
		}

		tickChorus(client, player, config, inGame);
	}

	// ---- chorus: hold to eat ----------------------------------------------------------------------

	/** Config key of the chorus bind. */
	public static final String CHORUS = "chorus";

	private static boolean chorusActive;
	private static int chorusOriginal = -1;
	/** Main-inventory slot the chorus was swapped from (-1 = it was in the hotbar). */
	private static int chorusFrom = -1;

	/**
	 * While the bind is held: chorus fruit in the hand and the use key held, so it is eaten (again and again).
	 * On release: eating stops and the previous slot / item comes back.
	 */
	private static int pendingFrom = -1;
	private static int pendingTo = -1;

	private static void flushPending(Minecraft client) {
		if (pendingFrom < 0 || InvSync.busy()) {
			return;
		}

		int from = pendingFrom;
		int to = pendingTo;

		if (InvSync.run(client, () -> swap(client, from, to))) {
			pendingFrom = -1;
			pendingTo = -1;
		}
	}

	private static void tickChorus(Minecraft client, LocalPlayer player, TriggerConfig config, boolean inGame) {
		if (player == null) {
			chorusActive = false;
			pendingFrom = -1;
			return;
		}

		flushPending(client);

		String key = config.helperKeys.get(CHORUS);
		boolean down = inGame && key != null && !key.isEmpty()
				&& Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(key));

		if (down && !chorusActive) {
			if (!InvSync.busy()) {
				startChorus(client, player);
			}

			return;
		}

		if (!down && chorusActive) {
			stopChorus(client, player);
			return;
		}

		if (chorusActive && player.getMainHandItem().is(Items.CHORUS_FRUIT)) {
			client.options.keyUse.setDown(true); // the game keeps eating while "use" is held
		}
	}

	private static void startChorus(Minecraft client, LocalPlayer player) {
		Inventory inventory = player.getInventory();
		int slot = find(inventory, Items.CHORUS_FRUIT);

		if (slot < 0) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Helper: нет хоруса")
					.withStyle(net.minecraft.ChatFormatting.RED));
			chorusActive = true; // wait for release, don't spam the message
			chorusOriginal = -1;
			chorusFrom = -1;
			return;
		}

		chorusActive = true;
		chorusOriginal = inventory.getSelectedSlot();

		if (slot < Inventory.getSelectionSize()) {
			chorusFrom = -1;
			inventory.setSelectedSlot(slot);
		} else {
			chorusFrom = slot;
			int original = chorusOriginal;
			InvSync.run(client, () -> swap(client, slot, original));
		}
	}

	private static void stopChorus(Minecraft client, LocalPlayer player) {
		chorusActive = false;

		// Give "use" back to the real mouse button and stop eating.
		boolean useHeld = Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(client.options.keyUse.saveString()));
		client.options.keyUse.setDown(useHeld);

		if (player.isUsingItem() && client.gameMode != null) {
			client.gameMode.releaseUsingItem(player);
		}

		if (chorusOriginal < 0) {
			return;
		}

		int original = chorusOriginal;
		int from = chorusFrom;
		chorusOriginal = -1;
		chorusFrom = -1;

		if (from >= 0) {
			// The item that was there comes back (retried next tick if another swap is still running).
			pendingFrom = from;
			pendingTo = original;
			flushPending(client);
		} else {
			player.getInventory().setSelectedSlot(original);
		}
	}

	// ---- wind charge + mace ---------------------------------------------------------------------

	/** Config key of the combo. */
	public static final String WIND_MACE = "windMace";

	/**
	 * Throws a wind charge straight down at your feet, jumps at the same moment (so the blast launches you
	 * high), then takes the mace into the hand for the falling hit.
	 */
	private static void windMace(Minecraft client, LocalPlayer player) {
		Inventory inventory = player.getInventory();
		int charge = find(inventory, Items.WIND_CHARGE);
		int mace = find(inventory, Items.MACE);

		if (charge < 0 || mace < 0) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
					charge < 0 ? "Helper: нет заряда ветра" : "Helper: нет булавы")
					.withStyle(net.minecraft.ChatFormatting.RED));
			return;
		}

		int original = inventory.getSelectedSlot();
		int hotbar = Inventory.getSelectionSize();
		boolean chargeInHotbar = charge < hotbar;
		boolean maceInHotbar = mace < hotbar;

		Runnable takeCharge = chargeInHotbar
				? () -> inventory.setSelectedSlot(charge)
				: () -> swap(client, charge, original);

		Runnable throwDown = () -> {
			LocalPlayer p = client.player;

			if (p == null || client.gameMode == null) {
				return;
			}

			if (p.onGround()) {
				p.jumpFromGround();
			}

			float pitch = p.getXRot();
			p.setXRot(90.0f);
			client.gameMode.useItem(p, InteractionHand.MAIN_HAND);
			p.swing(InteractionHand.MAIN_HAND);
			p.setXRot(pitch);
		};

		Runnable takeMace = () -> {
			if (!chargeInHotbar) {
				swap(client, charge, original); // wind charges back to their slot
			}

			if (maceInHotbar) {
				inventory.setSelectedSlot(mace);
			} else {
				inventory.setSelectedSlot(original);
				swap(client, mace, original);
			}
		};

		// Only hotbar switching: no need to stop the player (keeps your run-up speed).
		if (chargeInHotbar && maceInHotbar) {
			InvSync.runNoStop(client, takeCharge, throwDown, takeMace);
		} else {
			InvSync.run(client, takeCharge, throwDown, takeMace);
		}
	}

	/** Finds {@code item}, uses it and puts everything back (also used by ClickPearl). */
	public static boolean use(Minecraft client, LocalPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int slot = find(inventory, item);

		if (slot < 0 || InvSync.busy()) {
			return false;
		}

		int original = inventory.getSelectedSlot();
		int hotbar = Inventory.getSelectionSize();

		if (slot < hotbar) {
			// Only the selected slot changes, no inventory clicks: no need to stop the player.
			return InvSync.runNoStop(client,
					() -> inventory.setSelectedSlot(slot),
					() -> useMainHand(client),
					() -> inventory.setSelectedSlot(original),
					() -> {
						if (inventory.getSelectedSlot() != original) {
							inventory.setSelectedSlot(original);
						}
					});
		}

		// From the main inventory: swap into the selected hotbar slot, use, wait a tick, swap back, and check.
		ItemStack before = inventory.getItem(original).copy();
		return InvSync.run(client,
				() -> swap(client, slot, original),
				() -> useMainHand(client),
				() -> {
				}, // one quiet tick: the server finishes the use before the swap back
				() -> swap(client, slot, original),
				() -> verifyReturned(client, original, before));
	}

	/**
	 * If the hotbar slot does not hold its old item again (a click got lost / rejected while moving), find that
	 * item in the main inventory and swap it back once more.
	 */
	private static void verifyReturned(Minecraft client, int hotbarSlot, ItemStack before) {
		LocalPlayer player = client.player;

		if (player == null || before.isEmpty()) {
			return;
		}

		Inventory inventory = player.getInventory();

		if (ItemStack.isSameItemSameComponents(inventory.getItem(hotbarSlot), before)) {
			return;
		}

		for (int i = Inventory.getSelectionSize(); i < 36; i++) {
			if (ItemStack.isSameItemSameComponents(inventory.getItem(i), before)) {
				swap(client, i, hotbarSlot);
				return;
			}
		}
	}

	private static void swap(Minecraft client, int slot, int hotbarSlot) {
		LocalPlayer player = client.player;

		if (player != null && client.gameMode != null) {
			client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, hotbarSlot,
					ContainerInput.SWAP, player);
		}
	}

	private static void useMainHand(Minecraft client) {
		LocalPlayer player = client.player;

		if (player != null && client.gameMode != null) {
			client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			player.swing(InteractionHand.MAIN_HAND);
		}
	}

	/** Hotbar first, then the main inventory (0–35). */
	private static int find(Inventory inventory, Item item) {
		for (int i = 0; i < 36; i++) {
			if (inventory.getItem(i).is(item)) {
				return i;
			}
		}

		return -1;
	}
}
