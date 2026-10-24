package nl.greatrivers;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The street plan of a big city, the Dutch way instead of one big grid:
 * - an old centre ("binnenstad") of concentric canals ("grachtengordel") round the main square with the church,
 *   the canal houses along the canals, radial streets and cross alleys through the rings;
 * - around it the city is made of neighbourhoods ("wijken"), each with a street pattern of its own that does not
 *   line up with the next one;
 * - between the neighbourhoods run the curving main roads ("wijkontsluitingswegen") with cycle paths, trees and a
 *   green central reservation; where a neighbourhood's streets meet them, there is a strip of green;
 * - only the boulevards that cross the river on a bridge run straight through the city, as its main axes.
 *
 * Everything is a pure function of the city and the position (cached), so every chunk agrees.
 */
final class CityLayout {
	private CityLayout() {
	}

	static final int PU = Villages.PU, PW = Villages.PW, STREET = Villages.STREET;
	static final double SPACING = 360;        // neighbourhood size
	static final double ART = 10.5;           // half width of a main road
	static final int RING_P = 40;             // pitch of the canal rings of the old centre
	static final int SQUARE = 40;             // the main square in the middle
	static final double CHAMFER = 0.56;       // short diagonal sides: long straight canals with houses, cut corners

	/** What a column of the city is. */
	static final int ARTERIAL = 0, BLOCK = 1, STREETC = 2, GREEN = 3, OLD = 4, AXIS = 5, OUTSIDE = 6;

	static final class Info {
		int kind;
		double q;                 // ARTERIAL: distance from the middle of the road
		int u, wv;                // position in the neighbourhood grid (STREETC and BLOCK)
		int x0, z0;               // BLOCK / STREETC: origin of the grid cell (absolute)
		int wi, wj;               // the neighbourhood
	}

	// ------------------------------------------------------------------ the old centre

	/** Radius of the old centre with its canal rings (0: none, as in a low-rise town). */
	static double oldRadius(Metros.Metro mt) {
		if (mt.low()) return 0;
		if (mt.mega()) return 440;
		return Math.max(160, Math.min(320, mt.radius() * (mt.river() ? 0.24 : 0.18)));
	}

	/** Octagonal distance from the city centre (the rings of the old centre are octagons). */
	static double d8(double dx, double dz) {
		double ax = Math.abs(dx), az = Math.abs(dz);
		return Math.max(Math.max(ax, az), (ax + az) * CHAMFER);
	}

	/** Is (dx, dz) on a straight (axis) side of the octagon, not on a diagonal one? */
	static boolean axisSide(double dx, double dz) {
		double ax = Math.abs(dx), az = Math.abs(dz);
		return Math.max(ax, az) >= (ax + az) * CHAMFER - 0.01;
	}

	/** Number of whole canal rings. */
	static int rings(Metros.Metro mt) {
		return (int) Math.floor((oldRadius(mt) - SQUARE) / RING_P);
	}

	/** The old centre covers this point (the square and the rings). */
	static boolean inOld(Metros.Metro mt, double dx, double dz) {
		int n = rings(mt);
		return n > 0 && d8(dx, dz) < SQUARE + n * RING_P;
	}

	/** Is this column canal water of the old centre? */
	static boolean canal(Metros.Metro mt, int x, int z) {
		double dx = x + 0.5 - mt.cx(), dz = z + 0.5 - mt.cz();
		if (!inOld(mt, dx, dz)) return false;
		double s = d8(dx, dz) - SQUARE;
		if (s < 0) return false;
		double m = s - Math.floor(s / RING_P) * RING_P;
		return m >= 1 && m < 7;
	}

	// ------------------------------------------------------------------ the neighbourhoods

	private static final ConcurrentHashMap<Long, double[]> SEEDS = new ConcurrentHashMap<>();

