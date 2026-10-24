package nl.greatrivers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dutch villages, towns and cities: a grid of streets, rows of narrow brick houses with gables to the street, a
 * church with a tall spire in the middle. Towns have blocks of flats around the centre and office towers in it.
 * Half of the towns are river cities: they lie on both banks of the river, with quays along the water, a
 * boulevard with trees, and a ferry between the two halves.
 * Villages are about 120 blocks across, towns about 300, river cities about 480. One candidate per 1100 cell.
 * Every town lies on a slightly raised site (like the old Dutch towns on their mounds) that slopes gently back down
 * to the polder.
 */
public final class Villages {
	public static final int CELL = 1100;
	public static final int PU = 34, PW = 26;      // street pitch along x and along z
	public static final int STREET = 5;            // street width (incl. pavements)
	public static final int GROUND = 64;           // top block of the streets
	public static final double RING = 30;          // the slope from the town site back to the land

	/**
	 * A village or town. dike: it lies right behind the river dike (a "dijkwijk"): its blocks stop at the foot of
	 * the dike, the dike road runs along its front; a dike town with city = true has flats and towers along the dike.
	 */
	public record Town(int cx, int cz, double radius, boolean city, long seed, boolean river, boolean dike) {
		public String kind() {
			return river ? "river city" : dike ? (city ? "town behind the dike" : "village behind the dike") : city ? "town" : "village";
		}
	}

	private static final Town NONE = new Town(0, 0, 0, false, 0, false, false);
	/** Blocks of a dike town stop this far from the river bank (behind the foreland and the dike). */
	static final double DIKE_KEEP = 36;
	private final RiverField field;
	private final long seed;
	private final ConcurrentHashMap<Long, Town> towns = new ConcurrentHashMap<>();

	Villages(RiverField field, long seed) {
		this.field = field;
		this.seed = seed;
	}

	public Town town(int cellX, int cellZ) {
		long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
		Town t = towns.computeIfAbsent(key, k -> make(cellX, cellZ));
		return t == NONE ? null : t;
	}

	private Town make(int cellX, int cellZ) {
		long h = Harbours.mix(seed * 31 + Harbours.mix(cellX * 0x632BE59BD9B4E019L + cellZ * 0x9E3779B97F4A7C15L + 77));
		if ((h & 7) >= 6) return NONE;                       // three in four cells get a village or town
		boolean city = ((h >>> 3) & 3) == 0;
		// every village and town has its own size
		double r = city ? 120 + Math.floorMod(h >>> 50, 81L) : 45 + Math.floorMod(h >>> 50, 66L);
		boolean dike = false;
		int px = cellX * CELL + 250 + (int) ((h >>> 8 & 0xffff) % (CELL - 500));
		int pz = cellZ * CELL + 250 + (int) ((h >>> 24 & 0xffff) % (CELL - 500));
		boolean river = false;
		if (city && ((h >>> 5) & 1) == 0) {
			// a river city: on both banks of a straight reach of the river, the whole town inside its cell
			RiverField.Kind k = field.great;
			int[] c = GreatRivers.nearest(k, px, pz, 900);
			if (c != null) {
				double rr = 240, m = rr + 16 + RING + 6;
				boolean ok = c[0] >= cellX * CELL + m && c[0] <= (cellX + 1) * CELL - m && c[1] >= cellZ * CELL + m && c[1] <= (cellZ + 1) * CELL - m;
				double hw = k.halfWidth(c[0], c[1]);
				if (ok && hw <= 115) {
					double[] n = k.normal(c[0], c[1]);
					double tx = -n[1], tz = n[0];
					for (double s = -280; s <= 280 && ok; s += 20) {
						double qx = c[0] + tx * s, qz = c[1] + tz * s;
						if (k.distance(qx, qz) > hw * 0.25 + 6 || Math.abs(k.halfWidth(qx, qz) - hw) > 15) ok = false;
					}
				}
				if (ok) {
					px = c[0];
					pz = c[1];
					r = 240;
					river = true;
				}
			}
		}
		// a quarter right behind the river dike: one in four of the other villages and towns
		if (!river && ((h >>> 12) & 3) == 0) {
			RiverField.Kind k = field.great;
			int[] c = GreatRivers.nearest(k, px, pz, 900);
			if (c != null) {
				double hw = k.halfWidth(c[0], c[1]);
				double[] n = k.normal(c[0], c[1]);
				double side = (px - c[0]) * n[0] + (pz - c[1]) * n[1] >= 0 ? 1 : -1;
				double rr = 110 + Math.floorMod(h >>> 40, 91L);
				double back = hw + Lowlands.foreland(k) + DIKE_KEEP + rr * 0.45;
				double qx = c[0] + n[0] * side * back, qz = c[1] + n[1] * side * back;
				double tx = -n[1], tz = n[0];
				boolean ok = qx - rr - RING - 20 >= cellX * CELL && qx + rr + RING + 20 <= (cellX + 1) * CELL
						&& qz - rr - RING - 20 >= cellZ * CELL && qz + rr + RING + 20 <= (cellZ + 1) * CELL;
				// a fairly straight reach, so the dike runs along the front of the town
				for (double sa = -rr; sa <= rr && ok; sa += 20) {
					double ax = c[0] + tx * sa, az = c[1] + tz * sa;
					if (k.distance(ax, az) > hw * 0.3 + 12 || Math.abs(k.halfWidth(ax, az) - hw) > 20) ok = false;
				}
				if (ok) {
					px = (int) qx;
					pz = (int) qz;
					r = rr;
					dike = true;
					city = ((h >>> 14) & 1) == 0;
				}
			}
		}
		// snap the centre to the street grid so the middle block is whole
		px = Math.floorDiv(px, PU) * PU + STREET;
		pz = Math.floorDiv(pz, PW) * PW + STREET;
		// on land, away from harbours and motorways (and, except a river city, away from the river and its dikes)
		for (int a = 0; a < 16; a++) {
			double ang = a * Math.PI / 8;
			for (double rr : new double[]{0, r * 0.5, r * 0.85, r + 20}) {
				int x = (int) (px + Math.cos(ang) * rr), z = (int) (pz + Math.sin(ang) * rr);
				if (field.continentalness(x, z) < Lowlands.COAST + 0.08) return NONE;
				RiverField.Kind k = field.great;
				if (!river && !dike && k.distance(x, z) - k.halfWidth(x, z) < Lowlands.foreland(k) + 45) return NONE;
				if (field.harbours.siteAt(x, z) != null) return NONE;
				if (field.infra.motorwayDistance(x, z) < Infra.EDGE + 30) return NONE;
				if (field.metros.near(x, z, 150)) return NONE;
				if (field.rails.distance(x, z) < Rails.HALF + 40) return NONE;
			}
		}
		return new Town(px, pz, r, city, h, river, dike);
	}

