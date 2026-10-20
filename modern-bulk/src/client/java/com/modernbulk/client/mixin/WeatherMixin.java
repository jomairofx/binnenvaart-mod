package com.modernbulk.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import com.modernbulk.ship.ShipEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Regen en spatten stoppen op het dak en dek van het schip, in plaats van erdoorheen te vallen. */
@Mixin(WorldRenderer.class)
public abstract class WeatherMixin {
	@WrapOperation(method = "renderWeather", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;getTopY(Lnet/minecraft/world/Heightmap$Type;II)I"))
	private int modernbulk$rainOnShip(World world, Heightmap.Type type, int x, int z, Operation<Integer> op) {
		ShipEntity.rainChecks++;
		return ShipEntity.rainTop(world, x, z, op.call(world, type, x, z));
	}

	@WrapOperation(method = "tickRainSplashing", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/WorldView;getTopPosition(Lnet/minecraft/world/Heightmap$Type;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;"))
	private BlockPos modernbulk$splashOnShip(WorldView view, Heightmap.Type type, BlockPos pos, Operation<BlockPos> op) {
		BlockPos p = op.call(view, type, pos);
		if (!(view instanceof World w)) return p;
		int y = ShipEntity.rainTop(w, p.getX(), p.getZ(), p.getY());
		return y == p.getY() ? p : new BlockPos(p.getX(), y, p.getZ());
	}
}
