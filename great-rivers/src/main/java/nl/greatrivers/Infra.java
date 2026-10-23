package nl.greatrivers;

/**
 * Motorways ("snelwegen") across the whole land: two families of long, gently curving roads about 13 km apart,
 * one running roughly north-south and one roughly east-west. 2 x 2 lanes with hard shoulders and a central
 * reservation, on a low embankment in the polder and graded through the rises.
 * - Where a motorway meets the river it climbs on an earth ramp to a high bridge: steel arch over the fairway,
 *   concrete spans on piers over the rest of the river, the forelands and the dike. The deck lies so high that every
 *   ship fits under it with its wheelhouse raised.
 * - Where two motorways cross, the north-south one goes over the other on a viaduct.
 * Locks with their weirs and the roads to them are in {@link Locks}.
 *
 * Everything is a pure function of the seed and the position, so every chunk agrees on the roads.
 */
public final class Infra {
	public static final int MW = 13000;              // distance between the motorways of one family
	public static final int ROAD_Y = 63;             // road surface block in the polder (one above the land)
	public static final int DECK = 88;               // road surface block on the river bridges: underside at 86
	public static final double HALF = 12.5;          // half width of the paved road
	public static final double EDGE = 13.5;         // with verge / parapet
	public static final double BR_END = 100;         // the bridge reaches this far from the water's edge
	public static final double RAMP = 430;           // length of the earth ramp up to the bridge
	public static final double VIA_SPAN = 15.5;      // half span of a viaduct (across the road below)
	public static final double VIA_RAMP = 170;
	public static final int VIA_CLEAR = 9;           // viaduct deck this much above the road below
	public static final int ROAD = 0, RAMPK = 1, BRIDGE = 2, VIADUCT = 3;

	private final RiverField f;
	private final long seed;
	private final int[] off = new int[2];
	public final Locks locks;
	public final Junctions junctions;

	Infra(RiverField f, long seed) {
		this.f = f;
		this.seed = seed;
		off[0] = (int) Math.floorMod(Harbours.mix(seed + 0x51ED27L), (long) MW);
		off[1] = (int) Math.floorMod(Harbours.mix(seed + 0x7A3B19L), (long) MW);
		locks = new Locks(f, this, seed);
		junctions = new Junctions(f, this);
	}

	private double ph(int fam, int k, int n) {
		long h = Harbours.mix(seed * 31 + fam * 0x9E3779B97F4A7C15L + k * 0x632BE59BD9B4E019L + n * 0x2545F4914F6CDD1DL);
		return (h >>> 11) * 0x1.0p-53 * 2 * Math.PI;
	}

	/** Across-coordinate of motorway k of a family at along-coordinate u (family 0: x as a function of z). */
	public double line(int fam, int k, double u) {
		return off[fam] + (double) k * MW + 260 * Math.sin(u / 2900 + ph(fam, k, 1)) + 110 * Math.sin(u / 1150 + ph(fam, k, 2));
	}

	double slope(int fam, int k, double u) {
		return 260 / 2900.0 * Math.cos(u / 2900 + ph(fam, k, 1)) + 110 / 1150.0 * Math.cos(u / 1150 + ph(fam, k, 2));
	}

	double offset(int fam) {
		return off[fam];
	}

	/** Nearest motorway of a family: {k, signed distance across, u}. */
	public double[] nearestLine(int fam, double x, double z) {
		double p = fam == 0 ? x : z, u = fam == 0 ? z : x;
		int kc = (int) Math.round((p - off[fam]) / (double) MW);
		double best = 1e18;
		int bk = kc;
		for (int k = kc - 1; k <= kc + 1; k++) {
			double d = p - line(fam, k, u);
			if (Math.abs(d) < Math.abs(best)) {
				best = d;
				bk = k;
			}
		}
		double s = slope(fam, bk, u);
		return new double[]{bk, best / Math.sqrt(1 + s * s), u};
	}

