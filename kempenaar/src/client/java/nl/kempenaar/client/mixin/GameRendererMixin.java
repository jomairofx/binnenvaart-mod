package nl.kempenaar.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import nl.kempenaar.ship.ShipEntity;
import nl.kempenaar.ship.ShipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Laat de speler precies de blokken van het schip aanwijzen (deuren, roer, laadruim). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "findCrosshairTarget", at = @At("RETURN"), cancellable = true)
	private void kempenaar$targetShip(Entity camera, double blockRange, double entityRange, float tickDelta, CallbackInfoReturnable<HitResult> cir) {
		if (camera == null || camera.getWorld() == null) return;
		var ships = ShipHolder.of(camera.getWorld());
		if (ships.isEmpty()) return;
		Vec3d eye = camera.getCameraPosVec(tickDelta);
		Vec3d end = eye.add(camera.getRotationVec(tickDelta).multiply(blockRange));
		HitResult current = cir.getReturnValue();
		double best = current == null || current.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : current.getPos().squaredDistanceTo(eye);
		ShipEntity bestShip = null;
		Vec3d bestPos = null;
		for (ShipEntity ship : ships) {
			Vec3d hit;
			try {
				hit = ship.raycast(eye, end, camera);
			} catch (RuntimeException ex) {
				ShipEntity.reportError("raycast", ex);
				continue;
			}
			if (hit != null) {
				double d = hit.squaredDistanceTo(eye);
				if (d < best) {
					best = d;
					bestShip = ship;
					bestPos = hit;
				}
			}
		}
		if (bestShip != null) cir.setReturnValue(new EntityHitResult(bestShip, bestPos));
	}
}
