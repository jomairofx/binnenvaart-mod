package nl.greatrivers;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Dutch villages and towns in the polder: a grid of brick-paved streets, rows of narrow brick houses with
 * gables to the street (two floors, furnished), a church with a tall spire on a paved square in the middle.
 * Villages are about 120 blocks across, towns about 300. One candidate per 1100 x 1100 cell.
 */
public final class Villages {
	public static final int CELL = 1100;
	public static final int PU = 34, PW = 26;      // street pitch along x and along z
	public static final int STREET = 5;            // street width (incl. pavements)

	public record Town(int cx, int cz, double radius, boolean city, long seed) {
		public String kind() {
			return city ? "town" : "village";
		}
	}

	private static final Town NONE = new Town(0, 0, 0, false, 0);
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
		// snap the centre to the street grid so the middle block is whole
		int px = cellX * CELL + 250 + (int) ((h >>> 8 & 0xffff) % (CELL - 500));
		int pz = cellZ * CELL + 250 + (int) ((h >>> 24 & 0xffff) % (CELL - 500));
		px = Math.floorDiv(px, PU) * PU + STREET;
		pz = Math.floorDiv(pz, PW) * PW + STREET;
		// on land, away from the river, its dikes and the harbours
		for (int a = 0; a < 12; a++) {
			double ang = a * Math.PI / 6;
			for (double rr : new double[]{0, r * 0.6, r + 20}) {
				int x = (int) (px + Math.cos(ang) * rr), z = (int) (pz + Math.sin(ang) * rr);
				if (field.continentalness(x, z) < Lowlands.COAST + 0.08) return NONE;
				RiverField.Kind k = field.great;
				if (k.distance(x, z) - k.halfWidth(x, z) < Lowlands.foreland(k) + 45) return NONE;
				if (field.harbours.siteAt(x, z) != null) return NONE;
				if (field.infra.motorwayDistance(x, z) < Infra.EDGE + 30) return NONE;
			}
		}
		return new Town(px, pz, r, city, h);
	}

	/** The village or town that covers this column, or null. */
	public Town at(int x, int z) {
		Town t = town(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (t == null) return null;
		double d = Math.hypot(x - t.cx, z - t.cz);
		return d <= t.radius + 24 ? t : null;
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

	/** Within the flattened area of the town. */
	public boolean flat(int x, int z) {
		Town t = at(x, z);
		return t != null && Math.hypot(x - t.cx, z - t.cz) <= t.radius + 16;
	}
}
