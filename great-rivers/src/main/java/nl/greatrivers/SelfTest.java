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
		double[] nrm = kind.normal(x, z);
		double gx = nrm[0], gz = nrm[1];
		int width = 0;
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			for (int s = sgn < 0 ? 1 : 0; s < 800; s++) {
				int px = (int) Math.round(x + sgn * s * gx), pz = (int) Math.round(z + sgn * s * gz);
				if (!water(w, px, RiverField.WATER_TOP, pz)) break;
				width++;
			}
		}
		double expect = kind.halfWidth(x, z) * 2;
		RiverField rf = RiverField.current;
		for (int s2 = -320; s2 <= 320; s2 += 20) {
			int px = (int) Math.round(x + s2 * gx), pz = (int) Math.round(z + s2 * gz);
			w.getChunk(px >> 4, pz >> 4);
			log.add(String.format("  dwars %4d: afstand %.0f halfbreedte %.0f hoogte %.1f kust %.3f grond %d biome %s", s2, kind.distance(px, pz), kind.halfWidth(px, pz),
					rf.dutchHeight(px, pz), rf.coastal(px, pz), w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, px, pz) - 1,
					w.getBiome(new BlockPos(px, 62, pz)).getKey().map(k -> k.getValue().getPath()).orElse("?")));
		}
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
				for (var b : new net.minecraft.registry.RegistryKey[]{net.minecraft.world.biome.BiomeKeys.PLAINS, net.minecraft.world.biome.BiomeKeys.FOREST, net.minecraft.world.biome.BiomeKeys.BIRCH_FOREST, net.minecraft.world.biome.BiomeKeys.FROZEN_OCEAN, net.minecraft.world.biome.BiomeKeys.DEEP_FROZEN_OCEAN, net.minecraft.world.biome.BiomeKeys.ICE_SPIKES}) {
					var biome = server.getRegistryManager().get(net.minecraft.registry.RegistryKeys.BIOME).get(b);
					for (var step : biome.getGenerationSettings().getFeatures())
						for (var pf : step)
							if (java.util.Arrays.stream(GreatRivers.NO_ICE).anyMatch(id -> pf.matchesId(net.minecraft.util.Identifier.ofVanilla(id)))) lava++;
							else if (pf.matchesKey(net.minecraft.world.gen.feature.MiscPlacedFeatures.LAKE_LAVA_SURFACE)
									|| pf.matchesKey(net.minecraft.world.gen.feature.MiscPlacedFeatures.LAKE_LAVA_UNDERGROUND)) lava++;
				}
				check("geen lavaplassen en geen ijsbergen", lava == 0, lava + " lava- of ijs-features over");
			}
			if (f != null) {
				testRiver(w, f.great, f.river, 60, 15);
				// villages and towns
				for (boolean city : new boolean[]{false, true}) {
					Villages.Town t = GreatRivers.findTown(f, 0, 0, 15, city, Boolean.FALSE);
					String nm = city ? "stad" : "dorp";
					check(nm + " gevonden", t != null, t == null ? "geen" : "op " + t.cx() + ", " + t.cz());
					if (t == null) continue;
					int tx = t.cx() + Villages.STREET + 4, tz = t.cz() + Villages.STREET + 8;
					w.getChunk(tx >> 4, tz >> 4);
					var tower = w.getBlockState(new BlockPos(tx, VillageBuilder.Y0 + 12, tz));
					check(nm + ": kerktoren", tower.isOf(net.minecraft.block.Blocks.BRICKS), tower.getBlock().getName().getString() + " op toren-hoogte");
					int hx = t.cx() + Villages.PU + Villages.STREET, hz = t.cz() + Villages.STREET;
					w.getChunk(hx >> 4, hz >> 4);
					var wall = w.getBlockState(new BlockPos(hx, VillageBuilder.Y0 + 2, hz));
					for (int du = 0; du < 29 && (wall.isAir() || wall.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)); du += 2)
						for (int dw = 0; dw < 21 && (wall.isAir() || wall.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)); dw += 2)
							wall = w.getBlockState(new BlockPos(hx + du, VillageBuilder.Y0 + 2, hz + dw));
					var street = w.getBlockState(new BlockPos(t.cx() + Villages.PU + 10, VillageBuilder.G, t.cz() + 2));
					check(nm + ": huizen en straten", !wall.isAir() && !wall.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)
									&& (street.isOf(net.minecraft.block.Blocks.BRICKS) || street.isOf(net.minecraft.block.Blocks.SMOOTH_STONE)),
							"gevel " + wall.getBlock().getName().getString() + ", straat " + street.getBlock().getName().getString());
				}
				// a river city: quays along the water, towers in the middle
				{
					Villages.Town t = GreatRivers.findTown(f, 0, 0, 25, true, Boolean.TRUE);
					check("stad aan de rivier gevonden", t != null, t == null ? "geen" : "op " + t.cx() + ", " + t.cz());
					if (t != null) {
						double[] n = f.great.normal(t.cx(), t.cz());
						String found = "geen kade";
						boolean ok = false;
						for (int sg = -1; sg <= 1 && !ok; sg += 2)
							for (double sd = 0; sd < 200 && !ok; sd += 0.5) {
								int qx = (int) Math.floor(t.cx() + n[0] * sd * sg), qz = (int) Math.floor(t.cz() + n[1] * sd * sg);
								double[] rv = f.riverAt(qx, qz, true);
								double e = rv[0] - rv[1];
								if (e >= 0.2 && e < 1.3) {
									w.getChunk(qx >> 4, qz >> 4);
									BlockState top = w.getBlockState(new BlockPos(qx, VillageBuilder.G, qz));
									BlockState wall = w.getBlockState(new BlockPos(qx, VillageBuilder.G - 2, qz));
									found = top.getBlock().getName().getString() + " op " + wall.getBlock().getName().getString() + " bij " + qx + ", " + qz;
									ok = top.isOf(net.minecraft.block.Blocks.SMOOTH_STONE) && (wall.isOf(net.minecraft.block.Blocks.BRICKS) || wall.isOf(net.minecraft.block.Blocks.STONE_BRICKS));
								}
							}
						check("stad aan de rivier: kademuur", ok, found);
						int tall = 0;
						for (int bx = -5; bx <= 5; bx++)
							for (int bz = -6; bz <= 6; bz++) {
								int qx = t.cx() + bx * Villages.PU + Villages.STREET + 3, qz = t.cz() + bz * Villages.PW + Villages.STREET + 10;   // on the facade
								if (Math.hypot(qx - t.cx(), qz - t.cz()) > t.radius() * 0.6) continue;
								w.getChunk(qx >> 4, qz >> 4);
								if (!w.getBlockState(new BlockPos(qx, VillageBuilder.Y0 + 21, qz)).isAir()) tall++;
							}
						check("stad aan de rivier: hoogbouw", tall >= 2, tall + " torens hoger dan 22");
					}
				}
				// a big city: districts, boulevards, and (on the river) high bridges
				{
					Metros.Metro mt = f.metros.nearest(0, 0, 4);
					check("grote stad gevonden", mt != null, mt == null ? "geen" : "op " + mt.cx() + ", " + mt.cz() + ", " + (int) (mt.radius() * 2) + " breed" + (mt.river() ? ", aan de rivier" : ""));
					if (mt != null) {
						int[] count = new int[Metros.DISTRICT.length];
						for (int bx = -40; bx <= 40; bx++)
							for (int bz = -50; bz <= 50; bz++) {
								if (Math.hypot(bx * Villages.PU, bz * Villages.PW) > mt.radius()) continue;
								count[mt.district(bx, bz)]++;
							}
						StringBuilder sb = new StringBuilder();
						int kinds = 0;
						for (int i = 0; i < count.length; i++) {
							if (count[i] > 0) kinds++;
							sb.append(Metros.DISTRICT[i]).append(' ').append(count[i]).append(", ");
						}
						check("grote stad: wijken", kinds >= 7, sb.toString());
						// a tower in the business district
						int tall = 0;
						for (int bx = -12; bx <= 12; bx++)
							for (int bz = -16; bz <= 16; bz++) {
								if (mt.district(bx, bz) != Metros.CBD) continue;
								int qx = mt.cx() + bx * Villages.PU + Villages.STREET + 3, qz = mt.cz() + bz * Villages.PW + Villages.STREET + 10;
								w.getChunk(qx >> 4, qz >> 4);
								if (!w.getBlockState(new BlockPos(qx, VillageBuilder.Y0 + 30, qz)).isAir()) tall++;
								if (tall >= 3) break;
							}
						check("grote stad: hoogbouw", tall >= 3, tall + " torens hoger dan 30");
						// the old centre has canals with water
						int canal = 0;
						for (int bx = -4; bx <= 4 && canal == 0; bx++)
							for (int bz = -5; bz <= 5 && canal == 0; bz++) {
								int qx = mt.cx() + bx * Villages.PU + Villages.STREET + 12, qz = mt.cz() + bz * Villages.PW + Villages.STREET + 10;
								if (!MetroBuilder.canalWater(mt, qx, qz)) continue;
								double[] rv = f.riverAt(qx, qz, true);
								if (rv[0] - rv[1] < 20) continue;
								w.getChunk(qx >> 4, qz >> 4);
								if (water(w, qx, RiverField.WATER_TOP, qz) && water(w, qx, RiverField.WATER_TOP - 2, qz)) canal = 1;
								else canal = -1;
							}
						check("grote stad: grachten", canal >= 0, canal == 1 ? "gracht met water" : canal == 0 ? "(geen gracht op het land gevonden)" : "gracht droog");
					}
					Metros.Metro rm = null;
					for (int i = -4; i <= 4 && rm == null; i++)
						for (int j = -4; j <= 4 && rm == null; j++) {
							Metros.Metro c = f.metros.metro(i, j);
							if (c != null && c.river()) rm = c;
						}
					if (mt != null && mt.river()) rm = mt;
					log.add(rm == null ? "  (geen grote stad aan de rivier in de buurt)" : "  grote stad aan de rivier op " + rm.cx() + ", " + rm.cz());
					if (rm != null) {
						double[] cb = MetroBuilder.findBridge(f, rm);
						check("grote stad: brug over de rivier", cb != null, cb == null ? "geen" : "op " + (int) cb[0] + ", " + (int) cb[1] + (cb[2] == 1 ? " (boogbrug)" : " (tuibrug)"));
						if (cb != null) {
							int qx = (int) Math.floor(cb[0]), qz = (int) Math.floor(cb[1]);
							w.getChunk(qx >> 4, qz >> 4);
							BlockState dk = w.getBlockState(new BlockPos(qx, MetroBuilder.DECK - 1, qz));
							int blocked = 0;
							for (int y = RiverField.WATER_TOP + 1; y <= MetroBuilder.DECK - 4; y++) if (!w.getBlockState(new BlockPos(qx, y, qz)).isAir()) blocked++;
							double[] py = MetroBuilder.findPylon(f, rm);
							if (py != null) {
								int px = (int) Math.floor(py[0]), pz = (int) Math.floor(py[1]);
								w.getChunk(px >> 4, pz >> 4);
								BlockState mast = w.getBlockState(new BlockPos(px, MetroBuilder.DECK + 40, pz));
								check("grote stad: pyloon van de tuibrug", mast.isOf(net.minecraft.block.Blocks.WHITE_CONCRETE), mast.getBlock().getName().getString() + " op " + px + ", " + (MetroBuilder.DECK + 40) + ", " + pz);
							}
							check("grote stad: brugdek hoog genoeg", dk.isOf(net.minecraft.block.Blocks.LIGHT_GRAY_CONCRETE) && blocked == 0,
									dk.getBlock().getName().getString() + " op y " + (MetroBuilder.DECK - 1) + ", " + blocked + " blokken eronder in de weg");
						}
					}
				}
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
				// motorway bridge over the river: high enough for every ship
				double[] br = f.infra.findBridge(0, 0, 40000);
				check("brug gevonden", br != null, br == null ? "geen" : "op " + (int) br[0] + ", " + (int) br[1]);
				if (br != null) {
					int bx = (int) Math.floor(br[0]), bz = (int) Math.floor(br[1]);
					w.getChunk(bx >> 4, bz >> 4);
					BlockState dk = w.getBlockState(new BlockPos(bx, Infra.DECK - 1, bz));
					int blocked = 0;
					for (int y = RiverField.WATER_TOP + 1; y <= Infra.DECK - 4; y++) if (!w.getBlockState(new BlockPos(bx, y, bz)).isAir()) blocked++;
					check("brug: wegdek", dk.isOf(net.minecraft.block.Blocks.LIGHT_GRAY_CONCRETE), dk.getBlock().getName().getString() + " op y " + (Infra.DECK - 1));
					check("brug: doorvaarthoogte", blocked == 0 && water(w, bx, RiverField.WATER_TOP, bz),
							blocked + " blokken in de weg, " + (Infra.DECK - 3 - RiverField.WATER_TOP) + " blokken vrij boven het water");
				}
				// a motorway junction: viaduct over the motorway, slip roads up to it
				{
					double[] jn = f.infra.junctions.nearest(0, 0);
					check("afrit gevonden", jn != null, jn == null ? "geen" : "op " + (int) jn[0] + ", " + (int) jn[1]);
					if (jn != null) {
						int qx = (int) Math.floor(jn[0]), qz = (int) Math.floor(jn[1]);
						w.getChunk(qx >> 4, qz >> 4);
						Object[] o = f.infra.junctions.at(qx + 0.5, qz + 0.5);
						if (o != null) {
							Junctions.Junction j = (Junctions.Junction) o[0];
							BlockState dk = w.getBlockState(new BlockPos(qx, j.hm() + Junctions.RISE - 1, qz));
							BlockState under = w.getBlockState(new BlockPos(qx, j.hm() + 3, qz));
							check("afrit: viaduct over de snelweg", dk.isOf(net.minecraft.block.Blocks.LIGHT_GRAY_CONCRETE) && !under.isOf(net.minecraft.block.Blocks.DIRT),
									"dek " + dk.getBlock().getName().getString() + ", onder: " + under.getBlock().getName().getString());
						} else check("afrit: viaduct over de snelweg", false, "geen afrit op dat punt");
					}
				}
				// viaduct where two motorways cross
				double[] vd = f.infra.findViaduct(0, 0, 3);
				check("viaduct gevonden", vd != null, vd == null ? "geen" : "op " + (int) vd[0] + ", " + (int) vd[1]);
				if (vd != null) {
					int vx = (int) Math.floor(vd[0]), vz = (int) Math.floor(vd[1]);
					w.getChunk(vx >> 4, vz >> 4);
					Infra.Hit vh = f.infra.hit(0, vx + 0.5, vz + 0.5, 30);
					if (vh != null && vh.kind == Infra.VIADUCT) {
						BlockState top = w.getBlockState(new BlockPos(vx, vh.y - 1, vz));
						BlockState under = w.getBlockState(new BlockPos(vx, vh.underY + 3, vz));
						check("viaduct: dek en weg eronder vrij", top.isOf(net.minecraft.block.Blocks.LIGHT_GRAY_CONCRETE) && under.isAir(),
								"dek " + top.getBlock().getName().getString() + " op y " + (vh.y - 1) + ", onder: " + under.getBlock().getName().getString());
					} else check("viaduct: dek en weg eronder vrij", false, "geen viaduct in het profiel");
				}
				// a lock with its weir
				Locks.Site lk = f.infra.locks.nearest(0, 0, 8);
				check("sluis gevonden", lk != null, lk == null ? "geen" : "op " + lk.cx() + ", " + lk.cz() + (lk.hasRoad() ? ", met weg naar een " + lk.town() : ", zonder weg"));
				if (lk != null) {
					int wet = 0;
					for (double ww = lk.ch0() + 0.5; ww < lk.outer0(); ww += 1) {
						int qx = (int) Math.floor(lk.wx(0.5, ww)), qz = (int) Math.floor(lk.wz(0.5, ww));
						w.getChunk(qx >> 4, qz >> 4);
						boolean open = true;
						for (int y = RiverField.WATER_TOP + 1; y <= RiverField.WATER_TOP + 25; y++) if (!w.getBlockState(new BlockPos(qx, y, qz)).isAir()) open = false;
						if (water(w, qx, 56, qz) && water(w, qx, RiverField.WATER_TOP, qz) && open) wet++;
					}
					check("sluis: kolk open en diep", wet >= Locks.CH - 2, wet + " van " + Locks.CH + " blokken breed open water van 6 diep");
					int gx = (int) Math.floor(lk.wx(0.5, lk.mid0() - 15)), gz = (int) Math.floor(lk.wz(0.5, lk.mid0() - 15));
					w.getChunk(gx >> 4, gz >> 4);
					BlockState gate = w.getBlockState(new BlockPos(gx, 60, gz));
					check("sluis: stuw", gate.isOf(net.minecraft.block.Blocks.CYAN_TERRACOTTA), gate.getBlock().getName().getString() + " in de stuw");
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
						if (t >= 61 && t <= 63) flat++;
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
					check("alle sloten met water", allWet >= all * 0.95, allWet + " van " + all + " slootvakjes in 200x200" + dry);
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
						double c = f.coastal(ox, oz);
						if (c < Lowlands.COAST - 0.2 && c > -0.95) {
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
