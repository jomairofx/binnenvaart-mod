package nl.kempenaar.mixin;

import net.minecraft.entity.LivingEntity;
import nl.kempenaar.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ladders op het schip zijn beklimbaar. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "isClimbing", at = @At("RETURN"), cancellable = true)
	private void kempenaar$shipLadders(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			LivingEntity self = (LivingEntity) (Object) this;
			if (!self.isSpectator() && ShipEntity.isClimbing(self)) cir.setReturnValue(true);
		}
	}
}
