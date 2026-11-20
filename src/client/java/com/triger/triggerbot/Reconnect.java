package com.triger.triggerbot;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Esc menu → "Reconnect": leaves the current server and joins it again right away.
 */
public final class Reconnect {
	private Reconnect() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof PauseScreen) || client.isLocalServer() || client.getCurrentServer() == null) {
				return;
			}

			List<AbstractWidget> widgets = Screens.getWidgets(screen);

			if (widgets.isEmpty()) {
				return; // "pause without menu" (F3 + Esc)
			}

			int bottom = 0;

			for (AbstractWidget widget : widgets) {
				bottom = Math.max(bottom, widget.getBottom());
			}

			int width = 204;
			int y = Math.min(bottom + 4, h - 24);
			widgets.add(Button.builder(Component.literal("Reconnect"), button -> reconnect(client))
					.bounds(w / 2 - width / 2, y, width, 20)
					.build());
		});
	}

	private static void reconnect(Minecraft client) {
		ServerData server = client.getCurrentServer();

		if (server == null) {
			return;
		}

		ServerData copy = new ServerData(server.name, server.ip, server.type());
		copy.copyFrom(server);

		client.disconnectFromWorld(Component.literal("Reconnect"));
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client,
				ServerAddress.parseString(copy.ip), copy, false, null);
	}
}
