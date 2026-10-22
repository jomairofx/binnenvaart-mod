package nl.greatrivers;

import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * The river network of one world. Two kinds of rivers, each the zero line of a slow, warped noise:
 * - great rivers (300-500 wide, deep, wide flood plain) roughly every 7 km
 * - Maas-size rivers (70-130 wide, strongly meandering) roughly every 2 km
 * Zero lines never end, so the rivers run on endlessly, cross each other (confluences) and flow into the seas.
 *
 * The water level is the sea level everywhere (y 63), so the rivers are one connected waterway with the oceans.
 */
public final class RiverField {
	public static final int WATER_TOP = 62;   // highest water block (sea level 63)

	/** The world currently being generated (set when the overworld noise is built). */
	public static volatile RiverField current;

	public static final class Kind {
		final String name;
		final double scale, warpAmp, warpScale, meanderAmp, meanderScale;
		final double wMin, wMax, wScale, depth, flood, valley;
		final SimplexNoiseSampler n, wx, wz, mx, mz, w, bank;
		final double ox, oz;   // random shift per world: simplex noise is zero at the origin
		boolean off;            // this family of rivers is not used

		Kind(String name, Random r, double scale, double warpAmp, double warpScale, double meanderAmp, double meanderScale,
			 double wMin, double wMax, double wScale, double depth, double flood, double valley) {
			this.name = name;
			this.scale = scale;
			this.warpAmp = warpAmp;
			this.warpScale = warpScale;
			this.meanderAmp = meanderAmp;
			this.meanderScale = meanderScale;
			this.wMin = wMin;
			this.wMax = wMax;
			this.wScale = wScale;
			this.depth = depth;
			this.flood = flood;
			this.valley = valley;
			n = new SimplexNoiseSampler(r);
			wx = new SimplexNoiseSampler(r);
			wz = new SimplexNoiseSampler(r);
			mx = new SimplexNoiseSampler(r);
			mz = new SimplexNoiseSampler(r);
			w = new SimplexNoiseSampler(r);
			bank = new SimplexNoiseSampler(r);
			ox = (r.nextDouble() - 0.5) * 400000;
			oz = (r.nextDouble() - 0.5) * 400000;
		}

		double value(double x, double z) {
			x += ox;
			z += oz;
			double px = x + warpAmp * wx.sample(x / warpScale, z / warpScale) + meanderAmp * mx.sample(x / meanderScale, z / meanderScale);
			double pz = z + warpAmp * wz.sample(x / warpScale, z / warpScale) + meanderAmp * mz.sample(x / meanderScale, z / meanderScale);
			return n.sample(px / scale, pz / scale);
		}

		/** Unit vector across the river (works in the middle of the river too, unlike the gradient of the distance). */
		public double[] normal(double x, double z) {
			double h = 6;
			double gx = value(x + h, z) - value(x - h, z), gz = value(x, z + h) - value(x, z - h);
			double l = Math.hypot(gx, gz);
			if (l < 1e-12) return new double[]{1, 0};
			return new double[]{gx / l, gz / l};
		}

		/** Approximate distance (blocks) to the middle of this river. */
		public double distance(double x, double z) {
			if (off) return 1e9;
			double v = value(x, z);
			double h = 6;
			double gx = (value(x + h, z) - value(x - h, z)) / (2 * h);
			double gz = (value(x, z + h) - value(x, z - h)) / (2 * h);
			double g = Math.sqrt(gx * gx + gz * gz);
			// where two stretches of river come close (a saddle of the noise) the gradient becomes small and the
			// estimate would flood a wide area: keep a minimum gradient, so the river just runs on at its width
			return Math.abs(v) / Math.max(g, 0.7 / scale);
		}

		public double halfWidth(double x, double z) {
			double t = 0.5 + 0.5 * w.sample((x + ox) / wScale + 17.3, (z + oz) / wScale - 41.1);
			return (wMin + (wMax - wMin) * t) / 2;
		}