	/** Neighbourhood (i, j): {seed x, seed z relative to the centre, grid offset u, grid offset w}. */
	static double[] seed(Metros.Metro mt, int i, int j) {
		if (SEEDS.size() > 200000) SEEDS.clear();
		long key = mt.seed() * 31 + ((long) i << 32) + (j & 0xffffffffL);
		return SEEDS.computeIfAbsent(key, k -> {
			long h = Harbours.mix(mt.seed() + i * 0x9E3779B97F4A7C15L + j * 0x632BE59BD9B4E019L + 0x3171);
			if (i == 0 && j == 0) return new double[]{0, 0, 0, 0};
			double jx = ((h & 0xffff) / 65536.0 - 0.5) * SPACING * 0.6;
			double jz = (((h >>> 16) & 0xffff) / 65536.0 - 0.5) * SPACING * 0.6;
			return new double[]{i * SPACING + jx, j * SPACING + jz, Math.floorMod(h >>> 32, (long) PU), Math.floorMod(h >>> 44, (long) PW)};
		});
	}

	/** A gentle warp, so the main roads between the neighbourhoods curve. */
	private static double wx(Metros.Metro mt, double dx, double dz) {
		double a = (mt.seed() & 1023) * 0.01;
		return dx + 38 * Math.sin(dz / 173.0 + a) + 17 * Math.sin(dz / 61.0 + a * 2.3);
	}

	private static double wz(Metros.Metro mt, double dx, double dz) {
		double a = ((mt.seed() >>> 10) & 1023) * 0.01;
		return dz + 38 * Math.sin(dx / 157.0 + a) + 17 * Math.sin(dx / 67.0 + a * 1.7);
	}

	/** {i, j of the nearest neighbourhood, distance to the border with the next one}. */
	static double[] wijk(Metros.Metro mt, double dx, double dz) {
		double px = wx(mt, dx, dz), pz = wz(mt, dx, dz);
		int i0 = (int) Math.round(px / SPACING), j0 = (int) Math.round(pz / SPACING);
		double b1 = Double.MAX_VALUE, b2 = Double.MAX_VALUE;
		int bi = 0, bj = 0;
		double s1x = 0, s1z = 0, s2x = 0, s2z = 0;
		for (int i = i0 - 1; i <= i0 + 1; i++)
			for (int j = j0 - 1; j <= j0 + 1; j++) {
				double[] s = seed(mt, i, j);
				double d = (px - s[0]) * (px - s[0]) + (pz - s[1]) * (pz - s[1]);
				if (d < b1) {
					b2 = b1;
					s2x = s1x;
					s2z = s1z;
					b1 = d;
					bi = i;
					bj = j;
					s1x = s[0];
					s1z = s[1];
				} else if (d < b2) {
					b2 = d;
					s2x = s[0];
					s2z = s[1];
				}
			}
		double sep = Math.hypot(s2x - s1x, s2z - s1z);
		double border = sep < 1e-6 ? 1e9 : (b2 - b1) / (2 * sep);
		return new double[]{bi, bj, border};
	}

	// ------------------------------------------------------------------ the straight axes over the river

	/** Is this column on one of the straight boulevards that lead to a bridge? {ns 1/0, p across, index} or null. */
	static int[] axis(RiverField f, Metros.Metro mt, int x, int z) {
		if (!mt.river()) return null;
		int gx = x - mt.cx(), gz = z - mt.cz();
		int bx = Math.floorDiv(gx, PU), bz = Math.floorDiv(gz, PW);
		int u = Math.floorMod(gx, PU), wv = Math.floorMod(gz, PW);
		int[] ab = MetroBuilder.avenueBridgeAt(f, mt, bx, bz);
		if (ab == null) return null;
		boolean ns = ab[0] == 1;
		int p = ns ? u - STREET : wv - STREET;
		if (p < 0) return null;
		return new int[]{ab[0], p, ab[1]};
	}

	// ------------------------------------------------------------------ blocks and streets

	private static final ConcurrentHashMap<Long, Boolean> BLOCKS = new ConcurrentHashMap<>();

