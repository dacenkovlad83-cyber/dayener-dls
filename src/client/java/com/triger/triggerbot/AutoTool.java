package com.triger.triggerbot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Player → AutoTool: while you mine a block, the fastest tool from the hotbar is selected. Optionally stops
 * you for a moment on the swap, and goes back to the previous slot when you stop mining.
 */
public final class AutoTool {
	private static int previousSlot = -1;

	private AutoTool() {
	}

	public static void tick(Minecraft client, TriggerConfig config) {
		LocalPlayer player = client.player;

		if (!config.autoTool || player == null || client.level == null || client.gui.screen() != null
				|| player.isCreative() || player.isSpectator()) {
			restore(player, config);
			return;
		}

		boolean mining = client.options.keyAttack.isDown()
				&& client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK;

		if (!mining) {
			restore(player, config);
			return;
		}

		BlockState state = client.level.getBlockState(((BlockHitResult) client.hitResult).getBlockPos());

		if (state.isAir() || state.getDestroySpeed(client.level, ((BlockHitResult) client.hitResult).getBlockPos()) < 0) {
			return;
		}

		Inventory inventory = player.getInventory();
		int current = inventory.getSelectedSlot();
		int best = current;
		float bestScore = score(inventory.getItem(current), state);

		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (LockSlot.isLocked(i) && i != current) {
				continue;
			}

			float s = score(inventory.getItem(i), state);

			if (s > bestScore + 0.01f) {
				best = i;
				bestScore = s;
			}
		}

		if (best == current) {
			return;
		}

		if (previousSlot < 0) {
			previousSlot = current;
		}

		inventory.setSelectedSlot(best);

		if (config.autoToolStop && player.onGround() && !player.isFallFlying()) {
			Movement.stopNow(player);
			Movement.pauseTicks = Math.max(Movement.pauseTicks, 2);
		}
	}

	/** Mining speed, with a bonus for tools that actually drop the block. Nearly broken tools are skipped. */
	private static float score(ItemStack stack, BlockState state) {
		if (stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 2) {
			return 0.0f;
		}

		float speed = stack.getDestroySpeed(state);
		return state.requiresCorrectToolForDrops() && stack.isCorrectToolForDrops(state) ? speed + 100.0f : speed;
	}

	private static void restore(LocalPlayer player, TriggerConfig config) {
		if (previousSlot >= 0 && player != null && config.autoToolBack) {
			player.getInventory().setSelectedSlot(previousSlot);
		}

		previousSlot = -1;
	}
}