		/** Terrain may not stick out above this height here (the river bed, the banks and the valley sides). */
		double ceiling(double x, double z) {
			return ceilingAt(x, z, distance(x, z), halfWidth(x, z));
		}

		/** River bed depth profile inside the channel (u = 0 middle, 1 edge). */
		double bed(double d, double hw) {
			double u = d / hw;
			double u2 = u * u;
			return WATER_TOP - depth * (1 - u2 * u2) + 0.3;
		}

		double ceilingAt(double x, double z, double d, double hw) {
			double reach = hw + flood + valley * 2.2;
			if (d > reach) return Double.POSITIVE_INFINITY;
			if (d < hw) {
				double u = d / hw;
				double u2 = u * u;
				// flat, deep fairway in the middle, sloping sides towards the banks
				return WATER_TOP - depth * (1 - u2 * u2) + 0.3;
			}
			double e = d - hw;
			double wobble = 1.2 * bank.sample((x + ox) / 40.0, (z + oz) / 40.0);
			if (e < 5) return WATER_TOP + 0.3 + e / 5 * (1.7 + wobble * 0.5);      // the bank: rises out of the water
			if (e < flood) {
				double f = (e - 5) / Math.max(1, flood - 5);
				return WATER_TOP + 2 + 3 * f * f + wobble;                           // flood plain, a few blocks above the water
			}
			double v = (e - flood) / valley;
			return WATER_TOP + 5 + wobble + 75 * Math.pow(v, 1.6);                  // valley sides up into the hills
		}

		public String name() {
			return name;
		}
	}

	public final Kind great, river;
	public final Harbours harbours;
	public final Lowlands low;
	public final Villages villages;
	public final Infra infra;
	public final Roads roads;
	public final Metros metros;

	/** Vanilla continentalness: below -0.19 is sea, below -0.455 deep sea, below -1.05 mushroom islands. */
	private final DensityFunction continents;
	public static final double OCEAN_DEPTH = 100;

	public double continentalness(int x, int z) {
		return continents == null ? 0 : continents.sample(new DensityFunction.UnblendedNoisePos(x, 0, z));
	}

	/** Seas get a deep floor: from the normal shallow coast down to 100 blocks deep out at sea. */
	public double oceanCeiling(int x, int z) {
		double c = continentalness(x, z);
		if (c > -0.17) return Double.POSITIVE_INFINITY;
		double f = (-0.17 - c) / (0.50 - 0.17);           // 0 at the coast, 1 out at (deep) sea
		if (c < -0.98) f = Math.max(0, (c + 1.06) / 0.08);   // keep the mushroom islands
		f = Math.max(0, Math.min(1, f));
		f = f * f * (3 - 2 * f);
		if (f < 0.001) return Double.POSITIVE_INFINITY;
		return WATER_TOP + 0.3 - 6 - (OCEAN_DEPTH - 6) * f;
	}

	public RiverField(long seed, DensityFunction continents) {
		this.continents = continents;
		Random r = Random.create(seed * 0x9E3779B97F4A7C15L + 0x6772656174L);
		// one single family of rivers: their middle lines never cross each other, so you can sail for kilometres
		// on the same river; the width changes slowly along it, from Maas-size (70) to Waal-size (240)
		great = new Kind("river", r, 9000, 1100, 4300, 80, 1800, 70, 240, 9000, 20, 45, 200);
		// long, fairly straight reaches with wide bends (no sharp meanders)
		river = new Kind("river", r, 7500, 520, 5200, 45, 2200, 70, 130, 7000, 15, 55, 150);
		river.off = true;
		harbours = new Harbours(this, seed);
		low = new Lowlands(r);
		villages = new Villages(this, seed);
		metros = new Metros(this, seed);
		roads = new Roads(this);
		infra = new Infra(this, seed);
	}

