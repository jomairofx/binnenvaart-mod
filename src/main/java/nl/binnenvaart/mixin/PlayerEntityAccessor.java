package nl.binnenvaart.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface PlayerEntityAccessor {
	@Accessor("sleepTimer")
	void binnenvaart$setSleepTimer(int value);
}
