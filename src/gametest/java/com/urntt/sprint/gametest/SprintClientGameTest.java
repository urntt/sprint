package com.urntt.sprint.gametest;

import static com.urntt.sprint.gametest.GameTestSupport.LOGGER;
import static com.urntt.sprint.gametest.GameTestSupport.SETTINGS_TEST_KEY;
import static com.urntt.sprint.gametest.GameTestSupport.bindKey;
import static com.urntt.sprint.gametest.GameTestSupport.check;
import static com.urntt.sprint.gametest.GameTestSupport.configure;
import static com.urntt.sprint.gametest.GameTestSupport.enableOnly;
import static com.urntt.sprint.gametest.GameTestSupport.isEnabled;
import static com.urntt.sprint.gametest.GameTestSupport.loadSavedConfig;
import static com.urntt.sprint.gametest.GameTestSupport.pressToggleKey;
import static com.urntt.sprint.gametest.GameTestSupport.unbindKey;

import com.urntt.sprint.Feature;
import com.urntt.sprint.SprintClient;
import com.urntt.sprint.config.MultiplayerMode;
import com.urntt.sprint.config.ServerListScreen;
import com.urntt.sprint.config.SprintConfigScreen;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2d;
import org.jspecify.annotations.Nullable;

/**
 * Checks every feature, the toggle keys, the settings key, and the reset on world exit in singleplayer worlds. Each
 * feature is measured against vanilla first, with the feature off, so a check cannot pass without the feature.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SprintClientGameTest implements FabricClientGameTest {
	/** The highest food level at which vanilla does not allow sprinting. */
	private static final int HUNGRY_FOOD_LEVEL = 6;
	private static final int FULL_FOOD_LEVEL = 20;
	/** How far the teleport check moves the player, well beyond the render distance. */
	private static final int TELEPORT_DISTANCE = 500;
	/** Standing on the Nether's bedrock roof gives flat, safe ground in the Nether. */
	private static final int NETHER_ROOF_Y = 128;
	/** Ticks the player stands still before the sprint state is read again. */
	private static final int STANDING_TICKS = 5;
	/** A sideways or backward sprint jump must cover at least this share of a forward one. */
	private static final double MIN_JUMP_DISTANCE_RATIO = 0.9;
	/** A jump in place must not move the player further than this. */
	private static final double MAX_JUMP_IN_PLACE_DRIFT = 0.1;
	private static final String MULTIPLAYER_MODE_CAPTION = "options.sprint.multiplayer_mode";

	@Override
	public void runTest(final ClientGameTestContext context) {
		configure(context, config -> {
			config.setResetOnWorldExit(false);
			config.setResetOnGameExit(false);
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
		});
		enableOnly(context, Feature.FORCE_SPRINT);

		TestWorldSave save;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			save = singleplayer.getWorldSave();
			singleplayer.getConnection().waitForChunksRender();
			Arena arena = Arena.create(context, singleplayer.getServer());

			checkForceSprint(context, arena);
			checkForceSprintAfterRespawn(context, arena);
			checkForceSprintAfterTeleport(context, arena, singleplayer);
			checkForceSprintAfterDimensionChange(context, arena, singleplayer);
			checkIgnoreHunger(context, arena);
			checkKeepOnCollision(context, arena);
			checkSprintInPlace(context, arena);
			checkOmnidirectional(context, arena);
			checkSprintWhileSneaking(context, arena);
			checkToggleKeys(context);
			checkSettingsScreens(context);
			enableOnly(context, Feature.FORCE_SPRINT);
			arena.reset();
		}

		try (TestSingleplayerContext singleplayer = save.open()) {
			singleplayer.getConnection().waitForChunksRender();
			Arena arena = Arena.create(context, singleplayer.getServer());
			arena.checkSprints("after rejoining the world", options -> options.keyUp);

			// Leave this world with force sprint off to check the reset on world exit below.
			configure(context, config -> config.setResetOnWorldExit(true));
			pressToggleKey(context, Feature.FORCE_SPRINT);
			check(!isEnabled(context, Feature.FORCE_SPRINT), "toggle key should disable force sprint before leaving");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(isEnabled(context, Feature.FORCE_SPRINT), "reset on world exit should restore the singleplayer default");

			configure(context, config -> config.setResetOnWorldExit(false));
			pressToggleKey(context, Feature.FORCE_SPRINT);
			check(!isEnabled(context, Feature.FORCE_SPRINT), "toggle key should disable force sprint before leaving");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(!isEnabled(context, Feature.FORCE_SPRINT), "without reset on world exit the state should carry over");
		}

		enableOnly(context, Feature.FORCE_SPRINT);
	}

	private static void checkForceSprint(final ClientGameTestContext context, final Arena arena) {
		arena.checkSprints("walking forward with force sprint", options -> options.keyUp);

		pressToggleKey(context, Feature.FORCE_SPRINT);
		check(!isEnabled(context, Feature.FORCE_SPRINT), "toggle key should disable force sprint");
		check(!loadSavedConfig().isEnabled(Feature.FORCE_SPRINT), "disabled state should be saved to the config file");
		context.takeScreenshot("sprint-force-sprint-toggled-off");
		arena.checkDoesNotSprint("walking forward without force sprint", options -> options.keyUp);
		arena.checkSprints("holding the sprint key without force sprint", options -> options.keyUp,
				options -> options.keySprint);

		pressToggleKey(context, Feature.FORCE_SPRINT);
		check(isEnabled(context, Feature.FORCE_SPRINT), "toggle key should enable force sprint again");
		check(loadSavedConfig().isEnabled(Feature.FORCE_SPRINT), "enabled state should be saved to the config file");
	}

	private static void checkForceSprintAfterRespawn(final ClientGameTestContext context, final Arena arena) {
		arena.reset();
		arena.server().runCommand("kill @a");
		context.waitForScreen(DeathScreen.class);
		context.runOnClient(client -> client.player.respawn());
		context.waitFor(client -> client.player != null && client.player.isAlive()
				&& !(client.gui.screen() instanceof DeathScreen));
		context.setScreen(() -> null);
		// Dying clears the saturation effect.
		arena.keepFed();
		arena.checkSprints("after dying and respawning", options -> options.keyUp);
	}

	private static void checkForceSprintAfterTeleport(final ClientGameTestContext context, final Arena arena,
			final TestSingleplayerContext singleplayer) {
		arena.reset();
		arena.server().runCommand("execute as @a at @s run tp @s ~%d ~ ~".formatted(TELEPORT_DISTANCE));
		context.waitFor(client -> client.player.blockPosition().getX() == arena.start().getX() + TELEPORT_DISTANCE);
		singleplayer.getConnection().waitForChunksRender();
		context.waitFor(client -> client.player.onGround());
		check(arena.sprintsWhileHolding("after teleporting", options -> options.keyUp),
				"expected to sprint after teleporting");

		arena.reset();
		singleplayer.getConnection().waitForChunksRender();
	}

	private static void checkForceSprintAfterDimensionChange(final ClientGameTestContext context, final Arena arena,
			final TestSingleplayerContext singleplayer) {
		arena.reset();
		arena.server().runCommand("execute in minecraft:the_nether run tp @a 0 %d 0 0 0".formatted(NETHER_ROOF_Y));
		context.waitFor(client -> client.level != null && client.level.dimension() == Level.NETHER);
		singleplayer.getConnection().waitForChunksRender();
		context.waitFor(client -> client.player.onGround());
		check(arena.sprintsWhileHolding("after changing to the Nether", options -> options.keyUp),
				"expected to sprint after changing dimension");

		BlockPos start = arena.start();
		arena.server().runCommand("execute in minecraft:overworld run tp @a %d %d %d 0 0"
				.formatted(start.getX(), start.getY(), start.getZ()));
		context.waitFor(client -> client.level != null && client.level.dimension() == Level.OVERWORLD);
		singleplayer.getConnection().waitForChunksRender();
		arena.reset();
	}

	private static void checkIgnoreHunger(final ClientGameTestContext context, final Arena arena) {
		enableOnly(context, Feature.FORCE_SPRINT);
		arena.server().runCommand("effect clear @a minecraft:saturation");
		setFoodLevel(context, arena.server(), HUNGRY_FOOD_LEVEL);
		arena.checkDoesNotSprint("when hungry", options -> options.keyUp);

		pressToggleKey(context, Feature.IGNORE_HUNGER);
		check(isEnabled(context, Feature.IGNORE_HUNGER), "toggle key should enable sprint when hungry");
		arena.checkSprints("when hungry with sprint when hungry", options -> options.keyUp);

		pressToggleKey(context, Feature.IGNORE_HUNGER);
		setFoodLevel(context, arena.server(), FULL_FOOD_LEVEL);
		arena.keepFed();
	}

	private static void checkKeepOnCollision(final ClientGameTestContext context, final Arena arena) {
		enableOnly(context, Feature.FORCE_SPRINT);
		BlockPos wall = arena.start().south(2);
		arena.server().runCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(
				wall.getX() - 2, wall.getY(), wall.getZ(), wall.getX() + 2, wall.getY() + 2, wall.getZ()));
		context.waitFor(client -> !client.level.getBlockState(wall).isAir());

		arena.checkDoesNotSprint("running into a wall", options -> options.keyUp);
		pressToggleKey(context, Feature.KEEP_ON_COLLISION);
		check(isEnabled(context, Feature.KEEP_ON_COLLISION), "toggle key should enable keep sprinting at walls");
		arena.checkSprints("running into a wall with keep sprinting at walls", options -> options.keyUp);
		pressToggleKey(context, Feature.KEEP_ON_COLLISION);

		arena.server().runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(
				wall.getX() - 2, wall.getY(), wall.getZ(), wall.getX() + 2, wall.getY() + 2, wall.getZ()));
		context.waitFor(client -> client.level.getBlockState(wall).isAir());
	}

	private static void checkSprintInPlace(final ClientGameTestContext context, final Arena arena) {
		enableOnly(context, Feature.FORCE_SPRINT);
		check(!sprintsAfterStopping(context, arena), "expected to stop sprinting when standing still");
		pressToggleKey(context, Feature.SPRINT_IN_PLACE);
		check(isEnabled(context, Feature.SPRINT_IN_PLACE), "toggle key should enable sprint in place");
		check(sprintsAfterStopping(context, arena), "expected to keep sprinting when standing still with sprint in place");

		// Start sprinting without moving: forced, then with the sprint key.
		pressToggleKey(context, Feature.SPRINT_IN_PLACE);
		arena.reset();
		check(!arena.isSprinting(), "expected not to sprint while standing without sprint in place");
		pressToggleKey(context, Feature.SPRINT_IN_PLACE);
		context.waitTicks(STANDING_TICKS);
		LOGGER.info("Sprinting while standing with force sprint and sprint in place: {}", arena.isSprinting());
		check(arena.isSprinting(), "force sprint should start sprinting in place");
		// Standing still with every feature off ends the sprint, which sprint in place alone would keep.
		enableOnly(context);
		arena.reset();
		enableOnly(context, Feature.SPRINT_IN_PLACE);
		check(!arena.sprintsWhileHolding("standing with sprint in place only"),
				"sprint in place alone should not start sprinting");
		check(arena.sprintsWhileHolding("standing with sprint in place and the sprint key", options -> options.keySprint),
				"holding the sprint key should start sprinting in place");

		enableOnly(context, Feature.FORCE_SPRINT, Feature.SPRINT_IN_PLACE);
		arena.reset();
		context.waitFor(client -> client.player.isSprinting());
		Vec3 drift = arena.jump();
		LOGGER.info("Jump in place while sprinting: moved {}", drift);
		check(drift.horizontalDistance() < MAX_JUMP_IN_PLACE_DRIFT,
				"a jump in place should not move the player, moved " + drift);
		enableOnly(context, Feature.FORCE_SPRINT);
	}

	/**
	 * Runs forward until sprinting, stops, and returns whether the player is still sprinting after standing still.
	 */
	private static boolean sprintsAfterStopping(final ClientGameTestContext context, final Arena arena) {
		arena.reset();
		check(arena.sprintsWhileHolding("before stopping", options -> options.keyUp), "expected to sprint before stopping");
		context.waitTicks(STANDING_TICKS);
		boolean sprinting = arena.isSprinting();
		LOGGER.info("Sprinting after standing still for {} ticks: {}", STANDING_TICKS, sprinting);
		return sprinting;
	}

	private static void checkOmnidirectional(final ClientGameTestContext context, final Arena arena) {
		enableOnly(context, Feature.FORCE_SPRINT);
		Vec3 forwardJump = arena.sprintJump("forward", options -> options.keyUp);

		arena.checkDoesNotSprint("walking backward", options -> options.keyDown);
		arena.checkDoesNotSprint("walking left", options -> options.keyLeft);
		pressToggleKey(context, Feature.OMNIDIRECTIONAL);
		check(isEnabled(context, Feature.OMNIDIRECTIONAL), "toggle key should enable omnidirectional sprint");
		arena.checkSprints("walking backward with omnidirectional sprint", options -> options.keyDown);
		arena.checkSprints("walking left with omnidirectional sprint", options -> options.keyLeft);
		arena.checkSprints("walking backward and right with omnidirectional sprint", options -> options.keyDown,
				options -> options.keyRight);

		// Sprint jumps follow the movement, so they cover as much ground as a forward sprint jump.
		Vec3 backwardJump = arena.sprintJump("backward", options -> options.keyDown);
		Vec3 leftJump = arena.sprintJump("left", options -> options.keyLeft);
		check(-backwardJump.z >= forwardJump.z * MIN_JUMP_DISTANCE_RATIO,
				"a backward sprint jump should move backward as far as a forward one moves forward, got "
						+ backwardJump + " vs " + forwardJump);
		check(leftJump.x >= forwardJump.z * MIN_JUMP_DISTANCE_RATIO,
				"a sprint jump to the left should move left as far as a forward one moves forward, got "
						+ leftJump + " vs " + forwardJump);

		enableOnly(context, Feature.OMNIDIRECTIONAL);
		arena.checkDoesNotSprint("walking backward with omnidirectional sprint only", options -> options.keyDown);
		arena.checkSprints("walking backward with omnidirectional sprint and the sprint key",
				options -> options.keyDown, options -> options.keySprint);
		enableOnly(context, Feature.FORCE_SPRINT);
	}

	private static void checkSprintWhileSneaking(final ClientGameTestContext context, final Arena arena) {
		enableOnly(context, Feature.FORCE_SPRINT);
		arena.reset();
		check(!arena.sprintsWhileSneakingAndHolding("sneaking forward", options -> options.keyUp),
				"expected not to sprint when starting to run while sneaking");
		pressToggleKey(context, Feature.SPRINT_WHILE_SNEAKING);
		check(isEnabled(context, Feature.SPRINT_WHILE_SNEAKING), "toggle key should enable sprint while sneaking");
		arena.reset();
		check(arena.sprintsWhileSneakingAndHolding("sneaking forward with sprint while sneaking",
				options -> options.keyUp), "expected to sprint when starting to run while sneaking");

		// Double-tapping forward, without force sprint.
		enableOnly(context);
		arena.reset();
		check(arena.sprintsAfterDoubleTap("after double-tapping forward"), "double-tapping forward should sprint");
		check(!sprintsAfterDoubleTapWhileSneaking(context, arena), "sneaking should cancel the double-tap in vanilla");
		enableOnly(context, Feature.SPRINT_WHILE_SNEAKING);
		check(sprintsAfterDoubleTapWhileSneaking(context, arena),
				"double-tapping forward while sneaking should sprint with sprint while sneaking");
		enableOnly(context, Feature.FORCE_SPRINT);
	}

	private static boolean sprintsAfterDoubleTapWhileSneaking(final ClientGameTestContext context, final Arena arena) {
		arena.reset();
		context.getInput().holdKey(options -> options.keyShift);
		context.waitFor(client -> client.player.isCrouching());
		boolean sprinting = arena.sprintsAfterDoubleTap("after double-tapping forward while sneaking");
		context.getInput().releaseKey(options -> options.keyShift);
		return sprinting;
	}

	/**
	 * Flips every feature with its toggle key and back, checking the saved configuration each time.
	 */
	private static void checkToggleKeys(final ClientGameTestContext context) {
		for (Feature feature : Feature.values()) {
			boolean before = isEnabled(context, feature);
			pressToggleKey(context, feature);
			check(isEnabled(context, feature) != before, "toggle key should flip " + feature);
			check(loadSavedConfig().isEnabled(feature) != before, "the flipped state of " + feature + " should be saved");
			pressToggleKey(context, feature);
			check(isEnabled(context, feature) == before, "toggle key should flip " + feature + " back");
		}
	}

	/**
	 * Opens the settings with the key binding, then takes screenshots of both settings screens in English and in
	 * Simplified Chinese.
	 */
	private static void checkSettingsScreens(final ClientGameTestContext context) {
		KeyMapping openSettingsKey = bindKey(context, SprintClient.OPEN_SETTINGS_KEY_NAME, SETTINGS_TEST_KEY);
		context.getInput().pressKey(openSettingsKey);
		context.waitForScreen(SprintConfigScreen.class);
		context.setScreen(() -> null);
		unbindKey(context, openSettingsKey);

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.WHITELIST);
			config.setServers(List.of("mc.example.com", "192.168.1.5"));
		});
		takeSettingsScreenshots(context, "en_us");
		switchLanguage(context, "zh_cn");
		takeSettingsScreenshots(context, "zh_cn");
		switchLanguage(context, "en_us");

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
	}

	private static void takeSettingsScreenshots(final ClientGameTestContext context, final String language) {
		context.setScreen(() -> new SprintConfigScreen(null));
		// Opening a screen centers the cursor; keep it away from the widgets so no tooltip covers them.
		context.getInput().setCursorPos(0, 0);
		context.takeScreenshot("sprint-config-screen-top-" + language);
		scrollSettings(context, 0.5);
		context.takeScreenshot("sprint-config-screen-middle-" + language);
		scrollSettings(context, 1.0);
		context.takeScreenshot("sprint-config-screen-bottom-" + language);
		hoverOption(context, MULTIPLAYER_MODE_CAPTION);
		context.takeScreenshot("sprint-config-screen-multiplayer-warning-" + language);

		context.setScreen(() -> new ServerListScreen(new SprintConfigScreen(null), SprintClient.config()));
		context.getInput().setCursorPos(0, 0);
		context.takeScreenshot("sprint-server-list-" + language);
		context.setScreen(() -> null);
	}

	/**
	 * Scrolls the settings list to {@code fraction} of its height. Scrolling with the mouse wheel would need the cursor
	 * over the list, where it would show a tooltip.
	 */
	private static void scrollSettings(final ClientGameTestContext context, final double fraction) {
		context.runOnClient(client -> {
			AbstractScrollArea list = findChild(client.gui.screen(), AbstractScrollArea.class);
			check(list != null, "the settings screen should have a scrolling list");
			list.setScrollAmount(list.maxScrollAmount() * fraction);
		});
		// Rendering a frame moves the widgets to their scrolled positions.
		context.waitTick();
	}

	/**
	 * Moves the cursor over the option button whose caption has the translation key {@code captionKey}, so that its
	 * tooltip shows. Captions are matched by translation key, so this works in every language.
	 */
	private static void hoverOption(final ClientGameTestContext context, final String captionKey) {
		Vector2d position = context.computeOnClient(client -> {
			AbstractWidget option = findWidget(client.gui.screen(), widget -> hasCaption(widget, captionKey));
			check(option != null, "the option " + captionKey + " should be on the screen");
			int scale = client.getWindow().getGuiScale();
			return new Vector2d((option.getX() + option.getWidth() / 2.0) * scale,
					(option.getY() + option.getHeight() / 2.0) * scale);
		});
		context.getInput().setCursorPos(position.x, position.y);
		context.waitTick();
	}

	private static @Nullable AbstractWidget findWidget(final ContainerEventHandler container,
			final Predicate<AbstractWidget> predicate) {
		return findChild(container, AbstractWidget.class, predicate);
	}

	private static <T> @Nullable T findChild(final ContainerEventHandler container, final Class<T> type) {
		return findChild(container, type, child -> true);
	}

	/**
	 * Searches the widget tree below {@code container}, depth first, for a {@code type} matching {@code predicate}.
	 */
	private static <T> @Nullable T findChild(final ContainerEventHandler container, final Class<T> type,
			final Predicate<? super T> predicate) {
		for (GuiEventListener child : container.children()) {
			if (type.isInstance(child) && predicate.test(type.cast(child))) {
				return type.cast(child);
			}
			if (child instanceof ContainerEventHandler nested) {
				T found = findChild(nested, type, predicate);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** Option buttons show "caption: value" through vanilla's {@code options.generic_value}. */
	private static boolean hasCaption(final AbstractWidget widget, final String captionKey) {
		return widget.getMessage().getContents() instanceof TranslatableContents message
				&& message.getKey().equals("options.generic_value")
				&& message.getArgs().length > 0
				&& message.getArgs()[0] instanceof Component caption
				&& caption.getContents() instanceof TranslatableContents captionContents
				&& captionContents.getKey().equals(captionKey);
	}

	private static void switchLanguage(final ClientGameTestContext context, final String language) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(language);
			client.options.languageCode = language;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
	}

	private static void setFoodLevel(final ClientGameTestContext context, final TestServerContext server,
			final int foodLevel) {
		server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers()
				.forEach(player -> player.getFoodData().setFoodLevel(foodLevel)));
		context.waitFor(client -> client.player.getFoodData().getFoodLevel() == foodLevel);
	}
}
