package nl.kempenaar.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface PlayerEntityAccessor {
	@Accessor("sleepTimer")
	void kempenaar$setSleepTimer(int value);
}
