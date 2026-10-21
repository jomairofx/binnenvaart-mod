package nl.greatrivers;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Small inland harbours along the rivers: a basin cut into the bank with concrete quay walls on three sides,
 * a flat paved quay behind it (room for a crane) and a warehouse. Deep water in the basin and in front of it.
 *
 * One candidate per 1100 x 1100 block cell; it is used only on a fairly straight stretch of river on land.
 * Everything is a pure function of the world seed, so every chunk agrees on where the harbour is.
 */
public final class Harbours {
	public static final int CELL = 1100;
	public static final int QUAY_Y = 64;          // top of the quay (two blocks above the water)
	public static final int BASIN_FLOOR = 53;     // nine blocks of water
	static final double A = 56;                    // half length of the basin along the river
	static final double B = 26;                    // basin depth into the bank
	static final double SIDE = 22;                 // width of the quays beside the basin
	static final double BACK = 32;                 // depth of the quay behind the basin
	static final double BLEND = 20;                // slope back to the natural land

	public record Site(int cx, int cz, double tx, double tz, double gx, double gz, double bank, String river) {
		/** Along the river. */
		public double a(double x, double z) {
			return (x - cx) * tx + (z - cz) * tz;
		}

		/** From the middle of the river towards the harbour side. */
		public double v(double x, double z) {
			return (x - cx) * gx + (z - cz) * gz;
		}
	}

	private static final Site NONE = new Site(0, 0, 0, 0, 0, 0, 0, "");
	private final RiverField field;
	private final long seed;
	private final ConcurrentHashMap<Long, Site> sites = new ConcurrentHashMap<>();

	Harbours(RiverField field, long seed) {
		this.field = field;
		this.seed = seed;
	}

	private static long mix(long h) {
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
		int px = cellX * CELL + 150 + (int) ((h & 0xffff) % (CELL - 300));
		int pz = cellZ * CELL + 150 + (int) (((h >>> 16) & 0xffff) % (CELL - 300));
		RiverField.Kind best = null;
		int[] bc = null;
		double bd = Double.MAX_VALUE;
		for (RiverField.Kind k : new RiverField.Kind[]{field.river, field.great}) {
			int[] c = GreatRivers.nearest(k, px, pz, 420);
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
		// keep the whole harbour inside this cell, so neighbouring cells never overlap
		int m = 130;
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
		// a straight stretch: the river middle stays close to our straight line over the whole harbour
		for (int s = -80; s <= 80; s += 20) {
			double qx = x + tx * s, qz = z + tz * s;
			if (best.distance(qx, qz) > hw * 0.25 + 6) return NONE;
		}
		// on land: no sea anywhere around it
		for (int s = -1; s <= 1; s++)
			for (double v = 0; v <= hw + B + BACK + 40; v += 40) {
				int qx = (int) (x + tx * s * 80 + gx * v), qz = (int) (z + tz * s * 80 + gz * v);
				if (field.continentalness(qx, qz) < -0.12) return NONE;
			}
		return new Site(x, z, tx, tz, gx, gz, hw, best.name());
	}

	/** The harbour covering this column, or null. */
	public Site siteAt(int x, int z) {
		Site s = site(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (s == null) return null;
		double a = Math.abs(s.a(x, z)), v = s.v(x, z);
		if (a > A + SIDE + BLEND + 2 || v < s.bank - 30 || v > s.bank + B + BACK + BLEND + 2) return null;
		return s;
	}

	public static final int NO = 0, WATER = 1, FLAT = 2;

	/** What the harbour wants at this column: NO, WATER (basin or deepened fairway) or FLAT (quay, with blend weight). */
	public static int kind(Site s, double x, double z) {
		double a = Math.abs(s.a(x, z)), v = s.v(x, z), bank = s.bank;
		if (a <= A && v >= bank - 4 && v <= bank + B) return WATER;
		if (a <= A + SIDE && v >= bank - 26 && v < bank - 4) return WATER;
		if (a <= A + SIDE && v >= bank - 4 && v <= bank + B + BACK) return FLAT;
		return NO;
	}

	/** Weight of the flat quay level (1 on the quay, fading to 0 over the slope around it). */
	public static double flatWeight(Site s, double x, double z) {
		double a = Math.abs(s.a(x, z)), v = s.v(x, z), bank = s.bank;
		if (v < bank - 4) return 0;
		double ea = Math.max(0, a - (A + SIDE)), ev = Math.max(0, v - (bank + B + BACK));
		double e = Math.hypot(ea, ev);
		if (e >= BLEND) return 0;
		double f = 1 - e / BLEND;
		return f * f * (3 - 2 * f);
	}

	/** Shed behind the quay: local rectangle. */
	public static boolean inShed(Site s, double x, double z) {
		double a = s.a(x, z), v = s.v(x, z) - s.bank;
		return a >= -15 && a <= 15 && v >= B + 15 && v <= B + 29;
	}
}
