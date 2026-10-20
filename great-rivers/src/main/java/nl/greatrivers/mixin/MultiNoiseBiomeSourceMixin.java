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

	@Inject(method = "getBiome", at = @At("RETURN"), cancellable = true)
	private void greatrivers$river(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		RiverField field = RiverField.current;
		if (field == null || y < 10 || y > 20) return;   // only from the river bed up to just above the water (y 40..83)
		if (!greatrivers$looked) {
			greatrivers$looked = true;
			for (RegistryEntry<Biome> e : ((BiomeSource) (Object) this).getBiomes())
				if (e.matchesKey(BiomeKeys.RIVER)) greatrivers$river = e;
		}
		if (greatrivers$river == null) return;   // not an overworld-like biome source
		RegistryEntry<Biome> b = cir.getReturnValue();
		if (b.isIn(BiomeTags.IS_OCEAN) || b.isIn(BiomeTags.IS_RIVER)) return;
		if (field.inChannel((x << 2) + 2, (z << 2) + 2)) cir.setReturnValue(greatrivers$river);
	}
}
