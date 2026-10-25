package com.speedboat.mixin;

import net.minecraft.world.World;
import com.speedboat.ship.ShipEntity;
import com.speedboat.ship.ShipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(World.class)
public abstract class WorldMixin implements ShipHolder {
	@Unique
	private final List<ShipEntity> speedboat$shipList = new ArrayList<>();

	@Override
	public List<ShipEntity> speedboat$ships() {
		return speedboat$shipList;
	}
}