	/** Distance to the middle of the nearest motorway line (whether or not it is built there). */
	public double motorwayDistance(double x, double z) {
		return Math.min(Math.abs(nearestLine(0, x, z)[1]), Math.abs(nearestLine(1, x, z)[1]));
	}

	public double[] centre(int fam, int k, double u) {
		double p = line(fam, k, u);
		return fam == 0 ? new double[]{p, u} : new double[]{u, p};
	}

	// ------------------------------------------------------------------ profile nodes (cached per thread)

	private static final int NC = 1 << 13;

	private static final class Nodes {
		final long[] key = new long[NC];
		final Infra[] owner = new Infra[NC];
		final double[] land = new double[NC], rise = new double[NC], e = new double[NC];
	}

	private static final ThreadLocal<Nodes> NODES = ThreadLocal.withInitial(Nodes::new);

	private double riseAt(double x, double z) {
		int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
		double lowl = f.low.lowland(ix, iz);
		double eRiver = f.great.distance(ix, iz) - f.great.halfWidth(ix, iz) - Lowlands.foreland(f.great);
		return f.low.rise(ix, iz, lowl) * Lowlands.smooth((eRiver - 40) / 220);
	}

	private static final int SC = 1 << 13;

	private static final class Sea {
		final long[] key = new long[SC];
		final Infra[] owner = new Infra[SC];
		final boolean[] sea = new boolean[SC];
	}

	private static final ThreadLocal<Sea> SEA = ThreadLocal.withInitial(Sea::new);

	/** Is the road middle at grid point g (every 40 blocks along the road) over the sea (or its foreshore)? */
	private boolean seaAt(int fam, int k, int g) {
		Sea c = SEA.get();
		long key = ((long) fam << 62) ^ ((long) (k & 0xfffff) << 40) ^ (g & 0xffffffffffL) ^ 0x5EA5EA5EA5L;
		int i = (int) (Harbours.mix(key) & (SC - 1));
		if (c.owner[i] == this && c.key[i] == key) return c.sea[i];
		double[] p = centre(fam, k, g * 40.0);
		boolean sea = f.coastal((int) Math.floor(p[0]), (int) Math.floor(p[1])) < Lowlands.COAST + 0.03;
		c.owner[i] = this;
		c.key[i] = key;
		c.sea[i] = sea;
		return sea;
	}

	/** Length of the stretch of sea around grid point g (in grid steps, capped). */
	private int seaRun(int fam, int k, int g, int cap) {
		int n = 1;
		for (int d = 1; d <= cap && seaAt(fam, k, g + d); d++) n++;
		for (int d = 1; d <= cap && seaAt(fam, k, g - d); d++) n++;
		return n;
	}

	/** {on land 0/1, smoothed rise of the land, distance of the road middle to the water's edge (river or sea)}. */
	private int node(int fam, int k, int un) {
		Nodes n = NODES.get();
		long key = ((long) fam << 62) ^ ((long) (k & 0xfffff) << 40) ^ (un & 0xffffffffffL);
		int i = (int) (Harbours.mix(key) & (NC - 1));
		if (n.owner[i] == this && n.key[i] == key) return i;
		double u = un * 8.0;
		double[] c = centre(fam, k, u);
		int cx = (int) Math.floor(c[0]), cz = (int) Math.floor(c[1]);
		// the sea along the road: a narrow arm is bridged like a river, at open sea the motorway ends well before
		// the coast (so it never stops on a ramp or halfway a bridge)
		int g0 = (int) Math.floor(u / 40);
		double seaE = Double.MAX_VALUE;
		boolean open = false;
		int reach = (int) ((BR_END + RAMP + 120) / 40);
		for (int d = 0; d <= reach + 4; d++) {
			for (int sg = -1; sg <= 1; sg += 2) {
				int g = g0 + sg * d;
				if (!seaAt(fam, k, g)) continue;
				double dist = Math.max(0, Math.abs(g * 40.0 - u) - 40);
				if (seaRun(fam, k, g, 26) > 25) {
					open = true;
				} else if (dist < seaE) seaE = dist;
			}
			if (open) break;
		}
		n.land[i] = open ? 0 : 1;
		double r = 0;
		for (int s = -96; s <= 96; s += 32) {
			double[] q = centre(fam, k, u + s);
			r += riseAt(q[0], q[1]);
		}
		n.rise[i] = r / 7;
		n.e[i] = Math.min(seaE, f.great.distance(cx, cz) - f.great.halfWidth(cx, cz));
		n.owner[i] = this;
		n.key[i] = key;
		return i;
	}

