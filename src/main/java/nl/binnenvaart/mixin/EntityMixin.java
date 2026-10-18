package nl.binnenvaart.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import nl.binnenvaart.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Laat spelers en dieren op het schip staan en ertegen botsen. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@ModifyVariable(method = "adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), argsOnly = true)
	private Vec3d binnenvaart$shipCollisions(Vec3d movement) {
		return ShipEntity.collideAll((Entity) (Object) this, movement);
	}
}