	/** Is the grid cell with origin (x0, z0) of neighbourhood (i, j) a whole building block? */
	static boolean blockOk(RiverField f, Metros.Metro mt, int i, int j, int x0, int z0) {
		if (BLOCKS.size() > 400000) BLOCKS.clear();
		long key = mt.seed() ^ (x0 * 0x9E3779B97F4A7C15L) ^ (z0 * 0x632BE59BD9B4E019L) ^ ((long) i * 1000003 + j) * 0x2545F4914F6CDD1DL;
		return BLOCKS.computeIfAbsent(key, k -> {
			int[][] pts = {{x0 + STREET, z0 + STREET}, {x0 + PU - 1, z0 + STREET}, {x0 + STREET, z0 + PW - 1}, {x0 + PU - 1, z0 + PW - 1},
					{x0 + PU / 2, z0 + PW / 2}, {x0 + PU / 2, z0 + STREET}, {x0 + PU / 2, z0 + PW - 1}, {x0 + STREET, z0 + PW / 2}, {x0 + PU - 1, z0 + PW / 2}};
			for (int[] p : pts) {
				double dx = p[0] + 0.5 - mt.cx(), dz = p[1] + 0.5 - mt.cz();
				if (Math.hypot(dx, dz) > mt.radius() - 4) return false;
				if (inOld(mt, dx, dz) || d8(dx, dz) < SQUARE + rings(mt) * RING_P + 6 && rings(mt) > 0) return false;
				double[] wk = wijk(mt, dx, dz);
				if ((int) wk[0] != i || (int) wk[1] != j || wk[2] < ART + 2) return false;
				if (axis(f, mt, p[0], p[1]) != null) return false;
			}
			if (!Villages.level(x0, z0)) return false;
			// keep the axes clear also between the sample points (a boulevard is 29 wide)
			for (int sx = x0 + STREET; sx < x0 + PU; sx += 4)
				for (int sz = z0 + STREET; sz < z0 + PW; sz += 4)
					if (axis(f, mt, sx, sz) != null) return false;
			return true;
		});
	}

	/** Lays out this column. */
	static Info at(RiverField f, Metros.Metro mt, int x, int z) {
		Info in = new Info();
		double dx = x + 0.5 - mt.cx(), dz = z + 0.5 - mt.cz();
		if (Math.hypot(dx, dz) > mt.radius() + 2) {
			in.kind = OUTSIDE;
			return in;
		}
		int[] ax = axis(f, mt, x, z);
		if (ax != null) {
			in.kind = AXIS;
			return in;
		}
		if (inOld(mt, dx, dz)) {
			in.kind = OLD;
			return in;
		}
		double[] wk = wijk(mt, dx, dz);
		in.wi = (int) wk[0];
		in.wj = (int) wk[1];
		boolean nearOld = rings(mt) > 0 && d8(dx, dz) < SQUARE + rings(mt) * RING_P + 6;
		if (wk[2] < ART && !nearOld) {
			in.kind = ARTERIAL;
			in.q = wk[2];
			return in;
		}
		double[] s = seed(mt, in.wi, in.wj);
		int ox = mt.cx() + (int) s[2], oz = mt.cz() + (int) s[3];
		int gx = x - ox, gz = z - oz;
		in.u = Math.floorMod(gx, PU);
		in.wv = Math.floorMod(gz, PW);
		in.x0 = ox + Math.floorDiv(gx, PU) * PU;
		in.z0 = oz + Math.floorDiv(gz, PW) * PW;
		if (in.u >= STREET && in.wv >= STREET) {
			in.kind = blockOk(f, mt, in.wi, in.wj, in.x0, in.z0) ? BLOCK : GREEN;
			return in;
		}
		// a street of the neighbourhood: next to one of its blocks, or leading from its blocks to a main road
		boolean alongX = in.wv < STREET && in.u >= STREET, alongZ = in.u < STREET && in.wv >= STREET;
		boolean ok = false;
		if (alongX) ok = blockOk(f, mt, in.wi, in.wj, in.x0, in.z0) || blockOk(f, mt, in.wi, in.wj, in.x0, in.z0 - PW);
		else if (alongZ) ok = blockOk(f, mt, in.wi, in.wj, in.x0, in.z0) || blockOk(f, mt, in.wi, in.wj, in.x0 - PU, in.z0);
		else ok = blockOk(f, mt, in.wi, in.wj, in.x0, in.z0) || blockOk(f, mt, in.wi, in.wj, in.x0 - PU, in.z0)
					|| blockOk(f, mt, in.wi, in.wj, in.x0, in.z0 - PW) || blockOk(f, mt, in.wi, in.wj, in.x0 - PU, in.z0 - PW);
		if (!ok && (alongX || alongZ)) ok = connector(f, mt, in, x, z, alongX);
		in.kind = ok ? STREETC : GREEN;
		return in;
	}

