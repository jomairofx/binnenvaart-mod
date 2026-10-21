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
		int[] c = SelfTestSpots.clearSpot(kind, other, w);
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
		int good = 0;
		for (int k = -2; k <= 2; k++) {
			int px = (int) Math.round(x + k * gx * hw * 0.3), pz = (int) Math.round(z + k * gz * hw * 0.3);
			int y = RiverField.WATER_TOP;
			while (y > -50 && water(w, px, y, pz)) y--;
			int solid = 0;
			for (int yy = y; yy > y - 11; yy--) if (!w.getBlockState(new BlockPos(px, yy, pz)).isAir() && !water(w, px, yy, pz)) solid++;
			if (solid >= 6) good++;
		}
		check(kind.name() + " vaste bodem", good >= 3, good + " van 5 plekken met een vaste bodem onder de bedding");
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		if (tick != 40) return;
		try {
			ServerWorld w = server.getOverworld();
			RiverField f = RiverField.current;
			check("riviernet actief", f != null, f == null ? "geen" : "ja");
			{
				int lava = 0;
				for (var b : new net.minecraft.registry.RegistryKey[]{net.minecraft.world.biome.BiomeKeys.PLAINS, net.minecraft.world.biome.BiomeKeys.FOREST, net.minecraft.world.biome.BiomeKeys.BIRCH_FOREST}) {
					var biome = server.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).get(b);
					for (var step : biome.getGenerationSettings().getFeatures())
						for (var pf : step)
							if (pf.matchesKey(net.minecraft.world.gen.feature.MiscPlacedFeatures.LAKE_LAVA_SURFACE)
									|| pf.matchesKey(net.minecraft.world.gen.feature.MiscPlacedFeatures.LAKE_LAVA_UNDERGROUND)) lava++;
				}
				check("geen lavaplassen", lava == 0, lava + " lava-features over");
			}
			if (f != null) {
				testRiver(w, f.river, f.great, 50, 13);
				testRiver(w, f.great, f.river, 250, 21);
				// harbours: one of each size if we can find them
				for (int size = 0; size <= 2; size++) {
					Harbours.Site h = GreatRivers.findHarbour(f, 0, 0, 25, size);
					String nm = new String[]{"kleine", "middelgrote", "grote"}[size];
					check(nm + " haven gevonden", h != null, h == null ? "geen" : "op " + h.cx() + ", " + h.cz() + " aan een " + h.river() + ", lading " + h.goods());
					if (h == null) continue;
					int[] q = GreatRivers.quaySpot(h);
					w.getChunk(q[0] >> 4, q[1] >> 4);
					BlockState top = w.getBlockState(new BlockPos(q[0], Harbours.QUAY_Y, q[1]));
					check(nm + " haven: kade verhard", top.isOf(net.minecraft.block.Blocks.SMOOTH_STONE) || top.isOf(net.minecraft.block.Blocks.POLISHED_ANDESITE),
							top.getBlock().getName().getString() + " op y " + Harbours.QUAY_Y + " bij " + q[0] + ", " + q[1]);
					double bv = h.bank() + h.B() * 0.5;
					int bx = (int) Math.round(h.wx(0, bv)), bz = (int) Math.round(h.wz(0, bv));
					int depth = 0;
					for (int y = RiverField.WATER_TOP; y > 0 && water(w, bx, y, bz); y--) depth++;
					check(nm + " haven: bekken diep", depth >= 9, depth + " blokken water in het bekken bij " + bx + ", " + bz);
					java.util.List<int[]> cranes = f.harbours.cranes(h);
					int[] c = cranes.get(0);
					w.getChunk(c[0] >> 4, c[1] >> 4);
					var cb = w.getBlockState(new BlockPos(c[0], Harbours.QUAY_Y + 1, c[1]));
					var be = w.getBlockEntity(new BlockPos(c[0], Harbours.QUAY_Y + 1, c[1]));
					boolean full = be instanceof net.minecraft.inventory.Inventory inv && !inv.getStack(0).isEmpty() && !inv.getStack(inv.size() - 1).isEmpty();
					check(nm + " haven: kranen", net.minecraft.registry.Registries.BLOCK.getId(cb.getBlock()).toString().equals("kempenaar:laadkraan") && full,
							cranes.size() + " kranen, eerste: " + net.minecraft.registry.Registries.BLOCK.getId(cb.getBlock()) + (full ? ", vol met " + h.goods() : ", leeg"));
					// the cranes stand 20 blocks back from the quay edge
					double[] fr = Harbours.quayFrame(h, c[0] + 0.5, c[1] + 0.5);
					check(nm + " haven: kraan op 20 van de rand", fr != null && Math.abs(fr[1] - Harbours.CRANE_BACK) < 1.5, fr == null ? "?" : String.format("%.1f blokken van de rand", fr[1]));
				}
				// Dutch landscape: flat polder with ditches
				int[] pp = SelfTestSpots.polderSpot(f);
				check("polder gevonden", pp != null, pp == null ? "geen" : "op " + pp[0] + ", " + pp[1]);
				if (pp != null) {
					int flat = 0, n = 0;
					StringBuilder hs = new StringBuilder();
					for (int k = 0; k < 9; k++) {
						int px = pp[0] + (k % 3 - 1) * 37, pz = pp[1] + (k / 3 - 1) * 37;
						if (f.low.ditch(px, pz) || f.low.ditch(px + 1, pz) || f.low.ditch(px, pz + 1)) continue;
						w.getChunk(px >> 4, pz >> 4);
						int t = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, px, pz) - 1;
						hs.append(t).append(' ');
						n++;
						if (t >= 63 && t <= 65) flat++;
					}
					check("polder vlak", n > 0 && flat >= n - 2, "hoogtes " + hs + "(" + flat + " van " + n + " op polderhoogte 64)");
					int ditches = 0, wet = 0;
					for (int s = 0; s < 300; s++) {
						int px = pp[0] + s, pz = pp[1];
						if (!f.low.ditch(px, pz) || f.polder(px, pz) < 0.9 || f.low.canalDistance(px, pz) < 40) continue;
						ditches++;
						if (water(w, px, RiverField.WATER_TOP, pz)) wet++;
					}
					check("sloten met water", ditches > 0 && wet >= ditches * 0.7, wet + " van " + ditches + " slootvakjes met water");
					// over a whole area, both directions of ditches
					int all = 0, allWet = 0;
					String dry = "";
					for (int px = pp[0] - 100; px < pp[0] + 100; px++)
						for (int pz = pp[1] - 100; pz < pp[1] + 100; pz += 3) {
							if (!f.low.ditch(px, pz) || f.dutchHeight(px, pz) >= RiverField.WATER_TOP) continue;
							all++;
							w.getChunk(px >> 4, pz >> 4);
							if (water(w, px, RiverField.WATER_TOP, pz)) allWet++;
							else if (dry.isEmpty()) {
								StringBuilder col = new StringBuilder();
								for (int y = 58; y <= 65; y++) col.append(y).append('=').append(w.getBlockState(new BlockPos(px, y, pz)).getBlock().getName().getString()).append(' ');
								dry = " droog bijv. " + px + "," + pz + " t=" + String.format("%.2f", f.dutchHeight(px, pz)) + " : " + col;
							}
						}
					check("alle sloten met water", all > 0 && allWet >= all * 0.95, allWet + " van " + all + " slootvakjes in 200x200" + dry);
				}
				int[] cs = SelfTestSpots.canalSpot(f);
				log.add(cs == null ? "  (geen kanaal in de buurt: kanalen zijn zeldzaam)" : "  kanaal op " + cs[0] + ", " + cs[1]);
				if (cs != null) {
					int depth = 0;
					for (int y = RiverField.WATER_TOP; y > 0 && water(w, cs[0], y, cs[1]); y--) depth++;
					check("kanaal diep", depth >= 7, depth + " blokken water in het kanaal");
				}
				double[] ds = SelfTestSpots.dikeSpot(f);
				check("rivier met dijk gevonden", ds != null, ds == null ? "geen" : "op " + (int) ds[0] + ", " + (int) ds[1]);
				if (ds != null) {
					int maxT = 0;
					for (double e = ds[5]; e < ds[5] + 35; e += 1) {
						int px = (int) Math.round(ds[0] + ds[2] * (ds[4] + e)), pz = (int) Math.round(ds[1] + ds[3] * (ds[4] + e));
						w.getChunk(px >> 4, pz >> 4);
						maxT = Math.max(maxT, w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, px, pz) - 1);
					}
					check("dijk langs de rivier", maxT >= 68 && maxT <= 71, "dijk tot y " + maxT);
				}
				// deep sea
				int ox = 0, oz = 0;
				boolean found = false;
				search:
				for (int r = 0; r < 30000; r += 256)
					for (int a = 0; a < 24; a++) {
						ox = (int) (r * Math.cos(a * Math.PI / 12));
						oz = (int) (r * Math.sin(a * Math.PI / 12));
						double c = f.continentalness(ox, oz);
						if (c < -0.6 && c > -0.95) {
							found = true;
							break search;
						}
					}
				check("diepe zee gevonden", found, "op " + ox + ", " + oz);
				if (found) {
					int depth = 0;
					for (int y = RiverField.WATER_TOP; y > -64 && water(w, ox, y, oz); y--) depth++;
					check("zee 100 blokken diep", depth >= 90, depth + " blokken water");
				}
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