	/** A point of a motorway: where it is, how high, and what kind of structure carries it. */
	public static final class Hit {
		public int fam, k, kind, y;
		public double c, u, cx, cz, e, h, base;
		/** Viaduct: signed distance to the middle of the road below, and its surface block. */
		public double dv;
		public int underY;
	}

	/** The motorway profile at u (null where it is not built: out at sea). */
	public Hit prof(int fam, int k, double u) {
		int un = (int) Math.floor(u / 8);
		double fr = (u - un * 8.0) / 8;
		Nodes n = NODES.get();
		int a = node(fam, k, un);
		double la = n.land[a], ra = n.rise[a], ea = n.e[a];
		int b = node(fam, k, un + 1);
		if (la < 0.5 || n.land[b] < 0.5) return null;
		double rise = ra + (n.rise[b] - ra) * fr, e = ea + (n.e[b] - ea) * fr;
		Hit h = new Hit();
		h.fam = fam;
		h.k = k;
		h.u = u;
		double[] c = centre(fam, k, u);
		h.cx = c[0];
		h.cz = c[1];
		double base = ROAD_Y + rise;
		h.base = base;
		double H = base;
		int kind = ROAD;
		if (e < BR_END) {
			H = DECK;
			kind = BRIDGE;
		} else if (e < BR_END + RAMP) {
			double t = 1 - Lowlands.smooth((e - BR_END) / RAMP);
			double r = base + (DECK - base) * t;
			if (r > H + 0.5) {
				H = r;
				kind = RAMPK;
			}
		}
		if (fam == 0 && kind != BRIDGE) {
			double[] o = nearestLine(1, h.cx, h.cz);
			double dv = Math.abs(o[1]);
			if (dv < VIA_SPAN + VIA_RAMP) {
				Hit under = prof(1, (int) o[0], o[2]);
				if (under != null && under.kind == ROAD) {
					int yo = under.y;
					double hv = Math.max(Math.floor(base + 0.5), yo) + VIA_CLEAR;
					if (dv < VIA_SPAN) {
						H = hv;
						kind = VIADUCT;
						h.dv = o[1];
						h.underY = yo;
					} else {
						double t = 1 - Lowlands.smooth((dv - VIA_SPAN) / VIA_RAMP);
						double r = base + (hv - base) * t;
						if (r > H + 0.5) {
							H = r;
							kind = RAMPK;
						}
					}
				}
			}
		}
		h.e = e;
		h.h = H;
		h.kind = kind;
		h.y = (int) Math.floor(H + 0.5);
		return h;
	}

	/** Does the motorway end within d blocks along it (at open sea)? */
	public boolean endsNear(int fam, int k, double u, double d) {
		return prof(fam, k, u + d) == null || prof(fam, k, u - d) == null;
	}

	/** The motorway of a family that runs within maxC of this column, or null. */
	public Hit hit(int fam, double x, double z, double maxC) {
		double[] nl = nearestLine(fam, x, z);
		if (Math.abs(nl[1]) > maxC) return null;
		Hit h = prof(fam, (int) nl[0], nl[2]);
		if (h == null) return null;
		h.c = nl[1];
		return h;
	}

	/** Is anything of the roads (motorways, lock roads, locks) within r of this point? For windmills, farms ... */
	public boolean near(int x, int z, double r) {
		if (motorwayDistance(x, z) < Junctions.REACH + r) return true;
		if (f.roads.near(x, z, r)) return true;
		if (f.metros.near(x, z, r)) return true;
		return locks.near(x, z, r);
	}

