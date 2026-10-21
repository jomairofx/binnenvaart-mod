package nl.greatrivers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inland harbours along the rivers, each for one kind of goods, in three sizes:
 * - SMALL:  a basin of 112 x 26 along the river ("loswal" with a basin), quay 50 deep behind it
 * - MEDIUM: twice as long (224), quay 60 deep
 * - LARGE:  an "insteekhaven": a basin 80 wide cut 300 blocks into the land, with 60 deep quays on both sides
 *           and at the head (about five times the quay length of a small harbour)
 * Quay walls of concrete, paving, bollards and ladders; cranes 20 blocks back from the edge; behind the cranes
 * the goods yard (containers, piles of sand, silos ...); buildings at the far ends, never where ships moor.
 *
 * Local frame: a = along the river, v = from the middle of the river into the land. Everything is a pure
 * function of the world seed, so every chunk agrees on where everything is.
 */
public final class Harbours {
	public static final int CELL = 1400;
	public static final int QUAY_Y = 64;          // top of the quay (two blocks above the water)
	public static final int BASIN_FLOOR = 52;     // ten blocks of water
	public static final int SMALL = 0, MEDIUM = 1, LARGE = 2;
	public static final String[] GOODS = {"containers", "sand", "gravel", "coal", "grain", "timber", "iron_ore", "clay"};
	static final double BLEND = 22;               // slope back to the natural land
	public static final double CRANE_BACK = 20;   // cranes stand this far from the quay edge
	public static final double YARD_FROM = 27;    // goods start this far from the edge

	public record Site(int cx, int cz, double tx, double tz, double gx, double gz, double bank, String river,
					   int size, String goods, double A, double B, double Q, double side) {
		/** Along the river. */
		public double a(double x, double z) {
			return (x - cx) * tx + (z - cz) * tz;
		}

		/** From the middle of the river towards the harbour side. */
		public double v(double x, double z) {
			return (x - cx) * gx + (z - cz) * gz;
		}

		public double wx(double a, double v) {
			return cx + tx * a + gx * v;
		}

		public double wz(double a, double v) {
			return cz + tz * a + gz * v;
		}

		public String sizeName() {
			return size == SMALL ? "small" : size == MEDIUM ? "medium" : "large";
		}

		/** Half extent along the river, and how far inland it reaches (from the bank). */
		double alongExtent() {
			return size == LARGE ? A + Q : A + side;
		}

		double inland() {
			return size == LARGE ? B + Q : B + Q;
		}
	}

	private static final Site NONE = new Site(0, 0, 0, 0, 0, 0, 0, "", 0, "", 0, 0, 0, 0);
	private final RiverField field;
	private final long seed;
	private final ConcurrentHashMap<Long, Site> sites = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Site, List<int[]>> cranes = new ConcurrentHashMap<>();

	Harbours(RiverField field, long seed) {
		this.field = field;
		this.seed = seed;
	}

	static long mix(long h) {
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		h *= 0xc4ceb9fe1a85ec53L;
		return h ^ (h >>> 33);
	}

	/** The harbour of a cell, or null. */
	public Site site(int cellX, int cellZ) {
		long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
		Site s = sites.computeIfAbsent(key, k -> make(cellX, cellZ));
		return s == NONE ? null : s;
	}

	private Site make(int cellX, int cellZ) {
		long h = mix(seed ^ mix(cellX * 0x9E3779B97F4A7C15L + cellZ));
		int px = cellX * CELL + 300 + (int) ((h & 0xffff) % (CELL - 600));
		int pz = cellZ * CELL + 300 + (int) (((h >>> 16) & 0xffff) % (CELL - 600));
		RiverField.Kind best = null;
		int[] bc = null;
		double bd = Double.MAX_VALUE;
		for (RiverField.Kind k : new RiverField.Kind[]{field.river, field.great}) {
			int[] c = GreatRivers.nearest(k, px, pz, 300);
			if (c == null) continue;
			double d = Math.hypot(c[0] - px, c[1] - pz);
			if (d < bd) {
				bd = d;
				best = k;
				bc = c;
			}
		}
		if (best == null) return NONE;
		int x = bc[0], z = bc[1];
		int roll = (int) ((h >>> 44) % 100);
		int size = roll < 42 ? SMALL : roll < 77 ? MEDIUM : LARGE;
		String goods = GOODS[(int) ((h >>> 52) % GOODS.length)];
		double A, B, Q, side;
		if (size == SMALL) {
			A = 56;
			B = 26;
			Q = 52;
			side = 30;
		} else if (size == MEDIUM) {
			A = 112;
			B = 30;
			Q = 62;
			side = 36;
		} else {
			A = 40;      // half width of the basin
			B = 300;     // length of the basin into the land
			Q = 62;      // quay depth on both sides and at the head
			side = 0;
		}
		// keep the whole harbour inside this cell, so neighbouring cells never overlap
		int m = (int) (size == LARGE ? B + Q + 160 : A + side + 120);
		m = Math.min(m, CELL / 2 - 10);
		if (x < cellX * CELL + m || x > (cellX + 1) * CELL - m || z < cellZ * CELL + m || z > (cellZ + 1) * CELL - m) return NONE;
		RiverField.Kind other = best == field.river ? field.great : field.river;
		if (other.distance(x, z) < other.halfWidth(x, z) + 160) return NONE;      // not at a confluence
		double e = 4;
		double gx = best.distance(x + e, z) - best.distance(x - e, z), gz = best.distance(x, z + e) - best.distance(x, z - e);
		double l = Math.hypot(gx, gz);
		if (l < 1e-9) return NONE;
		gx /= l;
		gz /= l;
		if (((h >>> 40) & 1) == 0) {
			gx = -gx;
			gz = -gz;
		}
		double tx = -gz, tz = gx;
		double hw = best.halfWidth(x, z);
		double along = size == LARGE ? A + Q : A + side;
		// a straight stretch: the river middle stays close to our straight line over the whole quay front
		for (double s = -along; s <= along; s += 20) {
			double qx = x + tx * s, qz = z + tz * s;
			if (best.distance(qx, qz) > hw * 0.25 + 6) return NONE;
		}
		// on land: no sea anywhere around it, and away from canals
		double inland = B + Q + 30;
		for (double s = -along; s <= along; s += Math.max(20, along / 3))
			for (double v = 0; v <= hw + inland; v += 40) {
				int qx = (int) (x + tx * s + gx * v), qz = (int) (z + tz * s + gz * v);
				if (field.continentalness(qx, qz) < -0.12) return NONE;
				if (field.low.canalDistance(qx, qz) < Lowlands.CANAL_HALF + 30) return NONE;
			}
		return new Site(x, z, tx, tz, gx, gz, hw, best.name(), size, goods, A, B, Q, side);
	}

