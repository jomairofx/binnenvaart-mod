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
	private RegistryEntry<Biome> greatrivers$plains, greatrivers$forest, greatrivers$birch, greatrivers$beach,
			greatrivers$ocean, greatrivers$deepOcean, greatrivers$coldOcean, greatrivers$deepColdOcean, greatrivers$pine;

	/** Only grassland and woods on the land, and no ice anywhere: you can sail everywhere, like in the Netherlands. */
	@Inject(method = "getBiome", at = @At("RETURN"), cancellable = true)
	private void greatrivers$river(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
		RiverField field = RiverField.current;
		if (field == null || y < 10) return;     // caves keep their own biomes
		if (!greatrivers$looked) {
			greatrivers$looked = true;
			for (RegistryEntry<Biome> e : ((BiomeSource) (Object) this).getBiomes()) {
				if (e.matchesKey(BiomeKeys.RIVER)) greatrivers$river = e;
				if (e.matchesKey(BiomeKeys.PLAINS)) greatrivers$plains = e;
				if (e.matchesKey(BiomeKeys.FOREST)) greatrivers$forest = e;
				if (e.matchesKey(BiomeKeys.OLD_GROWTH_BIRCH_FOREST)) greatrivers$birch = e;
				if (e.matchesKey(BiomeKeys.OLD_GROWTH_PINE_TAIGA)) greatrivers$pine = e;
				if (e.matchesKey(BiomeKeys.BEACH)) greatrivers$beach = e;
				if (e.matchesKey(BiomeKeys.OCEAN)) greatrivers$ocean = e;
				if (e.matchesKey(BiomeKeys.DEEP_OCEAN)) greatrivers$deepOcean = e;
				if (e.matchesKey(BiomeKeys.COLD_OCEAN)) greatrivers$coldOcean = e;
				if (e.matchesKey(BiomeKeys.DEEP_COLD_OCEAN)) greatrivers$deepColdOcean = e;
			}
		}
		if (greatrivers$river == null || greatrivers$plains == null) return;   // not an overworld-like biome source
		RegistryEntry<Biome> b = cir.getReturnValue();
		int bx = (x << 2) + 2, bz = (z << 2) + 2;
		double c = field.coastal(bx, bz);
		if (c < nl.greatrivers.Lowlands.COAST) {
			// the sea, never frozen
			if (b.matchesKey(BiomeKeys.FROZEN_OCEAN) || b.matchesKey(BiomeKeys.DEEP_FROZEN_OCEAN) || !b.isIn(BiomeTags.IS_OCEAN)) {
				RegistryEntry<Biome> o = c < nl.greatrivers.Lowlands.COAST - 0.1 ? greatrivers$deepOcean : greatrivers$ocean;
				if (o != null) cir.setReturnValue(o);
			}
			return;
		}
		if (y <= 20 && field.inChannel(bx, bz)) {
			cir.setReturnValue(greatrivers$river);
			return;
		}
		// no woods in the forelands and on the dikes: open grass along the water
		var k = field.great;
		boolean nearWater = k.distance(bx, bz) - k.halfWidth(bx, bz) < nl.greatrivers.Lowlands.foreland(k) + 40;
		int wood = nearWater ? 0 : field.low.wood(bx, bz, field.low.lowland(bx, bz));
		RegistryEntry<Biome> land = wood == 3 && greatrivers$pine != null ? greatrivers$pine
				: wood == 2 && greatrivers$birch != null ? greatrivers$birch
				: wood >= 1 && greatrivers$forest != null ? greatrivers$forest : greatrivers$plains;
		cir.setReturnValue(land);
	}
}
