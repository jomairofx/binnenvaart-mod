package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provincial roads ("N-wegen") between neighbouring villages and towns on the same side of the river, and from the
 * locks to the nearest village: a two-lane asphalt road with a row of trees along it and a separate red cycle path,
 * the most Dutch of all roads. They follow the land (over the dikes, over culverts across the ditches).
 */
public final class Roads {
	public static final double HALF = 3.0;        // half width of the asphalt
	public static final double OUTER = 7.5;       // with verge, trees and cycle path

	/** A straight stretch of road, with the side of its cycle path (+1 / -1). */
	public record Seg(double x0, double z0, double x1, double z1, int bike) {
		/** {signed distance across, distance along} or null when beside the ends. */
		double[] at(double x, double z) {
			double dx = x1 - x0, dz = z1 - z0, l2 = dx * dx + dz * dz;
			double t = ((x - x0) * dx + (z - z0) * dz) / l2;
			if (t < 0 || t > 1) return null;
			double l = Math.sqrt(l2);
			return new double[]{((x - x0) * dz - (z - z0) * dx) / l, t * l};
		}
	}

	private final RiverField f;
	private final ConcurrentHashMap<Long, List<Seg>> segs = new ConcurrentHashMap<>();

	Roads(RiverField f) {
		this.f = f;
	}

	/** The roads that start at the town of this cell (to its east and south neighbours). */
	public List<Seg> from(int cellX, int cellZ) {
		long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
		return segs.computeIfAbsent(key, k -> make(cellX, cellZ));
	}

	private List<Seg> make(int cellX, int cellZ) {
		Villages.Town a = f.villages.town(cellX, cellZ);
		if (a == null) return List.of();
		List<Seg> out = new ArrayList<>(2);
		for (Villages.Town b : f.villages.neighbours(cellX, cellZ)) {
			Seg s = link(a, b);
			if (s != null) out.add(s);
		}
		// and into the nearest big city
		Metros.Metro mt = f.metros.nearest(a.cx(), a.cz(), 1);
		if (mt != null) {
			Seg s = link(a, mt.asTown(), mt.radius() - 12, mt);
			if (s != null) out.add(s);
		}
		// business parks beside the roads, each with its own access road
		List<Park> ps = new ArrayList<>(2);
		int n = out.size();
		for (int i = 0; i < n; i++) {
			Park p = park(out.get(i), out, a.seed() + i * 7919L);
			if (p != null) {
				ps.add(p);
				out.add(p.access());
			}
		}
		parks.put(((long) cellX << 32) ^ (cellZ & 0xffffffffL), ps);
		return out;
	}

	// ------------------------------------------------------------------ business parks ("bedrijventerreinen")

	/**
	 * A business park beside a provincial road: an axis-aligned rectangle (x0..x1, z0..z1) with its main street
	 * along the long side, reached by its own access road. type 0 = mixed companies, 1 = a data centre campus.
	 */
	public record Park(int x0, int z0, int x1, int z1, boolean alongX, long seed, int type, Seg access) {
		public boolean contains(double x, double z, double g) {
			return x >= x0 - g && x <= x1 + g && z >= z0 - g && z <= z1 + g;
		}

		public double outside(double x, double z) {
			double dx = Math.max(0, Math.max(x0 - x, x - x1)), dz = Math.max(0, Math.max(z0 - z, z - z1));
			return Math.hypot(dx, dz);
		}
	}

	private final ConcurrentHashMap<Long, List<Park>> parks = new ConcurrentHashMap<>();

