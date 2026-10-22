package nl.greatrivers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * River locks ("sluizen") with their weirs ("stuwen"), like on the Maas and the Nederrijn. One candidate per
 * 3300 x 3300 cell, on a straight reach of the river, away from bridges and harbours.
 *
 * Across the river (w = from the middle of the river towards the lock side):
 * - outer lock wall (6) at the bank, the lock chamber (24 wide, 236 long between the walls: room for the
 *   ZEELANDIA of 178 x 19), the middle wall (8) with the control tower;
 * - the weir over the rest of the river: concrete piers every 30 blocks with hoist towers and a portal on top,
 *   closed gates in between, a service road over it (y 72) down to the far bank.
 * The lock is always open: the mitre gates stand open in their recesses in the walls. Guide jetties of piles lead
 * the ships in at both ends. Behind the outer wall a paved strip, from which a provincial road runs to the
 * nearest village or town on that side of the river.
 */
public final class Locks {
	public static final int CELL = 3300;
	public static final int CH = 24, OW = 6, MWL = 8;
	public static final int WALL_L = 132, GATE0 = 116, GATE1 = 130, GUIDE = 110;
	public static final int WALL_TOP = 64, WEIR_A = 7, WEIR_DECK = 72, PIER_EVERY = 30;
	public static final double LAND = 16, ROAD_HALF = 3.5;

	public record Site(int cx, int cz, double tx, double tz, double gx, double gz, double hw, int side,
					   double rx0, double rz0, double rx1, double rz1, String town) {
		/** Along the river. */
		public double a(double x, double z) {
			return (x - cx) * tx + (z - cz) * tz;
		}

		/** Across the river, from its middle towards the lock side. */
		public double w(double x, double z) {
			return side * ((x - cx) * gx + (z - cz) * gz);
		}

		public double wx(double a, double w) {
			return cx + tx * a + gx * w * side;
		}

		public double wz(double a, double w) {
			return cz + tz * a + gz * w * side;
		}

		public boolean hasRoad() {
			return !Double.isNaN(rx0);
		}

		/** Chamber face of the outer wall. */
		public double outer0() {
			return hw - OW;
		}

		/** Chamber face of the middle wall. */
		public double ch0() {
			return hw - OW - CH;
		}

		/** Weir side of the middle wall. */
		public double mid0() {
			return hw - OW - CH - MWL;
		}

		/** Far bank end of the weir. */
		public double far() {
			return -hw - 6;
		}

		/** Inside the area of the lock and weir (not the road)? */
		public boolean inArea(double a, double w, double grow) {
			return Math.abs(a) <= WALL_L + GUIDE + 80 + grow && w >= -hw - 150 - grow && w <= hw + LAND + 12 + grow;
		}

		/** {distance across, distance along from the lock} to the road, or null without a road. */
		public double[] road(double x, double z) {
			if (!hasRoad()) return null;
			double dx = rx1 - rx0, dz = rz1 - rz0, l2 = dx * dx + dz * dz;
			double t = ((x - rx0) * dx + (z - rz0) * dz) / l2;
			if (t < 0 || t > 1) return null;
			double l = Math.sqrt(l2);
			double c = ((x - rx0) * dz - (z - rz0) * dx) / l;
			return new double[]{c, t * l};
		}
	}

	private static final Site NONE = new Site(0, 0, 0, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 0, "");
	private final RiverField f;
	private final Infra infra;
	private final long seed;
	private final ConcurrentHashMap<Long, Site> sites = new ConcurrentHashMap<>();

	Locks(RiverField f, Infra infra, long seed) {
		this.f = f;
		this.infra = infra;
		this.seed = seed;
	}

	public Site site(int cellX, int cellZ) {
		long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
		Site s = sites.computeIfAbsent(key, k -> make(cellX, cellZ));
		return s == NONE ? null : s;
	}

