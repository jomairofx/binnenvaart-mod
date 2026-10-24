package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motorway junctions ("aansluitingen"), the Dutch diamond: a local road crosses over the motorway on a viaduct, and
 * four slip roads lead off and on, rising from the motorway to the level of the viaduct. From the junction a
 * provincial road leads to the nearest village or town on each side, or a city road into a big city. Blue signs
 * over the motorway announce the exit.
 *
 * Local frame: c = across the motorway (as {@link Infra} measures it), u = along it, du = u - u0.
 */
public final class Junctions {
	public static final int SPACING = 4600;            // candidate junctions along a motorway
	public static final double CROSS = 3.5;           // half width of the asphalt of the crossing road
	public static final int RISE = 7;                 // the viaduct lies this much above the motorway
	public static final double SLIP0 = Infra.EDGE + 3, SLIP_W = 6;   // slip roads: inner edge, width
	public static final double SLIP_LEN = 150, TAPER = 30;
	public static final double KNOT = Infra.EDGE + 10; // where the slip roads meet the crossing road
	public static final double RAMPC = 110;            // the crossing road comes down to the land over this length
	public static final double REACH = KNOT + RAMPC + 2;

	/** A junction on motorway k of a family, at u0; hm = road height of the motorway; roads[0] on the - side, [1] on the + side. */
	public record Junction(int fam, int k, double u0, int hm, Roads.Seg[] roads) {
		public boolean side(int s) {
			return roads[s > 0 ? 1 : 0] != null;
		}
	}

	private static final Junction NONE = new Junction(0, 0, 0, 0, new Roads.Seg[2]);
	private final RiverField f;
	private final Infra infra;
	private final ConcurrentHashMap<Long, Junction> cache = new ConcurrentHashMap<>();

	Junctions(RiverField f, Infra infra) {
		this.f = f;
		this.infra = infra;
	}

	private double phase(int fam, int k) {
		return Math.floorMod(Harbours.mix(fam * 977L + k * 0x9E3779B97F4A7C15L + 1234), (long) SPACING);
	}

	public Junction junction(int fam, int k, long n) {
		long key = ((long) fam << 62) ^ ((long) (k & 0xfffff) << 40) ^ (n & 0xffffffffffL);
		Junction j = cache.computeIfAbsent(key, kk -> make(fam, k, n));
		return j == NONE ? null : j;
	}

	/** World point at (c, u) of a motorway. */
	public double[] point(int fam, int k, double u, double c) {
		double s = infra.slope(fam, k, u), l = Math.sqrt(1 + s * s);
		double[] p = infra.centre(fam, k, u);
		if (fam == 0) return new double[]{p[0] + c / l, p[1] - c * s / l};
		return new double[]{p[0] - c * s / l, p[1] + c / l};
	}

	private Junction make(int fam, int k, long n) {
		double u0 = n * SPACING + phase(fam, k);
		Infra.Hit h0 = infra.prof(fam, k, u0);
		if (h0 == null || h0.kind != Infra.ROAD) return NONE;
		for (double u = u0 - 420; u <= u0 + 420; u += 20) {
			Infra.Hit h = infra.prof(fam, k, u);
			if (h == null || h.kind != Infra.ROAD || Math.abs(h.y - h0.y) > 1) return NONE;
		}
		// the whole junction on free land
		for (double du = -SLIP_LEN - 10; du <= SLIP_LEN + 10; du += 20)
			for (double c = -REACH - 8; c <= REACH + 8; c += 14) {
				if (Math.abs(du) > 20 && Math.abs(c) > KNOT + 12) continue;      // only the cross road reaches out that far
				double[] p = point(fam, k, u0 + du, c);
				int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
				if (!free(x, z)) return NONE;
			}
		// roads to the nearest village, town or city on each side
		Roads.Seg[] roads = new Roads.Seg[2];
		for (int s = -1; s <= 1; s += 2) {
			double[] start = point(fam, k, u0, s * (REACH - 1));
			roads[s > 0 ? 1 : 0] = target(start[0], start[1], fam, k, u0, s);
		}
		if (roads[0] == null && roads[1] == null) return NONE;
		return new Junction(fam, k, u0, h0.y, roads);
	}

	private boolean free(int x, int z) {
		if (f.coastal(x, z) < Lowlands.COAST + 0.08) return false;
		RiverField.Kind kr = f.great;
		if (kr.distance(x, z) - kr.halfWidth(x, z) < Lowlands.foreland(kr) + 50) return false;
		if (f.villages.at(x, z) != null || f.harbours.siteAt(x, z) != null || f.metros.near(x, z, 10)) return false;
		if (f.rails.near(x, z, 6)) return false;
		return !infra.locks.near(x, z, 10);
	}