	private Park park(Seg s, List<Seg> others, long seed) {
		long h = Harbours.mix(seed ^ 0xB051E55L);
		if (Math.floorMod(h, 100L) >= 40) return null;               // two in five roads
		double len = Math.hypot(s.x1() - s.x0(), s.z1() - s.z0());
		if (len < 700) return null;
		double ux = (s.x1() - s.x0()) / len, uz = (s.z1() - s.z0()) / len;
		double nx = uz, nz = -ux;                                      // normal (same sign as Seg.at's c)
		int side = ((h >>> 8) & 1) == 0 ? 1 : -1;
		int type = Math.floorMod(h >>> 12, 4L) == 0 ? 1 : 0;
		int L = type == 1 ? 260 + (int) Math.floorMod(h >>> 20, 120L) : 220 + (int) Math.floorMod(h >>> 20, 160L);
		int D = type == 1 ? 180 : 150 + (int) Math.floorMod(h >>> 30, 40L);
		boolean alongX = Math.abs(ux) >= Math.abs(uz);
		int lx = alongX ? L : D, lz = alongX ? D : L;
		double t = len * (0.35 + 0.3 * ((h >>> 40) & 1023) / 1023.0);
		double px = s.x0() + ux * t, pz = s.z0() + uz * t;
		// push the rectangle off the road until all of it is beside it
		double off = 20;
		for (int it = 0; it < 40; it++) {
			double cx = px + nx * side * (off + Math.max(lx, lz) / 2.0), cz = pz + nz * side * (off + Math.max(lx, lz) / 2.0);
			int x0 = (int) Math.floor(cx - lx / 2.0), z0 = (int) Math.floor(cz - lz / 2.0);
			double minC = Double.MAX_VALUE;
			for (int[] c : new int[][]{{x0, z0}, {x0 + lx, z0}, {x0, z0 + lz}, {x0 + lx, z0 + lz}}) {
				double[] at = s.at(c[0], c[1]);
				double cc = at == null ? 1e9 : at[0] * side;
				minC = Math.min(minC, cc);
			}
			if (minC < OUTER + 8) {
				off += 8;
				continue;
			}
			// the land under it: polder, no river or dike, no town, harbour, motorway, railway, lock or other road
			RiverField.Kind k = f.great;
			for (int ix = x0 - 20; ix <= x0 + lx + 20; ix += 20)
				for (int iz = z0 - 20; iz <= z0 + lz + 20; iz += 20) {
					if (k.distance(ix, iz) - k.halfWidth(ix, iz) < Lowlands.foreland(k) + 90) return null;
					if (f.coastal(ix, iz) < Lowlands.COAST + 0.08) return null;
					if (f.villages.site(ix, iz) != null || f.metros.near(ix, iz, 60) || f.harbours.siteNear(ix, iz, 40) != null) return null;
					if (f.infra.motorwayDistance(ix, iz) < Infra.EDGE + 60 || f.rails.distance(ix, iz) < Rails.HALF + 40) return null;
					if (f.infra.locks.near(ix, iz, 40)) return null;
					for (Seg o : others) {
						if (o == s) continue;
						double[] at = o.at(ix, iz);
						if (at != null && Math.abs(at[0]) < OUTER + 20) return null;
					}
				}
			// the access road: from the provincial road straight to the middle of the park's main street
			double mx = alongX ? x0 + lx / 2.0 : x0 + lx / 2.0, mz = alongX ? z0 + lz / 2.0 : z0 + lz / 2.0;
			double[] foot = s.at(mx, mz);
			if (foot == null) return null;
			double fx = s.x0() + ux * foot[1], fz = s.z0() + uz * foot[1];
			Seg acc = new Seg(fx + (mx - fx) * 0.0, fz + (mz - fz) * 0.0, mx, mz, -s.bike());
			return new Park(x0, z0, x0 + lx, z0 + lz, alongX, h, type, acc);
		}
		return null;
	}

	/** The business park covering this point (grown by g), or null. */
	public Park parkAt(double x, double z, double g) {
		int cx = Math.floorDiv((int) Math.floor(x), Villages.CELL), cz = Math.floorDiv((int) Math.floor(z), Villages.CELL);
		for (int i = -2; i <= 2; i++)
			for (int j = -2; j <= 2; j++) {
				from(cx + i, cz + j);
				List<Park> ps = parks.get(((long) (cx + i) << 32) ^ ((cz + j) & 0xffffffffL));
				if (ps == null) continue;
				for (Park p : ps) if (p.contains(x, z, g)) return p;
			}
		return null;
	}

