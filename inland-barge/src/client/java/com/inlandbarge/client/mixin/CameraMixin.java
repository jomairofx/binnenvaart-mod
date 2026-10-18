package com.inlandbarge.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import com.inlandbarge.client.ShipCamera;
import com.inlandbarge.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** In derde persoon (F5) aan het roer: camera ver genoeg naar achteren om het hele schip te zien. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true)
	private float inlandbarge$shipDistance(float d) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null && mc.player.getVehicle() instanceof ShipEntity) return Math.max(d, ShipCamera.distance);
		return d;
	}
}
