package com.urntt.sprint;

import net.minecraft.client.player.ClientInput;

/**
 * The sprint rules shared by the mixins. Vanilla only lets the local player sprint forward; these rules decide when the
 * features allow more. Every rule is inactive whenever {@link SprintController} says so, which keeps vanilla
 * behavior in that case.
 */
public final class SprintRules {
	private SprintRules() {
	}

	public static boolean isActive(final Feature feature) {
		return SprintClient.controller().isActive(feature);
	}

	/**
	 * Returns whether the sprint key counts as held. Forced sprint makes it count as held all the time, like vanilla's
	 * "toggle sprint" option, but it is part of the saved configuration, so it survives death, teleports, dimension
	 * changes, rejoining a world, and restarting the game.
	 */
	public static boolean isSprintKeyDown(final boolean keyDown) {
		return keyDown || isActive(Feature.FORCE_SPRINT);
	}

	/**
	 * Returns whether the features let the player sprint with this movement input although it has no forward
	 * component: any other direction with omnidirectional sprint, and no input at all with sprint in place.
	 */
	public static boolean allowsNonForwardSprint(final ClientInput input) {
		return isMoving(input) ? isActive(Feature.OMNIDIRECTIONAL) : isActive(Feature.SPRINT_IN_PLACE);
	}

	/** Same test as vanilla's private {@code LocalPlayer.isMoving()}. */
	public static boolean isMoving(final ClientInput input) {
		return input.getMoveVector().lengthSquared() > 0.0F;
	}
}
