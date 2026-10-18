package com.inlandbarge.ship;

import net.minecraft.world.World;

import java.util.List;

/** Lijst met geladen schepen per wereld (via een mixin aan World toegevoegd). */
public interface ShipHolder {
	List<ShipEntity> inlandbarge$ships();

	static List<ShipEntity> of(World world) {
		List<ShipEntity> list = ((ShipHolder) world).inlandbarge$ships();
		list.removeIf(ShipEntity::isRemoved);
		return list;
	}
}
