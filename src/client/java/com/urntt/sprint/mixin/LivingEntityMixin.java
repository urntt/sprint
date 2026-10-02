package com.urntt.sprint.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.urntt.sprint.SprintRules;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/**
	 * Points the sprint-jump boost along the movement input. Vanilla always pushes a sprinting entity forward when it
	 * jumps, which is right as long as only forward sprinting exists. When omnidirectional sprint or sprint in place
	 * keeps the local player sprinting without forward input, the boost follows the input direction instead, and
	 * jumping without any input gets no boost, so jumping in place does not drift forward. Forward sprint jumps and
	 * every other entity stay vanilla.
	 */
	@WrapOperation(method = "jumpFromGround",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;addDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
	private void sprint$alignSprintJumpBoost(final LivingEntity entity, final Vec3 boost, final Operation<Void> original) {
		if (!(entity instanceof LocalPlayer player)) {
			original.call(entity, boost);
			return;
		}
		ClientInput input = player.input;
		if (input.hasForwardImpulse() || !SprintRules.allowsNonForwardSprint(input)) {
			original.call(entity, boost);
			return;
		}
		if (!SprintRules.isMoving(input)) {
			return;
		}

		// The vanilla boost points forward. Combining it with the same boost turned to the player's left (the input's
		// positive x, as in Entity.getInputVector) points it along the input while keeping its length.
		Vec2 direction = input.getMoveVector().normalized();
		Vec3 left = new Vec3(boost.z, 0.0, -boost.x);
		original.call(entity, boost.scale(direction.y).add(left.scale(direction.x)));
	}
}
