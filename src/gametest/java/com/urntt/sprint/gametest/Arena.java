package com.urntt.sprint.gametest;

import static com.urntt.sprint.gametest.GameTestSupport.LOGGER;
import static com.urntt.sprint.gametest.GameTestSupport.check;
import static com.urntt.sprint.gametest.GameTestSupport.releaseMovementKeys;

import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A spot on flat ground where the tests drive the player with real key presses and read its sprint state on the
 * client. Every scenario starts from {@link #reset()}: no keys held, standing on the start block, facing south
 * (positive z), so forward is +z and left is +x.
 */
@SuppressWarnings("UnstableApiUsage")
final class Arena {
	/** Ticks a movement key is held before the sprint state is read; vanilla decides within one tick. */
	static final int SETTLE_TICKS = 10;
	/** Ticks of running before a jump, enough to reach full sprint speed. */
	private static final int RUN_UP_TICKS = 15;

	private final ClientGameTestContext context;
	private final TestServerContext server;
	private final BlockPos start;

	private Arena(final ClientGameTestContext context, final TestServerContext server, final BlockPos start) {
		this.context = context;
		this.server = server;
		this.start = start;
	}

	/**
	 * Switches to survival, where food matters, keeps the player fed, and uses the block the player stands on as the
	 * start.
	 */
	static Arena create(final ClientGameTestContext context, final TestServerContext server) {
		server.runCommand("gamemode survival @a");
		context.waitFor(client -> client.player != null && client.player.onGround());
		Arena arena = new Arena(context, server, context.computeOnClient(client -> client.player.blockPosition()));
		arena.keepFed();
		return arena;
	}

	BlockPos start() {
		return this.start;
	}

	TestServerContext server() {
		return this.server;
	}

	/** Keeps the food level full, so that sprinting in the tests never runs into the hunger limit by accident. */
	void keepFed() {
		this.server.runCommand("effect give @a minecraft:saturation infinite 0 true");
	}

	/**
	 * Releases all movement keys, teleports the player to the start facing south, and waits until it stands there.
	 */
	void reset() {
		releaseMovementKeys(this.context);
		this.waitUntilStill();
		this.server.runCommand("tp @a %d %d %d 0 0".formatted(this.start.getX(), this.start.getY(), this.start.getZ()));
		this.context.waitFor(client -> client.player.blockPosition().equals(this.start));
		this.waitUntilStill();
	}

	private void waitUntilStill() {
		this.context.waitFor(client -> client.player.onGround()
				&& client.player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6);
		this.context.waitTick();
	}

	boolean isSprinting() {
		return this.context.computeOnClient(client -> client.player.isSprinting());
	}

	Vec3 position() {
		return this.context.computeOnClient(client -> client.player.position());
	}

	/**
	 * Holds {@code keys} for {@link #SETTLE_TICKS} ticks and returns whether the player is sprinting at the end, while
	 * the keys are still held.
	 */
	@SafeVarargs
	final boolean sprintsWhileHolding(final String situation, final Function<Options, KeyMapping>... keys) {
		List.of(keys).forEach(key -> this.context.getInput().holdKey(key));
		this.context.waitTicks(SETTLE_TICKS);
		boolean sprinting = this.isSprinting();
		List.of(keys).forEach(key -> this.context.getInput().releaseKey(key));
		LOGGER.info("Sprinting {}: {}", situation, sprinting);
		return sprinting;
	}

	/**
	 * Starts sneaking first, so that the player is already crouching when {@code keys} are pressed, then does the same
	 * as {@link #sprintsWhileHolding}.
	 */
	@SafeVarargs
	final boolean sprintsWhileSneakingAndHolding(final String situation, final Function<Options, KeyMapping>... keys) {
		this.context.getInput().holdKey(options -> options.keyShift);
		this.context.waitFor(client -> client.player.isCrouching());
		boolean sprinting = this.sprintsWhileHolding(situation, keys);
		this.context.getInput().releaseKey(options -> options.keyShift);
		return sprinting;
	}

	/**
	 * Taps the forward key, then holds it, which is vanilla's double-tap to sprint, and returns whether the player is
	 * sprinting after {@link #SETTLE_TICKS} ticks.
	 */
	boolean sprintsAfterDoubleTap(final String situation) {
		this.context.getInput().holdKeyFor(options -> options.keyUp, 1);
		this.context.waitTick();
		return this.sprintsWhileHolding(situation, options -> options.keyUp);
	}

	@SafeVarargs
	final void checkSprints(final String situation, final Function<Options, KeyMapping>... keys) {
		this.reset();
		check(this.sprintsWhileHolding(situation, keys), "expected to sprint " + situation);
	}

	@SafeVarargs
	final void checkDoesNotSprint(final String situation, final Function<Options, KeyMapping>... keys) {
		this.reset();
		check(!this.sprintsWhileHolding(situation, keys), "expected not to sprint " + situation);
	}

	/**
	 * Runs in {@code direction} until sprinting at full speed, jumps once, and returns how far the player moved from
	 * the take-off to the landing.
	 */
	Vec3 sprintJump(final String situation, final Function<Options, KeyMapping> direction) {
		this.reset();
		this.context.getInput().holdKey(direction);
		this.context.waitTicks(RUN_UP_TICKS);
		check(this.isSprinting(), "expected to sprint before the jump " + situation);
		Vec3 displacement = this.jump();
		this.context.getInput().releaseKey(direction);
		LOGGER.info("Sprint jump {}: moved {}", situation, displacement);
		return displacement;
	}

	/**
	 * Jumps once and returns how far the player moved from the take-off to the landing.
	 */
	Vec3 jump() {
		Vec3 before = this.position();
		this.context.getInput().holdKey(options -> options.keyJump);
		this.context.waitFor(client -> !client.player.onGround());
		this.context.getInput().releaseKey(options -> options.keyJump);
		this.context.waitFor(client -> client.player.onGround());
		return this.position().subtract(before);
	}
}
