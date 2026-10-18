package nl.binnenvaart.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import nl.binnenvaart.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Op een varend schip loopt de server altijd iets achter op de speler. Zonder deze aanpassing
 * zou de server spelers aan boord terugzetten ("moved wrongly") of wegsturen omdat ze "vliegen".
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "isEntityOnAir", at = @At("HEAD"), cancellable = true)
	private static void binnenvaart$notFloatingOnShip(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ShipEntity.isNearAnyShip(entity)) cir.setReturnValue(false);
	}

	@ModifyConstant(method = "onPlayerMove", constant = @Constant(doubleValue = 0.0625), require = 0)
	private double binnenvaart$tolerateOnShip(double original) {
		return ShipEntity.isNearAnyShip(this.player) ? Double.MAX_VALUE : original;
	}
}