	private Site make(int cellX, int cellZ) {
		long h = Harbours.mix(seed * 7 + Harbours.mix(cellX * 0x9E3779B97F4A7C15L + cellZ * 0x632BE59BD9B4E019L + 991));
		if ((h & 3) == 0) return NONE;   // three in four cells
		int px = cellX * CELL + 500 + (int) (((h >>> 8) & 0xffff) % (CELL - 1000));
		int pz = cellZ * CELL + 500 + (int) (((h >>> 24) & 0xffff) % (CELL - 1000));
		RiverField.Kind k = f.great;
		int[] c = GreatRivers.nearest(k, px, pz, 900);
		if (c == null) return NONE;
		int x = c[0], z = c[1];
		int m = 420;
		if (x < cellX * CELL + m || x > (cellX + 1) * CELL - m || z < cellZ * CELL + m || z > (cellZ + 1) * CELL - m) return NONE;
		double hw = k.halfWidth(x, z);
		if (hw < 55) return NONE;   // a narrow river needs no weir
		double[] nrm = k.normal(x, z);
		double gx = nrm[0], gz = nrm[1], tx = -gz, tz = gx;
		// a straight reach of even width
		for (double s = -340; s <= 340; s += 20) {
			double qx = x + tx * s, qz = z + tz * s;
			if (k.distance(qx, qz) > hw * 0.15 + 5) return NONE;
			if (Math.abs(k.halfWidth(qx, qz) - hw) > 7) return NONE;
		}
		int side = ((h >>> 40) & 1) == 0 ? 1 : -1;
		// on land on both sides, no motorway bridge, no harbour, no village nearby
		for (double s = -800; s <= 800; s += 40) {
			double qx = x + tx * s, qz = z + tz * s;
			if (infra.motorwayDistance(qx, qz) < 90) return NONE;
		}
		for (double s = -700; s <= 700; s += 50)
			for (double v : new double[]{-(hw + 150), -(hw + 60), 0, hw + 60, hw + 150}) {
				int qx = (int) (x + tx * s + gx * v), qz = (int) (z + tz * s + gz * v);
				if (f.coastal(qx, qz) < Lowlands.COAST + 0.1) return NONE;
				if (f.harbours.siteAt(qx, qz) != null) return NONE;
				if (f.villages.at(qx, qz) != null) return NONE;
			}
		// a provincial road from the lock to the nearest village or town on its side of the river
		double sx = x + gx * side * (hw + LAND + 1), sz = z + gz * side * (hw + LAND + 1);
		List<Villages.Town> towns = new ArrayList<>();
		int vcx = Math.floorDiv((int) sx, Villages.CELL), vcz = Math.floorDiv((int) sz, Villages.CELL);
		for (int i = -3; i <= 3; i++)
			for (int j = -3; j <= 3; j++) {
				Villages.Town t = f.villages.town(vcx + i, vcz + j);
				if (t != null) towns.add(t);
			}
		towns.sort((p, q) -> Double.compare(Math.hypot(p.cx() - sx, p.cz() - sz), Math.hypot(q.cx() - sx, q.cz() - sz)));
		for (Villages.Town t : towns) {
			double d = Math.hypot(t.cx() - sx, t.cz() - sz);
			if (d > 2800) break;
			if (side * ((t.cx() - x) * gx + (t.cz() - z) * gz) < hw + 80) continue;   // other side of the river
			double ex = t.cx() + (sx - t.cx()) / d * (t.radius() + 4), ez = t.cz() + (sz - t.cz()) / d * (t.radius() + 4);
			double len = Math.hypot(ex - sx, ez - sz);
			if (len < 60) continue;
			boolean ok = true;
			for (double s = 8; s <= len && ok; s += 12) {
				double qx = sx + (ex - sx) * s / len, qz = sz + (ez - sz) * s / len;
				if (k.distance(qx, qz) - k.halfWidth(qx, qz) < 12) ok = false;
				else if (infra.motorwayDistance(qx, qz) < Infra.EDGE + 10) ok = false;
				else if (f.harbours.siteAt((int) qx, (int) qz) != null) ok = false;
				else if (f.coastal((int) qx, (int) qz) < Lowlands.COAST + 0.07) ok = false;
				else {
					Villages.Town o = f.villages.at((int) qx, (int) qz);
					if (o != null && o != t && !(o.cx() == t.cx() && o.cz() == t.cz())) ok = false;
				}
			}
			if (ok) return new Site(x, z, tx, tz, gx, gz, hw, side, sx, sz, ex, ez, t.kind());
		}
		return new Site(x, z, tx, tz, gx, gz, hw, side, Double.NaN, 0, 0, 0, "");
	}