	/** Nearest business park to a point (for /findriver and the tests): {x, z, type}, or null. */
	public int[] nearestPark(int x, int z, int cells, int type) {
		int cx = Math.floorDiv(x, Villages.CELL), cz = Math.floorDiv(z, Villages.CELL);
		int[] best = null;
		double bd = Double.MAX_VALUE;
		for (int i = -cells; i <= cells; i++)
			for (int j = -cells; j <= cells; j++) {
				from(cx + i, cz + j);
				List<Park> ps = parks.get(((long) (cx + i) << 32) ^ ((cz + j) & 0xffffffffL));
				if (ps == null) continue;
				for (Park p : ps) {
					if (type >= 0 && p.type() != type) continue;
					int px = (p.x0() + p.x1()) / 2, pz = (p.z0() + p.z1()) / 2;
					double d = Math.hypot(px - x, pz - z);
					if (d < bd) {
						bd = d;
						best = new int[]{px, pz, p.type()};
					}
				}
			}
		return best;
	}

	private Seg link(Villages.Town a, Villages.Town b) {
		return link(a, b, b.radius() - 8, null);
	}

	private Seg link(Villages.Town a, Villages.Town b, double rb, Metros.Metro target) {
		double d = Math.hypot(b.cx() - a.cx(), b.cz() - a.cz());
		if (d - rb > 2600 || d < a.radius() + rb + 60) return null;
		double ux = (b.cx() - a.cx()) / d, uz = (b.cz() - a.cz()) / d;
		double x0 = a.cx() + ux * (a.radius() - 8), z0 = a.cz() + uz * (a.radius() - 8);
		double x1 = b.cx() - ux * rb, z1 = b.cz() - uz * rb;
		double len = Math.hypot(x1 - x0, z1 - z0);
		RiverField.Kind k = f.great;
		for (double s = 0; s <= len; s += 10) {
			double qx = x0 + (x1 - x0) * s / len, qz = z0 + (z1 - z0) * s / len;
			int ix = (int) Math.floor(qx), iz = (int) Math.floor(qz);
			if (k.distance(qx, qz) - k.halfWidth(qx, qz) < 14) return null;                 // never across the river
			if (inDike(f, ix, iz, qx, qz)) return null;                                      // nor through a dike
			if (f.infra.motorwayDistance(qx, qz) < Infra.EDGE + OUTER + 4) return null;     // nor across a motorway
			if (f.harbours.siteAt(ix, iz) != null) return null;
			if (f.coastal(ix, iz) < Lowlands.COAST + 0.07) return null;
			Villages.Town o = f.villages.at(ix, iz);
			if (o != null && !same(o, a) && !same(o, b)) return null;
			if (f.infra.locks.near(ix, iz, 10)) return null;
			Metros.Metro mo = f.metros.at(ix, iz);
			if (mo != null && mo != target) return null;
		}
		int bike = (Harbours.mix(a.seed() ^ b.seed()) & 1) == 0 ? 1 : -1;
		return new Seg(x0, z0, x1, z1, bike);
	}

	private static boolean same(Villages.Town p, Villages.Town q) {
		return p.cx() == q.cx() && p.cz() == q.cz();
	}

	private final ConcurrentHashMap<Long, List<Seg>> byCell = new ConcurrentHashMap<>();

	/** All road stretches that pass through (or near) this cell, from the towns up to four cells away. */
	private List<Seg> cell(int cx, int cz) {
		long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
		return byCell.computeIfAbsent(key, k -> {
			List<Seg> out = new ArrayList<>();
			double x0 = cx * (double) Villages.CELL - 20, z0 = cz * (double) Villages.CELL - 20;
			double x1 = x0 + Villages.CELL + 40, z1 = z0 + Villages.CELL + 40;
			List<Seg> all = new ArrayList<>();
			for (int i = -4; i <= 4; i++)
				for (int j = -4; j <= 4; j++) all.addAll(from(cx + i, cz + j));
			// and the roads from the motorway junctions
			all.addAll(f.infra.junctions.roadsNear(x0, z0, x1, z1));
			for (Seg s : all) {
				if (Math.max(s.x0(), s.x1()) < x0 || Math.min(s.x0(), s.x1()) > x1 || Math.max(s.z0(), s.z1()) < z0 || Math.min(s.z0(), s.z1()) > z1) continue;
				out.add(s);
			}
			return out;
		});
	}