	// ---------- small per-thread cache: every column is asked for many times while a chunk is generated ----------
	private static final int CACHE = 1 << 12;

	private static final class Cache {
		final long[] keys = new long[CACHE];
		final double[] vals = new double[CACHE];
		final double[] flat = new double[CACHE];
		final double[] dutch = new double[CACHE];
		final double[] pold = new double[CACHE];
		final double[] riv = new double[CACHE * 4];
		final RiverField[] owner = new RiverField[CACHE];
	}

	private static final ThreadLocal<Cache> CACHES = ThreadLocal.withInitial(Cache::new);

	/** Highest allowed terrain at block column (x, z); +infinity when no river is near. */
	public double ceiling(int x, int z) {
		Cache c = CACHES.get();
		long key = ((long) x << 32) ^ (z & 0xffffffffL);
		int i = (int) ((x * 31L + z * 0x9E3779B1L) & (CACHE - 1));
		if (c.owner[i] != this || c.keys[i] != key) fill(c, i, key, x, z);
		return c.vals[i];
	}

	/** Weight of the flat harbour quay at this column (0 = no harbour). */
	public double flatWeight(int x, int z) {
		Cache c = CACHES.get();
		long key = ((long) x << 32) ^ (z & 0xffffffffL);
		int i = (int) ((x * 31L + z * 0x9E3779B1L) & (CACHE - 1));
		if (c.owner[i] != this || c.keys[i] != key) fill(c, i, key, x, z);
		return c.flat[i];
	}

	/** Dutch landscape target height at this column (polder, dikes, canals, ditches). */
	public double dutchHeight(int x, int z) {
		Cache c = CACHES.get();
		long key = ((long) x << 32) ^ (z & 0xffffffffL);
		int i = (int) ((x * 31L + z * 0x9E3779B1L) & (CACHE - 1));
		if (c.owner[i] != this || c.keys[i] != key) fill(c, i, key, x, z);
		return c.dutch[i];
	}

	/** How much of the Dutch landscape is used here (0 hills .. 1 polder). */
	public double polderWeight(int x, int z) {
		Cache c = CACHES.get();
		long key = ((long) x << 32) ^ (z & 0xffffffffL);
		int i = (int) ((x * 31L + z * 0x9E3779B1L) & (CACHE - 1));
		if (c.owner[i] != this || c.keys[i] != key) fill(c, i, key, x, z);
		return c.pold[i];
	}

	/** Distance to and half width of the great river ({@code great} true) or the Maas-size river, cached per column. */
	public double[] riverAt(int x, int z, boolean great) {
		Cache c = CACHES.get();
		long key = ((long) x << 32) ^ (z & 0xffffffffL);
		int i = (int) ((x * 31L + z * 0x9E3779B1L) & (CACHE - 1));
		if (c.owner[i] != this || c.keys[i] != key) fill(c, i, key, x, z);
		int o = i * 4 + (great ? 0 : 2);
		return new double[]{c.riv[o], c.riv[o + 1]};
	}

	/** How much this is low polder land (with ditches): 0 at sea and on the sandy rises. */
	public double polder(int x, int z) {
		double c = continentalness(x, z);
		if (c < Lowlands.COAST + 0.06) return 0;
		return low.lowland(x, z);
	}

	/** Is this column on the road on top of a dike? */
	public boolean dikeRoad(int x, int z) {
		if (flatWeight(x, z) > 0.01) return false;      // a harbour lies here
		Harbours.Site hsd = harbours.siteNear(x, z, 60);
		if (hsd != null && Harbours.dikeCrest(hsd, x + 0.5, z + 0.5)) return true;
		for (Kind k : new Kind[]{great, river}) {
			double e = k.distance(x, z) - k.halfWidth(x, z);
			double f = Lowlands.foreland(k);
			if (e > f && e < f + 25 && Lowlands.crest(e, f)) {
				// not where a canal or the other river cuts through
				if (low.canalDistance(x, z) < Lowlands.CANAL_HALF + 8) return false;
				return true;
			}
		}
		return false;
	}

