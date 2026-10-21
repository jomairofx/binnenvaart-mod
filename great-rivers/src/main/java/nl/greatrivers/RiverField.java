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

		/** Approximate distance (blocks) to the middle of this river. */
		public double distance(double x, double z) {
			double v = value(x, z);
			double h = 6;
			double gx = (value(x + h, z) - value(x - h, z)) / (2 * h);
			double gz = (value(x, z + h) - value(x, z - h)) / (2 * h);
			double g = Math.sqrt(gx * gx + gz * gz);
			if (g < 1e-12) return 1e9;
			return Math.abs(v) / g;
		}

		public double halfWidth(double x, double z) {
			double t = 0.5 + 0.5 * w.sample((x + ox) / wScale + 17.3, (z + oz) / wScale - 41.1);
			return (wMin + (wMax - wMin) * t) / 2;
		}

		/** Terrain may not stick out above this height here (the river bed, the banks and the valley sides). */
		double ceiling(double x, double z) {
			double hw = halfWidth(x, z);
			double d = distance(x, z);
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
		great = new Kind("great river", r, 15000, 1700, 7000, 120, 2600, 300, 500, 18000, 25, 220, 360);
		// long, fairly straight reaches with wide bends (no sharp meanders)
		river = new Kind("river", r, 7500, 520, 5200, 45, 2200, 70, 130, 7000, 15, 55, 150);
		harbours = new Harbours(this, seed);
	}

	// ---------- small per-thread cache: every column is asked for many times while a chunk is generated ----------
	private static final int CACHE = 1 << 12;

	private static final class Cache {
		final long[] keys = new long[CACHE];
		final double[] vals = new double[CACHE];
		final double[] flat = new double[CACHE];
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

	private void fill(Cache c, int i, long key, int x, int z) {
		double v = Math.min(Math.min(great.ceiling(x, z), river.ceiling(x, z)), oceanCeiling(x, z));
		double w = 0;
		Harbours.Site s = harbours.siteAt(x, z);
		if (s != null) {
			int k = Harbours.kind(s, x, z);
			if (k == Harbours.WATER) v = Math.min(v, Harbours.BASIN_FLOOR + 0.5);
			else w = Harbours.flatWeight(s, x, z);
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
