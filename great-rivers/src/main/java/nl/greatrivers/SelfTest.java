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
			if (f != null) {
				testRiver(w, f.river, f.great, 50, 13);
				testRiver(w, f.great, f.river, 250, 21);
				// harbour
				Harbours.Site h = GreatRivers.nearestHarbour(f, 0, 0, 15);
				check("haven gevonden", h != null, h == null ? "geen" : "op " + h.cx() + ", " + h.cz() + " aan een " + h.river());
				if (h != null) {
					int[] q = GreatRivers.quaySpot(h);
					w.getChunk(q[0] >> 4, q[1] >> 4);
					BlockState top = w.getBlockState(new BlockPos(q[0], Harbours.QUAY_Y, q[1]));
					boolean free = w.getBlockState(new BlockPos(q[0], Harbours.QUAY_Y + 1, q[1])).isAir()
							|| w.getBlockState(new BlockPos(q[0], Harbours.QUAY_Y + 1, q[1])).isOf(net.minecraft.block.Blocks.BARREL);
					check("kade vlak en verhard", (top.isOf(net.minecraft.block.Blocks.SMOOTH_STONE) || top.isOf(net.minecraft.block.Blocks.POLISHED_ANDESITE)) && free,
							top.getBlock().getName().getString() + " op y " + Harbours.QUAY_Y + " bij " + q[0] + ", " + q[1]);
					double bv = h.bank() + Harbours.B * 0.5;
					int bx = (int) Math.round(h.cx() + h.gx() * bv), bz = (int) Math.round(h.cz() + h.gz() * bv);
					int depth = 0;
					for (int y = RiverField.WATER_TOP; y > 0 && water(w, bx, y, bz); y--) depth++;
					check("havenbekken diep", depth >= 8, depth + " blokken water in het bekken bij " + bx + ", " + bz);
					// a quay wall: walk from the basin towards the back quay and find the concrete face
					boolean wallFound = false;
					for (double vv = bv; vv < h.bank() + Harbours.B + 4 && !wallFound; vv += 0.5) {
						int wx = (int) Math.floor(h.cx() + h.gx() * vv), wz = (int) Math.floor(h.cz() + h.gz() * vv);
						wallFound = w.getBlockState(new BlockPos(wx, 60, wz)).isOf(net.minecraft.block.Blocks.GRAY_CONCRETE);
					}
					check("kademuur", wallFound, wallFound ? "betonnen kademuur gevonden" : "geen kademuur");
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
				}
				int[] cs = SelfTestSpots.canalSpot(f);
				check("kanaal gevonden", cs != null, cs == null ? "geen" : "op " + cs[0] + ", " + cs[1]);
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
