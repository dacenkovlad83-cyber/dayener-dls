package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * Player → LockSlot: locked hotbar slots (one by one or the whole hotbar) cannot be dropped with Q, clicked,
 * shift-clicked, or swapped with number keys / F in any inventory. A key locks / unlocks the selected slot.
 */
public final class LockSlot {
	private static boolean wasDown;

	private LockSlot() {
	}

	public static boolean isLocked(int hotbarSlot) {
		TriggerConfig c = TriggerBotClient.config();

		if (c == null || !c.lockSlot || hotbarSlot < 0 || hotbarSlot >= Inventory.getSelectionSize()) {
			return false;
		}

		return c.lockHotbar || (c.lockedSlots & (1 << hotbarSlot)) != 0;
	}

	/** Q / Ctrl+Q on the selected slot. */
	public static boolean blocksDrop(LocalPlayer player) {
		return isLocked(player.getInventory().getSelectedSlot());
	}

	/** A click made by the player in an inventory screen. */
	public static boolean blocksClick(Slot slot, int button, ContainerInput input) {
		if (slot != null && slot.container instanceof Inventory && isLocked(slot.getContainerSlot())) {
			return true;
		}

		// Number keys 1–9 swap the hovered slot with that hotbar slot.
		return input == ContainerInput.SWAP && isLocked(button);
	}

	/** The "lock selected slot" key. */
	public static void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;
		boolean down = config.lockSlot && player != null && client.gui.screen() == null && !config.lockSlotKey.isEmpty()
				&& Movement.isPhysicallyDown(client.getWindow(), InputConstants.getKey(config.lockSlotKey));

		if (down && !wasDown) {
			int slot = player.getInventory().getSelectedSlot();
			config.lockedSlots ^= 1 << slot;
			config.save();
			boolean locked = (config.lockedSlots & (1 << slot)) != 0;
			player.sendOverlayMessage(Component.literal("Слот " + (slot + 1) + (locked ? " заблокирован" : " разблокирован"))
					.withStyle(locked ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		}

		wasDown = down;
	}
}