	/** The locks whose area or road may reach this column. */
	public List<Site> around(int x, int z) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		List<Site> out = null;
		for (int i = -1; i <= 1; i++)
			for (int j = -1; j <= 1; j++) {
				Site s = site(cx + i, cz + j);
				if (s == null) continue;
				if (out == null) out = new ArrayList<>(2);
				out.add(s);
			}
		return out == null ? List.of() : out;
	}

	public boolean near(int x, int z, double r) {
		for (Site s : around(x, z)) {
			if (s.inArea(s.a(x, z), s.w(x, z), r)) return true;
			double[] rd = s.road(x, z);
			if (rd != null && Math.abs(rd[0]) < ROAD_HALF + r) return true;
		}
		return false;
	}

	/** Land shaped for the lock: deep water in the chamber and its approaches, the paved strip, the weir road ramp. */
	public double shape(int x, int z, double t) {
		for (Site s : around(x, z)) {
			double px = x + 0.5, pz = z + 0.5;
			double a = s.a(px, pz), w = s.w(px, pz), aa = Math.abs(a);
			if (s.inArea(a, w, 0)) {
				// deep water in the chamber and in the approaches to it
				if (w >= s.ch0() - 4 && w < s.outer0() + 1) {
					double fa = Math.max(0, aa - (WALL_L + GUIDE)) / 80.0;
					double deep = RiverField.WATER_TOP - 8.7;
					double tt = deep + (t - deep) * Lowlands.smooth(fa);
					if (tt < t) t = tt;
				}
				// the paved strip behind the outer wall, fading back into the land
				if (w >= s.hw - 0.5) {
					double ea = Math.max(0, aa - WALL_L), ew = Math.max(0, w - (s.hw + LAND));
					double e = Math.max(ea, ew);
					if (e < 10) {
						double g = 1 - Lowlands.smooth(e / 10);
						t = t * (1 - g) + (WALL_TOP + 0.3) * g;
					}
				}
				// the ramp of the weir's service road on the far bank
				double wb = s.far();
				if (aa <= 30 && w < wb && w > wb - 150) {
					double top = WEIR_DECK + 0.3 - (wb - w) / 12.0;
					double lo = top - Math.max(0, aa - 4.5) / 1.5;
					if (t < lo) t = lo;
				}
			}
			// the provincial road: no ditches or lakes under it
			double[] rd = s.road(px, pz);
			if (rd != null && Math.abs(rd[0]) <= ROAD_HALF + 1.5 && t < Lowlands.POLDER) t = Lowlands.POLDER;
		}
		return t;
	}

	/** Nearest lock to a point. */
	public Site nearest(int x, int z, int cells) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Site best = null;
		double bd = Double.MAX_VALUE;
		for (int r = 0; r <= cells; r++) {
			for (int i = -r; i <= r; i++)
				for (int j = -r; j <= r; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
					Site s = site(cx + i, cz + j);
					if (s == null) continue;
					double d = Math.hypot(s.cx() - x, s.cz() - z);
					if (d < bd) {
						bd = d;
						best = s;
					}
				}
			if (best != null && bd < r * CELL) return best;
		}
		return best;
	}
}
