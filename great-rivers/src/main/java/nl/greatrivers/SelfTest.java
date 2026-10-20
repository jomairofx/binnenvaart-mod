package nl.greatrivers;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Automatic test (only with -Dgreatrivers.selftest=server): generates real river chunks and measures them. */
final class SelfTest {
	private static int tick;
	private static final List<String> log = new ArrayList<>();
	private static boolean failed;

	static void register() {
		if (!System.getProperty("greatrivers.selftest", "").equals("server")) return;
		ServerTickEvents.END_SERVER_TICK.register(SelfTest::onTick);
	}

	private static void check(String name, boolean ok, String detail) {
		log.add((ok ? "PASS " : "FAIL ") + name + " - " + detail);
		GreatRivers.LOGGER.info("SELFTEST {} {} {}", ok ? "PASS" : "FAIL", name, detail);
		if (!ok) failed = true;
	}

	private static boolean water(ServerWorld w, int x, int y, int z) {
		w.getChunk(x >> 4, z >> 4);
		return w.getFluidState(new BlockPos(x, y, z)).isIn(FluidTags.WATER);
	}

	private static void testRiver(ServerWorld w, RiverField.Kind kind, RiverField.Kind other, int minWidth, int minDepth) {
		int[] c = SelfTestSpots.clearSpot(kind, other);
		check(kind.name() + " gevonden", c != null, c == null ? "geen" : "midden op " + c[0] + ", " + c[1]);
		if (c == null) return;
		int x = c[0], z = c[1];
		w.getChunk(x >> 4, z >> 4);
		RegistryEntry<Biome> b = w.getBiome(new BlockPos(x, 60, z));
		boolean sea = b.isIn(BiomeTags.IS_OCEAN);
		log.add("  biome in het midden: " + b.getKey().map(k -> k.getValue().toString()).orElse("?") + (sea ? " (zee)" : ""));
		int depth = 0;
		for (int y = RiverField.WATER_TOP; y > -60 && water(w, x, y, z); y--) depth++;
		check(kind.name() + " diep genoeg", depth >= minDepth, depth + " blokken water in het midden");
		check(kind.name() + " water tot zeeniveau", water(w, x, RiverField.WATER_TOP, z) && !water(w, x, RiverField.WATER_TOP + 1, z),
				"bovenste water op y " + RiverField.WATER_TOP);
		check(kind.name() + " rivierbiome", sea || b.isIn(BiomeTags.IS_RIVER), b.getKey().map(k -> k.getValue().toString()).orElse("?"));
		BlockState bed = w.getBlockState(new BlockPos(x, RiverField.WATER_TOP - depth, z));
		log.add("  bodem: " + bed.getBlock().getName().getString());
		// across the river: along the gradient of the distance
		double h = 4;
		double gx = kind.distance(x + h, z) - kind.distance(x - h, z), gz = kind.distance(x, z + h) - kind.distance(x, z - h);
		double l = Math.hypot(gx, gz);
		if (l < 1e-9) {
			gx = 1;
			gz = 0;
			l = 1;
		}
		gx /= l;
		gz /= l;
		int width = 0;
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			for (int s = sgn < 0 ? 1 : 0; s < 800; s++) {
				int px = (int) Math.round(x + sgn * s * gx), pz = (int) Math.round(z + sgn * s * gz);
				if (!water(w, px, RiverField.WATER_TOP, pz)) break;
				width++;
			}
		}
		double expect = kind.halfWidth(x, z) * 2;
		check(kind.name() + " breedte klopt", width >= minWidth && width >= expect * 0.75 && (sea || width <= expect * 1.6 + 20),
				width + " blokken water dwars over de rivier (ontwerp " + Math.round(expect) + ")");
		// the bank: dry flood plain just above the water
		double hw = kind.halfWidth(x, z);
		int bx = (int) Math.round(x + gx * (hw + 15)), bz = (int) Math.round(z + gz * (hw + 15));
		w.getChunk(bx >> 4, bz >> 4);
		int top = w.getTopY(Heightmap.Type.OCEAN_FLOOR, bx, bz);
		log.add("  oever op " + bx + ", " + bz + ": land tot y " + top);
		// under the river the ground is normal ground with stone below (caves and ores untouched)
		int solid = 0;
		for (int y = RiverField.WATER_TOP - depth - 1; y > RiverField.WATER_TOP - depth - 12; y--)
			if (!w.getBlockState(new BlockPos(x, y, z)).isAir() && !water(w, x, y, z)) solid++;
		check(kind.name() + " vaste bodem", solid >= 6, solid + " van 11 blokken onder de bedding vast");
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		if (tick != 40) return;
		try {
			ServerWorld w = server.getOverworld();
			RiverField f = RiverField.current;
			check("riviernet actief", f != null, f == null ? "geen" : "ja");
			if (f != null) {
				testRiver(w, f.river, f.great, 50, 5);
				testRiver(w, f.great, f.river, 250, 10);
				// survival: far from rivers the land is untouched
				int lx = 0, lz = 0;
				outer:
				for (int r = 0; r < 4000; r += 64)
					for (int a = 0; a < 16; a++) {
						lx = (int) (r * Math.cos(a * Math.PI / 8));
						lz = (int) (r * Math.sin(a * Math.PI / 8));
						if (f.ceiling(lx, lz) == Double.POSITIVE_INFINITY) break outer;
					}
				w.getChunk(lx >> 4, lz >> 4);
				int top = w.getTopY(Heightmap.Type.WORLD_SURFACE, lx, lz);
				check("gewoon land buiten de rivieren", w.getBlockState(new BlockPos(lx, -30, lz)).isSolidBlock(w, new BlockPos(lx, -30, lz)) || top > 0,
						"op " + lx + ", " + lz + " hoogte " + top);
			}
			Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\n" + (failed ? "RESULT FAIL" : "RESULT PASS") + "\n");
		} catch (Throwable t) {
			GreatRivers.LOGGER.error("SELFTEST fout", t);
			try {
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\nFAIL " + t + "\nRESULT FAIL\n");
			} catch (Exception ignored) {
			}
		}
		server.stop(false);
	}
}
