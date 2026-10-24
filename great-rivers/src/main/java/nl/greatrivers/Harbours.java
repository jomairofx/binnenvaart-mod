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
	public static final int SMALL = 0, MEDIUM = 1, LARGE = 2, PORT = 3;
	/** A big port: one per 10 x 10 km cell, always in the middle 5 x 5 km of it, so two ports are at least 5 km apart. */
	public static final int PORT_CELL = 10000;
	public static final double P_BW = 90, P_PW = 150, P_BL = 560, P_BACK = 240, P_END = 120;
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
			return size == SMALL ? "small" : size == MEDIUM ? "medium" : size == LARGE ? "large" : "big port";
		}

		/** Number of basins of a big port. */
		public int basins() {
			return 3 + Math.floorMod(cx, 2);
		}

		/** Middle of basin i of a big port, along the river. */
		public double basinA(int i) {
			return (i - (basins() - 1) / 2.0) * (P_BW + P_PW);
		}

		/** Half extent along the river, and how far inland it reaches (from the bank). */
		double alongExtent() {
			if (size == PORT) return (basins() * P_BW + (basins() - 1) * P_PW) / 2 + P_END;
			return size == LARGE ? A + Q : A + side;
		}

		double inland() {
			if (size == PORT) return P_BL + P_BACK;
			return size == LARGE ? B + Q : B + Q;
		}
	}

	private static final Site NONE = new Site(0, 0, 0, 0, 0, 0, 0, "", 0, "", 0, 0, 0, 0);
	private final RiverField field;
	private final long seed;
	private final ConcurrentHashMap<Long, Site> sites = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Site, List<int[]>> cranes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, Site> ports = new ConcurrentHashMap<>();

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

	/** The nearest big port, for /findriver and the tests. */
	public Site nearestPort(int x, int z, int cells) {
		int i0 = Math.floorDiv(x, PORT_CELL), j0 = Math.floorDiv(z, PORT_CELL);
		Site best = null;
		double bd = Double.MAX_VALUE;
		for (int i = -cells; i <= cells; i++)
			for (int j = -cells; j <= cells; j++) {
				Site s = port(i0 + i, j0 + j);
				if (s == null) continue;
				double d = Math.hypot(s.cx - x, s.cz - z);
				if (d < bd) {
					bd = d;
					best = s;
				}
			}
		return best;
	}

	/** The harbour of a cell, or null. */
	public Site site(int cellX, int cellZ) {
		long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
		Site s = sites.computeIfAbsent(key, k -> make(cellX, cellZ));
		return s == NONE ? null : s;
	}

	/** The big port of a 10 km cell, or null. */
	public Site port(int i, int j) {
		long key = ((long) i << 32) ^ (j & 0xffffffffL);
		Site s = ports.computeIfAbsent(key, k -> makePort(i, j));
		return s == NONE ? null : s;
	}

	private Site portAround(int x, int z) {
		return port(Math.floorDiv(x, PORT_CELL), Math.floorDiv(z, PORT_CELL));
	}

	/** Why a cell got no big port (for the tests). */
	public static final ConcurrentHashMap<Long, String> WHY = new ConcurrentHashMap<>();

	private Site makePort(int i, int j) {
		long h = mix(seed * 7 ^ mix(i * 0x632BE59BD9B4E019L + j * 0x9E3779B97F4A7C15L + 0x9087));
		long wk = ((long) i << 32) ^ (j & 0xffffffffL);
		if (Math.floorMod(h, 3L) == 0) {
			WHY.put(wk, "none");
			return NONE;          // two in three cells
		}
		int lo = PORT_CELL / 4, hi = PORT_CELL * 3 / 4;
		StringBuilder why = new StringBuilder();
		for (int attempt = 0; attempt < 16; attempt++) {
			long a = mix(h + attempt * 101);
			int px = i * PORT_CELL + lo + (int) Math.floorMod(a, (long) (hi - lo));
			int pz = j * PORT_CELL + lo + (int) Math.floorMod(a >>> 24, (long) (hi - lo));
			RiverField.Kind k = field.great;
			int[] c = GreatRivers.nearest(k, px, pz, 2500);
			if (c == null) {
				why.append(" noriver");
				continue;
			}
			int x = c[0], z = c[1];
			if (x < i * PORT_CELL + lo || x > i * PORT_CELL + hi || z < j * PORT_CELL + lo || z > j * PORT_CELL + hi) {
				why.append(" outside");
				continue;
			}
			double[] nrm = k.normal(x, z);
			double gx = nrm[0], gz = nrm[1];
			if (((a >>> 50) & 1) == 0) {
				gx = -gx;
				gz = -gz;
			}
			double tx = -gz, tz = gx;
			double hw = k.halfWidth(x, z);
			if (hw < 45) {
				why.append(" narrow");
				continue;                             // only on the wide rivers
			}
			Site s = new Site(x, z, tx, tz, gx, gz, hw, k.name(), PORT, "port", P_BW / 2, P_BL, P_BACK, P_PW);
			double along = s.alongExtent(), inland = s.inland();
			boolean ok = true;
			// a straight reach in front of it
			for (double sa = -along; sa <= along && ok; sa += 20)
				if (k.distance(x + tx * sa, z + tz * sa) > hw * 0.6 + 20) ok = false;
			if (!ok) why.append(" bend");
			// all of it on dry land, away from the motorways, railways, canals and cities
			for (double sa = -along - 60; sa <= along + 60 && ok; sa += 40)
				for (double v = hw + 30; v <= hw + inland + 60 && ok; v += 40) {
					int qx = (int) (x + tx * sa + gx * v), qz = (int) (z + tz * sa + gz * v);
					String r = null;
					if (field.continentalness(qx, qz) < Lowlands.COAST + 0.06) r = " sea";
					else if (k.distance(qx, qz) < k.halfWidth(qx, qz) + 10) r = " river";      // the river bends back
					else if (field.low.canalDistance(qx, qz) < Lowlands.CANAL_HALF + 30) r = " canal";
					else if (field.infra.motorwayDistance(qx, qz) < Infra.EDGE + 80) r = " motorway";
					else if (field.metros.near(qx, qz, 60)) r = " city";
					else if (field.rails.distance(qx, qz) < 90) r = " rail";
					if (r != null) {
						ok = false;
						why.append(r);
					}
				}
			if (ok) {
				WHY.put(wk, "ok");
				return s;
			}
		}
		WHY.put(wk, why.toString());
		return NONE;
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
		double[] nrm = best.normal(x, z);
		double gx = nrm[0], gz = nrm[1];
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
				if (field.continentalness(qx, qz) < Lowlands.COAST + 0.06) return NONE;
				if (field.low.canalDistance(qx, qz) < Lowlands.CANAL_HALF + 30) return NONE;
				if (field.infra.motorwayDistance(qx, qz) < Infra.EDGE + 60) return NONE;
				if (field.metros.near(qx, qz, 40)) return NONE;
				if (field.rails.distance(qx, qz) < 70) return NONE;
			}
		// not in or next to a big port
		Site pt = portAround(x, z);
		if (pt != null && outside(pt, x, z) < 400) return NONE;
		return new Site(x, z, tx, tz, gx, gz, hw, best.name(), size, goods, A, B, Q, side);
	}

	/** The harbour covering this column, or null. */
	public Site siteAt(int x, int z) {
		Site p = portAround(x, z);
		if (p != null) {
			double a = Math.abs(p.a(x, z)), v = p.v(x, z);
			if (!(a > p.alongExtent() + BLEND + 2 || v < p.bank - 30 || v > p.bank + p.inland() + BLEND + 2)) return p;
		}
		Site s = site(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (s == null) return null;
		double a = Math.abs(s.a(x, z)), v = s.v(x, z);
		if (a > s.alongExtent() + BLEND + 2 || v < s.bank - 30 || v > s.bank + s.inland() + BLEND + 2) return null;
		return s;
	}

	/** The harbour whose area, grown by g blocks all round, covers this column, or null. */
	public Site siteNear(int x, int z, double g) {
		Site p = portAround(x, z);
		if (p != null) {
			double a = Math.abs(p.a(x, z)), v = p.v(x, z);
			if (!(a > p.alongExtent() + BLEND + g || v < p.bank - 30 || v > p.bank + p.inland() + BLEND + g)) return p;
		}
		Site s = site(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (s == null) return null;
		double a = Math.abs(s.a(x, z)), v = s.v(x, z);
		if (a > s.alongExtent() + BLEND + g || v < s.bank - 30 || v > s.bank + s.inland() + BLEND + g) return null;
		return s;
	}

	/** How far this point lies outside the level harbour area (0 inside). */
	public static double outside(Site s, double x, double z) {
		double a = Math.abs(s.a(x, z)), v = s.v(x, z) - s.bank;
		return Math.hypot(Math.max(0, a - s.alongExtent()), Math.max(0, v - s.inland()));
	}

	/** The dike around a harbour: like the river dike, it keeps the polder dry behind the harbour. NaN = none here. */
	public static double dike(Site s, double x, double z) {
		double v = s.v(x, z) - s.bank;
		if (v < 35) return Double.NaN;          // the forelands and the river side have no dike of their own
		double k = outside(s, x, z) - BLEND;
		if (k < 0) return Double.NaN;
		return Lowlands.dike(k, 0);
	}

	public static boolean dikeCrest(Site s, double x, double z) {
		double v = s.v(x, z) - s.bank;
		if (v < 35) return false;
		return Lowlands.crest(outside(s, x, z) - BLEND, 0);
	}

	public static final int NO = 0, WATER = 1, FLAT = 2;

	/** What the harbour wants at this column: NO, WATER (basin or deepened fairway) or FLAT (quay). */
	public static int kind(Site s, double x, double z) {
		double a = Math.abs(s.a(x, z)), v = s.v(x, z) - s.bank;
		double along = s.alongExtent();
		if (s.size == PORT) {
			if (v >= -4 && v <= P_BL) for (int i = 0; i < s.basins(); i++) if (Math.abs(a0(s, x, z) - s.basinA(i)) <= P_BW / 2) return WATER;
		} else if (s.size == LARGE) {
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
	/** Signed position along the river (not the absolute value). */
	static double a0(Site s, double x, double z) {
		return s.a(x, z);
	}

	/** Big port: {nearest basin, distance from its quay edge (negative in the water), v} . */
	public static double[] portFrame(Site s, double x, double z) {
		double a = s.a(x, z), v = s.v(x, z) - s.bank;
		int best = 0;
		double bd = Double.MAX_VALUE;
		for (int i = 0; i < s.basins(); i++) {
			double d = Math.abs(a - s.basinA(i));
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		return new double[]{best, bd - P_BW / 2, v, a - s.basinA(best)};
	}

	public static double[] quayFrame(Site s, double x, double z) {
		double a = s.a(x, z), v = s.v(x, z) - s.bank;
		if (s.size == PORT) return null;
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
		if (s.size == PORT) return list;          // the big port has its own container cranes
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
		if (s.size == PORT) return false;
		double a = s.a(x, z), v = s.v(x, z) - s.bank;
		if (s.size == LARGE) {
			// at the head of the basin, on one side
			return a >= s.A + 6 && a <= s.A + s.Q - 6 && v >= s.B - 10 && v <= s.B + s.Q - 6;
		}
		// on the side quay, at the back
		return a >= s.A + 3 && a <= s.A + s.side - 3 && v >= s.B + 10 && v <= s.B + s.Q - 4;
	}
}
