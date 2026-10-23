package nl.greatrivers;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The big cities: 1.3 to 2.2 km across, one candidate per 9 x 9 km, on land or with the river running through
 * the middle. Each is laid out on the street grid of the towns, in districts:
 * - the old centre with canals ("grachten") lined with tall narrow canal houses, and the old church;
 * - a business district of office towers next to it;
 * - a ring of older neighbourhoods (row houses with gables, shops, schools) and post-war blocks of flats;
 * - outer neighbourhoods: new estates of modern row houses, a villa quarter, a large city park, and an industrial
 *   estate with distribution centres and factories;
 * - wide boulevards every few blocks, with a tram line, cycle paths and trees; in a river city every few of them
 *   cross the river on a high bridge (steel arch or cable-stayed), so every ship fits under.
 * Motorways keep away from the cities; provincial roads lead in from the villages around.
 */
public final class Metros {
	public static final int CELL = 9000;
	public static final double RING = 60;        // the slope from the city back down to the land
	public static final int OLD = 0, CBD = 1, MIXED = 2, FLATS = 3, ROWS = 4, SUBURB = 5, VILLAS = 6, INDUSTRY = 7, PARK = 8;
	public static final String[] DISTRICT = {"old centre", "business district", "inner city", "flats", "row houses",
			"new estate", "villas", "industrial estate", "city park"};

	public record Metro(int cx, int cz, double radius, long seed, boolean river, double rot) {
		/** The same city seen as a town (for the house builders). */
		public Villages.Town asTown() {
			return new Villages.Town(cx, cz, radius, true, seed, river);
		}

		/** District of the block (bx, bz) of the street grid. */
		public int district(int bx, int bz) {
			double mx = bx * Villages.PU + Villages.PU / 2.0, mz = bz * Villages.PW + Villages.PW / 2.0;
			double r = Math.hypot(mx, mz) / radius;
			double ang = Math.atan2(mz, mx) + rot;
			int sector = (int) Math.floor(Math.floorMod((long) Math.floor(ang / (2 * Math.PI) * 600), 600L) / 100.0);   // 0..5
			// a little irregularity at the borders between districts
			long h = Harbours.mix(seed + bx * 0x9E3779B97F4A7C15L + bz * 0x632BE59BD9B4E019L);
			r += ((h & 15) - 7.5) * 0.004;
			if (r < (river ? 0.24 : 0.15)) return OLD;
			if (r < (river ? 0.38 : 0.32)) return sector == 0 || sector == 1 ? CBD : MIXED;
			if (r < 0.62) {
				if (sector == 4 && r > 0.4 && r < 0.56) return PARK;
				return (sector & 1) == 0 ? FLATS : ROWS;
			}
			return switch (sector) {
				case 0, 3 -> SUBURB;
				case 1 -> INDUSTRY;
				case 2 -> VILLAS;
				case 4 -> r < 0.8 ? PARK : SUBURB;
				default -> ROWS;
			};
		}

		/** Is block column bx a boulevard running north-south / block row bz one running east-west? */
		public static boolean avenueX(int bx) {
			return Math.floorMod(bx, 6) == 3;
		}

		public static boolean avenueZ(int bz) {
			return Math.floorMod(bz, 7) == 3;
		}
	}

	private static final Metro NONE = new Metro(0, 0, 0, 0, false, 0);
	private final RiverField f;
	private final long seed;
	private final ConcurrentHashMap<Long, Metro> metros = new ConcurrentHashMap<>();

	Metros(RiverField f, long seed) {
		this.f = f;
		this.seed = seed;
	}

	public Metro metro(int cellX, int cellZ) {
		long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
		Metro m = metros.computeIfAbsent(key, k -> make(cellX, cellZ));
		return m == NONE ? null : m;
	}

