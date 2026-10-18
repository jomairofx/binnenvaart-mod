package nl.binnenvaart.mixin;

import net.minecraft.block.BedBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import nl.binnenvaart.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ladders op het schip zijn beklimbaar; in een scheepsbed lig je precies op het matras. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "isClimbing", at = @At("RETURN"), cancellable = true)
	private void binnenvaart$shipLadders(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			LivingEntity self = (LivingEntity) (Object) this;
			if (!self.isSpectator() && ShipEntity.isClimbing(self)) cir.setReturnValue(true);
		}
	}

	@Inject(method = "setPositionInBed", at = @At("HEAD"), cancellable = true)
	private void binnenvaart$shipBed(BlockPos pos, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().getBlockState(pos).getBlock() instanceof BedBlock) return;
		ShipEntity.BedRef ref = ShipEntity.findBed(self.getWorld(), pos);
		if (ref == null) return;
		Vec3d s = ref.ship().bedSpot(ref.head());
		self.setPosition(s.x, s.y, s.z);
		ci.cancel();
	}
}