	/** The village or town that covers this column (its streets and edge), or null. */
	public Town at(int x, int z) {
		Town t = town(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (t == null) return null;
		double d = Math.hypot(x - t.cx, z - t.cz);
		return d <= t.radius + 24 ? t : null;
	}

	/** The town whose site (with the slope around it) covers this column, or null. */
	public Town site(int x, int z) {
		Town t = town(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (t == null) return null;
		double d = Math.hypot(x - t.cx, z - t.cz);
		return d <= t.radius + 16 + RING ? t : null;
	}

	/** Is the block (between streets) at this column part of the town? */
	public static boolean blockIn(Town t, int x, int z) {
		int bx = Math.floorDiv(x - t.cx, PU), bz = Math.floorDiv(z - t.cz, PW);
		double mx = t.cx + bx * PU + PU / 2.0, mz = t.cz + bz * PW + PW / 2.0;
		if (Math.hypot(mx - t.cx, mz - t.cz) > t.radius) return false;
		return !t.dike || dikeBank(t, bx, bz) >= Lowlands.foreland(RiverField.current.great) + DIKE_KEEP;
	}

	private static final ConcurrentHashMap<Long, Double> BANK = new ConcurrentHashMap<>();

	/** Least distance from the river bank of the block (bx, bz) of a dike town (cached). */
	static double dikeBank(Town t, int bx, int bz) {
		if (BANK.size() > 100000) BANK.clear();
		RiverField f = RiverField.current;
		long key = t.seed ^ (bx * 0x9E3779B97F4A7C15L) ^ (bz * 0x632BE59BD9B4E019L);
		return BANK.computeIfAbsent(key, k -> {
			double e = Double.MAX_VALUE;
			int x0 = t.cx + bx * PU, z0 = t.cz + bz * PW;
			for (int i = 0; i <= 2; i++)
				for (int j = 0; j <= 2; j++) {
					double x = x0 + i * PU / 2.0, z = z0 + j * PW / 2.0;
					e = Math.min(e, f.great.distance(x, z) - f.great.halfWidth(x, z));
				}
			return e;
		});
	}

	/** Street column: a street running next to a block that belongs to the town. */
	public static boolean street(Town t, int x, int z) {
		int u = Math.floorMod(x - t.cx, PU), w = Math.floorMod(z - t.cz, PW);
		if (u >= STREET && w >= STREET) return false;
		// next to at least one block of the town
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				if (blockIn(t, x + dx * STREET, z + dz * STREET)) return true;
		return false;
	}

	/** Within the level area of the town. */
	public boolean flat(int x, int z) {
		Town t = at(x, z);
		return t != null && Math.hypot(x - t.cx, z - t.cz) <= t.radius + 16;
	}

	/** Neighbouring towns that get a provincial road to this one (east and south neighbours). */
	public List<Town> neighbours(int cellX, int cellZ) {
		List<Town> out = new ArrayList<>(2);
		Town a = town(cellX + 1, cellZ), b = town(cellX, cellZ + 1);
		if (a != null) out.add(a);
		if (b != null) out.add(b);
		return out;
	}
}
