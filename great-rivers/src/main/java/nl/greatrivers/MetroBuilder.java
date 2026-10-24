package nl.greatrivers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.RailShape;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;

/** Builds the big cities of {@link Metros}, one column at a time. */
final class MetroBuilder {
	private MetroBuilder() {
	}

	static final int G = Villages.GROUND, Y0 = G + 1;
	static final int DECK = Infra.DECK;          // bridges over the river: every ship fits under
	static final double RAMP = 384;              // the ramp of a city bridge, along the boulevard

	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState LINE = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState WALK = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState WALK2 = Blocks.POLISHED_ANDESITE.getDefaultState();
	private static final BlockState CYCLE = Blocks.RED_TERRACOTTA.getDefaultState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
	private static final BlockState CONCRETE = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
	private static final BlockState BRICK = Blocks.BRICKS.getDefaultState();
	private static final BlockState GLASS = VillageBuilder.paneNS(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
	private static final BlockState GLASS_EW = VillageBuilder.paneEW(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);

	static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	static long hash(long a, long b, long c) {
		return VillageBuilder.hash(a, b, c);
	}

	// ------------------------------------------------------------------ the canals of the old centre (also used by the terrain)

	/** Is this column water of a canal in the old centre? */
	static boolean canalWater(Metros.Metro mt, int x, int z) {
		if (mt.low()) return false;
		int bx = Math.floorDiv(x - mt.cx(), Villages.PU), bz = Math.floorDiv(z - mt.cz(), Villages.PW);
		if (Math.floorMod(bz, 2) != 1 || Metros.Metro.avenueZ(bz) || Metros.Metro.avenueX(bx)) return false;
		if (mt.district(bx, bz) != Metros.OLD) return false;
		int bw = Math.floorMod(z - mt.cz(), Villages.PW) - Villages.STREET;
		return bw >= 6 && bw <= 14;
	}

	// ------------------------------------------------------------------ one column

	static boolean column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Metros.Metro mt, int x, int z) {
		Villages.Town t = mt.asTown();
		double e = Double.MAX_VALUE;
		if (mt.river()) {
			double[] rv = f.riverAt(x, z, true);
			e = rv[0] - rv[1];
		}
		int u = Math.floorMod(x - mt.cx(), Villages.PU), wv = Math.floorMod(z - mt.cz(), Villages.PW);
		int bx = Math.floorDiv(x - mt.cx(), Villages.PU), bz = Math.floorDiv(z - mt.cz(), Villages.PW);
		int bu = u - Villages.STREET, bw = wv - Villages.STREET;
		if (e < 0) {
			// on the river: only the bridges
			return mt.river() && bridge(w, m, f, mt, x, z, bx, bz, u, wv);
		}
		boolean ground = ground(w, m, f, mt, t, x, z, e, u, wv, bx, bz, bu, bw);
		boolean br = mt.river() && bridge(w, m, f, mt, x, z, bx, bz, u, wv);
		return ground || br;
	}