	private Metro make(int cellX, int cellZ) {
		long h = Harbours.mix(seed * 97 + Harbours.mix(cellX * 0x2545F4914F6CDD1DL + cellZ * 0x9E3779B97F4A7C15L + 31337));
		if ((h & 3) == 0) return NONE;   // three in four cells
		double R = 650 + (int) (((h >>> 8) & 0xffff) % 450);
		double margin = R + RING + 250;
		double rot = ((h >>> 24) & 0xffff) / 65536.0 * 2 * Math.PI;
		RiverField.Kind k = f.great;
		// candidates: first on the river (half of the cities), then anywhere in the cell
		for (int attempt = 0; attempt < 14; attempt++) {
			long a = Harbours.mix(h + attempt * 77);
			int px = (int) (cellX * CELL + margin + Math.floorMod(a, (long) (CELL - 2 * margin)));
			int pz = (int) (cellZ * CELL + margin + Math.floorMod(a >>> 21, (long) (CELL - 2 * margin)));
			boolean river = false;
			if (attempt < 6 && ((h >>> 40) & 1) == 0) {
				int[] c = GreatRivers.nearest(k, px, pz, 2500);
				if (c == null) continue;
				if (c[0] < cellX * CELL + margin || c[0] > (cellX + 1) * CELL - margin || c[1] < cellZ * CELL + margin || c[1] > (cellZ + 1) * CELL - margin) continue;
				if (k.halfWidth(c[0], c[1]) > 118) continue;
				px = c[0];
				pz = c[1];
				river = true;
			}
			px = Math.floorDiv(px, Villages.PU) * Villages.PU + Villages.STREET;
			pz = Math.floorDiv(pz, Villages.PW) * Villages.PW + Villages.STREET;
			if (ok(px, pz, R, river)) return new Metro(px, pz, R, h, river || k.distance(px, pz) < R * 0.8, rot);
		}
		return NONE;
	}

	private boolean ok(int px, int pz, double R, boolean river) {
		RiverField.Kind k = f.great;
		for (int i = 0; i < 24; i++) {
			double ang = i * Math.PI / 12;
			for (double rr : new double[]{0, R * 0.5, R, R + RING + 40}) {
				int x = (int) (px + Math.cos(ang) * rr), z = (int) (pz + Math.sin(ang) * rr);
				if (f.continentalness(x, z) < Lowlands.COAST + 0.1) return false;
				if (f.infra.motorwayDistance(x, z) < Infra.EDGE + 60) return false;
				if (f.rails.distance(x, z) < Rails.HALF + 60) return false;
				if (!river && rr == 0 && k.distance(x, z) - k.halfWidth(x, z) < 200) return false;
			}
		}
		return true;
	}

	/** The city whose buildings and streets cover this column (within its edge), or null. */
	public Metro at(int x, int z) {
		Metro m = metro(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (m == null) return null;
		return Math.hypot(x - m.cx, z - m.cz) <= m.radius + 30 ? m : null;
	}

	/** The city whose site (with the slope around it) covers this column, or null. */
	public Metro site(int x, int z) {
		Metro m = metro(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (m == null) return null;
		return Math.hypot(x - m.cx, z - m.cz) <= m.radius + 30 + RING ? m : null;
	}

	/** Is a city within r of its edge from this point? */
	public boolean near(int x, int z, double r) {
		Metro m = metro(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (m == null) return false;
		return Math.hypot(x - m.cx, z - m.cz) <= m.radius + 30 + RING + r;
	}

	/** Nearest city to a point. */
	public Metro nearest(int x, int z, int cells) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Metro best = null;
		double bd = Double.MAX_VALUE;
		for (int i = -cells; i <= cells; i++)
			for (int j = -cells; j <= cells; j++) {
				Metro m = metro(cx + i, cz + j);
				if (m == null) continue;
				double d = Math.hypot(m.cx - x, m.cz - z);
				if (d < bd) {
					bd = d;
					best = m;
				}
			}
		return best;
	}
}
