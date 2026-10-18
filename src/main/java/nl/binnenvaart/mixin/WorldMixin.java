package nl.binnenvaart.mixin;

import net.minecraft.world.World;
import nl.binnenvaart.ship.ShipEntity;
import nl.binnenvaart.ship.ShipHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

@Mixin(World.class)
public abstract class WorldMixin implements ShipHolder {
	@Unique
	private final List<ShipEntity> binnenvaart$shipList = new ArrayList<>();

	@Override
	public List<ShipEntity> binnenvaart$ships() {
		return binnenvaart$shipList;
	}
}