	private double dutch(int x, int z, double dg, double hwg, double dr, double hwr) {
		double cont = coastal(x, z);
		double lowl = low.lowland(x, z);
		// hills fade out towards the river, so the dikes and forelands stay as they are
		double eRiver = dg - hwg - Lowlands.foreland(great);
		double t = Lowlands.POLDER + low.rise(x, z, lowl) * Lowlands.smooth((eRiver - 40) / 220);
		boolean sea = cont < Lowlands.COAST;
		if (cont < Lowlands.COAST + 0.045) {
			if (sea) {
				// the sea floor: three deep at the shore, down to 100 out at sea
				double f = Lowlands.smooth((Lowlands.COAST - cont) / 0.15);
				return Math.min(WATER_TOP + 0.3 - 3 - (OCEAN_DEPTH - 3) * f, riverMin(dg, hwg, dr, hwr, WATER_TOP + 0.3 - 3));
			}
			double u = (cont - Lowlands.COAST) / 0.045;   // 0 at the waterline, 1 behind the sea dike
			if (u < 0.2) t = WATER_TOP + 1.3;           // grassy foreshore
			else t = Math.max(t, Lowlands.POLDER + 6 * Math.sin(Math.PI * Math.min(1, (u - 0.2) / 0.6)));   // the sea dike
		}
		Kind[] ks = {great, river};
		double[] ds = {dg, dr}, hs = {hwg, hwr};
		boolean nearRiver = false;
		for (int k = 0; k < 2; k++) {
			double e = ds[k] - hs[k], f = Lowlands.foreland(ks[k]);
			if (e < f + 60) nearRiver = true;
			double h = Lowlands.dike(e, f);
			if (!Double.isNaN(h)) t = Math.max(t, h);
		}
		// a harbour cuts through the river dike: a dike goes round the back of the harbour instead
		Harbours.Site hsd = harbours.siteNear(x, z, 60);
		if (hsd != null) {
			double h = Harbours.dike(hsd, x + 0.5, z + 0.5);
			if (!Double.isNaN(h)) t = Math.max(t, h);
		}
		for (int k = 0; k < 2; k++) {
			double e = ds[k] - hs[k], f = Lowlands.foreland(ks[k]);
			if (e < 0) t = Math.min(t, ks[k].bed(ds[k], hs[k]));
			else if (e < 4) t = Math.min(t, WATER_TOP + 0.3 + e / 4 * 0.9);
			else if (e < f) t = Math.min(t, WATER_TOP + 1.3 + 0.6 * Lowlands.smooth((e - 4) / Math.max(1, f - 4)));  // the foreland, just above the water
		}
		double dc = low.canalDistance(x, z);
		if (dc < Lowlands.CANAL_HALF) {
			double u = dc / Lowlands.CANAL_HALF, u2 = u * u;
			t = Math.min(t, WATER_TOP + 0.3 - Lowlands.CANAL_DEPTH * (1 - u2 * u2 * u2));
		} else if (dc < Lowlands.CANAL_HALF + 2) t = Math.min(t, WATER_TOP + 1.3);                 // the canal bank
		else if (!nearRiver && dc > Lowlands.CANAL_HALF + 12 && lowl > 0.75 && cont > Lowlands.COAST + 0.06 && villages.site(x, z) == null && metros.site(x, z) == null) {
			if (low.lake(x, z)) t = Math.min(t, WATER_TOP - 3.7);                                       // a small lake
			else if (low.ditch(x, z)) t = Math.min(t, WATER_TOP - 1.7);                                 // ditch: two deep
		}
		// villages and towns stand on a level, slightly raised site that slopes gently back to the land; in a
		// river city the quays stand straight up from the water
		Villages.Town tw = villages.site(x, z);
		if (tw != null && cont > Lowlands.COAST + 0.06) {
			double d = Math.hypot(x - tw.cx(), z - tw.cz()), flatR = tw.radius() + 16;
			double g = d <= flatR ? 1 : 1 - Lowlands.smooth((d - flatR) / Villages.RING);
			boolean water = tw.river() && dg - hwg < 0;
			if (!water && g > 0) t = t * (1 - g) + (Villages.GROUND + 0.3) * g;
		}
		// the big cities: level ground at street height, the river stays, canals in the old centre
		Metros.Metro mt = metros.site(x, z);
		if (mt != null && cont > Lowlands.COAST + 0.06) {
			double d = Math.hypot(x - mt.cx(), z - mt.cz()), flatR = mt.radius() + 30;
			double g = d <= flatR ? 1 : 1 - Lowlands.smooth((d - flatR) / Metros.RING);
			boolean water = dg - hwg < 0;
			if (!water && g > 0) t = t * (1 - g) + (Villages.GROUND + 0.3) * g;
			if (!water && d <= mt.radius() + 30 && MetroBuilder.canalWater(mt, x, z)) t = Math.min(t, RiverField.WATER_TOP - 3.7);
		}
		// motorways, bridge ramps, viaducts, locks and the roads to them
		return infra.shape(x, z, t);
	}

