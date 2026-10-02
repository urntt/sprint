package com.urntt.sprint.gametest;

import static com.urntt.sprint.gametest.GameTestSupport.check;

import com.urntt.sprint.Feature;
import com.urntt.sprint.Scene;
import com.urntt.sprint.ServerAddresses;
import com.urntt.sprint.SprintController;
import com.urntt.sprint.config.MultiplayerMode;
import com.urntt.sprint.config.SprintConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Checks the address matching, the configuration defaults, and the controller's rules without a world. Each check
 * uses its own configuration file, so the game's configuration is left alone. Restarting the game is not possible
 * in a game test, so this is also where "reset on game exit" is covered.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SprintLogicGameTest implements FabricClientGameTest {
	private static final Scene SERVER_A = Scene.multiplayer("a.example.com");
	private static final Scene SERVER_B = Scene.multiplayer("b.example.com:25570");
	private static final Scene UNKNOWN_SERVER = Scene.multiplayer(null);

	@Override
	public void runTest(final ClientGameTestContext context) {
		Path directory = createTempDirectory();
		checkAddressMatching();
		checkDefaults(directory);
		checkUpgradeFromOlderConfig(directory);
		checkPersistence(directory);
		checkMultiplayerRules(directory);
		checkResetRules(directory);
		GameTestSupport.LOGGER.info("Logic checks passed");
	}

	private static void checkAddressMatching() {
		checkMatch("mc.example.com", "mc.example.com", true);
		checkMatch("MC.Example.com", "mc.example.COM:25565", true);
		checkMatch("mc.example.com", "mc.example.com:25566", true);
		checkMatch("mc.example.com:25565", "mc.example.com", true);
		checkMatch("mc.example.com:25566", "mc.example.com", false);
		checkMatch("mc.example.com", "play.example.com", false);
		checkMatch("  mc.example.com  ", "mc.example.com", true);
		checkMatch("192.168.1.5", "192.168.1.5:51234", true);
		checkMatch("[::1]:25565", "[::1]", true);
		checkMatch("::1", "[::1]:25570", true);
		checkMatch("bücher.example", "xn--bcher-kva.example", true);
		checkMatch("localhost", "localhost:41234", true);

		for (String valid : List.of("mc.example.com:25566", "localhost", "192.168.1.5", "[::1]:25565", "bücher.example")) {
			check(ServerAddresses.isValid(valid), "'" + valid + "' should be valid");
		}
		for (String invalid : List.of("", "   ", "mc.example.com:abc", "mc.example.com:99999", "[::1", "mc example.com",
				"not a host:port:x")) {
			check(!ServerAddresses.isValid(invalid), "'" + invalid + "' should be invalid");
		}
	}

	private static void checkMatch(final String entry, final String address, final boolean expected) {
		check(ServerAddresses.matches(entry, address) == expected,
				"'" + entry + "' should " + (expected ? "" : "not ") + "match '" + address + "'");
	}

	private static void checkDefaults(final Path directory) {
		SprintConfig config = SprintConfig.load(directory.resolve("defaults.json"));
		check(config.isEnabled(Feature.FORCE_SPRINT), "force sprint should be enabled by default");
		for (Feature feature : List.of(Feature.IGNORE_HUNGER, Feature.KEEP_ON_COLLISION, Feature.SPRINT_IN_PLACE,
				Feature.OMNIDIRECTIONAL, Feature.SPRINT_WHILE_SNEAKING)) {
			check(!config.isEnabled(feature), feature + " should be disabled by default");
		}
		for (Feature feature : Feature.values()) {
			check(config.singleplayerDefault(feature) == config.isEnabled(feature),
					"the singleplayer default of " + feature + " should match its initial state");
			check(config.multiplayerDefault(feature) == config.isEnabled(feature),
					"the server default of " + feature + " should match its initial state");
		}
		check(!config.resetOnWorldExit(), "reset on world exit should be off by default");
		check(!config.resetOnGameExit(), "reset on game exit should be off by default");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "multiplayer should be disabled by default");
		check(config.servers().isEmpty(), "the server list should be empty by default");
		check(Files.exists(directory.resolve("defaults.json")), "a missing config file should be created");
	}

	private static void checkUpgradeFromOlderConfig(final Path directory) {
		Path path = directory.resolve("upgrade.json");
		write(path, """
				{
					"features": {
						"force_sprint": {"enabled": false},
						"omnidirectional": {"enabled": true, "multiplayerDefault": true},
						"removed_feature": {"enabled": true}
					},
					"multiplayerMode": "bogus",
					"servers": [" a.example.com ", ""]
				}
				""");

		SprintConfig config = SprintConfig.load(path);
		check(!config.isEnabled(Feature.FORCE_SPRINT), "existing settings should be kept");
		check(config.singleplayerDefault(Feature.FORCE_SPRINT), "a missing default should use the feature's default");
		check(config.isEnabled(Feature.OMNIDIRECTIONAL), "existing settings should be kept");
		check(config.multiplayerDefault(Feature.OMNIDIRECTIONAL), "existing settings should be kept");
		check(!config.singleplayerDefault(Feature.OMNIDIRECTIONAL), "a missing default should use the feature's default");
		check(!config.isEnabled(Feature.SPRINT_IN_PLACE), "a missing feature should use its default");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "an unknown mode should fall back to disabled");
		check(config.servers().equals(List.of("a.example.com")), "entries should be trimmed and blanks dropped");
		String saved = read(path);
		check(saved.contains("\"sprint_while_sneaking\"") && saved.contains("\"resetOnWorldExit\""),
				"missing settings should be written to the file");
		check(!saved.contains("removed_feature"), "unknown features should be dropped");
	}

	private static void checkPersistence(final Path directory) {
		Path path = directory.resolve("persistence.json");
		SprintConfig config = SprintConfig.load(path);
		config.setEnabled(Feature.KEEP_ON_COLLISION, true);
		config.setSingleplayerDefault(Feature.SPRINT_IN_PLACE, true);
		config.setMultiplayerDefault(Feature.FORCE_SPRINT, false);

		SprintConfig reloaded = SprintConfig.load(path);
		check(reloaded.isEnabled(Feature.KEEP_ON_COLLISION), "a toggled state should survive reloading");
		check(reloaded.singleplayerDefault(Feature.SPRINT_IN_PLACE), "a singleplayer default should survive reloading");
		check(!reloaded.multiplayerDefault(Feature.FORCE_SPRINT), "a server default should survive reloading");
	}

	private static void checkMultiplayerRules(final Path directory) {
		SprintConfig config = SprintConfig.load(directory.resolve("rules.json"));
		SprintController controller = new SprintController(config);

		check(!controller.isActive(Feature.FORCE_SPRINT), "nothing should be active outside a world");
		controller.onJoin(Scene.SINGLEPLAYER);
		check(controller.isActive(Feature.FORCE_SPRINT), "singleplayer should always be allowed");
		check(!controller.isActive(Feature.OMNIDIRECTIONAL), "a disabled feature should not be active");

		controller.onJoin(SERVER_A);
		check(!controller.isAllowed(), "the disabled mode should rule out every server");
		check(!controller.isActive(Feature.FORCE_SPRINT), "no feature should be active on a ruled-out server");
		check(controller.toggle(Feature.FORCE_SPRINT) == SprintController.ToggleResult.BLOCKED, "toggling should be blocked");
		check(config.isEnabled(Feature.FORCE_SPRINT), "a blocked toggle should leave the state unchanged");

		config.setServers(List.of("a.example.com"));
		config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		check(controller.isActive(Feature.FORCE_SPRINT), "a whitelisted server should be allowed");
		check(controller.toggle(Feature.OMNIDIRECTIONAL) == SprintController.ToggleResult.ENABLED,
				"toggling should work on an allowed server");
		check(controller.isActive(Feature.OMNIDIRECTIONAL), "a toggled feature should be active");
		controller.onJoin(SERVER_B);
		check(!controller.isAllowed(), "a server missing from the whitelist should be ruled out");
		controller.onJoin(UNKNOWN_SERVER);
		check(!controller.isAllowed(), "an unknown address should not count as whitelisted");

		config.setMultiplayerMode(MultiplayerMode.BLACKLIST);
		check(controller.isAllowed(), "an unknown address should not count as blacklisted");
		controller.onJoin(SERVER_B);
		check(controller.isAllowed(), "a server missing from the blacklist should be allowed");
		controller.onJoin(SERVER_A);
		check(!controller.isAllowed(), "a blacklisted server should be ruled out");

		controller.onDisconnect();
		check(!controller.isActive(Feature.FORCE_SPRINT), "nothing should be active after disconnecting");
	}

	private static void checkResetRules(final Path directory) {
		SprintConfig config = SprintConfig.load(directory.resolve("reset.json"));
		config.setResetOnGameExit(true);
		config.setEnabled(Feature.FORCE_SPRINT, false);
		config.setEnabled(Feature.OMNIDIRECTIONAL, true);

		// A fresh controller stands for a game that has just started.
		SprintController controller = new SprintController(config);
		controller.onJoin(SERVER_A);
		check(!config.isEnabled(Feature.FORCE_SPRINT), "a ruled-out server should not apply the game exit reset");
		controller.onDisconnect();
		controller.onJoin(Scene.SINGLEPLAYER);
		check(config.isEnabled(Feature.FORCE_SPRINT) && !config.isEnabled(Feature.OMNIDIRECTIONAL),
				"the first allowed world after starting should restore every default");
		controller.onDisconnect();
		config.setEnabled(Feature.FORCE_SPRINT, false);
		controller.onJoin(Scene.SINGLEPLAYER);
		check(!config.isEnabled(Feature.FORCE_SPRINT), "later worlds should keep the state without reset on world exit");
		controller.onDisconnect();

		config.setResetOnWorldExit(true);
		config.setSingleplayerDefault(Feature.SPRINT_IN_PLACE, true);
		controller.onJoin(Scene.SINGLEPLAYER);
		check(config.isEnabled(Feature.FORCE_SPRINT) && config.isEnabled(Feature.SPRINT_IN_PLACE),
				"reset on world exit should restore the singleplayer defaults");
		controller.onDisconnect();

		config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		config.setServers(List.of("a.example.com"));
		config.setMultiplayerDefault(Feature.FORCE_SPRINT, false);
		config.setMultiplayerDefault(Feature.IGNORE_HUNGER, true);
		controller.onJoin(SERVER_A);
		check(!config.isEnabled(Feature.FORCE_SPRINT) && config.isEnabled(Feature.IGNORE_HUNGER)
				&& !config.isEnabled(Feature.SPRINT_IN_PLACE), "an allowed server should restore the server defaults");
		controller.onDisconnect();
	}

	private static Path createTempDirectory() {
		try {
			return Files.createTempDirectory("sprint-gametest");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(final Path path, final String content) {
		try {
			Files.writeString(path, content);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(final Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
