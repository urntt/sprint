package com.urntt.sprint.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.sprint.Feature;
import com.urntt.sprint.SprintClient;
import com.urntt.sprint.config.SprintConfig;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the client game tests.
 */
@SuppressWarnings("UnstableApiUsage")
final class GameTestSupport {
	static final Logger LOGGER = LoggerFactory.getLogger("sprint-gametest");

	/** Key the tests bind a toggle key to while pressing it. Vanilla does not use it. */
	static final String TEST_KEY = "key.keyboard.j";
	/** Key the tests bind the settings key to. Vanilla does not use it. */
	static final String SETTINGS_TEST_KEY = "key.keyboard.k";

	/** Every vanilla key that moves the player or changes how it moves. */
	static final List<Function<Options, KeyMapping>> MOVEMENT_KEYS = List.of(
			options -> options.keyUp,
			options -> options.keyDown,
			options -> options.keyLeft,
			options -> options.keyRight,
			options -> options.keyJump,
			options -> options.keyShift,
			options -> options.keySprint);

	private GameTestSupport() {
	}

	/**
	 * Returns the mod's key mapping registered under {@code name}, bound to {@code key} for the test.
	 */
	static KeyMapping bindKey(final ClientGameTestContext context, final String name, final String key) {
		KeyMapping mapping = Objects.requireNonNull(KeyMapping.get(name), name);
		context.runOnClient(client -> {
			mapping.setKey(InputConstants.getKey(key));
			KeyMapping.resetMapping();
		});
		return mapping;
	}

	static void unbindKey(final ClientGameTestContext context, final KeyMapping mapping) {
		context.runOnClient(client -> {
			mapping.setKey(mapping.getDefaultKey());
			KeyMapping.resetMapping();
		});
	}

	/**
	 * Presses the toggle key of {@code feature}, bound only for this press so that the six toggle keys never share a
	 * key, and waits for the toggle to be handled.
	 */
	static void pressToggleKey(final ClientGameTestContext context, final Feature feature) {
		KeyMapping key = bindKey(context, feature.toggleKeyName(), TEST_KEY);
		context.getInput().pressKey(key);
		context.waitTick();
		unbindKey(context, key);
	}

	/**
	 * Changes the mod's configuration on the client thread.
	 */
	static void configure(final ClientGameTestContext context, final Consumer<SprintConfig> change) {
		context.runOnClient(client -> change.accept(SprintClient.config()));
	}

	/**
	 * Sets the state of every feature: those in {@code enabled} on, all others off.
	 */
	static void enableOnly(final ClientGameTestContext context, final Feature... enabled) {
		List<Feature> enabledFeatures = List.of(enabled);
		configure(context, config -> {
			for (Feature feature : Feature.values()) {
				config.setEnabled(feature, enabledFeatures.contains(feature));
			}
		});
	}

	static boolean isEnabled(final ClientGameTestContext context, final Feature feature) {
		return context.computeOnClient(client -> SprintClient.config().isEnabled(feature));
	}

	static SprintConfig loadSavedConfig() {
		return SprintConfig.load(SprintConfig.defaultPath());
	}

	static void releaseMovementKeys(final ClientGameTestContext context) {
		MOVEMENT_KEYS.forEach(key -> context.getInput().releaseKey(key));
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
