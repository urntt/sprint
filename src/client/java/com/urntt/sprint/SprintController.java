package com.urntt.sprint;

import com.urntt.sprint.config.SprintConfig;
import org.jspecify.annotations.Nullable;

/**
 * Decides which features are active right now, from the configuration and the current {@link Scene}. It is the only
 * place that combines the toggle states, the defaults, the reset rules, and the multiplayer rules.
 */
public final class SprintController {
	/** Outcome of {@link #toggle(Feature)}. */
	public enum ToggleResult {
		ENABLED,
		DISABLED,
		/** The current server is not allowed, so the toggle state was left unchanged. */
		BLOCKED
	}

	private final SprintConfig config;
	private Scene scene = Scene.NONE;
	private boolean allowedWorldJoinedSinceStart = false;

	public SprintController(final SprintConfig config) {
		this.config = config;
	}

	public Scene scene() {
		return this.scene;
	}

	/**
	 * Called when the player joins a world. On an allowed scene, restores the scene's defaults if a reset rule
	 * applies: always with "reset on world exit", and for the first allowed world since the game started with
	 * "reset on game exit". Applying the reset on join instead of on exit lets it pick the next scene's defaults and
	 * also works after a crash.
	 */
	public void onJoin(final Scene scene) {
		this.scene = scene;
		if (!this.isAllowed()) {
			return;
		}

		boolean firstWorld = !this.allowedWorldJoinedSinceStart;
		this.allowedWorldJoinedSinceStart = true;
		if (this.config.resetOnWorldExit() || (this.config.resetOnGameExit() && firstWorld)) {
			this.config.restoreDefaults(scene instanceof Scene.Multiplayer);
		}
	}

	public void onDisconnect() {
		this.scene = Scene.NONE;
	}

	/**
	 * Returns whether {@code feature} should change the local player's sprinting now.
	 */
	public boolean isActive(final Feature feature) {
		return this.config.isEnabled(feature) && this.isAllowed();
	}

	/**
	 * Returns whether the multiplayer rules allow the features in the current scene.
	 */
	public boolean isAllowed() {
		return switch (this.scene) {
			case Scene.None none -> false;
			case Scene.Singleplayer singleplayer -> true;
			case Scene.Multiplayer multiplayer -> switch (this.config.multiplayerMode()) {
				case DISABLED -> false;
				case WHITELIST -> this.isListed(multiplayer.address());
				case BLACKLIST -> !this.isListed(multiplayer.address());
			};
		};
	}

	/**
	 * Flips and saves the toggle state of {@code feature}, unless the current server is not allowed.
	 */
	public ToggleResult toggle(final Feature feature) {
		if (this.scene instanceof Scene.Multiplayer && !this.isAllowed()) {
			return ToggleResult.BLOCKED;
		}
		boolean enabled = !this.config.isEnabled(feature);
		this.config.setEnabled(feature, enabled);
		return enabled ? ToggleResult.ENABLED : ToggleResult.DISABLED;
	}

	private boolean isListed(final @Nullable String address) {
		return address != null && this.config.servers().stream().anyMatch(entry -> ServerAddresses.matches(entry, address));
	}
}
