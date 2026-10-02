package com.urntt.sprint;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.sprint.config.SprintConfig;
import com.urntt.sprint.config.SprintConfigScreen;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SprintClient implements ClientModInitializer {
	public static final String MOD_ID = "sprint";
	public static final String OPEN_SETTINGS_KEY_NAME = "key." + MOD_ID + ".open_settings";

	/** Action bar message shown when a toggle key is pressed on a server the multiplayer rules rule out. */
	public static final Component BLOCKED_MESSAGE = Component.translatable("message." + MOD_ID + ".blocked");

	private static SprintConfig config;
	private static SprintController controller;

	@Override
	public void onInitializeClient() {
		config = SprintConfig.load(SprintConfig.defaultPath());
		controller = new SprintController(config);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		Map<Feature, KeyMapping> toggleKeys = new EnumMap<>(Feature.class);
		for (Feature feature : Feature.values()) {
			toggleKeys.put(feature, KeyMappingHelper.registerKeyMapping(
					new KeyMapping(feature.toggleKeyName(), InputConstants.UNKNOWN.getValue(), category)));
		}
		KeyMapping openSettingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(OPEN_SETTINGS_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			toggleKeys.forEach((feature, key) -> {
				while (key.consumeClick()) {
					toggle(client, feature);
				}
			});
			while (openSettingsKey.consumeClick()) {
				client.gui.setScreen(new SprintConfigScreen(client.gui.screen()));
			}
		});

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> controller.onJoin(Scene.of(client)));
		// The disconnect event may arrive on the network thread; the controller is only used on the client thread.
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(controller::onDisconnect));
	}

	public static SprintConfig config() {
		return config;
	}

	public static SprintController controller() {
		return controller;
	}

	private static void toggle(final Minecraft client, final Feature feature) {
		SprintController.ToggleResult result = controller.toggle(feature);
		if (client.player == null) {
			return;
		}

		Component message = switch (result) {
			case ENABLED -> CommonComponents.optionStatus(feature.displayName(), true);
			case DISABLED -> CommonComponents.optionStatus(feature.displayName(), false);
			case BLOCKED -> BLOCKED_MESSAGE;
		};
		client.player.sendOverlayMessage(message);
	}
}
