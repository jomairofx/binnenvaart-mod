package com.speedboat.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.util.math.MathHelper;
import com.speedboat.client.ShipCamera;
import com.speedboat.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Muiswiel in derde persoon aan het roer: in- en uitzoomen in plaats van van hotbar wisselen. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void speedboat$zoom(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (window != mc.getWindow().getHandle() || mc.currentScreen != null || mc.player == null || vertical == 0) return;
		if (!(mc.player.getVehicle() instanceof ShipEntity) || mc.options.getPerspective().isFirstPerson()) return;
		ShipCamera.distance = MathHelper.clamp(ShipCamera.distance - (float) vertical * 5f, ShipCamera.MIN, ShipCamera.MAX);
		ci.cancel();
	}
}
