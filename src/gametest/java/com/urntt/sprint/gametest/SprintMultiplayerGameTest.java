package com.urntt.sprint.gametest;

import static com.urntt.sprint.gametest.GameTestSupport.check;
import static com.urntt.sprint.gametest.GameTestSupport.configure;
import static com.urntt.sprint.gametest.GameTestSupport.enableOnly;
import static com.urntt.sprint.gametest.GameTestSupport.isEnabled;
import static com.urntt.sprint.gametest.GameTestSupport.pressToggleKey;

import com.urntt.sprint.Feature;
import com.urntt.sprint.Scene;
import com.urntt.sprint.SprintClient;
import com.urntt.sprint.config.MultiplayerMode;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

/**
 * Checks the multiplayer modes on a local dedicated server, which the client reaches as {@code localhost}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SprintMultiplayerGameTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		configure(context, config -> {
			config.setResetOnWorldExit(false);
			config.setResetOnGameExit(false);
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
		enableOnly(context, Feature.FORCE_SPRINT, Feature.OMNIDIRECTIONAL);

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			Arena arena = Arena.create(context, server);
			Scene scene = context.computeOnClient(client -> SprintClient.controller().scene());
			GameTestSupport.LOGGER.info("Connected to {}", scene);
			check(scene instanceof Scene.Multiplayer, "a dedicated server should count as multiplayer, got " + scene);

			checkVanilla(arena, "on a server with multiplayer disabled");
			pressToggleKey(context, Feature.FORCE_SPRINT);
			check(isEnabled(context, Feature.FORCE_SPRINT), "a blocked toggle should leave the state unchanged");
			context.takeScreenshot("sprint-blocked-on-server");

			configure(context, config -> {
				config.setServers(List.of("localhost"));
				config.setMultiplayerMode(MultiplayerMode.WHITELIST);
			});
			arena.checkSprints("forward on a whitelisted server", options -> options.keyUp);
			arena.checkSprints("backward on a whitelisted server", options -> options.keyDown);

			configure(context, config -> config.setMultiplayerMode(MultiplayerMode.BLACKLIST));
			checkVanilla(arena, "on a blacklisted server");
			arena.reset();
		}

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
		enableOnly(context, Feature.FORCE_SPRINT);
	}

	private static void checkVanilla(final Arena arena, final String situation) {
		arena.checkDoesNotSprint("forward " + situation, options -> options.keyUp);
		arena.checkDoesNotSprint("backward with the sprint key " + situation, options -> options.keyDown,
				options -> options.keySprint);
		arena.checkSprints("forward with the sprint key " + situation, options -> options.keyUp,
				options -> options.keySprint);
	}
}
