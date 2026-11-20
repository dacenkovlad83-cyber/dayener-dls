package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import com.triger.triggerbot.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Player → ItemScroller, for inventories and chests:
 * <ul>
 *   <li>Hold Shift + left mouse button and drag over slots — every slot is shift-clicked once.</li>
 *   <li>Scroll the mouse wheel over a slot — its stack is shift-clicked.</li>
 * </ul>
 * Clicks are sent one by one with the delay from the settings (1–50 ms).
 */
public final class ItemScroller {
	private static final Deque<Integer> QUEUE = new ArrayDeque<>();
	private static final Set<Integer> DRAGGED = new HashSet<>();
	private static long nextClickAt;

	private ItemScroller() {
	}

	public static void register(TriggerConfig config) {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof CreativeModeInventoryScreen) {
				return;
			}

			QUEUE.clear();
			DRAGGED.clear();

			// Shift + LMB press: vanilla already moves the slot under the cursor — remember it so it is not
			// clicked twice. Every other slot you then drag over gets moved by us.
			ScreenMouseEvents.beforeMouseClick(screen).register((s, event) -> {
				if (config.itemScroller && event.button() == 0) {
					DRAGGED.clear();
					Slot pressed = ((AbstractContainerScreenAccessor) container).dls$getHoveredSlot();
					DRAGGED.add(pressed == null ? -1 : pressed.index);
				}
			});

			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, partialTick) -> {
				if (config.itemScroller) {
					update(client, container, config);
				}
			});

			ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, scrollX, scrollY) -> {
				if (!config.itemScroller || scrollY == 0.0) {
					return true;
				}

				Slot slot = ((AbstractContainerScreenAccessor) container).dls$getHoveredSlot();

				if (slot == null || !slot.hasItem() || locked(slot)) {
					return true;
				}

				QUEUE.add(slot.index);
				return false;
			});
		});
	}

	private static boolean locked(Slot slot) {
		return slot.container instanceof net.minecraft.world.entity.player.Inventory && LockSlot.isLocked(slot.getContainerSlot());
	}

	private static void update(Minecraft client, AbstractContainerScreen<?> screen, TriggerConfig config) {
		long window = client.getWindow().handle();
		boolean shift = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
		boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

		if (shift && leftDown) {
			Slot slot = ((AbstractContainerScreenAccessor) screen).dls$getHoveredSlot();

			// Each slot once per drag; the slot that was under the cursor on press was moved by vanilla.
			if (slot != null && slot.hasItem() && !locked(slot) && DRAGGED.add(slot.index)) {
				QUEUE.add(slot.index);
			}
		} else if (!leftDown) {
			DRAGGED.clear();
		}

		long now = System.nanoTime();

		if (QUEUE.isEmpty() || now < nextClickAt || client.gameMode == null || client.player == null) {
			return;
		}

		int index = QUEUE.poll();

		if (index >= 0 && index < screen.getMenu().slots.size() && screen.getMenu().slots.get(index).hasItem()) {
			client.gameMode.handleContainerInput(screen.getMenu().containerId, index, 0, ContainerInput.QUICK_MOVE, client.player);
			nextClickAt = now + config.itemScrollerDelay * 1_000_000L;
		}
	}
}