	/** Nearest road stretch within r of this point: {c, along} with the stretch, or null. */
	public Object[] hit(double x, double z, double r) {
		int cx = Math.floorDiv((int) Math.floor(x), Villages.CELL), cz = Math.floorDiv((int) Math.floor(z), Villages.CELL);
		Object[] best = null;
		double bd = r;
		for (Seg s : cell(cx, cz)) {
			double[] p = s.at(x, z);
			if (p == null || Math.abs(p[0]) > bd) continue;
			bd = Math.abs(p[0]);
			best = new Object[]{p, s};
		}
		return best;
	}

	public boolean near(int x, int z, double r) {
		return hit(x + 0.5, z + 0.5, OUTER + 17 + r) != null || parkAt(x + 0.5, z + 0.5, r + 6) != null;
	}

	/** No ditches or lakes under the road and the cycle path. */
	public double shape(int x, int z, double t) {
		if (t >= Lowlands.POLDER) return t;
		return hit(x + 0.5, z + 0.5, OUTER + 1) != null ? Lowlands.POLDER : t;
	}

	// ------------------------------------------------------------------ petrol stations

	public static double stationAtPublic(Seg s) {
		return stationAt(s);
	}

	/** Where on a stretch of road its petrol station stands (distance along), or -1 when it has none. */
	static double stationAt(Seg s) {
		double len = Math.hypot(s.x1() - s.x0(), s.z1() - s.z0());
		if (len < 520) return -1;
		long h = Harbours.mix(Double.doubleToLongBits(s.x0()) * 31 + Double.doubleToLongBits(s.z1()));
		if ((h & 1) == 0) return -1;
		return len * (0.35 + 0.3 * ((h >>> 8) & 1023) / 1023.0);
	}

