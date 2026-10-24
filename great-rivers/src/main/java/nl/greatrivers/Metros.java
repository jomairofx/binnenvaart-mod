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

	public static final int NORMAL = 0, MEGA = 1, LOW = 2;

	/**
	 * A city. type NORMAL: 1.3-2.2 km; MEGA: a metropolis of 5 to 10 km with several centres and the railway on
	 * a viaduct through it; LOW: a big low-rise town of 3 to 5 km (old village centre, row houses, new estates,
	 * villas, no tall buildings).
	 */
	public record Metro(int cx, int cz, double radius, long seed, boolean river, double rot, int type, boolean coast) {
		/** The same city seen as a town (for the house builders). */
		public Villages.Town asTown() {
			return new Villages.Town(cx, cz, radius, true, seed, river, false);
		}

		public boolean mega() {
			return type == MEGA;
		}

		public boolean low() {
			return type == LOW;
		}

		public String kindName() {
			return type == MEGA ? "metropolis" : type == LOW ? "big town (low-rise)" : "big city";
		}

		/** District of the block (bx, bz) of the street grid. */
		public int district(int bx, int bz) {
			double mx = bx * Villages.PU + Villages.PU / 2.0, mz = bz * Villages.PW + Villages.PW / 2.0;
			long h = Harbours.mix(seed + bx * 0x9E3779B97F4A7C15L + bz * 0x632BE59BD9B4E019L);
			if (type == MEGA) return megaDistrict(mx, mz, h);
			if (type == LOW) return lowDistrict(mx, mz, h);
			double r = Math.hypot(mx, mz) / radius;
			double ang = Math.atan2(mz, mx) + rot;
			int sector = (int) Math.floor(Math.floorMod((long) Math.floor(ang / (2 * Math.PI) * 600), 600L) / 100.0);   // 0..5
			// a little irregularity at the borders between districts
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

		/** Second business centres of a metropolis ("Zuidas"): {x, z} relative to the centre. */
		public double[][] subCentres() {
			int n = 3;
			double[][] out = new double[n][];
			for (int i = 0; i < n; i++) {
				double a = rot + i * 2 * Math.PI / n + 0.7;
				double d = radius * (0.45 + 0.15 * ((seed >>> (i * 4)) & 3) / 3.0);
				out[i] = new double[]{Math.cos(a) * d, Math.sin(a) * d};
			}
			return out;
		}

		private int megaDistrict(double mx, double mz, long h) {
			double d = Math.hypot(mx, mz) + ((h & 15) - 7.5) * 6;
			double r = d / radius;
			if (d < 420) return OLD;
			double ang = Math.atan2(mz, mx) + rot;
			// a sector pattern that changes with the distance, so every part of the city looks different
			int ring = (int) Math.floor(r * 6);
			int sector = (int) Math.floorMod((long) Math.floor((ang / (2 * Math.PI) + ring * 0.37) * 12), 12L);
			if (d < 1000) return sector % 3 == 0 ? MIXED : CBD;
			for (double[] c : subCentres()) {
				double dc = Math.hypot(mx - c[0], mz - c[1]);
				if (dc < 330) return CBD;
				if (dc < 520) return sector % 2 == 0 ? FLATS : MIXED;
			}
			if (d < 1700) return switch (sector % 4) {
				case 0 -> FLATS;
				case 1 -> MIXED;
				default -> ROWS;
			};
			if (r < 0.62) return switch (sector) {
				case 0, 5 -> FLATS;
				case 3 -> PARK;
				case 8 -> INDUSTRY;
				case 10 -> MIXED;
				default -> ROWS;
			};
			return switch (sector) {
				case 0, 4, 9 -> SUBURB;
				case 1, 6 -> INDUSTRY;
				case 2, 11 -> VILLAS;
				case 7 -> PARK;
				case 3 -> FLATS;
				default -> SUBURB;
			};
		}

		private int lowDistrict(double mx, double mz, long h) {
			double d = Math.hypot(mx, mz) + ((h & 15) - 7.5) * 5;
			double r = d / radius;
			if (d < 230) return OLD;
			double ang = Math.atan2(mz, mx) + rot;
			int ring = (int) Math.floor(r * 4);
			int sector = (int) Math.floorMod((long) Math.floor((ang / (2 * Math.PI) + ring * 0.29) * 8), 8L);
			if (r < 0.4) return sector == 5 ? PARK : ROWS;
			return switch (sector) {
				case 0, 3, 6 -> SUBURB;
				case 1 -> r > 0.7 ? INDUSTRY : ROWS;
				case 2, 7 -> VILLAS;
				case 4 -> PARK;
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

	private static final Metro NONE = new Metro(0, 0, 0, 0, false, 0, NORMAL, false);
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
			if (ok(px, pz, R, river)) return new Metro(px, pz, R, h, river || k.distance(px, pz) < R * 0.8, rot, NORMAL, false);
		}
		return NONE;
	}

	private boolean ok(int px, int pz, double R, boolean river) {
		RiverField.Kind k = f.great;
		for (int i = 0; i < 24; i++) {
			double ang = i * Math.PI / 12;
			for (double rr : new double[]{0, R * 0.5, R, R + RING + 40}) {
				int x = (int) (px + Math.cos(ang) * rr), z = (int) (pz + Math.sin(ang) * rr);
				if (big(x, z, 400) != null) return false;
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
		Metro b = big(x, z, 30);
		if (b != null) return b.coast && f.continentalness(x, z) < Lowlands.COAST + 0.06 ? null : b;
		Metro m = metro(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (m == null) return null;
		return Math.hypot(x - m.cx, z - m.cz) <= m.radius + 30 ? m : null;
	}

	/** The city whose site (with the slope around it) covers this column, or null. */
	public Metro site(int x, int z) {
		Metro b = big(x, z, 30 + RING);
		if (b != null) return b;
		Metro m = metro(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (m == null) return null;
		return Math.hypot(x - m.cx, z - m.cz) <= m.radius + 30 + RING ? m : null;
	}

	/** Is a city within r of its edge from this point? */
	public boolean near(int x, int z, double r) {
		if (big(x, z, 30 + RING + r) != null) return true;
		Metro m = metro(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
		if (m == null) return false;
		return Math.hypot(x - m.cx, z - m.cz) <= m.radius + 30 + RING + r;
	}

	// ------------------------------------------------------------------ the metropolises and the big low-rise towns

	private final ConcurrentHashMap<Long, Metro> bigs = new ConcurrentHashMap<>();

	/**
	 * One per square between the motorways (17 x 17 km), in the middle of it, so the motorways run round it like
	 * ring roads: three in eight squares get a metropolis, two in eight a big low-rise town.
	 */
	public Metro big(int i, int j) {
		long key = ((long) i << 32) ^ (j & 0xffffffffL);
		Metro m = bigs.computeIfAbsent(key, k -> makeBig(i, j));
		return m == NONE ? null : m;
	}

	/** The metropolis or big town within m of its edge around this point, or null. */
	public Metro big(double x, double z, double m) {
		int i = (int) Math.floor((x - f.infra.offset(0)) / Infra.MW), j = (int) Math.floor((z - f.infra.offset(1)) / Infra.MW);
		Metro b = big(i, j);
		if (b == null) return null;
		return Math.hypot(x - b.cx, z - b.cz) <= b.radius + m ? b : null;
	}

	/** Why a square got no metropolis or big town (for the tests). */
	public static final ConcurrentHashMap<Long, String> WHY = new ConcurrentHashMap<>();

	private Metro makeBig(int i, int j) {
		long h = Harbours.mix(seed * 131 + Harbours.mix(i * 0x632BE59BD9B4E019L + j * 0x2545F4914F6CDD1DL + 0xB16));
		int roll = (int) Math.floorMod(h, 8L);
		int type = roll < 3 ? MEGA : roll < 5 ? LOW : -1;
		long wk = ((long) i << 32) ^ (j & 0xffffffffL);
		if (type < 0) {
			WHY.put(wk, "none");
			return NONE;
		}
		double rot = ((h >>> 28) & 0xffff) / 65536.0 * 2 * Math.PI;
		double want = type == MEGA ? 2500 + Math.floorMod(h >>> 44, 2500L) : 1500 + Math.floorMod(h >>> 44, 1000L);
		double min = type == MEGA ? 2500 : 1500;
		String why = "";
		// a few places in the square, from the middle outwards
		for (int attempt = 0; attempt < 6; attempt++) {
			long ha = Harbours.mix(h + attempt * 977L);
			double spread = attempt == 0 ? 512 : 1600;
			double cx0 = f.infra.offset(0) + (i + 0.5) * Infra.MW + Math.floorMod(ha, (long) (2 * spread)) - spread;
			double cz0 = f.infra.offset(1) + (j + 0.5) * Infra.MW + Math.floorMod(ha >>> 24, (long) (2 * spread)) - spread;
			int px = Math.floorDiv((int) cx0, Villages.PU) * Villages.PU + Villages.STREET;
			int pz = Math.floorDiv((int) cz0, Villages.PW) * Villages.PW + Villages.STREET;
			double max = want;
			// room to the motorways all round
			for (int a = 0; a < 64; a++) {
				double ang = a * Math.PI / 32;
				for (double rr = 300; rr <= max + RING + 700; rr += 100) {
					double x = px + Math.cos(ang) * rr, z = pz + Math.sin(ang) * rr;
					if (f.infra.motorwayDistance(x, z) < Junctions.REACH + 80) {
						max = Math.min(max, rr - RING - 700);
						break;
					}
				}
			}
			if (max < min) {
				why += " motorways(" + (int) max + ")";
				continue;
			}
			// on land: shrink until all of it is dry land
			// mostly on land: the heart of the city on dry land, the outer districts may end at the sea (a port city)
			for (double R = max; R >= min - 1; R = R > min ? Math.max(min, R * 0.9) : min - 2) {
				boolean ok = true;
				int sea = 0, all = 0;
				for (int a = 0; a < 48 && ok; a++) {
					double ang = a * Math.PI / 24;
					for (double rr : new double[]{0, R * 0.2, R * 0.4, R * 0.6, R * 0.8, R}) {
						int x = (int) (px + Math.cos(ang) * rr), z = (int) (pz + Math.sin(ang) * rr);
						all++;
						if (f.continentalness(x, z) < Lowlands.COAST + 0.06) {
							if (rr < R * 0.5) ok = false;
							sea++;
						}
					}
				}
				if (!ok || sea > all / 4) continue;
				boolean coast = sea > 0;
				boolean river = false;
				for (int a = 0; a < 48 && !river; a++) {
					double ang = a * Math.PI / 24;
					for (double rr = 0; rr <= R && !river; rr += 150)
						if (f.great.distance(px + Math.cos(ang) * rr, pz + Math.sin(ang) * rr) < f.great.halfWidth(px, pz) + 20) river = true;
				}
				WHY.put(wk, "ok");
				return new Metro(px, pz, R, h, river, rot, type, coast);
			}
			why += " sea";
		}
		WHY.put(wk, (type == MEGA ? "mega:" : "low:") + why);
		return NONE;
	}

	/** Nearest city to a point (of the normal cities within `cells` cells, and the metropolises and big towns). */
	public Metro nearest(int x, int z, int cells) {
		int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
		Metro best = null;
		double bd = Double.MAX_VALUE;
		int bi = (int) Math.floor((x - f.infra.offset(0)) / Infra.MW), bj = (int) Math.floor((z - f.infra.offset(1)) / Infra.MW);
		int bc = Math.max(1, cells * CELL / Infra.MW);
		for (int i = -bc; i <= bc; i++)
			for (int j = -bc; j <= bc; j++) {
				Metro m = big(bi + i, bj + j);
				if (m == null) continue;
				double d = Math.hypot(m.cx - x, m.cz - z) - m.radius;
				if (d < bd) {
					bd = d;
					best = m;
				}
			}
		for (int i = -cells; i <= cells; i++)
			for (int j = -cells; j <= cells; j++) {
				Metro m = metro(cx + i, cz + j);
				if (m == null) continue;
				double d = Math.hypot(m.cx - x, m.cz - z) - m.radius;
				if (d < bd) {
					bd = d;
					best = m;
				}
			}
		return best;
	}

	/** Nearest normal big city (not a metropolis or a big low-rise town), for the tests. */
	public Metro nearestNormal(int x, int z, int cells) {
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

	/** Nearest metropolis (type MEGA) or big low-rise town (LOW) to a point, within `squares` motorway squares. */
	public Metro nearestBig(int x, int z, int type, int squares) {
		int bi = (int) Math.floor((x - f.infra.offset(0)) / Infra.MW), bj = (int) Math.floor((z - f.infra.offset(1)) / Infra.MW);
		Metro best = null;
		double bd = Double.MAX_VALUE;
		for (int i = -squares; i <= squares; i++)
			for (int j = -squares; j <= squares; j++) {
				Metro m = big(bi + i, bj + j);
				if (m == null || m.type != type) continue;
				double d = Math.hypot(m.cx - x, m.cz - z);
				if (d < bd) {
					bd = d;
					best = m;
				}
			}
		return best;
	}
}
