package nl.greatrivers.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseRouter;
import nl.greatrivers.GreatRivers;
import nl.greatrivers.RiverCarve;
import nl.greatrivers.RiverField;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the rivers into the overworld terrain (only worlds with normal overworld settings: stone, water, sea level 63). */
@Mixin(NoiseConfig.class)
public abstract class NoiseConfigMixin {
	@Shadow
	@Final
	@Mutable
	private NoiseRouter noiseRouter;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void greatrivers$carve(ChunkGeneratorSettings settings, RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> params, long seed, CallbackInfo ci) {
		if (settings.seaLevel() != 63 || !settings.defaultBlock().isOf(Blocks.STONE) || !settings.defaultFluid().isOf(Blocks.WATER)) return;
		RiverField field = new RiverField(seed);
		RiverField.current = field;
		NoiseRouter r = noiseRouter;
		noiseRouter = new NoiseRouter(r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(),
				r.temperature(), r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(),
				new RiverCarve(r.initialDensityWithoutJaggedness(), field), new RiverCarve(r.finalDensity(), field),
				r.veinToggle(), r.veinRidged(), r.veinGap());
		GreatRivers.LOGGER.info("Great Rivers: river network added to the overworld terrain");
	}
}
