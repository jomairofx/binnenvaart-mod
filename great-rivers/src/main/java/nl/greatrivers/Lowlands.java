package nl.greatrivers;

import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

/**
 * The Dutch landscape: large flat polders between the hills, with
 * - dikes along the rivers (river, foreland "uiterwaard", dike with a road on top, then the polder behind it)
 * - straight canals that link the rivers (every few kilometres, east-west and north-south)
 * - a grid of narrow drainage ditches ("sloten") through the meadows
 * Outside the polder regions the land keeps its hills and mountains.
 */
public final class Lowlands {
	public static final double POLDER = 62.3;      // top block of the polder land: y 62, level with the water in the ditches
	public static final double DIKE_TOP = 69.3;    // top of the dikes: y 69
	public static final int CANAL_X = 11000, CANAL_Z = 14000;
	public static final double CANAL_HALF = 17, CANAL_DEPTH = 9;
	public static final int DITCH_A = 140, DITCH_B = 700;

	private final SimplexNoiseSampler p1, p2, wood1, wood2;
	private final double ox, oz;
	private final int cx0, cz0, dx0, dz0;
	private final boolean ditchAlongX;

	Lowlands(Random r) {
		p1 = new SimplexNoiseSampler(r);
		p2 = new SimplexNoiseSampler(r);
		wood1 = new SimplexNoiseSampler(r);
		wood2 = new SimplexNoiseSampler(r);
		ox = (r.nextDouble() - 0.5) * 400000;
		oz = (r.nextDouble() - 0.5) * 400000;
		cx0 = r.nextInt(CANAL_X);
		cz0 = r.nextInt(CANAL_Z);
		dx0 = r.nextInt(DITCH_A);
		dz0 = r.nextInt(DITCH_B);
		ditchAlongX = r.nextBoolean();
	}

	/**
	 * Woods: 0 = open grassland, 1 = oak forest, 2 = tall birch forest, 3 = tall pine forest (on the sandy hills, like
	 * the Veluwe). Open meadows in the low polders, more and bigger woods on the higher sandy ground.
	 */
	public int wood(double x, double z, double lowland) {
		double n = 0.75 * wood1.sample((x + ox) / 900, (z + oz) / 900) + 0.25 * wood1.sample((x + ox) / 260, (z + oz) / 260);
		double limit = 0.22 + 0.45 * lowland;
		if (n < limit) return 0;
		double t = wood2.sample((x + ox) / 700, (z + oz) / 700);
		if (lowland < 0.4 && t < -0.1) return 3;
		return t > 0.35 ? 2 : 1;
	}

	/** Sea below this continentalness, land above it. Inland "oceans" of vanilla become land. */
	public static final double COAST = -0.45;

	/** 1 = low peat/clay polder (ditches, meadows), 0 = higher sandy ground (gentle rises, more woods). */
	public double lowland(double x, double z) {
		double n = 0.7 * p1.sample((x + ox) / 5200, (z + oz) / 5200) + 0.3 * p2.sample((x + ox) / 1500, (z + oz) / 1500);
		return smooth((n + 0.25) / 0.3);
	}

	/** Gentle rises on the sandy ground: at most three blocks, like the Dutch "zandgronden". */
	public double rise(double x, double z, double lowland) {
		// rolling hills on the higher sandy ground (up to about 16 blocks), flat in the polders
		double n = 0.65 * wood2.sample((x + ox) / 700 + 7.7, (z + oz) / 700 - 3.1) + 0.35 * p2.sample((x + ox) / 180 - 5.3, (z + oz) / 180 + 9.9);
		double h = Math.max(0, n + 0.1);
		return Math.pow(h, 1.4) * 22 * (1 - lowland);
	}

	/** Along the rivers: below -0.25 no trees, up to 0.3 a few, above 0.3 a row of pollard willows. */
	public double treeZone(double x, double z) {
		return wood1.sample((x + ox) / 420 - 13.7, (z + oz) / 420 + 29.1);
	}

	/** Now and then a small lake ("plas") in the polder. */
	public boolean lake(double x, double z) {
		return p1.sample((x + ox) / 260 + 41.3, (z + oz) / 260 - 17.9) > 0.78;
	}

	/** 0 = natural hills, 1 = flat polder land. */
	public double polder(double x, double z, double continentalness) {
		if (continentalness < -0.11) return 0;
		double n = 0.7 * p1.sample((x + ox) / 5200, (z + oz) / 5200) + 0.3 * p2.sample((x + ox) / 1500, (z + oz) / 1500);
		double f = clamp((n + 0.22) / 0.3);
		f *= clamp((continentalness + 0.11) / 0.07);   // gentle towards the coast and the beaches
		return f * f * (3 - 2 * f);
	}

	static double clamp(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	/** Distance to the middle of the nearest canal. */
	public double canalDistance(double x, double z) {
		if (true) return 1e9;   // no canals: they cut the rivers into pieces
		double a = Math.abs(Math.floorMod((long) Math.floor(x) - cx0, CANAL_X) + (x - Math.floor(x)));
		a = Math.min(a, CANAL_X - a);
		double b = Math.abs(Math.floorMod((long) Math.floor(z) - cz0, CANAL_Z) + (z - Math.floor(z)));
		b = Math.min(b, CANAL_Z - b);
		return Math.min(a, b);
	}

	/** On a drainage ditch line? */
	public boolean ditch(int x, int z) {
		int a = ditchAlongX ? z : x, b = ditchAlongX ? x : z;
		return Math.floorMod(a - dx0, DITCH_A) < 2 || Math.floorMod(b - dz0, DITCH_B) < 2;
	}

	/** Height of the dike profile at e blocks from the river edge, behind a foreland of width f; NaN past the dike. */
	public static double dike(double e, double f) {
		if (e < f) return Double.NaN;
		double k = e - f;
		if (k < 15) return POLDER + (DIKE_TOP - POLDER) * smooth(k / 15);
		if (k < 20) return DIKE_TOP;
		if (k < 35) return DIKE_TOP - (DIKE_TOP - POLDER) * smooth((k - 20) / 15);
		return Double.NaN;
	}

	/** Is this the road on top of the dike? */
	public static boolean crest(double e, double f) {
		double k = e - f;
		return k >= 15.5 && k < 19.5;
	}

	static double smooth(double t) {
		t = clamp(t);
		return t * t * (3 - 2 * t);
	}

	public static double foreland(RiverField.Kind k) {
		return k.flood;
	}
}
