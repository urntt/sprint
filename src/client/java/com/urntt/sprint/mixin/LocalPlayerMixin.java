package com.urntt.sprint.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.urntt.sprint.Feature;
import com.urntt.sprint.SprintRules;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Changes when the local player starts and stops sprinting. {@code LocalPlayer.aiStep} starts sprinting when
 * {@code canStartSprinting()} holds and the sprint key is held (or forward is double-tapped), and stops run sprinting
 * when {@code shouldStopRunSprinting()} holds. Each handler below relaxes one condition for one feature and returns
 * the vanilla value whenever that feature is not active. Swim sprinting, which has its own stop rule, stays vanilla.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	/**
	 * Forced sprint: the sprint key counts as held when {@code aiStep} decides whether to start sprinting. Only this
	 * read changes, so the input sent to the server still reports the real key state.
	 */
	@ModifyExpressionValue(method = "aiStep",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;sprint()Z"))
	private boolean sprint$forceSprint(final boolean keyDown) {
		return SprintRules.isSprintKeyDown(keyDown);
	}

	/**
	 * Sprint while sneaking: sneaking no longer cancels a double-tap of the forward key. This is the sneak state of
	 * the previous tick, read before the input is updated.
	 */
	@ModifyExpressionValue(method = "aiStep",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;shift()Z"),
			slice = @Slice(to = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V")))
	private boolean sprint$keepDoubleTapWhileSneaking(final boolean wasSneaking) {
		return wasSneaking && !SprintRules.isActive(Feature.SPRINT_WHILE_SNEAKING);
	}

	/**
	 * Sprint while sneaking: sneaking no longer prevents starting to sprint. Crawling still does.
	 */
	@ModifyExpressionValue(method = "canStartSprinting",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"))
	private boolean sprint$allowStartWhileSneaking(final boolean movingSlowly) {
		if (SprintRules.isActive(Feature.SPRINT_WHILE_SNEAKING)) {
			return ((LocalPlayer) (Object) this).isVisuallyCrawling();
		}
		return movingSlowly;
	}

	/**
	 * Omnidirectional sprint and sprint in place: allow starting to sprint without forward input. This only applies
	 * when the sprint key is held (or forced), so a single tap of another movement key cannot complete vanilla's
	 * forward double-tap, and not in water, where sprinting turns into swimming, which keeps the vanilla rules.
	 */
	@ModifyExpressionValue(method = "canStartSprinting",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
	private boolean sprint$allowStartInAnyDirection(final boolean hasForwardImpulse) {
		if (hasForwardImpulse) {
			return true;
		}
		LocalPlayer player = (LocalPlayer) (Object) this;
		return SprintRules.isSprintKeyDown(player.input.keyPresses.sprint())
				&& !player.isInWater()
				&& SprintRules.allowsNonForwardSprint(player.input);
	}

	/**
	 * Omnidirectional sprint and sprint in place: keep run sprinting without forward input.
	 */
	@ModifyExpressionValue(method = "shouldStopRunSprinting",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
	private boolean sprint$keepSprintingInAnyDirection(final boolean hasForwardImpulse) {
		return hasForwardImpulse || SprintRules.allowsNonForwardSprint(((LocalPlayer) (Object) this).input);
	}

	/**
	 * Keep sprinting at walls: a head-on collision no longer stops run sprinting.
	 */
	@ModifyExpressionValue(method = "shouldStopRunSprinting",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;horizontalCollision:Z"))
	private boolean sprint$ignoreWallCollision(final boolean horizontalCollision) {
		return horizontalCollision && !SprintRules.isActive(Feature.KEEP_ON_COLLISION);
	}

	/**
	 * Sprint when hungry: the food level no longer limits sprinting. Vanilla only checks it here, for sprinting.
	 */
	@ModifyExpressionValue(method = "isSprintingPossible",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/player/LocalPlayer;hasEnoughFoodToDoExhaustiveManoeuvres()Z"))
	private boolean sprint$ignoreHunger(final boolean hasEnoughFood) {
		return hasEnoughFood || SprintRules.isActive(Feature.IGNORE_HUNGER);
	}
}