	/** A road from the end of the junction to the nearest village, town or big city on side s. */
	private Roads.Seg target(double sx, double sz, int fam, int k, double u0, int s) {
		List<Object[]> cands = new ArrayList<>();
		int vcx = Math.floorDiv((int) sx, Villages.CELL), vcz = Math.floorDiv((int) sz, Villages.CELL);
		for (int i = -3; i <= 3; i++)
			for (int j = -3; j <= 3; j++) {
				Villages.Town t = f.villages.town(vcx + i, vcz + j);
				if (t != null) cands.add(new Object[]{t, t.radius() - 8, null});
			}
		Metros.Metro mt = f.metros.nearest((int) sx, (int) sz, 1);
		if (mt != null) cands.add(new Object[]{mt.asTown(), mt.radius() - 12, mt});
		cands.sort((a, b) -> Double.compare(edge(a, sx, sz), edge(b, sx, sz)));
		for (Object[] c : cands) {
			Villages.Town t = (Villages.Town) c[0];
			double rb = (double) c[1];
			double d = Math.hypot(t.cx() - sx, t.cz() - sz);
			if (d - rb > 3200 || d - rb < 60) continue;
			// on this side of the motorway
			double[] nl = infra.nearestLine(fam, t.cx(), t.cz());
			if ((int) nl[0] != k || Math.signum(nl[1]) != s) continue;
			double ux = (t.cx() - sx) / d, uz = (t.cz() - sz) / d;
			double ex = t.cx() - ux * rb, ez = t.cz() - uz * rb;
			if (pathOk(sx, sz, ex, ez, t, (Metros.Metro) c[2])) return new Roads.Seg(sx, sz, ex, ez, s);
		}
		return null;
	}

	private static double edge(Object[] c, double x, double z) {
		Villages.Town t = (Villages.Town) c[0];
		return Math.hypot(t.cx() - x, t.cz() - z) - (double) c[1];
	}

	private boolean pathOk(double x0, double z0, double x1, double z1, Villages.Town b, Metros.Metro target) {
		double len = Math.hypot(x1 - x0, z1 - z0);
		RiverField.Kind kr = f.great;
		for (double s = 10; s <= len; s += 10) {
			double qx = x0 + (x1 - x0) * s / len, qz = z0 + (z1 - z0) * s / len;
			int ix = (int) Math.floor(qx), iz = (int) Math.floor(qz);
			if (kr.distance(qx, qz) - kr.halfWidth(qx, qz) < 14) return false;
			if (Roads.inDike(f, ix, iz, qx, qz)) return false;
			if (infra.motorwayDistance(qx, qz) < REACH - 2) return false;
			if (f.harbours.siteAt(ix, iz) != null) return false;
			if (f.coastal(ix, iz) < Lowlands.COAST + 0.07) return false;
			Villages.Town o = f.villages.at(ix, iz);
			if (o != null && !(o.cx() == b.cx() && o.cz() == b.cz())) return false;
			if (infra.locks.near(ix, iz, 10)) return false;
			Metros.Metro mo = f.metros.at(ix, iz);
			if (mo != null && mo != target) return false;
		}
		return true;
	}

	/** The junction (with the column's c and du) whose area covers this column, or null. */
	public Object[] at(double x, double z) {
		for (int fam = 0; fam < 2; fam++) {
			double[] nl = infra.nearestLine(fam, x, z);
			double c = nl[1];
			if (Math.abs(c) > REACH + 10) continue;
			int k = (int) nl[0];
			double u = nl[2];
			long n = Math.round((u - phase(fam, k)) / SPACING);
			double u0 = n * SPACING + phase(fam, k);
			double du = u - u0;
			if (Math.abs(du) > 340) continue;
			Junction j = junction(fam, k, n);
			if (j == null) continue;
			return new Object[]{j, c, du};
		}
		return null;
	}

