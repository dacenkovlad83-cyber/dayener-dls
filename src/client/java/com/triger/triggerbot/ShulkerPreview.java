package com.triger.triggerbot;

import com.mojang.blaze3d.platform.InputConstants;
import com.triger.triggerbot.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.lwjgl.glfw.GLFW;

/**
 * Visuals → ShulkerPreview: hold Shift over a shulker box (or any item that stores items) in an inventory and
 * its contents are shown as a 9×3 grid next to the cursor.
 */
public final class ShulkerPreview {
	private static final int SLOT = 18;
	private static final int PAD = 6;
	private static final int TITLE = 12;

	private ShulkerPreview() {
	}

	public static void register(TriggerConfig config) {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}

			ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
				if (config.shulkerPreview) {
					draw(graphics, client, container, mouseX, mouseY, s.width, s.height);
				}
			});
		});
	}

	private static void draw(GuiGraphicsExtractor graphics, Minecraft client, AbstractContainerScreen<?> screen,
			int mouseX, int mouseY, int width, int height) {
		boolean shift = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);

		if (!shift) {
			return;
		}

		Slot slot = ((AbstractContainerScreenAccessor) screen).dls$getHoveredSlot();

		if (slot == null || !slot.hasItem()) {
			return;
		}

		ItemStack box = slot.getItem();
		ItemContainerContents contents = box.get(DataComponents.CONTAINER);

		if (contents == null) {
			return;
		}

		NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
		contents.copyInto(items);

		Font font = client.font;
		int rows = 3;
		int w = PAD * 2 + 9 * SLOT;
		int h = PAD * 2 + TITLE + rows * SLOT;

		// Left of the cursor, so the vanilla tooltip on the right stays readable.
		int x = mouseX - w - 10;

		if (x < 4) {
			x = Math.min(mouseX + 14, width - w - 4);
		}

		int y = Math.clamp(mouseY - h / 2, 4, Math.max(4, height - h - 4));

		graphics.nextStratum();
		HudStyle.panel(graphics, x, y, w, h, 10);

		String title = box.getHoverName().getString();
		graphics.text(font, font.plainSubstrByWidth(title, w - PAD * 2), x + PAD, y + PAD, HudStyle.TEXT, false);

		int gridY = y + PAD + TITLE;

		for (int i = 0; i < 27; i++) {
			int sx = x + PAD + (i % 9) * SLOT;
			int sy = gridY + (i / 9) * SLOT;
			UiDraw.fill(graphics, sx, sy, SLOT - 2, SLOT - 2, 4, 0x26FFFFFF);
			ItemStack stack = items.get(i);

			if (!stack.isEmpty()) {
				graphics.item(stack, sx, sy);
				graphics.itemDecorations(font, stack, sx, sy);
			}
		}
	}
}
