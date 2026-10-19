package com.modernbulk.mixin;

import net.minecraft.world.World;
import com.modernbulk.ship.ShipEntity;
import com.modernbulk.ship.ShipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(World.class)
public abstract class WorldMixin implements ShipHolder {
	@Unique
	private final List<ShipEntity> modernbulk$shipList = new ArrayList<>();

	@Override
	public List<ShipEntity> modernbulk$ships() {
		return modernbulk$shipList;
	}
}
