package nl.nokelp;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.feature.OceanPlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Automatische test (alleen met -Dnokelp.selftest=server). */
final class SelfTest {
	private static int tick;
	private static final List<String> log = new ArrayList<>();
	private static boolean failed;

	static void register() {
		if (!System.getProperty("nokelp.selftest", "").equals("server")) return;
		ServerTickEvents.END_SERVER_TICK.register(SelfTest::onTick);
	}

	private static void check(String name, boolean ok, String detail) {
		log.add((ok ? "PASS " : "FAIL ") + name + " - " + detail);
		NoKelp.LOGGER.info("SELFTEST {} {} {}", ok ? "PASS" : "FAIL", name, detail);
		if (!ok) failed = true;
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		try {
			ServerWorld w = server.getOverworld();
			if (tick == 40) {
				var biomes = server.getRegistryManager().get(RegistryKeys.BIOME);
				for (RegistryKey<Biome> key : List.of(BiomeKeys.OCEAN, BiomeKeys.COLD_OCEAN, BiomeKeys.LUKEWARM_OCEAN, BiomeKeys.DEEP_LUKEWARM_OCEAN, BiomeKeys.WARM_OCEAN, BiomeKeys.RIVER)) {
					Biome b = biomes.get(key);
					int kelp = 0;
					for (RegistryEntryList<PlacedFeature> step : b.getGenerationSettings().getFeatures())
						for (RegistryEntry<PlacedFeature> f : step)
							if (f.matchesKey(OceanPlacedFeatures.KELP_COLD) || f.matchesKey(OceanPlacedFeatures.KELP_WARM)) kelp++;
					check("geen kelp in " + key.getValue().getPath(), kelp == 0, kelp + " kelp-features");
				}
			}
			if (tick == 60) {
				BlockPos.Mutable m = new BlockPos.Mutable(8, -40, 8);
				while (m.getY() > -64 && !w.getFluidState(m).isIn(FluidTags.WATER)) m.move(0, -1, 0);
				int top = m.getY();
				int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
				for (int y = top - 5; y < top; y++) w.setBlockState(new BlockPos(8, y, 8), Blocks.KELP_PLANT.getDefaultState(), flags);
				w.setBlockState(new BlockPos(8, top, 8), Blocks.KELP.getDefaultState(), flags);
				check("kelp geplaatst", w.getBlockState(new BlockPos(8, top, 8)).isOf(Blocks.KELP), "kelpkolom van " + (top - 5) + " tot " + top);
				NoKelp.scanChunk(w, w.getWorldChunk(new BlockPos(8, top, 8)));
				NoKelp.flush(server);
				int left = 0;
				for (int y = top - 5; y <= top; y++) if (NoKelp.isKelp(w.getBlockState(new BlockPos(8, y, 8)))) left++;
				boolean water = w.getFluidState(new BlockPos(8, top, 8)).isIn(FluidTags.WATER);
				check("bestaande kelp weg", left == 0 && water, left + " kelpblokken over, water: " + water);
			}
			if (tick == 70) {
				// oude, eerder losgeraakte kelp die in het water drijft
				for (int i = 0; i < 20; i++) {
					ItemEntity it = new ItemEntity(w, 4 + i * 0.5, -52.5, 4, new net.minecraft.item.ItemStack(net.minecraft.item.Items.KELP, 3));
					w.spawnEntity(it);
				}
			}
			if (tick == 100) {
				int items = w.getEntitiesByClass(ItemEntity.class, new Box(-8, -64, -8, 24, 0, 24), e -> true).size();
				check("drijvende kelp opgeruimd", items == 0, items + " kelp-items nog in het water");
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\n" + (failed ? "RESULT FAIL" : "RESULT PASS") + "\n");
				server.stop(false);
			}
		} catch (Throwable t) {
			NoKelp.LOGGER.error("SELFTEST fout", t);
			try {
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\nFAIL " + t + "\nRESULT FAIL\n");
			} catch (Exception ignored) {
			}
			server.stop(false);
		}
	}
}
