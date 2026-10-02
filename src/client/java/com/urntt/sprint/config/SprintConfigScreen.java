package com.urntt.sprint.config;

import com.urntt.sprint.Feature;
import com.urntt.sprint.SprintClient;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Configuration screen built from vanilla widgets. Every change is saved immediately.
 */
public final class SprintConfigScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("options.sprint.title");
	private static final Component MULTIPLAYER_WARNING = Component.translatable("options.sprint.multiplayer_mode.warning");

	public SprintConfigScreen(final @Nullable Screen parent) {
		super(parent, Minecraft.getInstance().options, TITLE);
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		SprintConfig config = SprintClient.config();

		this.list.addHeader(Component.translatable("options.sprint.section.current"));
		for (Feature feature : Feature.values()) {
			this.list.addBig(toggle(feature.nameKey(), Component.translatable(feature.nameKey() + ".tooltip"),
					config.isEnabled(feature), value -> config.setEnabled(feature, value)));
		}

		this.list.addHeader(Component.translatable("options.sprint.section.singleplayer_defaults"));
		this.list.addSmall(defaultToggles("options.sprint.singleplayer_default.tooltip", config::singleplayerDefault,
				config::setSingleplayerDefault));

		this.list.addHeader(Component.translatable("options.sprint.section.multiplayer_defaults"));
		this.list.addSmall(defaultToggles("options.sprint.multiplayer_default.tooltip", config::multiplayerDefault,
				config::setMultiplayerDefault));

		this.list.addHeader(Component.translatable("options.sprint.section.reset"));
		this.list.addBig(toggle("options.sprint.reset_on_world_exit",
				Component.translatable("options.sprint.reset_on_world_exit.tooltip"), config.resetOnWorldExit(),
				config::setResetOnWorldExit));
		this.list.addBig(toggle("options.sprint.reset_on_game_exit",
				Component.translatable("options.sprint.reset_on_game_exit.tooltip"), config.resetOnGameExit(),
				config::setResetOnGameExit));

		this.list.addHeader(Component.translatable("options.sprint.section.multiplayer"));
		this.list.addBig(new OptionInstance<>(
				"options.sprint.multiplayer_mode",
				mode -> Tooltip.create(mode.description().copy().append("\n\n").append(MULTIPLAYER_WARNING)),
				// The button itself prepends the caption, so this only names the value.
				(caption, mode) -> mode.label(),
				new OptionInstance.Enum<>(List.of(MultiplayerMode.values()), MultiplayerMode.CODEC),
				config.multiplayerMode(),
				config::setMultiplayerMode));
		this.list.addBig(Button.builder(Component.translatable("options.sprint.edit_servers"),
						button -> this.minecraft.gui.setScreen(new ServerListScreen(this, config)))
				.tooltip(Tooltip.create(Component.translatable("options.sprint.edit_servers.tooltip")))
				.build());
	}

	/**
	 * Creates one default toggle per feature, captioned with the feature's name. The tooltip is the translation of
	 * {@code tooltipKey} with the feature's name as its argument.
	 */
	private static OptionInstance<?>[] defaultToggles(final String tooltipKey, final Predicate<Feature> getter,
			final BiConsumer<Feature, Boolean> setter) {
		List<OptionInstance<Boolean>> toggles = new ArrayList<>();
		for (Feature feature : Feature.values()) {
			toggles.add(toggle(feature.nameKey(), Component.translatable(tooltipKey, feature.displayName()),
					getter.test(feature), value -> setter.accept(feature, value)));
		}
		return toggles.toArray(OptionInstance<?>[]::new);
	}

	private static OptionInstance<Boolean> toggle(final String captionKey, final Component tooltip, final boolean value,
			final Consumer<Boolean> onChange) {
		return OptionInstance.createBoolean(captionKey, OptionInstance.cachedConstantTooltip(tooltip), value,
				onChange::accept);
	}
}
