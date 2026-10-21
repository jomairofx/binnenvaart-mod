package nl.greatrivers.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import nl.greatrivers.RiverField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Open water up to the sea level wherever our landscape lies below it: rivers, canals, harbour basins, lakes,
 * the sea and also the narrow ditches (which the vanilla aquifers would leave dry or fill at odd levels).
 */
@Mixin(targets = "net.minecraft.world.gen.chunk.AquiferSampler$Impl")
public abstract class AquiferMixin {
	private static final BlockState WATER = Blocks.WATER.getDefaultState();

	@Inject(method = "apply", at = @At("HEAD"), cancellable = true)
	private void greatrivers$openWater(DensityFunction.NoisePos pos, double density, CallbackInfoReturnable<BlockState> cir) {
		if (density > 0) return;
		RiverField f = RiverField.current;
		if (f == null) return;
		int y = pos.blockY();
		if (y > RiverField.WATER_TOP || y < -60) return;
		double t = f.dutchHeight(pos.blockX(), pos.blockZ());
		if (y >= t - 0.5) cir.setReturnValue(WATER);
	}
}
