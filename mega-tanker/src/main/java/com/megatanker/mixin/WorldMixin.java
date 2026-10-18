package com.megatanker.mixin;

import net.minecraft.world.World;
import com.megatanker.ship.ShipEntity;
import com.megatanker.ship.ShipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(World.class)
public abstract class WorldMixin implements ShipHolder {
	@Unique
	private final List<ShipEntity> megatanker$shipList = new ArrayList<>();

	@Override
	public List<ShipEntity> megatanker$ships() {
		return megatanker$shipList;
	}
}
