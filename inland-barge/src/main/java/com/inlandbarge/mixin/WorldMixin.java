package com.inlandbarge.mixin;

import net.minecraft.world.World;
import com.inlandbarge.ship.ShipEntity;
import com.inlandbarge.ship.ShipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(World.class)
public abstract class WorldMixin implements ShipHolder {
	@Unique
	private final List<ShipEntity> inlandbarge$shipList = new ArrayList<>();

	@Override
	public List<ShipEntity> inlandbarge$ships() {
		return inlandbarge$shipList;
	}
}
