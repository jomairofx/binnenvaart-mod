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

	public record Town(int cx, int cz, double radius, boolean city, long seed, boolean river) {
		public String kind() {
			return river ? "river city" : city ? "town" : "village";
		}
	}

	private static final Town NONE = new Town(0, 0, 0, false, 0, false);
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
		double r = city ? 150 : 62;
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
				if (!river && k.distance(x, z) - k.halfWidth(x, z) < Lowlands.foreland(k) + 45) return NONE;
				if (field.harbours.siteAt(x, z) != null) return NONE;
				if (field.infra.motorwayDistance(x, z) < Infra.EDGE + 30) return NONE;
				if (field.metros.near(x, z, 150)) return NONE;
			}
		}
		return new Town(px, pz, r, city, h, river);
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
		return Math.hypot(mx - t.cx, mz - t.cz) <= t.radius;
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
