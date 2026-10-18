package nl.kempenaar.mixin;

import net.minecraft.world.World;
import nl.kempenaar.ship.ShipEntity;
import nl.kempenaar.ship.ShipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(World.class)
public abstract class WorldMixin implements ShipHolder {
	@Unique
	private final List<ShipEntity> kempenaar$shipList = new ArrayList<>();

	@Override
	public List<ShipEntity> kempenaar$ships() {
		return kempenaar$shipList;
	}
}
