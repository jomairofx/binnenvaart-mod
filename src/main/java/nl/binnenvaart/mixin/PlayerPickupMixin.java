package nl.binnenvaart.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import nl.binnenvaart.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla laat een speler die ergens in zit alles oppakken binnen de hele omtrek van het voertuig.
 * Bij een schip is dat tientallen blokken: aan het roer pakte je dan al het drijvende zeewier op.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerPickupMixin {
	@ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Box;union(Lnet/minecraft/util/math/Box;)Lnet/minecraft/util/math/Box;"))
	private Box binnenvaart$pickupAroundPlayer(Box original) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		return self.getVehicle() instanceof ShipEntity ? self.getBoundingBox() : original;
	}
}
