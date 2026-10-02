package com.urntt.sprint.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.urntt.sprint.Feature;
import com.urntt.sprint.SprintClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's settings, persisted as JSON. Every change is written to disk immediately.
 */
public final class SprintConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(SprintClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final Settings settings;

	private SprintConfig(final Path path, final Settings settings) {
		this.path = path;
		this.settings = settings;
	}

	/**
	 * Returns the location of the config file in the Fabric config directory.
	 */
	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(SprintClient.MOD_ID + ".json");
	}

	/**
	 * Loads the configuration from {@code path}. A missing file is created with the defaults. A readable file is
	 * rewritten with any settings it lacks. An unreadable file falls back to the defaults and is left untouched
	 * until the next change is saved.
	 */
	public static SprintConfig load(final Path path) {
		if (Files.notExists(path)) {
			SprintConfig config = new SprintConfig(path, Settings.defaults());
			config.save();
			return config;
		}

		Settings settings;
		try {
			settings = GSON.fromJson(Files.readString(path), Settings.class);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to read config file {}, using defaults", path, e);
			return new SprintConfig(path, Settings.defaults());
		}

		if (settings == null) {
			settings = new Settings();
		}
		settings.normalize();
		SprintConfig config = new SprintConfig(path, settings);
		config.save();
		return config;
	}

	/** The current state of {@code feature}, as toggled by its key binding. */
	public boolean isEnabled(final Feature feature) {
		return this.feature(feature).enabled();
	}

	public void setEnabled(final Feature feature, final boolean enabled) {
		this.feature(feature).enabled = enabled;
		this.save();
	}

	/** The state of {@code feature} that a reset restores in singleplayer worlds. */
	public boolean singleplayerDefault(final Feature feature) {
		return this.feature(feature).singleplayerDefault();
	}

	public void setSingleplayerDefault(final Feature feature, final boolean singleplayerDefault) {
		this.feature(feature).singleplayerDefault = singleplayerDefault;
		this.save();
	}

	/** The state of {@code feature} that a reset restores on multiplayer servers that the multiplayer mode allows. */
	public boolean multiplayerDefault(final Feature feature) {
		return this.feature(feature).multiplayerDefault();
	}

	public void setMultiplayerDefault(final Feature feature, final boolean multiplayerDefault) {
		this.feature(feature).multiplayerDefault = multiplayerDefault;
		this.save();
	}

	/**
	 * Sets every feature to its singleplayer default, or to its server default if {@code multiplayer} is true.
	 */
	public void restoreDefaults(final boolean multiplayer) {
		for (FeatureSettings settings : this.settings.features.values()) {
			settings.enabled = multiplayer ? settings.multiplayerDefault() : settings.singleplayerDefault();
		}
		this.save();
	}

	/** Whether every world starts from its default state. */
	public boolean resetOnWorldExit() {
		return this.settings.resetOnWorldExit;
	}

	public void setResetOnWorldExit(final boolean resetOnWorldExit) {
		this.settings.resetOnWorldExit = resetOnWorldExit;
		this.save();
	}

	/** Whether the first world after starting the game starts from its default state. */
	public boolean resetOnGameExit() {
		return this.settings.resetOnGameExit;
	}

	public void setResetOnGameExit(final boolean resetOnGameExit) {
		this.settings.resetOnGameExit = resetOnGameExit;
		this.save();
	}

	public MultiplayerMode multiplayerMode() {
		return this.settings.multiplayerMode;
	}

	public void setMultiplayerMode(final MultiplayerMode multiplayerMode) {
		this.settings.multiplayerMode = multiplayerMode;
		this.save();
	}

	/** The server list used by the whitelist and blacklist modes. */
	public List<String> servers() {
		return List.copyOf(this.settings.servers);
	}

	public void setServers(final List<String> servers) {
		this.settings.servers = new ArrayList<>(servers);
		this.settings.normalize();
		this.save();
	}

	private FeatureSettings feature(final Feature feature) {
		return this.settings.features.get(feature.id());
	}

	private void save() {
		try {
			Files.createDirectories(this.path.getParent());
			Files.writeString(this.path, GSON.toJson(this.settings));
		} catch (IOException e) {
			LOGGER.error("Failed to write config file {}", this.path, e);
		}
	}

	/**
	 * The serialized form. Field initializers are the defaults, which also apply to keys missing from the file. The
	 * per-feature defaults come from {@link Feature#enabledByDefault()} and are filled in by {@link #normalize()}.
	 */
	private static final class Settings {
		private Map<String, FeatureSettings> features = new LinkedHashMap<>();
		private boolean resetOnWorldExit = false;
		private boolean resetOnGameExit = false;
		private MultiplayerMode multiplayerMode = MultiplayerMode.DISABLED;
		private List<String> servers = new ArrayList<>();

		private static Settings defaults() {
			Settings settings = new Settings();
			settings.normalize();
			return settings;
		}

		/**
		 * Fills in missing features and feature settings, drops unknown features, replaces values Gson could not
		 * map (unknown mode names, a missing list), and tidies the server list.
		 */
		private void normalize() {
			Map<String, FeatureSettings> normalized = new LinkedHashMap<>();
			for (Feature feature : Feature.values()) {
				FeatureSettings settings = this.features != null ? this.features.get(feature.id()) : null;
				normalized.put(feature.id(), FeatureSettings.complete(settings, feature.enabledByDefault()));
			}
			this.features = normalized;

			if (this.multiplayerMode == null) {
				this.multiplayerMode = MultiplayerMode.DISABLED;
			}
			List<String> entries = new ArrayList<>();
			if (this.servers != null) {
				for (String entry : this.servers) {
					if (entry != null && !entry.isBlank()) {
						entries.add(entry.trim());
					}
				}
			}
			this.servers = entries;
		}
	}

	/**
	 * The settings of one feature. The fields are boxed so that keys missing from the file can be told apart and
	 * filled in with the feature's own default.
	 */
	private static final class FeatureSettings {
		private @Nullable Boolean enabled;
		private @Nullable Boolean singleplayerDefault;
		private @Nullable Boolean multiplayerDefault;

		private static FeatureSettings complete(final @Nullable FeatureSettings settings, final boolean fallback) {
			FeatureSettings complete = settings != null ? settings : new FeatureSettings();
			if (complete.enabled == null) {
				complete.enabled = fallback;
			}
			if (complete.singleplayerDefault == null) {
				complete.singleplayerDefault = fallback;
			}
			if (complete.multiplayerDefault == null) {
				complete.multiplayerDefault = fallback;
			}
			return complete;
		}

		private boolean enabled() {
			return Boolean.TRUE.equals(this.enabled);
		}

		private boolean singleplayerDefault() {
			return Boolean.TRUE.equals(this.singleplayerDefault);
		}

		private boolean multiplayerDefault() {
			return Boolean.TRUE.equals(this.multiplayerDefault);
		}
	}
}