	private static boolean ground(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Metros.Metro mt, Villages.Town t, int x, int z, double e,
								  int u, int wv, int bx, int bz, int bu, int bw) {
		boolean covered = mt.river() && bridgeOver(f, mt, x, z, bx, bz, u, wv);
		// clear trees and plants above the ground
		for (int y = G + 1; y <= G + 36; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && (st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS) || st.isReplaceable() || st.isIn(BlockTags.FLOWERS)))
				set(w, m, x, y, z, AIR);
		}
		boolean any = false;
		if (e < VillageBuilder.QUAY) {
			VillageBuilder.quay(w, m, f, x, z, e);
			return true;
		}
		boolean avX = Metros.Metro.avenueX(bx), avZ = Metros.Metro.avenueZ(bz);
		boolean inStreet = Villages.street(t, x, z);
		boolean inBlock = Villages.blockIn(t, x, z);
		if (!inStreet && !inBlock) return VillageBuilder.edge(w, m, f, t, x, z) || any;
		// boulevards take the whole block (and run on over the streets that cross them)
		if (avX && bu >= 0 && (inBlock || inStreet)) {
			boulevard(w, m, x, z, bu, z - mt.cz(), true, avZ && bw >= 0, covered);
			return true;
		}
		if (avZ && bw >= 0 && (inBlock || inStreet)) {
			boulevard(w, m, x, z, bw, x - mt.cx(), false, false, covered);
			return true;
		}
		int district = mt.district(bx, bz);
		if (inStreet) {
			street(w, m, mt, x, z, u, wv, district);
			return true;
		}
		// under the railway viaduct through a metropolis: a long square with bike parking, the station on top
		if (mt.mega() && railBlock(f, mt, bx, bz)) {
			railSquare(w, m, f, x, z, bu, bw);
			return true;
		}
		if (mt.river() && VillageBuilder.waterfront(w, m, f, t, x, z, bu, bw, bx, bz, !mt.low())) return true;
		long bh = hash(mt.seed(), bx * 31L + bz, 7);
		double zone = Math.hypot(bx * Villages.PU, bz * Villages.PW) / mt.radius();
		switch (district) {
			case Metros.OLD -> {
				if (mt.low()) {
					// the old village centre of a big low-rise town: church, brick houses with shops
					if (bx == 0 && bz == 0) VillageBuilder.church(w, m, t, x, z, bu, bw);
					else if ((bh & 7) == 5) VillageBuilder.park(w, m, x, z, bu, bw);
					else shopHouses(w, m, t, x, z, bu, bw, bx, bz);
				} else if (Math.floorMod(bz, 2) == 1) canal(w, m, x, z, bu, bw, bh);
				else if (bx == 0 && bz == 0) VillageBuilder.church(w, m, t, x, z, bu, bw);
				else canalHouses(w, m, x, z, bu, bw, bh);
			}
			case Metros.CBD -> {
				if ((bh & 7) == 0) plaza(w, m, x, z, bu, bw);
				else VillageBuilder.tower(w, m, x, z, bu, bw, bh, mt.mega() ? megaZone(mt, bx, bz) : Math.max(-0.6, (zone - 0.15) / 0.17 - 1.0));
			}
			case Metros.MIXED -> {
				int r = (int) Math.floorMod(bh >>> 3, 20L);
				if (r < 6) VillageBuilder.flats(w, m, x, z, bu, bw, bh);
				else if (r < 8) supermarket(w, m, x, z, bu, bw, bh);
				else if (r < 9) school(w, m, x, z, bu, bw, bh);
				else if (r < 11) VillageBuilder.tower(w, m, x, z, bu, bw, bh, 0.9);
				else houses(w, m, t, x, z, bu, bw, bx, bz);
			}
			case Metros.FLATS -> {
				int r = (int) Math.floorMod(bh >>> 3, 20L);
				if (r < 13) VillageBuilder.flats(w, m, x, z, bu, bw, bh);
				else if (r < 15) VillageBuilder.tower(w, m, x, z, bu, bw, bh, 1.1);
				else if (r < 16) supermarket(w, m, x, z, bu, bw, bh);
				else if (r < 17) school(w, m, x, z, bu, bw, bh);
				else VillageBuilder.park(w, m, x, z, bu, bw);
			}
			case Metros.ROWS -> {
				int r = (int) Math.floorMod(bh >>> 3, 20L);
				if (Math.floorMod(bh >>> 13, 71L) == 3) {
					VillageBuilder.church(w, m, t, x, z, bu, bw);      // a neighbourhood church
					return true;
				}
				if (r < 1) supermarket(w, m, x, z, bu, bw, bh);
				else if (r < 2) school(w, m, x, z, bu, bw, bh);
				else if (r < 3) VillageBuilder.park(w, m, x, z, bu, bw);
				else houses(w, m, t, x, z, bu, bw, bx, bz);
			}
			case Metros.SUBURB -> {
				int r = (int) Math.floorMod(bh >>> 3, 20L);
				if (Math.floorMod(bh >>> 9, 23L) == 5) VillageBuilder.sportsField(w, m, x, z, bu, bw);
				else if (Math.floorMod(bh >>> 9, 41L) == 7) VillageBuilder.waterTower(w, m, x, z, bu, bw, bh);
				else if (r < 1) supermarket(w, m, x, z, bu, bw, bh);
				else if (r < 2) school(w, m, x, z, bu, bw, bh);
				else if (r < 4) VillageBuilder.park(w, m, x, z, bu, bw);
				else modernRows(w, m, x, z, bu, bw, bh);
			}
			case Metros.VILLAS -> villas(w, m, x, z, bu, bw, bh);
			case Metros.INDUSTRY -> {
				int r = (int) Math.floorMod(bh >>> 3, 10L);
				if (r < 5) warehouse(w, m, x, z, bu, bw, bh);
				else if (r < 8) factory(w, m, x, z, bu, bw, bh);
				else offices(w, m, x, z, bu, bw, bh);
			}
			case Metros.PARK -> cityPark(w, m, x, z, bu, bw, bh);
			default -> houses(w, m, t, x, z, bu, bw, bx, bz);
		}
		return true;
	}

	/** How tall the towers of a business district of a metropolis are (lower = taller): the main centre and the second centres. */
	private static double megaZone(Metros.Metro mt, int bx, int bz) {
		double mx = bx * Villages.PU + Villages.PU / 2.0, mz = bz * Villages.PW + Villages.PW / 2.0;
		double z = -0.6 + Math.max(0, Math.hypot(mx, mz) - 420) / 700;
		for (double[] c : mt.subCentres()) z = Math.min(z, -0.4 + Math.hypot(mx - c[0], mz - c[1]) / 420);
		return Math.min(1.2, z);
	}

	/** Is the block (bx, bz) of a metropolis under or beside the railway viaduct? */
	static boolean railBlock(RiverField f, Metros.Metro mt, int bx, int bz) {
		double mx = mt.cx() + bx * Villages.PU + Villages.PU / 2.0, mz = mt.cz() + bz * Villages.PW + Villages.PW / 2.0;
		return f.rails.distance(mx, mz) < Rails.HALF + 10 + 21.5;
	}

	/** Is this column of a metropolis a street or a boulevard (no piers there)? */
	static boolean openGround(Metros.Metro mt, int x, int z) {
		int bx = Math.floorDiv(x - mt.cx(), Villages.PU), bz = Math.floorDiv(z - mt.cz(), Villages.PW);
		if (Metros.Metro.avenueX(bx) || Metros.Metro.avenueZ(bz)) return true;
		return Villages.street(mt.asTown(), x, z);
	}

	/** The square under the railway viaduct: paving, rows of bike racks, trees along the sides. */
	private static void railSquare(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z, int bu, int bw) {
		set(w, m, x, G, z, ((bu + bw) & 3) == 0 ? WALK2 : WALK);
		double d = f.rails.distance(x + 0.5, z + 0.5);
		if (d < Rails.HALF + 7) {
			// bike parking under the tracks
			boolean ns = Math.abs(f.rails.nearest(0, x + 0.5, z + 0.5)[1]) < Math.abs(f.rails.nearest(1, x + 0.5, z + 0.5)[1]);
			int across = (int) Math.floor(d);
			if (across % 3 == 1 && Math.floorMod(ns ? z : x, 40) > 2) {
				set(w, m, x, G + 1, z, Blocks.IRON_BARS.getDefaultState());
				if ((hash(x, z, 77) & 3) != 0) set(w, m, x, G + 1, z, Blocks.IRON_BARS.getDefaultState());
			}
			return;
		}
		long h = hash(x, z, 5);
		if (d > Rails.HALF + 12 && (h % 37) == 0) Countryside.tree(w, m, x, G + 1, z, h >>> 8);
		else if ((h % 53) == 0) set(w, m, x, G + 1, z, VillageBuilder.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
	}

	/** Old village centre of a big low-rise town: brick houses, shops downstairs. */
	private static void shopHouses(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z, int bu, int bw, int bx, int bz) {
		if (bu >= 28) {
			set(w, m, x, G, z, Blocks.DIRT_PATH.getDefaultState());
			return;
		}
		boolean front = bw <= 9;
		if (bw == 10) {
			set(w, m, x, G + 1, z, Blocks.OAK_FENCE.getDefaultState());
			return;
		}
		int hx = bu % 7, house = bu / 7;
		int pw = front ? bw : 20 - bw;
		long hh = hash(t.seed(), bx * 31L + bz, house * 2L + (front ? 0 : 1));
		VillageBuilder.house(w, m, t, x, z, hx, pw, front, hh, false);
		VillageBuilder.shopFront(w, m, x, z, hx, pw, front, hh);
	}

	// ------------------------------------------------------------------ streets and boulevards

	/** City street: asphalt with pavements (cobbles in the old centre), a canal bridge where it crosses a canal. */
	private static void street(StructureWorldAccess w, BlockPos.Mutable m, Metros.Metro mt, int x, int z, int u, int wv, int district) {
		boolean alongX = wv < Villages.STREET && u >= Villages.STREET;
		boolean alongZ = u < Villages.STREET && wv >= Villages.STREET;
		int across = alongX ? wv : alongZ ? u : -1;
		boolean old = district == Metros.OLD;
		if (canalWater(mt, x, z)) {
			// a little brick bridge over the canal, with low parapets
			set(w, m, x, G - 1, z, Blocks.BRICK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.TOP));
			set(w, m, x, G, z, BRICK);
			if (across == 0 || across == Villages.STREET - 1) set(w, m, x, G + 1, z, Blocks.BRICK_WALL.getDefaultState());
			return;
		}
		BlockState st;
		if (across == 0 || across == Villages.STREET - 1) st = old ? BRICK : WALK;
		else if (across < 0) st = old ? Blocks.STONE_BRICKS.getDefaultState() : ASPHALT;
		else st = old ? BRICK : ASPHALT;
		set(w, m, x, G, z, st);
		int along = alongX ? x : z;
		if (across == 0 && Math.floorMod(along, 16) == 0) {
			if (old) {
				for (int y = G + 1; y <= G + 3; y++) set(w, m, x, y, z, Blocks.DARK_OAK_FENCE.getDefaultState());
				set(w, m, x, G + 4, z, Blocks.LANTERN.getDefaultState());
			} else {
				Direction arm = alongX ? Direction.SOUTH : Direction.EAST;
				InfraBuilder.lamp(w, m, x, G, z, arm, 6);
			}
		}
	}

	/**
	 * A boulevard filling a block: pavements, red cycle paths, trees, two lanes each way and a tram line in the
	 * middle. p = position across (0..28 north-south, 0..20 east-west), along = coordinate along it.
	 */
	private static void boulevard(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int p, int along, boolean ns, boolean crossing, boolean covered) {
		int W = ns ? 28 : 20;
		int q = Math.min(p, W - p);          // distance from the nearest side
		int mid = W / 2;
		long a = Math.floorMod((long) along, 1L << 20);
		if (crossing) {
			// where two boulevards cross: asphalt with zebra crossings, traffic lights at the corners
			set(w, m, x, G, z, (q <= 1 && (a & 1) == 0) ? LINE : ASPHALT);
			return;
		}
		if (!covered && (q == 0) && (Math.floorMod(along, ns ? Villages.PW : Villages.PU) == (ns ? Villages.STREET + 1 : Villages.STREET + 1))) {
			// traffic lights where a street meets the boulevard
			for (int y = G + 1; y <= G + 3; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, G + 4, z, Blocks.BLACK_CONCRETE.getDefaultState());
			set(w, m, x, G + 5, z, ((a / 7) & 1) == 0 ? Blocks.LIME_STAINED_GLASS.getDefaultState() : Blocks.RED_STAINED_GLASS.getDefaultState());
		}
		int walk = ns ? 1 : 0, cyc = ns ? 3 : 2, lane0 = ns ? 5 : 3, lane1 = ns ? 10 : 7, line = ns ? 8 : 5, verge = ns ? 11 : 8;
		BlockState st;
		if (q <= walk) st = (a & 3) == 0 ? WALK2 : WALK;                                // pavement
		else if (q <= cyc) st = CYCLE;                                                    // cycle path
		else if (q < lane0) st = GRASS;                                                   // verge with trees
		else if (q <= lane1) st = q == line && (a % 12) < 4 ? LINE : ASPHALT;            // two lanes
		else if (q == verge) st = GRASS;
		else st = Blocks.GRAVEL.getDefaultState();                                        // tram line
		set(w, m, x, G, z, st);
		BlockState below = w.getBlockState(m.set(x, G - 1, z));
		if (below.isAir() || !below.getFluidState().isEmpty()) set(w, m, x, G - 1, z, Blocks.STONE.getDefaultState());
		int rail0 = ns ? 13 : 9, rail1 = ns ? 15 : 11;
		if (p == rail0 || p == rail1)
			set(w, m, x, G + 1, z, Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, ns ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST));
		if (covered) return;      // under a bridge ramp: no trees, lamps or shelters
		if (ns && q == 4 && a % 9 == 4) Countryside.tree(w, m, x, G + 1, z, Harbours.mix(x * 7L + z));
		if (q == verge && a % 30 == 0)
			InfraBuilder.lamp(w, m, x, G, z, ns ? (p < mid ? Direction.WEST : Direction.EAST) : (p < mid ? Direction.NORTH : Direction.SOUTH), 7);
		// a tram stop every few blocks: raised platforms with a glass shelter
		if (ns && q == verge && a % 156 >= 70 && a % 156 < 86) {
			set(w, m, x, G + 1, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
			if (a % 156 >= 74 && a % 156 < 82) {
				set(w, m, x, G + 2, z, GLASS);
				set(w, m, x, G + 3, z, GLASS);
				set(w, m, x, G + 4, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
			}
		}
	}

	// ------------------------------------------------------------------ bridges over the river

	/** Does the boulevard of block column/row index cross the river on a bridge, and which kind? 0 none, 1 arch, 2 cable-stayed. */
	private static final java.util.concurrent.ConcurrentHashMap<Long, Integer> KINDS = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Does the boulevard with this index cross the river on a bridge, and which kind? 0 none, 1 arch, 2 cable-stayed.
	 * Only where the ramps on both sides come back down to the street well inside the city.
	 */
	private static int bridgeKind(RiverField f, Metros.Metro mt, int index) {
		if (KINDS.size() > 50000) KINDS.clear();
		long key = mt.seed() ^ (index * 0x9E3779B97F4A7C15L) ^ 0xB81D6EL;
		return KINDS.computeIfAbsent(key, k -> {
			long hk = Harbours.mix(mt.seed() + index * 131L);
			if (Math.floorMod(hk, 3L) == 2) return 0;
			int i = (int) Math.floorMod(hk >>> 8, 5L);          // arch, cable-stayed, suspension, lift bridge, truss
			boolean ns = riverAlongX(f, mt);
			double end = VillageBuilder.QUAY + 6 + RAMP;
			double sWet0 = Double.NaN, sWet1 = Double.NaN;
			for (double sv = -mt.radius(); sv <= mt.radius(); sv += 4) {
				double[] p = avenuePoint(mt, ns, index, sv);
				if (f.great.distance(p[0], p[1]) - f.great.halfWidth(p[0], p[1]) < 0) {
					if (Double.isNaN(sWet0)) sWet0 = sv;
					sWet1 = sv;
				}
			}
			if (Double.isNaN(sWet0)) return 0;
			// a real crossing: one stretch of water, about as long as the river is wide, the river square across
			for (double sv = sWet0; sv <= sWet1; sv += 4) {
				double[] p = avenuePoint(mt, ns, index, sv);
				if (f.great.distance(p[0], p[1]) - f.great.halfWidth(p[0], p[1]) >= 0) return 0;
			}
			double[] pm = avenuePoint(mt, ns, index, (sWet0 + sWet1) / 2);
			double hwm = f.great.halfWidth(pm[0], pm[1]);
			if (sWet1 - sWet0 > 2.6 * hwm + 8) return 0;
			double[] nm = f.great.normal(pm[0], pm[1]);
			if (Math.abs(ns ? nm[1] : nm[0]) < 0.8) return 0;
			// walk out from the water on both sides until the ramp is down; that point must lie inside the city
			for (int dir = -1; dir <= 1; dir += 2) {
				double sv = dir < 0 ? sWet0 : sWet1;
				boolean down = false;
				for (double t = 0; t < 900; t += 4) {
					double[] p = avenuePoint(mt, ns, index, sv + dir * t);
					if (Math.hypot(p[0] - mt.cx(), p[1] - mt.cz()) > mt.radius() - 20) return 0;
					if (f.great.distance(p[0], p[1]) - f.great.halfWidth(p[0], p[1]) > end + 2) {
						down = true;
						break;
					}
				}
				if (!down) return 0;
			}
			return i + 1;
		});
	}

	/** Point on the middle line of a boulevard, sv along it from the city centre line. */
	private static double[] avenuePoint(Metros.Metro mt, boolean ns, int index, double sv) {
		if (ns) return new double[]{mt.cx() + index * Villages.PU + Villages.STREET + 14.5, mt.cz() + sv};
		return new double[]{mt.cx() + sv, mt.cz() + index * Villages.PW + Villages.STREET + 10.5};
	}

	/** Does the river run more along x than along z here (so the north-south boulevards cross it)? */
	private static boolean riverAlongX(RiverField f, Metros.Metro mt) {
		double[] n = f.great.normal(mt.cx(), mt.cz());
		return Math.abs(n[1]) >= Math.abs(n[0]);
	}

	/** Middle of a city bridge over the river: {x, z, kind}, or null. For /findriver and the tests. */
	static double[] findBridge(RiverField f, Metros.Metro mt) {
		if (!mt.river()) return null;
		boolean ns = riverAlongX(f, mt);
		int n = (int) (mt.radius() / (ns ? Villages.PU : Villages.PW));
		double[] best = null;
		double bd = Double.MAX_VALUE;
		for (int index = -n; index <= n; index++) {
			if (ns ? !Metros.Metro.avenueX(index) : !Metros.Metro.avenueZ(index)) continue;
			int kind = bridgeKind(f, mt, index);
			if (kind == 0) continue;
			for (double s = -mt.radius(); s <= mt.radius(); s += 2) {
				double cxl = ns ? mt.cx() + index * Villages.PU + Villages.STREET + 14.5 : mt.cx() + s;
				double czl = ns ? mt.cz() + s : mt.cz() + index * Villages.PW + Villages.STREET + 10.5;
				if (Math.hypot(cxl - mt.cx(), czl - mt.cz()) > mt.radius()) continue;
				double dc = f.great.distance(cxl, czl);
				if (dc < f.great.halfWidth(cxl, czl) * 0.3) {
					double d = Math.hypot(cxl - mt.cx(), czl - mt.cz());
					if (d < bd) {
						bd = d;
						best = new double[]{cxl, czl, kind};
					}
				}
			}
		}
		return best;
	}

	/** The pylon of a cable-stayed city bridge: {x, z} of the middle of its mast, or null. For the tests. */
	static double[] findPylon(RiverField f, Metros.Metro mt) {
		if (!mt.river()) return null;
		boolean ns = riverAlongX(f, mt);
		int n = (int) (mt.radius() / (ns ? Villages.PU : Villages.PW));
		for (int index = -n; index <= n; index++) {
			if (ns ? !Metros.Metro.avenueX(index) : !Metros.Metro.avenueZ(index)) continue;
			if (bridgeKind(f, mt, index) != 2) continue;
			for (double s = -mt.radius(); s <= mt.radius(); s += 0.5) {
				double cxl = ns ? mt.cx() + index * Villages.PU + Villages.STREET + 14.5 : mt.cx() + s;
				double czl = ns ? mt.cz() + s : mt.cz() + index * Villages.PW + Villages.STREET + 10.5;
				double dc = f.great.distance(cxl, czl), hw = f.great.halfWidth(cxl, czl);
				double signedD = dc * Math.signum(f.great.value(cxl, czl) + 1e-12);
				if (Math.abs(signedD - (hw - 4)) < 0.6 && signedD > 0) return new double[]{cxl, czl};
			}
		}
		return null;
	}

	/** Is there a bridge ramp (higher than a few blocks) over this column? */
	private static boolean bridgeOver(RiverField f, Metros.Metro mt, int x, int z, int bx, int bz, int u, int wv) {
		boolean ns = riverAlongX(f, mt);
		if (ns ? !Metros.Metro.avenueX(bx) : !Metros.Metro.avenueZ(bz)) return false;
		int index = ns ? bx : bz;
		if (bridgeKind(f, mt, index) == 0) return false;
		double cxl = ns ? mt.cx() + bx * Villages.PU + Villages.STREET + 14.5 : x + 0.5;
		double czl = ns ? z + 0.5 : mt.cz() + bz * Villages.PW + Villages.STREET + 10.5;
		double ec = f.great.distance(cxl, czl) - f.great.halfWidth(cxl, czl);
		return ec < VillageBuilder.QUAY + 6 + RAMP * 0.9;
	}

	/** The high bridges of a river city, with their ramps along the boulevards. Returns true if it built here. */
	private static boolean bridge(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Metros.Metro mt, int x, int z, int bx, int bz, int u, int wv) {
		boolean ns = riverAlongX(f, mt);
		int p, index;
		double cxl, czl;
		if (ns) {
			if (!Metros.Metro.avenueX(bx)) return false;
			p = u - Villages.STREET;
			index = bx;
			cxl = mt.cx() + bx * Villages.PU + Villages.STREET + 14.5;
			czl = z + 0.5;
		} else {
			if (!Metros.Metro.avenueZ(bz)) return false;
			p = wv - Villages.STREET;
			index = bz;
			cxl = x + 0.5;
			czl = mt.cz() + bz * Villages.PW + Villages.STREET + 10.5;
		}
		int W = ns ? 28 : 20;
		if (p < 2 || p > W - 2) return false;
		int kind = bridgeKind(f, mt, index);
		if (kind == 0) return false;
		if (Math.hypot(cxl - mt.cx(), czl - mt.cz()) > mt.radius() + 20) return false;
		double dc = f.great.distance(cxl, czl), hw = f.great.halfWidth(cxl, czl), ec = dc - hw;
		double end = VillageBuilder.QUAY + 6;
		if (ec > end + RAMP) return false;
		double H = ec < end ? DECK : G + (DECK - G) * (1 - (ec - end) / RAMP);
		int y = (int) Math.round(H);
		if (y <= G) return false;
		int q = Math.min(p, W - p), mid = W / 2;
		long along = Math.round(ns ? czl : cxl);
		// the deck
		if (y - G <= 3) {
			for (int yy = G + 1; yy < y; yy++) set(w, m, x, yy, z, CONCRETE);    // the ramp starts on solid ground
		} else {
			set(w, m, x, y - 1, z, CONCRETE);
			set(w, m, x, y - 2, z, Blocks.SMOOTH_STONE.getDefaultState());
		}
		BlockState top;
		if (q <= 2) top = CONCRETE;
		else if (q <= (ns ? 4 : 3)) top = CYCLE;
		else if (Math.abs(p - mid) <= 1) top = Blocks.GRAVEL.getDefaultState();
		else top = (q == (ns ? 8 : 6) && Math.floorMod(along, 12L) < 4) ? LINE : ASPHALT;
		set(w, m, x, y, z, top);
		if (q == 2) {
			set(w, m, x, y + 1, z, CONCRETE);                                   // parapet
			set(w, m, x, y + 2, z, Blocks.IRON_BARS.getDefaultState());
		}
		if (Math.abs(p - mid) == 1) set(w, m, x, y + 1, z, Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, ns ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST));
		if (q == 3 && Math.floorMod(along, 30L) == 0 && y - G > 3)
			InfraBuilder.lamp(w, m, x, y, z, ns ? (p < mid ? Direction.EAST : Direction.WEST) : (p < mid ? Direction.SOUTH : Direction.NORTH), 6);
		// piers under the ramps (on land, between the lanes below)
		if (ec >= 0 && y - G > 5 && Math.floorMod(along, 16L) < 2 && (q == 4 || q == (ns ? 11 : 8))) {
			for (int yy = G + 1; yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
		}
		// the main span over the water
		if (dc < hw + end) {
			double span = hw + end;
			double s = dc / span;
			if (kind == 1) {
				// steel arch on both sides, chains down to the deck
				double ah = Math.min(44, span * 0.42);
				double hc = ah * (1 - s * s);
				int yc = y + 1 + (int) Math.round(hc);
				if (q == 2) {
					double d1 = f.great.distance(ns ? cxl : cxl + 1, ns ? czl + 1 : czl) / span;
					double hc1 = ah * (1 - Math.min(1, d1 * d1));
					int y1 = y + 1 + (int) Math.round(hc1);
					for (int yy = Math.min(yc, y1) - 1; yy <= Math.max(yc, y1) + 1; yy++) if (yy > y + 2) set(w, m, x, yy, z, Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState());
				} else if (q == 3 && Math.floorMod(along, 6L) == 3 && hc > 3) {
					for (int yy = y + 1; yy < yc; yy++) set(w, m, x, yy, z, Blocks.CHAIN.getDefaultState());
				} else if (hc > ah * 0.7 && Math.floorMod(along, 14L) == 0) set(w, m, x, yc, z, Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState());
			} else if (kind == 3) {
				suspension(w, m, f, x, z, y, q, p, mid, W, dc, hw, cxl, czl, along, ns);
			} else if (kind == 4) {
				liftBridge(w, m, x, z, y, q, p, mid, W, dc, hw, cxl, czl, f, along);
			} else if (kind == 5) {
				// a steel truss over the whole river, on two piers
				if (q == 2 && dc < hw + 4) {
					int ph = (int) Math.floorMod(along, 12L);
					boolean end = dc > hw + 3;
					for (int yy = y + 2; yy <= y + 11; yy++) {
						int rel = yy - y;
						boolean chord = rel == 11 || rel == 2;
						boolean diag = rel == 2 + ph * 9 / 12 || rel == 11 - ph * 9 / 12;
						if (chord || ph == 0 || end || diag) set(w, m, x, yy, z, Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState());
					}
				} else if (q > 2 && dc < hw + 4 && Math.floorMod(along, 12L) == 0) set(w, m, x, y + 11, z, Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState());
				if (dc > hw * 0.78 && dc < hw - 4 && Math.floorMod(along, 40L) < 3 && q >= 4 && q <= 6)
					for (int yy = Math.max(30, groundY(w, m, x, z)); yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
			} else {
				// cable-stayed: one white pylon at the middle of the river side of the near bank, cables in a fan
				double pyl = hw - 4;                      // the pylon stands just inside the water line, out of the fairway
				double signedD = dc * Math.signum(f.great.value(cxl, czl) + 1e-12);
				double off = Math.abs(signedD - pyl);     // distance along the bridge from the pylon (on the + side)
				int ptop = y + 70;
				if (off < 2.5 && signedD > 0) {
					// the pylon: two legs below and through the deck, one mast above
					boolean leg = q == 4 || q == W - 4;
					boolean mast = Math.abs(p - mid) <= 1;
					if (leg) for (int yy = Math.max(30, groundY(w, m, x, z)); yy <= y + 12; yy++) set(w, m, x, yy, z, Blocks.WHITE_CONCRETE.getDefaultState());
					if (mast) for (int yy = y + 10; yy <= ptop; yy++) set(w, m, x, yy, z, Blocks.WHITE_CONCRETE.getDefaultState());
					if (q > 4 && Math.abs(p - mid) > 1 && Math.abs(p - mid) <= mid - 4) set(w, m, x, y + 11, z, Blocks.WHITE_CONCRETE.getDefaultState());
					if (mast && off < 0.6) set(w, m, x, ptop + 1, z, Blocks.REDSTONE_LAMP.getDefaultState());
				} else if (Math.abs(p - mid) == 0 && signedD > -span * 0.2 && off < 170) {
					// cables: from the mast down to the middle of the deck, on both sides of the pylon
					for (int k = 1; k <= 8; k++) {
						double anchor = 19.0 * k;
						if (off > anchor) continue;
						double yk = ptop - 5.5 * k;
						double yc = yk - (yk - (y + 1)) * (off / anchor);
						double yc1 = yk - (yk - (y + 1)) * (Math.min(anchor, off + 1) / anchor);
						for (int yy = (int) Math.floor(Math.min(yc, yc1)); yy <= (int) Math.floor(Math.max(yc, yc1)); yy++)
							if (yy > y + 1) set(w, m, x, yy, z, Blocks.IRON_BARS.getDefaultState());
					}
				}
			}
		}
		return true;
	}

	/**
	 * Suspension bridge: two grey towers just inside the water line on both banks, two main cables hanging between
	 * them and down to the ramps behind them, hangers every 5 blocks down to the deck edges.
	 */
	private static void suspension(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z, int y, int q, int p, int mid, int W,
								   double dc, double hw, double cxl, double czl, long along, boolean ns) {
		double tw = hw - 4;                         // the towers
		int top = y + 46;
		double off = dc - tw;
		BlockState tower = Blocks.POLISHED_ANDESITE.getDefaultState();
		if (Math.abs(off) < 2) {
			if (q == 2) for (int yy = Math.max(30, groundY(w, m, x, z)); yy <= top; yy++) set(w, m, x, yy, z, tower);
			else if (Math.abs(off) < 1) {
				set(w, m, x, top, z, tower);
				set(w, m, x, y + 24, z, tower);
				set(w, m, x, y - 3, z, CONCRETE);
			}
			if (q == 2 && Math.abs(off) < 0.6) set(w, m, x, top + 1, z, Blocks.REDSTONE_LAMP.getDefaultState());
			return;
		}
		if (q != 2) return;
		// the main cable: a parabola between the towers, straight backstays behind them
		double yc;
		if (dc < tw) yc = y + 4 + (top - 1 - (y + 4)) * (dc / tw) * (dc / tw);
		else if (off < 70) yc = top - 1 - (top - 1 - (y + 1)) * (off / 70);
		else return;
		double dn = dc + 1;
		double yn = dn < tw ? y + 4 + (top - 1 - (y + 4)) * (dn / tw) * (dn / tw) : top - 1 - (top - 1 - (y + 1)) * Math.min(1, (dn - tw) / 70);
		for (int yy = (int) Math.floor(Math.min(yc, yn)); yy <= (int) Math.floor(Math.max(yc, yn)); yy++)
			if (yy > y + 1) set(w, m, x, yy, z, Blocks.IRON_BLOCK.getDefaultState());
		if (dc < tw && Math.floorMod(along, 5L) == 0)
			for (int yy = y + 1; yy < (int) Math.floor(yc); yy++) set(w, m, x, yy, z, Blocks.CHAIN.getDefaultState());
	}

	/**
	 * Lift bridge ("hefbrug"), kept in the raised position: two steel lattice towers on both sides of the fairway
	 * with the counterweights at the top, the lifting span between them with truss sides.
	 */
	private static void liftBridge(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int y, int q, int p, int mid, int W,
								   double dc, double hw, double cxl, double czl, RiverField f, long along) {
		BlockState steel = Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState();
		double tp = Math.min(hw - 4, Math.max(30, hw * 0.8));      // towers at both ends of the lifting span, outside the fairway
		double off = dc - tp;
		int top = y + 30;
		if (Math.abs(off) < 3 && q == 2) {
			boolean leg = Math.abs(Math.abs(off) - 2) < 0.6;
			for (int yy = Math.max(30, groundY(w, m, x, z)); yy <= top; yy++) {
				int rel = yy - y;
				if (leg || rel % 6 == 0 || yy == top) set(w, m, x, yy, z, steel);
			}
			if (q == 2 && Math.abs(off) < 1) {
				for (int yy = top - 7; yy <= top - 3; yy++) set(w, m, x, yy, z, CONCRETE);      // counterweight
				set(w, m, x, top + 1, z, Blocks.REDSTONE_LAMP.getDefaultState());
			}
			return;
		}
		if (Math.abs(off) < 1 && q > 3) {
			set(w, m, x, top, z, steel);                       // the beam across between the towers
			set(w, m, x, y - 3, z, CONCRETE);
			if (q == 4) for (int yy = Math.max(30, groundY(w, m, x, z)); yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
			return;
		}
		if (dc < tp - 3 && q == 2) {
			// truss sides of the lifting span
			int ph = (int) Math.floorMod(along, 8L);
			for (int yy = y + 2; yy <= y + 7; yy++) {
				int rel = yy - y;
				if (rel == 2 || rel == 7 || ph == 0 || rel == 2 + ph * 5 / 8) set(w, m, x, yy, z, steel);
			}
		}
	}

	private static int groundY(StructureWorldAccess w, BlockPos.Mutable m, int x, int z) {
		int g = G;
		while (g > 20 && (w.getBlockState(m.set(x, g, z)).isAir() || !w.getBlockState(m).getFluidState().isEmpty())) g--;
		return g;
	}

	private static final java.util.concurrent.ConcurrentHashMap<Long, Boolean> NEAR_WATER = new java.util.concurrent.ConcurrentHashMap<>();

	/** Is this block of a river city on the quays (cached per block: it takes nine river samples)? */
	private static boolean nearWater(RiverField f, Metros.Metro mt, Villages.Town t, int bx, int bz) {
		if (NEAR_WATER.size() > 200000) NEAR_WATER.clear();
		long key = mt.seed() ^ (bx * 0x9E3779B97F4A7C15L) ^ (bz * 0x632BE59BD9B4E019L);
		return NEAR_WATER.computeIfAbsent(key, k -> VillageBuilder.blockNearWater(f, t, bx, bz));
	}

	// ------------------------------------------------------------------ squares

	private static void plaza(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw) {
		set(w, m, x, G, z, ((bu + bw) & 1) == 0 ? WALK : WALK2);
		long h = hash(x, z, 5);
		if ((bu % 7 == 3) && (bw % 7 == 3)) Countryside.tree(w, m, x, G + 1, z, h);
		else if (bu == 14 && bw == 10) {
			// a fountain
			set(w, m, x, G, z, Blocks.WATER.getDefaultState());
		} else if ((h % 41) == 0) set(w, m, x, G + 1, z, VillageBuilder.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
	}

	// ------------------------------------------------------------------ the old centre: canals and canal houses

	private static void canal(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		if (bw <= 4 || bw >= 16) {
			// the quay streets along the canal, with elms
			set(w, m, x, G, z, (bw == 0 || bw == 20) ? WALK : BRICK);
			if ((bw == 3 || bw == 17) && bu % 8 == 4) Countryside.tree(w, m, x, G + 1, z, hash(x, z, 9));
			if ((bw == 4 || bw == 16) && bu % 12 == 0) {
				for (int y = G + 1; y <= G + 3; y++) set(w, m, x, y, z, Blocks.DARK_OAK_FENCE.getDefaultState());
				set(w, m, x, G + 4, z, Blocks.LANTERN.getDefaultState());
			}
			if ((bw == 4 || bw == 16) && bu % 12 == 6) set(w, m, x, G + 1, z, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());   // "Amsterdammertje"
			return;
		}
		if (bw == 5 || bw == 15) {
			// brick quay walls
			for (int y = 55; y < G; y++) set(w, m, x, y, z, BRICK);
			set(w, m, x, G, z, Blocks.STONE_BRICKS.getDefaultState());
			return;
		}
		// the water, with a houseboat here and there along the north side
		long hb = hash(bh, bu / 9, 3);
		if ((hb & 1) == 0 && bw >= 6 && bw <= 8 && bu % 9 >= 1 && bu % 9 <= 7) {
			boolean rim = bu % 9 == 1 || bu % 9 == 7 || bw == 6 || bw == 8;
			Block wood = new Block[]{Blocks.GREEN_TERRACOTTA, Blocks.BLUE_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA}[(int) ((hb >>> 3) & 3)];
			set(w, m, x, RiverField.WATER_TOP, z, Blocks.BLACK_CONCRETE.getDefaultState());
			set(w, m, x, RiverField.WATER_TOP + 1, z, rim ? wood.getDefaultState() : Blocks.SPRUCE_PLANKS.getDefaultState());
			set(w, m, x, RiverField.WATER_TOP + 2, z, rim ? ((bu % 3 == 0 && !(bw == 7)) ? GLASS_EW : wood.getDefaultState()) : AIR);
			set(w, m, x, RiverField.WATER_TOP + 3, z, Blocks.DARK_OAK_SLAB.getDefaultState());
			if (bu % 9 == 4 && bw == 7) set(w, m, x, RiverField.WATER_TOP + 4, z, Blocks.FLOWER_POT.getDefaultState());
		}
	}

	private static final Block[] CANAL_BRICK = {Blocks.BRICKS, Blocks.MUD_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.BRICKS, Blocks.GRANITE, Blocks.POLISHED_GRANITE};

	/** Tall narrow canal houses ("grachtenpanden"): five wide, four floors, a gable on top, a hoisting beam. */
	static void canalHouses(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		if (bu >= 25) {
			set(w, m, x, G, z, Blocks.DIRT_PATH.getDefaultState());
			return;
		}
		boolean front = bw <= 9;
		if (bw == 10) {
			set(w, m, x, G + 1, z, Blocks.OAK_FENCE.getDefaultState());
			return;
		}
		int pw = front ? bw : 20 - bw;
		int hx = bu % 5, house = bu / 5;
		long hh = hash(bh, house, front ? 1 : 2);
		BlockState wall = CANAL_BRICK[(int) Math.floorMod(hh, (long) CANAL_BRICK.length)].getDefaultState();
		BlockState trim = Blocks.WHITE_CONCRETE.getDefaultState();
		int floors = 4 + (int) ((hh >>> 4) & 1);
		int eave = Y0 + floors * 4;
		boolean edge = hx == 0 || hx == 4 || pw == 0 || pw == 9;
		Direction out = front ? Direction.NORTH : Direction.SOUTH;
		set(w, m, x, G, z, Blocks.STONE_BRICKS.getDefaultState());
		set(w, m, x, Y0, z, edge ? wall : Blocks.OAK_PLANKS.getDefaultState());
		for (int y = Y0 + 1; y < eave; y++) {
			int rel = y - Y0;
			BlockState st = AIR;
			if (edge) {
				st = wall;
				if (pw == 0 || pw == 9) {
					boolean winCol = hx == 1 || hx == 3;
					if (winCol && rel % 4 >= 1 && rel % 4 <= 2) st = VillageBuilder.paneEW(Blocks.WHITE_STAINED_GLASS_PANE);
					if (winCol && rel % 4 == 3) st = trim;
					if (pw == 0 && hx == 2 && rel <= 2) st = AIR;                         // front door
				}
			} else if (rel % 4 == 0) st = Blocks.SPRUCE_PLANKS.getDefaultState();
			set(w, m, x, y, z, st);
		}
		if (pw == 0 && hx == 2) VillageBuilder.door(w, m, x, z, out, Blocks.DARK_OAK_DOOR);
		if (!edge && hx == 2 && pw == 8) for (int y = Y0 + 1; y < eave; y++) set(w, m, x, y, z, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, out));
		if (!edge && hx == 2 && pw == 4) for (int f = 0; f < floors; f++) set(w, m, x, Y0 + f * 4 + 3, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
		// the gable: stepped, bell or neck shaped, white cornice, a hoisting beam at the top
		int type = (int) ((hh >>> 6) % 3);
		int k = Math.min(hx, 4 - hx);
		for (int r = 0; r <= 5; r++) {
			int y = eave + r;
			BlockState st = AIR;
			if (pw == 0 || pw == 9) {
				int width = type == 0 ? 2 - r / 2 : type == 1 ? (r < 3 ? 2 : r < 5 ? 1 : 0) : (r < 2 ? 2 : r < 5 ? 1 : 0);
				if (k >= 2 - width) st = r == 0 ? trim : wall;
				if (hx == 2 && r == 2 && pw == 0) st = VillageBuilder.paneEW(Blocks.WHITE_STAINED_GLASS_PANE);
			} else {
				// the roof behind the gable, ridge from front to back
				if (r == k) st = hx == 2 ? Blocks.DEEPSLATE_TILES.getDefaultState()
						: VillageBuilder.stairs(Blocks.DEEPSLATE_TILE_STAIRS, hx < 2 ? Direction.EAST : Direction.WEST);
				else if (r < k) st = AIR;
			}
			set(w, m, x, y, z, st);
		}
		if (pw == 0 && hx == 2) {
			int fx = x + out.getOffsetX(), fz = z + out.getOffsetZ();
			set(w, m, fx, eave + 4, fz, Blocks.DARK_OAK_FENCE.getDefaultState());                  // hoisting beam
		}
	}

	// ------------------------------------------------------------------ houses

	private static void houses(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z, int bu, int bw, int bx, int bz) {
		if (bu >= 28) {
			set(w, m, x, G, z, Blocks.DIRT_PATH.getDefaultState());
			return;
		}
		boolean front = bw <= 9;
		if (bw == 10) {
			set(w, m, x, G + 1, z, Blocks.OAK_FENCE.getDefaultState());
			return;
		}
		int hx = bu % 7, house = bu / 7;
		int pw = front ? bw : 20 - bw;
		long hh = hash(t.seed(), bx * 31L + bz, house * 2L + (front ? 0 : 1));
		VillageBuilder.house(w, m, t, x, z, hx, pw, front, hh, false);
	}

	private static final Block[] MODERN = {Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_SANDSTONE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.BRICKS,
			Blocks.MUD_BRICKS, Blocks.DEEPSLATE_BRICKS};
	private static final Block[] CAR = {Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.BLUE_CONCRETE,
			Blocks.RED_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE};

	/** A small car standing along x (4 long, 2 wide) or z; (cu, cw) = position in the car. */
	private static void car(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int cu, int cw, long h) {
		BlockState body = CAR[(int) Math.floorMod(h, (long) CAR.length)].getDefaultState();
		set(w, m, x, G + 1, z, body);
		if (cu >= 1 && cu <= 2) set(w, m, x, G + 2, z, Blocks.BLACK_STAINED_GLASS.getDefaultState());
	}

	/** New estate ("Vinex"): modern row houses with flat or single-pitch roofs, front gardens, cars, solar panels. */
	static void modernRows(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean front = bw <= 9;
		int pw = front ? bw : 20 - bw;            // 0 at the street
		if (bw == 10) {
			set(w, m, x, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
			return;
		}
		int hx = bu % 6, house = bu / 6;
		long hh = hash(bh, house, front ? 1 : 2);
		if (house >= 4 || bu >= 24) {
			set(w, m, x, G, z, GRASS);
			return;
		}
		if (pw <= 2) {
			// front garden with a low hedge, a parking place with a car
			set(w, m, x, G, z, hx <= 1 ? WALK2 : GRASS);
			if (pw == 0 && hx >= 2) set(w, m, x, G + 1, z, Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
			if (hx <= 1 && pw <= 2 && (hh & 1) == 0) car(w, m, x, z, pw, hx, hh >>> 2);
			return;
		}
		if (pw >= 8) {
			set(w, m, x, G, z, GRASS);   // back garden
			if (pw == 9 && hx == 0) set(w, m, x, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
			return;
		}
		int lp = pw - 3;                                // 0..4 inside the house
		BlockState wall = MODERN[(int) Math.floorMod(bh, (long) MODERN.length)].getDefaultState();
		boolean edge = hx == 0 || hx == 5 || lp == 0 || lp == 4;
		boolean flat = ((bh >>> 5) & 1) == 0;
		int top = Y0 + 7;
		Direction out = front ? Direction.NORTH : Direction.SOUTH;
		set(w, m, x, G, z, WALK);
		set(w, m, x, Y0, z, edge ? wall : Blocks.BIRCH_PLANKS.getDefaultState());
		for (int y = Y0 + 1; y < top; y++) {
			int rel = y - Y0;
			BlockState st = edge ? wall : (rel == 4 ? Blocks.BIRCH_PLANKS.getDefaultState() : AIR);
			if (edge && (lp == 0 || lp == 4) && hx >= 2 && hx <= 4 && (rel == 2 || rel == 3 || rel == 6)) st = VillageBuilder.paneEW(Blocks.GLASS_PANE);
			if (edge && lp == 0 && hx == 1 && rel <= 2) st = AIR;
			set(w, m, x, y, z, st);
		}
		if (lp == 0 && hx == 1) VillageBuilder.door(w, m, x, z, out, Blocks.SPRUCE_DOOR);
		if (flat) {
			set(w, m, x, top, z, Blocks.GRAY_CONCRETE.getDefaultState());
			if (!edge && (hx & 1) == 1) set(w, m, x, top + 1, z, Blocks.DAYLIGHT_DETECTOR.getDefaultState());   // solar panels
		} else {
			// single-pitch roof rising to the back, with solar panels
			int ry = top + lp / 2;
			set(w, m, x, ry, z, Blocks.DEEPSLATE_TILES.getDefaultState());
			for (int y = top; y < ry; y++) set(w, m, x, y, z, wall);
			if (!edge && hx >= 2 && hx <= 4) set(w, m, x, ry + 1, z, Blocks.DAYLIGHT_DETECTOR.getDefaultState());
		}
	}

	/** Villa quarter: two detached houses per block in big gardens with hedges, one with a pool. */
	private static void villas(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		int v = bu < 14 ? 0 : 1;
		int lu = v == 0 ? bu : bu - 14;      // 0..13 (or 14)
		long hh = hash(bh, v, 4);
		set(w, m, x, G, z, GRASS);
		if (lu == 0 || bw == 0 || bw == 20) {
			if (!(bw == 0 && lu >= 5 && lu <= 7)) set(w, m, x, G + 1, z, Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
			return;
		}
		if (bw <= 4 && lu >= 5 && lu <= 7) {
			set(w, m, x, G, z, Blocks.GRAVEL.getDefaultState());     // drive
			if (bw >= 2 && bw <= 4 && lu == 6 && (hh & 1) == 0) car(w, m, x, z, bw - 1, 0, hh >>> 1);
			return;
		}
		boolean house = lu >= 2 && lu <= 11 && bw >= 5 && bw <= 12;
		if (!house) {
			if ((hh & 2) == 0 && lu >= 4 && lu <= 9 && bw >= 15 && bw <= 18) {
				set(w, m, x, G, z, (lu == 4 || lu == 9 || bw == 15 || bw == 18) ? WALK : Blocks.WATER.getDefaultState());   // pool
				return;
			}
			if ((hash(x, z, 6) % 53) == 0) Countryside.tree(w, m, x, G + 1, z, hash(x, z, 8));
			return;
		}
		int hu = lu - 2, hw = bw - 5;      // 0..9, 0..7
		boolean edge = hu == 0 || hu == 9 || hw == 0 || hw == 7;
		BlockState wall = ((hh >>> 3) & 1) == 0 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.BRICKS.getDefaultState();
		int eave = Y0 + 7;
		set(w, m, x, Y0, z, edge ? wall : Blocks.DARK_OAK_PLANKS.getDefaultState());
		for (int y = Y0 + 1; y < eave; y++) {
			int rel = y - Y0;
			BlockState st = edge ? wall : (rel == 4 ? Blocks.DARK_OAK_PLANKS.getDefaultState() : AIR);
			if (edge && (hu == 2 || hu == 3 || hu == 6 || hu == 7) && (rel == 2 || rel == 3 || rel == 5 || rel == 6)) st = VillageBuilder.paneEW(Blocks.GLASS_PANE);
			if (edge && (hw == 2 || hw == 5) && (rel == 2 || rel == 3 || rel == 6)) st = VillageBuilder.paneNS(Blocks.GLASS_PANE);
			if (edge && hw == 0 && hu == 4 && rel <= 2) st = AIR;
			set(w, m, x, y, z, st);
		}
		if (hw == 0 && hu == 4) VillageBuilder.door(w, m, x, z, Direction.NORTH, Blocks.DARK_OAK_DOOR);
		// hipped roof of dark tiles
		int k = Math.min(Math.min(hu, 9 - hu), Math.min(hw, 7 - hw));
		for (int r = 0; r <= 4; r++) {
			int y = eave + r;
			BlockState st = AIR;
			if (r == k) {
				Direction d = Math.min(hu, 9 - hu) <= Math.min(hw, 7 - hw) ? (hu < 5 ? Direction.EAST : Direction.WEST) : (hw < 4 ? Direction.SOUTH : Direction.NORTH);
				st = r >= 3 ? Blocks.DEEPSLATE_TILES.getDefaultState() : VillageBuilder.stairs(Blocks.DEEPSLATE_TILE_STAIRS, d);
			} else if (r > k && k >= 3 && r == 3) st = Blocks.DEEPSLATE_TILES.getDefaultState();
			set(w, m, x, y, z, st);
		}
	}

	// ------------------------------------------------------------------ shops, schools

	/** A supermarket with its car park. */
	private static void supermarket(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean hall = bu >= 2 && bu <= 22 && bw >= 9 && bw <= 19;
		if (!hall) {
			// car park: bays with white lines, some cars, trolley shelter
			set(w, m, x, G, z, ASPHALT);
			if (bw <= 7 && bu % 3 == 0 && bw != 4) set(w, m, x, G, z, LINE);
			if (bw <= 7 && bw != 4 && bu % 3 != 0) {
				long c = hash(bh, bu / 3, bw / 4);
				if ((c & 3) == 0) car(w, m, x, z, bw % 4, bu % 3, c >>> 2);
			}
			if (bu >= 24 && bw >= 10 && bw <= 13) {
				set(w, m, x, G + 3, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
				if (bu == 24 || bu == 27) for (int y = G + 1; y <= G + 2; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
			}
			return;
		}
		int hu = bu - 2, hw = bw - 9;      // 0..20, 0..10
		boolean edge = hu == 0 || hu == 20 || hw == 0 || hw == 10;
		int top = Y0 + 6;
		BlockState wall = ((bh >>> 9) & 1) == 0 ? Blocks.LIGHT_GRAY_CONCRETE.getDefaultState() : Blocks.BRICKS.getDefaultState();
		BlockState band = ((bh >>> 10) & 1) == 0 ? Blocks.BLUE_CONCRETE.getDefaultState() : Blocks.ORANGE_CONCRETE.getDefaultState();
		set(w, m, x, G, z, WALK);
		set(w, m, x, Y0, z, WALK);
		for (int y = Y0 + 1; y <= top; y++) {
			BlockState st = AIR;
			if (edge) {
				st = wall;
				if (hw == 0 && y <= Y0 + 4 && hu >= 2 && hu <= 18) st = Blocks.GLASS.getDefaultState();     // shop front
				if (hw == 0 && hu >= 9 && hu <= 11 && y <= Y0 + 3) st = AIR;                                // entrance
				if (hw == 0 && y >= top - 1) st = band;                                                       // the shop's colours
			} else if (y == Y0 + 1 && hw >= 3 && hw <= 8 && hu % 4 != 0) {
				st = Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP);                 // shelves
			} else if (y == Y0 + 2 && hw >= 3 && hw <= 8 && hu % 4 != 0 && (hu & 1) == 0) {
				st = Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP);
			} else if (y == top - 1 && hu % 5 == 2 && hw % 4 == 2) st = Blocks.SEA_LANTERN.getDefaultState();
			set(w, m, x, y, z, st);
		}
		set(w, m, x, top + 1, z, edge ? wall : Blocks.GRAY_CONCRETE.getDefaultState());
		if (hw == 0 && hu >= 8 && hu <= 12) {
			int fx = x, fz = z - 1;
			set(w, m, fx, Y0 + 4, fz, Blocks.SMOOTH_STONE_SLAB.getDefaultState());     // canopy over the entrance
		}
	}

	/** A primary school: brick, two floors, big windows, a schoolyard with a climbing frame and bike racks. */
	private static void school(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean wing1 = bu >= 2 && bu <= 26 && bw >= 13 && bw <= 19;
		boolean wing2 = bu >= 2 && bu <= 9 && bw >= 4 && bw <= 13;
		if (!wing1 && !wing2) {
			set(w, m, x, G, z, ASPHALT);
			if (bu >= 14 && bu <= 18 && bw >= 3 && bw <= 7) {
				boolean frame = (bu == 14 || bu == 18) && (bw == 3 || bw == 7);
				if (frame) for (int y = G + 1; y <= G + 4; y++) set(w, m, x, y, z, Blocks.BIRCH_FENCE.getDefaultState());
				if (!frame && (bw == 3 || bw == 7 || bu == 14 || bu == 18)) set(w, m, x, G + 4, z, Blocks.BIRCH_SLAB.getDefaultState());
			}
			if (bw == 1 && bu >= 12 && bu <= 26 && (bu & 1) == 0) set(w, m, x, G + 1, z, Blocks.IRON_BARS.getDefaultState());   // bike racks
			if (bw == 0 || bu == 28) set(w, m, x, G + 1, z, Blocks.IRON_BARS.getDefaultState());
			return;
		}
		boolean edge = wing1 ? (bu == 2 || bu == 26 || bw == 13 || bw == 19) : (bu == 2 || bu == 9 || bw == 4);
		if (wing2 && bw == 13) edge = false;
		int top = Y0 + 9;
		set(w, m, x, G, z, WALK);
		set(w, m, x, Y0, z, edge ? BRICK : Blocks.OAK_PLANKS.getDefaultState());
		for (int y = Y0 + 1; y < top; y++) {
			int rel = y - Y0;
			BlockState st = edge ? BRICK : (rel == 4 ? Blocks.OAK_PLANKS.getDefaultState() : AIR);
			int along = (bw == 13 || bw == 19 || bw == 4) ? bu : bw;
			if (edge && along % 3 != 0 && (rel == 2 || rel == 3 || rel == 6 || rel == 7)) st = (bw == 13 || bw == 19 || bw == 4) ? VillageBuilder.paneEW(Blocks.GLASS_PANE) : VillageBuilder.paneNS(Blocks.GLASS_PANE);
			if (edge && wing1 && bw == 13 && bu == 14 && rel <= 2) st = AIR;
			if (!edge && rel == 3 && bu % 4 == 0 && bw % 3 == 0) st = Blocks.SEA_LANTERN.getDefaultState();
			set(w, m, x, y, z, st);
		}
		set(w, m, x, top, z, edge ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.GRAY_CONCRETE.getDefaultState());
	}

	// ------------------------------------------------------------------ industrial estate

	/** A distribution centre: a big pale box with loading docks, trailers waiting at the docks, a small office. */
	private static void warehouse(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean hall = bu >= 1 && bu <= 27 && bw >= 7 && bw <= 19;
		set(w, m, x, G, z, ASPHALT);
		if (!hall) {
			// the yard: trailers backed up to the docks
			int dock = (bu - 3) % 5;
			if (bu >= 3 && bu <= 25 && (dock == 0 || dock == 1) && bw >= 1 && bw <= 6 && ((hash(bh, bu / 5, 2) & 1) == 0)) {
				BlockState box = Blocks.WHITE_CONCRETE.getDefaultState();
				set(w, m, x, G + 1, z, bw == 1 ? Blocks.BLACK_CONCRETE.getDefaultState() : Blocks.GRAY_CONCRETE.getDefaultState());
				set(w, m, x, G + 2, z, box);
				set(w, m, x, G + 3, z, box);
				if (bw == 1) set(w, m, x, G + 3, z, Blocks.LIGHT_BLUE_CONCRETE.getDefaultState());   // truck cab
			}
			if (bw == 0 && bu % 6 == 0) InfraBuilder.lamp(w, m, x, G, z, Direction.SOUTH, 8);
			return;
		}
		int hu = bu - 1, hw = bw - 7;      // 0..26, 0..12
		boolean edge = hu == 0 || hu == 26 || hw == 0 || hw == 12;
		int top = Y0 + 13;
		BlockState wall = ((bh >>> 7) & 1) == 0 ? Blocks.LIGHT_GRAY_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState();
		BlockState stripe = ((bh >>> 8) & 1) == 0 ? Blocks.BLUE_CONCRETE.getDefaultState() : Blocks.GREEN_CONCRETE.getDefaultState();
		set(w, m, x, Y0, z, Blocks.SMOOTH_STONE.getDefaultState());
		boolean office = hu <= 6;
		for (int y = Y0 + 1; y <= top; y++) {
			int rel = y - Y0;
			BlockState st = AIR;
			if (edge) {
				st = rel == 11 ? stripe : wall;
				if (hw == 0 && !office && (hu - 2) % 5 < 3 && hu >= 2 && rel <= 4) st = Blocks.GRAY_CONCRETE.getDefaultState();   // dock doors
				if (office && rel <= 8 && hu > 0 && hu < 6 && (rel % 4 != 0)) st = Blocks.LIGHT_BLUE_TERRACOTTA.getDefaultState();
			} else if (!office && rel <= 6 && hu % 6 == 3 && hw >= 2 && hw <= 10) {
				st = rel % 3 == 0 ? Blocks.SPRUCE_SLAB.getDefaultState() : AIR;                   // pallet racks
				if (hw % 4 == 1 && rel % 3 != 0) st = Blocks.IRON_BARS.getDefaultState();
			} else if (!office && rel <= 5 && hu % 6 == 3 && hw >= 3 && hw <= 9 && rel % 3 == 1) st = Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP);
			else if (rel == 12 && hu % 6 == 0 && hw % 4 == 2) st = Blocks.SEA_LANTERN.getDefaultState();
			set(w, m, x, y, z, st);
		}
		set(w, m, x, top + 1, z, edge ? wall : Blocks.GRAY_CONCRETE.getDefaultState());
	}

	/** A factory: brick hall with a sawtooth roof, a tall chimney, silos and pipes. */
	private static void factory(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		set(w, m, x, G, z, ASPHALT);
		boolean hall = bu >= 2 && bu <= 19 && bw >= 3 && bw <= 17;
		int cu = 23, cw = 13;
		if (Math.abs(bu - cu) <= 1 && Math.abs(bw - cw) <= 1) {
			// the chimney, red and white at the top
			int top = Y0 + 46;
			for (int y = Y0; y <= top; y++) {
				BlockState st = BRICK;
				if (y > top - 6) st = ((y - top) & 2) == 0 ? Blocks.RED_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState();
				if (bu == cu && bw == cw && y > Y0 + 1) st = AIR;
				set(w, m, x, y, z, st);
			}
			return;
		}
		if (bu >= 22 && bu <= 26 && bw >= 2 && bw <= 8) {
			// two silos
			int su = bu < 24 ? 22 : 25, sw = bw < 5 ? 3 : 6;
			int du = bu - (su + 0), dw = bw - (sw + 0);
			boolean in = Math.abs(du) + Math.abs(dw) <= 1 || (Math.abs(du) <= 1 && Math.abs(dw) <= 1 && (du == 0 || dw == 0));
			if (Math.abs(du) <= 1 && Math.abs(dw) <= 1 && !(Math.abs(du) == 1 && Math.abs(dw) == 1)) {
				for (int y = Y0; y <= Y0 + 20; y++) set(w, m, x, y, z, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
				set(w, m, x, Y0 + 21, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
			}
			return;
		}
		if (!hall) {
			if (bw == 18 && bu >= 2 && bu <= 22) set(w, m, x, Y0 + 6, z, Blocks.IRON_BARS.getDefaultState());   // pipe bridge
			return;
		}
		int hu = bu - 2, hw = bw - 3;      // 0..17, 0..14
		boolean edge = hu == 0 || hu == 17 || hw == 0 || hw == 14;
		int top = Y0 + 9;
		set(w, m, x, Y0, z, Blocks.SMOOTH_STONE.getDefaultState());
		for (int y = Y0 + 1; y <= top; y++) {
			int rel = y - Y0;
			BlockState st = AIR;
			if (edge) {
				st = BRICK;
				if ((hw == 0 || hw == 14) && hu % 3 == 1 && rel >= 3 && rel <= 7) st = VillageBuilder.paneEW(Blocks.GRAY_STAINED_GLASS_PANE);
				if (hw == 0 && hu >= 7 && hu <= 10 && rel <= 5) st = Blocks.GRAY_CONCRETE.getDefaultState();   // big door
			} else if (rel == 1 && hu % 5 == 2 && hw % 3 == 1) st = Blocks.IRON_BLOCK.getDefaultState();     // machines
			else if (rel == 2 && hu % 5 == 2 && hw % 3 == 1) st = Blocks.BLAST_FURNACE.getDefaultState();
			set(w, m, x, y, z, st);
		}
		// sawtooth roof: north lights
		int saw = hw % 4;
		int ry = top + 1 + (saw == 3 ? 0 : saw);
		for (int y = top + 1; y < ry; y++) set(w, m, x, y, z, edge && (hu == 0 || hu == 17) ? BRICK : AIR);
		set(w, m, x, ry, z, saw == 3 ? Blocks.GLASS.getDefaultState() : Blocks.GRAY_CONCRETE.getDefaultState());
		if (saw == 3) for (int y = top + 2; y <= top + 3; y++) set(w, m, x, y, z, Blocks.GLASS.getDefaultState());
	}

	/** Office pavilions of an office park, with a car park. */
	private static void offices(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean b = bu >= 4 && bu <= 24 && bw >= 8 && bw <= 17;
		if (!b) {
			set(w, m, x, G, z, ASPHALT);
			if (bw <= 6 && bu % 3 == 0) set(w, m, x, G, z, LINE);
			if (bw <= 6 && bu % 3 != 0 && (hash(bh, bu / 3, bw / 4) & 3) == 0) car(w, m, x, z, bw % 4, bu % 3, hash(bh, bu, bw));
			if (bw >= 18) set(w, m, x, G, z, GRASS);
			return;
		}
		VillageBuilder.tower(w, m, x, z, bu, bw, bh, 1.6);
	}

	// ------------------------------------------------------------------ the city park

	private static void cityPark(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		long h = hash(x, z, 13);
		// a pond in some blocks
		boolean pond = ((bh >>> 4) % 3) == 0;
		double du = (bu - 14) / 10.0, dw = (bw - 10) / 7.0;
		if (pond && du * du + dw * dw < 1) {
			set(w, m, x, G - 2, z, Blocks.MUD.getDefaultState());
			set(w, m, x, G - 1, z, Blocks.WATER.getDefaultState());
			set(w, m, x, G, z, Blocks.WATER.getDefaultState());
			if ((h % 31) == 0) set(w, m, x, G + 1, z, Blocks.LILY_PAD.getDefaultState());
			return;
		}
		if (bu == 14 || bw == 10 || (pond && du * du + dw * dw < 1.25)) {
			set(w, m, x, G, z, Blocks.GRAVEL.getDefaultState());
			if ((h % 23) == 0) set(w, m, x, G + 1, z, VillageBuilder.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
			return;
		}
		set(w, m, x, G, z, GRASS);
		if ((h % 29) == 0) Countryside.tree(w, m, x, G + 1, z, h >>> 8);
		else if ((h % 7) == 0) set(w, m, x, G + 1, z, Blocks.SHORT_GRASS.getDefaultState());
		else if ((h % 37) == 0) set(w, m, x, G + 1, z, (h & 64) == 0 ? Blocks.POPPY.getDefaultState() : Blocks.DANDELION.getDefaultState());
	}
}