	/** The harbour covering this column, or null. */
	public Site siteAt(int x, int z) {
		Site s = site(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (s == null) return null;
		double a = Math.abs(s.a(x, z)), v = s.v(x, z);
		if (a > s.alongExtent() + BLEND + 2 || v < s.bank - 30 || v > s.bank + s.inland() + BLEND + 2) return null;
		return s;
	}

	public static final int NO = 0, WATER = 1, FLAT = 2;

	/** What the harbour wants at this column: NO, WATER (basin or deepened fairway) or FLAT (quay). */
	public static int kind(Site s, double x, double z) {
		double a = Math.abs(s.a(x, z)), v = s.v(x, z) - s.bank;
		double along = s.alongExtent();
		if (s.size == LARGE) {
			if (a <= s.A && v >= -4 && v <= s.B) return WATER;
		} else if (a <= s.A && v >= -4 && v <= s.B) return WATER;
		if (a <= along && v >= -26 && v < -4) return WATER;
		if (a <= along && v >= -4 && v <= s.inland()) return FLAT;
		return NO;
	}

	/** Weight of the flat quay level (1 on the quay, fading to 0 over the slope around it). */
	public static double flatWeight(Site s, double x, double z) {
		double a = Math.abs(s.a(x, z)), v = s.v(x, z) - s.bank;
		if (v < -4) return 0;
		double ea = Math.max(0, a - s.alongExtent()), ev = Math.max(0, v - s.inland());
		double e = Math.hypot(ea, ev);
		if (e >= BLEND) return 0;
		double f = 1 - e / BLEND;
		return f * f * (3 - 2 * f);
	}

	/**
	 * Position on the quay, measured from the nearest mooring edge: {s, e, edgeId}.
	 * s runs along the edge, e is the distance back from it. Null where there is no mooring edge to measure from.
	 */
	public static double[] quayFrame(Site s, double x, double z) {
		double a = s.a(x, z), v = s.v(x, z) - s.bank;
		if (s.size == LARGE) {
			if (v < 6 || v > s.B - 4) return null;
			double e = Math.abs(a) - s.A;
			if (e < 0) return null;
			return new double[]{v, e, a > 0 ? 1 : -1};
		}
		if (Math.abs(a) > s.A) return null;
		double e = v - s.B;
		if (e < 0) return null;
		return new double[]{a, e, 0};
	}

	/** Crane positions (block x, z, facing x, facing z) along the mooring edges. */
	public List<int[]> cranes(Site s) {
		return cranes.computeIfAbsent(s, Harbours::makeCranes);
	}

	private static List<int[]> makeCranes(Site s) {
		List<int[]> list = new ArrayList<>();
		double spacing = 44;
		if (s.size == LARGE) {
			for (int side = -1; side <= 1; side += 2) {
				for (double v = 30; v <= s.B - 24; v += spacing) {
					double a = side * (s.A + CRANE_BACK);
					list.add(new int[]{(int) Math.floor(s.wx(a, s.bank + v)), (int) Math.floor(s.wz(a, s.bank + v)), side});
				}
			}
		} else {
			double len = 2 * s.A - 40;
			int n = Math.max(1, (int) (len / spacing) + 1);
			for (int i = 0; i < n; i++) {
				double a = n == 1 ? 0 : -s.A + 20 + len * i / (n - 1);
				list.add(new int[]{(int) Math.floor(s.wx(a, s.bank + s.B + CRANE_BACK)), (int) Math.floor(s.wz(a, s.bank + s.B + CRANE_BACK)), 0});
			}
		}
		return list;
	}

	/** Rectangle of the main building (warehouse / office): at the far end, away from the mooring edge. */
	public static boolean inBuilding(Site s, double x, double z) {
		double a = s.a(x, z), v = s.v(x, z) - s.bank;
		if (s.size == LARGE) {
			// at the head of the basin, on one side
			return a >= s.A + 6 && a <= s.A + s.Q - 6 && v >= s.B - 10 && v <= s.B + s.Q - 6;
		}
		// on the side quay, at the back
		return a >= s.A + 3 && a <= s.A + s.side - 3 && v >= s.B + 10 && v <= s.B + s.Q - 4;
	}
}
