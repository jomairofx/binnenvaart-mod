package nl.kempenaar.ship;

import net.minecraft.world.World;

import java.util.List;

/** Lijst met geladen schepen per wereld (via een mixin aan World toegevoegd). */
public interface ShipHolder {
	List<ShipEntity> kempenaar$ships();

	static List<ShipEntity> of(World world) {
		List<ShipEntity> list = ((ShipHolder) world).kempenaar$ships();
		list.removeIf(ShipEntity::isRemoved);
		return list;
	}
}