	/** A stretch of street between the last block of the neighbourhood and a main road (or the old centre). */
	private static boolean connector(RiverField f, Metros.Metro mt, Info in, int x, int z, boolean alongX) {
		boolean toBlock = false, toRoad = false;
		for (int sg = -1; sg <= 1; sg += 2) {
			for (int k = 1; k <= 3; k++) {
				// the cells along this street line, k cells away
				int cx0 = alongX ? in.x0 + sg * k * PU : in.x0, cz0 = alongX ? in.z0 : in.z0 + sg * k * PW;
				boolean blk = alongX ? (blockOk(f, mt, in.wi, in.wj, cx0, cz0) || blockOk(f, mt, in.wi, in.wj, cx0, cz0 - PW))
						: (blockOk(f, mt, in.wi, in.wj, cx0, cz0) || blockOk(f, mt, in.wi, in.wj, cx0 - PU, cz0));
				if (blk) {
					toBlock = true;
					break;
				}
				// a main road or the edge of the old centre within this cell?
				int px = alongX ? cx0 + PU / 2 : x, pz = alongX ? z : cz0 + PW / 2;
				double dx = px + 0.5 - mt.cx(), dz = pz + 0.5 - mt.cz();
				double[] wk = wijk(mt, dx, dz);
				if (wk[2] < ART || (int) wk[0] != in.wi || (int) wk[1] != in.wj || axis(f, mt, px, pz) != null
						|| (rings(mt) > 0 && d8(dx, dz) < SQUARE + rings(mt) * RING_P + 6)) {
					toRoad = true;
					break;
				}
				if (Math.hypot(dx, dz) > mt.radius()) break;
			}
		}
		return toBlock && toRoad;
	}

	/** Is this column open ground (a road, a street, a square): no railway piers here. */
	static boolean open(RiverField f, Metros.Metro mt, int x, int z) {
		Info in = at(f, mt, x, z);
		if (in.kind == ARTERIAL || in.kind == STREETC || in.kind == AXIS) return true;
		if (in.kind == OLD) {
			double dx = x + 0.5 - mt.cx(), dz = z + 0.5 - mt.cz();
			return oldStreet(dx, dz) || canal(mt, x, z);
		}
		return false;
	}

	/** Radial streets and cross alleys of the old centre (in the house grid of 5 along the canals). */
	static boolean oldStreet(double dx, double dz) {
		int ix = (int) Math.floor(dx), iz = (int) Math.floor(dz);
		if (Math.floorMod(ix, 400) < 5 && Math.abs(ix) < 5) return true;
		if ((ix >= 0 && ix < 5) || (iz >= 0 && iz < 5)) return true;               // the two main radial streets
		if (!axisSide(dx, dz)) return Math.abs(Math.abs(dx) - Math.abs(dz)) < 3;   // the diagonal streets
		int along = Math.abs(dx) >= Math.abs(dz) ? iz : ix;
		return Math.floorMod(along, 60) < 5;                                       // cross alleys
	}
}