	/** River bed where a river runs out into the sea. */
	private double riverMin(double dg, double hwg, double dr, double hwr, double t) {
		if (dg < hwg) t = Math.min(t, great.bed(dg, hwg));
		if (dr < hwr) t = Math.min(t, river.bed(dr, hwr));
		return t;
	}

	/** Is this column on land (not sea)? */
	/**
	 * Continentalness as used for the coast: small pockets and narrow arms of sea are filled in, so the only
	 * open water besides the rivers is the real, wide sea.
	 */
	public double coastal(int x, int z) {
		double c = continentalness(x, z);
		if (c >= Lowlands.COAST + 0.05) return c;
		int r = 128;
		c = Math.max(c, Math.max(Math.max(continentalness(x + r, z), continentalness(x - r, z)),
				Math.max(continentalness(x, z + r), continentalness(x, z - r))));
		return c;
	}

	public boolean land(int x, int z) {
		return coastal(x, z) >= Lowlands.COAST;
	}

	private void fill(Cache c, int i, long key, int x, int z) {
		double dg = great.distance(x, z), hwg = great.halfWidth(x, z);
		double dr = river.distance(x, z), hwr = river.halfWidth(x, z);
		// the whole world is Dutch lowland now: our own height everywhere, vanilla only for the deep underground
		double v = Double.POSITIVE_INFINITY;
		c.pold[i] = 1;
		c.dutch[i] = dutch(x, z, dg, hwg, dr, hwr);
		c.riv[i * 4] = dg;
		c.riv[i * 4 + 1] = hwg;
		c.riv[i * 4 + 2] = dr;
		c.riv[i * 4 + 3] = hwr;
		double w = 0;
		Harbours.Site s = harbours.siteAt(x, z);
		if (s != null) {
			int k = Harbours.kind(s, x + 0.5, z + 0.5);
			if (k == Harbours.WATER) {
				v = Math.min(v, Harbours.BASIN_FLOOR + 0.5);
				if (!Double.isNaN(c.dutch[i])) c.dutch[i] = Math.min(c.dutch[i], Harbours.BASIN_FLOOR + 0.5);
			}
			else w = Harbours.flatWeight(s, x + 0.5, z + 0.5);
		}
		c.owner[i] = this;
		c.keys[i] = key;
		c.vals[i] = v;
		c.flat[i] = w;
	}

	/** Is this column inside a river channel (for the river biome)? */
	public boolean inChannel(int x, int z) {
		return great.distance(x, z) < great.halfWidth(x, z) + 2 || river.distance(x, z) < river.halfWidth(x, z) + 2;
	}
}
