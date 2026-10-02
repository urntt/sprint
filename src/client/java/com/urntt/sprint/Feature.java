package com.urntt.sprint;

import net.minecraft.network.chat.Component;

/**
 * The sprint features. Each one is toggled on its own, has its own key binding, and only affects the local player.
 *
 * <p>The id is the feature's key in the configuration file and the suffix of its translation keys, so it must not
 * change.
 */
public enum Feature {
	/** Sprint whenever vanilla would let the player sprint, as if the sprint key were always held. */
	FORCE_SPRINT("force_sprint", true),
	/** Sprint with 6 or fewer food points (3 or fewer hunger shanks), which vanilla forbids. */
	IGNORE_HUNGER("ignore_hunger", false),
	/** Keep sprinting when running into a wall instead of stopping. */
	KEEP_ON_COLLISION("keep_on_collision", false),
	/** Keep sprinting, and allow starting to sprint, while not moving. */
	SPRINT_IN_PLACE("sprint_in_place", false),
	/** Sprint in every direction, not only forward. */
	OMNIDIRECTIONAL("omnidirectional", false),
	/** Allow starting to sprint while sneaking. */
	SPRINT_WHILE_SNEAKING("sprint_while_sneaking", false);

	private final String id;
	private final boolean enabledByDefault;
	private final Component displayName;

	Feature(final String id, final boolean enabledByDefault) {
		this.id = id;
		this.enabledByDefault = enabledByDefault;
		this.displayName = Component.translatable(this.nameKey());
	}

	public String id() {
		return this.id;
	}

	/** The state of a fresh installation, also used as its singleplayer and server defaults. */
	public boolean enabledByDefault() {
		return this.enabledByDefault;
	}

	/** Translation key of the feature's name. */
	public String nameKey() {
		return "options." + SprintClient.MOD_ID + ".feature." + this.id;
	}

	public Component displayName() {
		return this.displayName;
	}

	/** Translation key, and therefore name, of the key binding that toggles the feature. */
	public String toggleKeyName() {
		return "key." + SprintClient.MOD_ID + ".toggle." + this.id;
	}
}