	/** All provincial roads that start at junctions near this cell (for {@link Roads}). */
	public List<Roads.Seg> roadsNear(double x0, double z0, double x1, double z1) {
		List<Roads.Seg> out = new ArrayList<>();
		for (int fam = 0; fam < 2; fam++) {
			double pa = fam == 0 ? x0 : z0, pb = fam == 0 ? x1 : z1, ua = fam == 0 ? z0 : x0, ub = fam == 0 ? z1 : x1;
			int ka = (int) Math.floor((pa - 3800 - infra.offset(fam)) / Infra.MW), kb = (int) Math.ceil((pb + 3800 - infra.offset(fam)) / Infra.MW);
			for (int k = ka; k <= kb; k++) {
				long na = (long) Math.floor((ua - 3800 - phase(fam, k)) / SPACING), nb = (long) Math.ceil((ub + 3800 - phase(fam, k)) / SPACING);
				for (long n = na; n <= nb; n++) {
					Junction j = junction(fam, k, n);
					if (j == null) continue;
					for (Roads.Seg s : j.roads()) if (s != null) out.add(s);
				}
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ heights

	/** Height of a slip road at du from the middle of the junction. */
	static int slipY(Junction j, double d) {
		double f = Math.max(0, Math.min(1, (SLIP_LEN - d) / (SLIP_LEN - 20)));
		return j.hm() + (int) Math.round(RISE * f);
	}

	/** Inner edge of the slip road at d (it tapers into the hard shoulder at the far end). */
	static double slipOff(double d) {
		if (d <= SLIP_LEN - TAPER) return SLIP0;
		return SLIP0 - (SLIP0 - Infra.EDGE + 0.5) * Math.min(1, (d - (SLIP_LEN - TAPER)) / TAPER);
	}

	/** Height of the crossing road at |c|. */
	static double crossY(Junction j, double a) {
		if (a <= KNOT) return j.hm() + RISE;
		return j.hm() + RISE * Math.max(0, 1 - (a - KNOT) / RAMPC);
	}

	/** Shapes the land for the junctions: slip road and cross road embankments. */
	public double shape(int x, int z, double t) {
		Object[] o = at(x + 0.5, z + 0.5);
		if (o == null) return t;
		Junction j = (Junction) o[0];
		double c = (double) o[1], du = (double) o[2], a = Math.abs(c), d = Math.abs(du);
		int s = c >= 0 ? 1 : -1;
		double lo = Double.NEGATIVE_INFINITY;
		// the crossing road (not over the motorway itself: there it is a viaduct)
		boolean crossHere = a <= KNOT + 1 || (j.side(s) && a <= REACH);
		if (crossHere && a >= Infra.EDGE + 0.5) {
			double top = Math.floor(crossY(j, a) + 0.5) + 0.3;
			double off = Math.max(0, d - (CROSS + 1));
			lo = Math.max(lo, top - off / 1.5);
			if (d <= CROSS + 1) t = top;
		}
		// the slip roads
		if (d <= SLIP_LEN + 4) {
			double in = slipOff(d), out = in + SLIP_W;
			double top = slipY(j, d) + 0.3;
			if (a >= in && a < out) {
				if (d > CROSS + 1) t = top;
			} else if (a >= Infra.EDGE + 0.5) {
				double dist = a < in ? in - a : a - out;
				lo = Math.max(lo, top - dist / 1.5);
			}
		}
		if (t < lo) t = lo;
		return t;
	}

	// ------------------------------------------------------------------ blocks

	private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState LINE = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState CONCRETE = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private static void clear(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z) {
		for (int yy = y + 1; yy <= y + 12; yy++) {
			BlockState st = w.getBlockState(m.set(x, yy, z));
			if (!st.isAir() && st.getFluidState().isEmpty() && (st.isReplaceable() || st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS)
					|| st.isIn(BlockTags.FLOWERS) || st.isOf(Blocks.SUGAR_CANE))) set(w, m, x, yy, z, Blocks.AIR.getDefaultState());
		}
	}

	/** Builds this column of a junction. Returns true if it built something. */
	static boolean build(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		Object[] o = f.infra.junctions.at(x + 0.5, z + 0.5);
		if (o == null) return false;
		Junction j = (Junction) o[0];
		double c = (double) o[1], du = (double) o[2], a = Math.abs(c), d = Math.abs(du);
		int s = c >= 0 ? 1 : -1;
		boolean any = false;
		// blue signs over the motorway before the exit (traffic keeps right: on the + side it drives towards -u)
		double signU = -s * 300;
		if (Math.abs(du - signU) < 0.5 && a < Infra.EDGE) {
			int hm = j.hm();
			if (a >= Infra.HALF - 0.5) for (int y = hm + 1; y <= hm + 8; y++) set(w, m, x, y, z, Blocks.GRAY_CONCRETE.getDefaultState());
			else if (a >= 1.5) {
				set(w, m, x, hm + 8, z, Blocks.GRAY_CONCRETE.getDefaultState());
				set(w, m, x, hm + 7, z, Blocks.BLUE_CONCRETE.getDefaultState());
				set(w, m, x, hm + 6, z, a > 4 && a < 9 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.BLUE_CONCRETE.getDefaultState());
				set(w, m, x, hm + 5, z, Blocks.BLUE_CONCRETE.getDefaultState());
			}
			any = true;
		}
		boolean crossHere = a <= KNOT + 1 || (j.side(s) && a <= REACH);
		if (crossHere && d < CROSS + 1) {
			int y = (int) Math.floor(crossY(j, a) + 0.5);
			boolean edge = d >= CROSS;
			if (a < Infra.EDGE + 0.5) {
				// the viaduct over the motorway
				set(w, m, x, y - 1, z, CONCRETE);
				set(w, m, x, y - 2, z, Blocks.SMOOTH_STONE.getDefaultState());
				set(w, m, x, y, z, edge ? CONCRETE : (d >= CROSS - 1 ? LINE : ASPHALT));
				if (edge) set(w, m, x, y + 1, z, CONCRETE);
				if (a < 1 && d < CROSS - 0.5) for (int yy = j.hm() + 2; yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
				return true;
			}
			if (a < Infra.EDGE + 2.5) {
				// abutments
				for (int yy = j.hm() - 1; yy < y; yy++) set(w, m, x, yy, z, CONCRETE);
			}
			clear(w, m, x, y, z);
			BlockState st = edge ? Blocks.GRASS_BLOCK.getDefaultState() : (d >= CROSS - 1 ? LINE : ASPHALT);
			if (!edge && d < 0.5 && a > KNOT + 1 && Math.floorMod((long) Math.floor(a), 9L) < 3) st = LINE;
			set(w, m, x, y, z, st);
			BlockState below = w.getBlockState(m.set(x, y - 1, z));
			if (below.isAir() || !below.getFluidState().isEmpty()) set(w, m, x, y - 1, z, Blocks.DIRT.getDefaultState());
			if (edge && Math.floorMod((long) Math.floor(a), 20L) == 0 && a > Infra.EDGE + 3)
				InfraBuilder.lamp(w, m, x, y, z, cardinalToward(f, j, x, z, du), 6);
			return true;
		}
		if (d <= SLIP_LEN + 2 && d > CROSS + 1) {
			double in = slipOff(d), out = in + SLIP_W;
			if (a >= in && a < out) {
				int y = slipY(j, d);
				clear(w, m, x, y, z);
				BlockState st = (a >= out - 1) ? LINE : ASPHALT;
				if (a < in + 1 && d < SLIP_LEN - TAPER) st = LINE;
				set(w, m, x, y, z, st);
				BlockState below = w.getBlockState(m.set(x, y - 1, z));
				if (below.isAir() || !below.getFluidState().isEmpty()) set(w, m, x, y - 1, z, Blocks.DIRT.getDefaultState());
				return true;
			}
		}
		return any;
	}

	/** Lamps on the cross road point their arm over the road. */
	private static net.minecraft.util.math.Direction cardinalToward(RiverField f, Junction j, int x, int z, double du) {
		double[] p0 = f.infra.junctions.point(j.fam(), j.k(), j.u0(), 0);
		double[] p1 = f.infra.junctions.point(j.fam(), j.k(), j.u0() + 1, 0);
		double tx = p1[0] - p0[0], tz = p1[1] - p0[1];
		double sg = du > 0 ? -1 : 1;
		tx *= sg;
		tz *= sg;
		if (Math.abs(tx) > Math.abs(tz)) return tx > 0 ? net.minecraft.util.math.Direction.EAST : net.minecraft.util.math.Direction.WEST;
		return tz > 0 ? net.minecraft.util.math.Direction.SOUTH : net.minecraft.util.math.Direction.NORTH;
	}

	/** Nearest junction to a point: {x, z}. For /findriver and the tests. */
	public double[] nearest(int x, int z) {
		double[] best = null;
		double bd = Double.MAX_VALUE;
		for (int fam = 0; fam < 2; fam++) {
			double p = fam == 0 ? x : z, u = fam == 0 ? z : x;
			int kc = (int) Math.round((p - infra.offset(fam)) / Infra.MW);
			for (int k = kc - 2; k <= kc + 2; k++) {
				long nc = Math.round((u - phase(fam, k)) / SPACING);
				for (long n = nc - 8; n <= nc + 8; n++) {
					Junction j = junction(fam, k, n);
					if (j == null) continue;
					double[] q = point(fam, k, j.u0(), 0);
					double dd = Math.hypot(q[0] - x, q[1] - z);
					if (dd < bd) {
						bd = dd;
						best = q;
					}
				}
			}
		}
		return best;
	}
}
