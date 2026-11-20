package com.triger.triggerbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Utility → AutoEat: when hunger drops to the threshold, picks the best food from the hotbar,
 * holds "use" until it is eaten and switches back to the previous slot.
 */
public final class AutoEat {
	private boolean eating;
	private int previousSlot = -1;
	private int eatingSlot = -1;
	private int ticks;

	public void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (!config.autoEat || player == null || client.gameMode == null || player.isSpectator() || player.isCreative()) {
			stop(client);
			return;
		}

		if (this.eating) {
			continueEating(client, player, config);
			return;
		}

		if (client.gui.screen() != null || player.isUsingItem()) {
			return;
		}

		if (player.getFoodData().getFoodLevel() > config.autoEatHunger) {
			return;
		}

		if (client.gameMode.isDestroying()) {
			if (!config.autoEatStopMining) {
				return;
			}

			client.options.keyAttack.setDown(false);
			client.gameMode.stopDestroyBlock();
		}

		int slot = findFood(player);

		if (slot < 0) {
			return;
		}

		Inventory inventory = player.getInventory();
		this.previousSlot = inventory.getSelectedSlot();
		this.eatingSlot = slot;
		inventory.setSelectedSlot(slot);
		this.eating = true;
		this.ticks = 0;
		client.options.keyUse.setDown(true);
	}

	private void continueEating(Minecraft client, LocalPlayer player, TriggerConfig config) {
		this.ticks++;

		boolean slotChanged = player.getInventory().getSelectedSlot() != this.eatingSlot;
		boolean finished = this.ticks > 5 && !player.isUsingItem();
		boolean noFood = !isGoodFood(player, player.getInventory().getSelectedItem());

		if (slotChanged || finished || noFood || this.ticks > 100) {
			stop(client);
			return;
		}

		client.options.keyUse.setDown(true);

		if (config.autoEatStopMining) {
			client.options.keyAttack.setDown(false);
		}
	}

	public void stop(Minecraft client) {
		if (!this.eating) {
			return;
		}

		this.eating = false;
		client.options.keyUse.setDown(false);

		if (client.player != null && this.previousSlot >= 0
				&& client.player.getInventory().getSelectedSlot() == this.eatingSlot) {
			client.player.getInventory().setSelectedSlot(this.previousSlot);
		}

		this.previousSlot = -1;
		this.eatingSlot = -1;
	}

	public boolean isEating() {
		return this.eating;
	}

	private static int findFood(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		int best = -1;
		int bestNutrition = 0;

		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);

			if (!isGoodFood(player, stack)) {
				continue;
			}

			int nutrition = stack.get(DataComponents.FOOD).nutrition();

			if (nutrition > bestNutrition) {
				bestNutrition = nutrition;
				best = slot;
			}
		}

		return best;
	}

	private static boolean isGoodFood(LocalPlayer player, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		FoodProperties food = stack.get(DataComponents.FOOD);

		if (food == null || !player.canEat(food.canAlwaysEat())) {
			return false;
		}

		// Food with bad side effects is never picked automatically.
		return !stack.is(Items.ROTTEN_FLESH)
				&& !stack.is(Items.SPIDER_EYE)
				&& !stack.is(Items.POISONOUS_POTATO)
				&& !stack.is(Items.PUFFERFISH)
				&& !stack.is(Items.CHORUS_FRUIT)
				&& !stack.is(Items.SUSPICIOUS_STEW);
	}
}