	// ------------------------------------------------------------------ terrain

	/** Shapes the land for the roads: embankments, ramps, cuttings through the rises, spill slopes at the bridges. */
	public double shape(int x, int z, double t) {
		for (int fam = 0; fam < 2; fam++) {
			Hit h = hit(fam, x + 0.5, z + 0.5, EDGE + 48);
			if (h == null) continue;
			double a = Math.abs(h.c);
			if (h.kind == BRIDGE) {
				// earth spill slope under the end of the bridge, so the ramp does not end in a cliff
				double dEnd = BR_END - h.e;
				if (dEnd < 40) {
					double lo = DECK - 2.7 - Math.max(dEnd, Math.max(0, a - EDGE)) / 1.5;
					if (t < lo) t = lo;
				}
				continue;
			}
			if (h.kind == VIADUCT) continue;
			double top = h.y + 0.3;
			if (a <= EDGE) t = top;
			else {
				double lo = top - (a - EDGE) / 1.5, hi = top + (a - EDGE);
				if (t < lo) t = lo;
				if (t > hi) t = hi;
			}
		}
		t = junctions.shape(x, z, t);
		t = f.roads.shape(x, z, t);
		return locks.shape(x, z, t);
	}

	// ------------------------------------------------------------------ finding them (for /findriver and tests)

	/** Nearest river bridge: {x, z of the middle of the bridge, family, k}. */
	public double[] findBridge(int x, int z, int range) {
		double[] best = null;
		double bd = Double.MAX_VALUE;
		for (int fam = 0; fam < 2; fam++) {
			double p = fam == 0 ? x : z, u0 = fam == 0 ? z : x;
			int kc = (int) Math.round((p - off[fam]) / (double) MW);
			for (int k = kc - 2; k <= kc + 2; k++) {
				double prevD = Double.NaN;
				for (double u = u0 - range; u <= u0 + range; u += 24) {
					double[] c = centre(fam, k, u);
					double d = f.great.distance(c[0], c[1]) - f.great.halfWidth(c[0], c[1]);
					if (d < 0 && !(prevD < 0)) {
						// walk to the middle of the river along the road
						double bu = u, bdist = Double.MAX_VALUE;
						for (double v = u; v < u + 600; v += 4) {
							double[] q = centre(fam, k, v);
							double dd = f.great.distance(q[0], q[1]);
							if (dd < bdist) {
								bdist = dd;
								bu = v;
							}
						}
						double[] q = centre(fam, k, bu);
						if (prof(fam, k, bu) != null) {
							double dist = Math.hypot(q[0] - x, q[1] - z);
							if (dist < bd) {
								bd = dist;
								best = new double[]{q[0], q[1], fam, k};
							}
						}
					}
					prevD = d;
				}
			}
		}
		return best;
	}

	/** Nearest viaduct (crossing of two motorways on land, away from the river): {x, z}. */
	public double[] findViaduct(int x, int z, int cells) {
		int k0 = (int) Math.round((x - off[0]) / (double) MW), j0 = (int) Math.round((z - off[1]) / (double) MW);
		double[] best = null;
		double bd = Double.MAX_VALUE;
		for (int k = k0 - cells; k <= k0 + cells; k++)
			for (int j = j0 - cells; j <= j0 + cells; j++) {
				double cx = off[0] + (double) k * MW, cz = off[1] + (double) j * MW;
				for (int it = 0; it < 8; it++) {
					cx = line(0, k, cz);
					cz = line(1, j, cx);
				}
				Hit h = prof(0, k, cz);
				if (h == null || h.kind != VIADUCT) continue;
				double d = Math.hypot(cx - x, cz - z);
				if (d < bd) {
					bd = d;
					best = new double[]{cx, cz};
				}
			}
		return best;
	}
}
