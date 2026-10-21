package nl.greatrivers.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import nl.greatrivers.RiverField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The water of the rivers gets the river biome (fish, squid, sand/clay/gravel on the bed, river water colour). */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
	@Unique
	private RegistryEntry<Biome> greatrivers$river;
	@Unique
	private boolean greatrivers$looked;
	@Unique
	private RegistryEntry<Biome> greatrivers$plains, greatrivers$snowyPlains;

	@Inject(method = "getBiome", at = @At("RETURN"), cancellable = true)
	private void greatrivers$river(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		RiverField field = RiverField.current;
		if (field == null || y < 10) return;
		if (!greatrivers$looked) {
			greatrivers$looked = true;
			for (RegistryEntry<Biome> e : ((BiomeSource) (Object) this).getBiomes()) {
				if (e.matchesKey(BiomeKeys.RIVER)) greatrivers$river = e;
				if (e.matchesKey(BiomeKeys.PLAINS)) greatrivers$plains = e;
				if (e.matchesKey(BiomeKeys.SNOWY_PLAINS)) greatrivers$snowyPlains = e;
			}
		}
		if (greatrivers$river == null) return;   // not an overworld-like biome source
		RegistryEntry<Biome> b = cir.getReturnValue();
		if (b.isIn(BiomeTags.IS_OCEAN) || b.isIn(BiomeTags.IS_RIVER)) return;
		int bx = (x << 2) + 2, bz = (z << 2) + 2;
		if (y <= 20 && field.inChannel(bx, bz)) {
			cir.setReturnValue(greatrivers$river);
			return;
		}
		// the polders are meadows (plains), snowy where it is cold; up to the sky so the whole column agrees
		if (y <= 40 && greatrivers$plains != null && field.polder(bx, bz) > 0.6) {
			boolean cold = b.value().getTemperature() < 0.15f;
			cir.setReturnValue(cold && greatrivers$snowyPlains != null ? greatrivers$snowyPlains : greatrivers$plains);
		}
	}
}