	/**
	 * A petrol station beside the road (on the side without the cycle path): forecourt with pumps under a canopy,
	 * a small shop, a price sign. c, along = position relative to the road. Returns true if this column is part of it.
	 */
	static boolean station(StructureWorldAccess w, BlockPos.Mutable m, Seg s, double c, double along, int x, int z) {
		double at = stationAt(s);
		if (at < 0) return false;
		double da = along - at;
		int side = -s.bike();
		if (Math.abs(da) > 13.5 || (c >= 0 ? 1 : -1) != side) return false;
		double dc = Math.abs(c) - (OUTER + 1);
		if (dc < 0 || dc >= 16) return false;
		int ia = (int) Math.floor(da), ic = (int) Math.floor(dc);
		int top = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
		int g = Math.max(Math.min(top, 70), RiverField.WATER_TOP);
		for (int y = g - 3; y < g; y++) {
			BlockState b = w.getBlockState(m.set(x, y, z));
			if (b.isAir() || !b.getFluidState().isEmpty()) w.setBlockState(m, Blocks.DIRT.getDefaultState(), 2);
		}
		for (int y = g + 1; y <= g + 14; y++) {
			BlockState b = w.getBlockState(m.set(x, y, z));
			if (!b.isAir() && (b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || b.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || b.isReplaceable()
					|| b.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS))) w.setBlockState(m, Blocks.AIR.getDefaultState(), 2);
		}
		BlockState asph = ASPHALT, white = Blocks.WHITE_CONCRETE.getDefaultState(), red = Blocks.RED_CONCRETE.getDefaultState();
		w.setBlockState(m.set(x, g, z), asph, 2);
		boolean shop = ia >= 6 && ia <= 13 && ic >= 3 && ic <= 12;
		if (shop) {
			boolean edge = ia == 6 || ia == 13 || ic == 3 || ic == 12;
			w.setBlockState(m.set(x, g, z), Blocks.SMOOTH_STONE.getDefaultState(), 2);
			for (int y = g + 1; y <= g + 4; y++) {
				BlockState st = Blocks.AIR.getDefaultState();
				if (edge) {
					st = white;
					if (ia == 6 && y <= g + 3 && ic >= 5 && ic <= 10) st = Blocks.GLASS.getDefaultState();
					if (ia == 6 && ic == 7 && y <= g + 2) st = Blocks.AIR.getDefaultState();
					if (y == g + 4) st = red;
				} else if (y == g + 1 && (ic == 5 || ic == 10) && ia >= 8 && ia <= 11) st = Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP);
				else if (y == g + 3 && ic == 7 && ia == 10) st = Blocks.SEA_LANTERN.getDefaultState();
				w.setBlockState(m.set(x, y, z), st, 2);
			}
			w.setBlockState(m.set(x, g + 5, z), edge ? white : Blocks.GRAY_CONCRETE.getDefaultState(), 2);
			return true;
		}
		// forecourt: pumps under a canopy on pillars
		boolean canopy = ia >= -10 && ia <= 4 && ic >= 3 && ic <= 10;
		if (canopy) {
			boolean pillar = (ia == -7 || ia == 1) && (ic == 5 || ic == 8);
			boolean pump = (ia == -8 || ia == -6 || ia == 0 || ia == 2) && (ic == 5 || ic == 8);
			if (pillar) for (int y = g + 1; y <= g + 5; y++) w.setBlockState(m.set(x, y, z), white, 2);
			if (pump) {
				w.setBlockState(m.set(x, g + 1, z), Blocks.SMOOTH_STONE_SLAB.getDefaultState(), 2);
				w.setBlockState(m.set(x, g + 2, z), Blocks.WHITE_CONCRETE.getDefaultState(), 2);
			}
			boolean rim = ia == -10 || ia == 4 || ic == 3 || ic == 10;
			w.setBlockState(m.set(x, g + 6, z), rim ? red : white, 2);
			if (!rim && ((ia + 10) % 4 == 2) && (ic == 6)) w.setBlockState(m.set(x, g + 5, z), Blocks.LIGHT.getDefaultState().with(Properties.LEVEL_15, 15), 2);
			if (ic == 6 || ic == 7) w.setBlockState(m.set(x, g, z), Blocks.SMOOTH_STONE.getDefaultState(), 2);
			return true;
		}
		// the price sign at the entrance
		if (ia == -13 && ic == 1) {
			for (int y = g + 1; y <= g + 6; y++) w.setBlockState(m.set(x, y, z), Blocks.IRON_BARS.getDefaultState(), 2);
			w.setBlockState(m.set(x, g + 7, z), white, 2);
			w.setBlockState(m.set(x, g + 8, z), red, 2);
			w.setBlockState(m.set(x, g + 9, z), white, 2);
			return true;
		}
		if (ic >= 13) w.setBlockState(m.set(x, g, z), Blocks.GRASS_BLOCK.getDefaultState(), 2);
		if (ic == 15) w.setBlockState(m.set(x, g + 1, z), Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true), 2);
		return true;
	}

	// ------------------------------------------------------------------ blocks

	private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState LINE = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState CYCLE = Blocks.RED_TERRACOTTA.getDefaultState();

	/** Builds one column of a provincial road (also used for the lock roads). Returns true if it did. */
	static boolean build(StructureWorldAccess w, BlockPos.Mutable m, Seg s, double c, double along, int x, int z) {
		double a = Math.abs(c);
		if (a > OUTER + 1) return false;
		int top = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
		if (top < RiverField.WATER_TOP - 1) return false;
		BlockState ground = w.getBlockState(m.set(x, top, z));
		if (!ground.getFluidState().isEmpty()) return false;
		// no trees, grass or flowers on the road
		for (int y = top + 1; y <= top + 14; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && st.getFluidState().isEmpty() && (st.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
					|| st.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || st.isReplaceable() || st.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)
					|| st.isOf(Blocks.SUGAR_CANE) || st.isIn(net.minecraft.registry.tag.BlockTags.SAPLINGS)))
				w.setBlockState(m, Blocks.AIR.getDefaultState(), 2);
		}
		double dx = s.x1() - s.x0(), dz = s.z1() - s.z0(), l = Math.hypot(dx, dz);
		double lw = (Math.abs(dx) + Math.abs(dz)) / l;      // 1 for a road along x or z, 1.41 diagonal
		boolean straight = lw < 1.08;                        // only then the lines can be drawn cleanly
		int side = c >= 0 ? 1 : -1;
		long al = (long) Math.floor(along);
		if (a < HALF) {
			BlockState st = ASPHALT;
			if (straight && a >= HALF - 1) st = LINE;                                        // edge lines
			if (straight && a < 0.5 && Math.floorMod(al, 9L) < 3) st = LINE;                // centre line
			w.setBlockState(m.set(x, top, z), st, 2);
			return true;
		}
		// a bus stop now and then on the side without the cycle path: shelter, bench, the stop sign
		long bs = Math.floorMod(al, 900L);
		if (side != s.bike() && a >= HALF && a < HALF + 3 && bs >= 448 && bs <= 456) {
			boolean back = a >= HALF + 2;
			w.setBlockState(m.set(x, top, z), Blocks.SMOOTH_STONE.getDefaultState(), 2);
			if (bs == 448 && !back && a < HALF + 1) {
				for (int y = top + 1; y <= top + 3; y++) w.setBlockState(m.set(x, y, z), Blocks.IRON_BARS.getDefaultState(), 2);
				w.setBlockState(m.set(x, top + 4, z), Blocks.YELLOW_CONCRETE.getDefaultState(), 2);
			} else if (bs >= 451 && bs <= 455) {
				if (back) for (int y = top + 1; y <= top + 3; y++)
					w.setBlockState(m.set(x, y, z), (Math.abs(dx) > Math.abs(dz) ? VillageBuilder.paneEW(Blocks.GLASS_PANE) : VillageBuilder.paneNS(Blocks.GLASS_PANE)), 2);
				else if (a >= HALF + 1 && bs >= 452 && bs <= 454) w.setBlockState(m.set(x, top + 1, z), Blocks.SPRUCE_SLAB.getDefaultState(), 2);
				if (a >= HALF + 1) w.setBlockState(m.set(x, top + 4, z), Blocks.SMOOTH_STONE_SLAB.getDefaultState(), 2);
			}
			return true;
		}
		if (a >= HALF + 0.6 && a < HALF + 1.6 && side != s.bike() && Math.floorMod(al, 11L) == 0) {
			// a row of trees on the side without the cycle path
			Countryside.tree(w, m, x, top + 1, z, Harbours.mix(x * 31L + z));
			return true;
		}
		if (side == s.bike()) {
			if (a >= HALF + 1.5 && a < HALF + 3.5) {
				w.setBlockState(m.set(x, top, z), CYCLE, 2);
				return true;
			}
			if (a >= HALF + 0.4 && a < HALF + 1.4 && Math.floorMod(al, 11L) == 5) {
				// trees between the road and the cycle path
				Countryside.tree(w, m, x, top + 1, z, Harbours.mix(x * 17L + z * 5));
				return true;
			}
		}
		return a < OUTER;
	}

	/**
	 * Would a road here cut through the river dike (or run on the foreland)? Not inside a river town or a city,
	 * where the land is level with the quays anyway.
	 */
	static boolean inDike(RiverField f, int ix, int iz, double qx, double qz) {
		RiverField.Kind k = f.great;
		if (k.distance(qx, qz) - k.halfWidth(qx, qz) >= Lowlands.foreland(k) + 40) return false;
		Villages.Town t = f.villages.site(ix, iz);
		if (t != null && !t.dike()) return false;
		return f.metros.site(ix, iz) == null;
	}
}
