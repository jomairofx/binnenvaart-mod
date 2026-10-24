package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;

import java.util.concurrent.ConcurrentHashMap;

/**
 * High-voltage lines ("hoogspanningsmasten") across the polders: straight lines (north-south at a fixed x, or
 * east-west at a fixed z) about 8 km apart, a lattice tower about every 240 blocks.
 * - A tower that would land on a road, a dike or a farm yard moves up to 48 blocks along the line.
 * - Where a tower cannot stand at all (the river, a canal, a road junction) the line spans over it in one long
 *   span between two taller crossing towers, as long as that span does not cross a town, a city or a harbour.
 * - Every line is one unbroken chain of towers that starts and ends at a substation ("hoogspanningsstation"):
 *   a fenced yard with a gantry where the wires come down, transformers and a control building. Short pieces
 *   (fewer than five towers) are left out, so nothing stops in the middle of nowhere.
 */
final class Pylons {
	static final int PS = 8000, SPAN = 240, H = 34;
	static final int ARM1 = 24, ARM2 = 31, W1 = 7, W2 = 5;
	static final int CROSS_EXTRA = 22;               // crossing towers at a long span are this much taller
	static final int MIN_RUN = 5, SCAN = 260;
	static final int SUB_HALF_U = 14, SUB_HALF_C = 12, GANTRY = 10;
	private static final int[] SHIFTS = {0, 16, -16, 32, -32, 48, -48};
	private static final long NONE = Long.MIN_VALUE;
	private static final ConcurrentHashMap<Long, Long> SPOTS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Long, Long> RUNS = new ConcurrentHashMap<>();
	private static volatile RiverField owner;

	static final int NOTHING = 0, TOWER = 1, SUB = 2;

	private Pylons() {
	}

	private static long key(int fam, long k, long n) {
		return ((long) fam << 62) ^ ((k & 0xfffff) << 40) ^ (n & 0xffffffffffL);
	}

	private static void own(RiverField f) {
		if (owner != f) {
			SPOTS.clear();
			RUNS.clear();
			CLEAR.clear();
			owner = f;
		}
		if (SPOTS.size() > 200000) SPOTS.clear();
		if (RUNS.size() > 200000) RUNS.clear();
	}

	static double lineOff(long seed, int fam) {
		return Math.floorMod(Harbours.mix(seed + 0xE1EC + fam), (long) PS);
	}

	static double phase(long seed, int fam, long k) {
		return Math.floorMod(Harbours.mix(seed * 3 + fam * 977L + k * 31L), (long) SPAN);
	}

	static double[] point(int fam, long k, double u, double c, long seed) {
		double line = lineOff(seed, fam) + k * (double) PS + 0.5;
		return fam == 0 ? new double[]{line + c, u} : new double[]{u, line + c};
	}

	/** Where tower n of line (fam, k) stands: packed (shift, ground), or NONE. */
	private static long spot(RiverField f, int fam, long k, long n, long seed) {
		own(f);
		return SPOTS.computeIfAbsent(key(fam, k, n), kk -> {
			double u0 = n * (double) SPAN + phase(seed, fam, k);
			for (int s : SHIFTS) {
				double[] p = point(fam, k, u0 + s, 0, seed);
				int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
				boolean ok = true;
				// room for a substation too: 16 blocks all round
				for (int dx = -16; dx <= 16 && ok; dx += 16)
					for (int dz = -16; dz <= 16 && ok; dz += 16) if (!Landscape.free(f, x + dx, z + dz)) ok = false;
				if (!ok) continue;
				int g = (int) Math.floor(f.dutchHeight(x, z));
				if (g < RiverField.WATER_TOP + 1 || g > 90) continue;
				return ((long) (s + 64) << 32) | (g & 0xffffffffL);
			}
			return NONE;
		});
	}

	private static boolean present(RiverField f, int fam, long k, long n, long seed) {
		return spot(f, fam, k, n, seed) != NONE;
	}

	private static double pos(RiverField f, int fam, long k, long n, long seed) {
		long sp = spot(f, fam, k, n, seed);
		return n * (double) SPAN + phase(seed, fam, k) + ((int) (sp >>> 32) - 64);
	}

	private static int ground(RiverField f, int fam, long k, long n, long seed) {
		return (int) spot(f, fam, k, n, seed);
	}

	private static final ConcurrentHashMap<Long, Boolean> CLEAR = new ConcurrentHashMap<>();

	/** Can wires hang from tower a to tower b (a < b): not out over the sea, nor over a town, a city or a harbour? */
	private static boolean clear(RiverField f, int fam, long k, long a, long b, long seed) {
		if (CLEAR.size() > 200000) CLEAR.clear();
		return CLEAR.computeIfAbsent(key(fam, k, a) * 3 + (b - a), kk -> {
			double ua = pos(f, fam, k, a, seed), ub = pos(f, fam, k, b, seed);
			for (double u = ua; u <= ub; u += 16) {
				double[] p = point(fam, k, u, 0, seed);
				int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
				if (f.coastal(x, z) < Lowlands.COAST + 0.03) return false;
				if (f.villages.site(x, z) != null || f.metros.site(x, z) != null) return false;
				if (f.harbours.siteNear(x, z, 20) != null) return false;
			}
			return true;
		});
	}

	/** Tower n cannot stand, but the line can span over it from n-1 to n+1 (river, canal, junction). */
	private static boolean skippable(RiverField f, int fam, long k, long n, long seed) {
		if (present(f, fam, k, n, seed)) return false;
		if (!present(f, fam, k, n - 1, seed) || !present(f, fam, k, n + 1, seed)) return false;
		return clear(f, fam, k, n - 1, n + 1, seed);
	}

	/** The next tower along the line after n (+1, or +2 over a long span), or Long.MIN_VALUE. */
	private static long next(RiverField f, int fam, long k, long n, long seed, int dir) {
		if (present(f, fam, k, n + dir, seed))
			return clear(f, fam, k, Math.min(n, n + dir), Math.max(n, n + dir), seed) ? n + dir : Long.MIN_VALUE;
		if (skippable(f, fam, k, n + dir, seed)) return n + 2L * dir;
		return Long.MIN_VALUE;
	}

	/** Start and end tower of the line through tower n, packed as (n - start, end - n); -1 = no line here. */
	private static long run(RiverField f, int fam, long k, long n, long seed) {
		if (!present(f, fam, k, n, seed)) return -1;
		Long cached = RUNS.get(key(fam, k, n));
		if (cached != null) return cached;
		int count = 1;
		long[] end = new long[2];
		boolean[] open = new boolean[2];
		for (int side = 0; side < 2; side++) {
			int dir = side == 0 ? -1 : 1;
			long a = n;
			int steps = 0;
			while (true) {
				long b = next(f, fam, k, a, seed, dir);
				if (b == Long.MIN_VALUE) break;
				a = b;
				count++;
				if (++steps >= SCAN) {
					open[side] = true;
					break;
				}
			}
			end[side] = a;
		}
		long v;
		if (count < MIN_RUN && !open[0] && !open[1]) v = -1;
		else {
			long s = open[0] ? 0xffffffL : n - end[0], e = open[1] ? 0xffffffL : end[1] - n;
			v = (s << 32) | e;
		}
		RUNS.put(key(fam, k, n), v);
		return v;
	}

	/** What stands at tower spot n: NOTHING, TOWER or SUB (a substation at the end of a line). */
	static int role(RiverField f, int fam, long k, long n, long seed) {
		long r = run(f, fam, k, n, seed);
		if (r < 0) return NOTHING;
		return ((r >>> 32) == 0 || (r & 0xffffffffL) == 0) ? SUB : TOWER;
	}

	/** The crossing towers on both sides of a long span are taller. */
	private static int extra(RiverField f, int fam, long k, long n, long seed) {
		boolean longSpan = (!present(f, fam, k, n - 1, seed) && skippable(f, fam, k, n - 1, seed))
				|| (!present(f, fam, k, n + 1, seed) && skippable(f, fam, k, n + 1, seed));
		return longSpan ? CROSS_EXTRA : 0;
	}

	/** Direction (+1 / -1 along u) in which the line leaves a substation. */
	private static int subDir(RiverField f, int fam, long k, long n, long seed) {
		return next(f, fam, k, n, seed, 1) != Long.MIN_VALUE ? 1 : -1;
	}

	/** Height where conductor c (0 = earth wire on top) hangs at tower or substation n, and its position along u. */
	private static double[] attach(RiverField f, int fam, long k, long n, long seed, int c, int towards) {
		int g = ground(f, fam, k, n, seed);
		double u = pos(f, fam, k, n, seed);
		if (role(f, fam, k, n, seed) == SUB) {
			int dir = subDir(f, fam, k, n, seed);
			return new double[]{u + dir * GANTRY, c == 0 ? g + 14 : g + 11, 0.5};
		}
		int e = extra(f, fam, k, n, seed);
		int arm = Math.abs(c) == W1 ? ARM1 : Math.abs(c) == W2 ? ARM2 : H + 1;
		double hang = c == 0 ? 0 : 3;
		return new double[]{u, g + arm + e - hang, 3.5};
	}

	/** Builds this column of a high-voltage line. Returns true if it built something. */
	static boolean column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		long seed = w.getSeed();
		boolean any = false;
		for (int fam = 0; fam < 2; fam++) {
			double p = fam == 0 ? x + 0.5 : z + 0.5, u = fam == 0 ? z + 0.5 : x + 0.5;
			long k = Math.round((p - lineOff(seed, fam) - 0.5) / PS);
			int c = (int) Math.round(p - (lineOff(seed, fam) + k * (double) PS + 0.5));
			if (Math.abs(c) > SUB_HALF_C + 1) continue;
			double ph = phase(seed, fam, k);
			long n0 = (long) Math.floor((u - ph) / SPAN);
			// towers and substations
			for (long n = n0 - 1; n <= n0 + 2; n++) {
				double un = n * (double) SPAN + ph;
				if (Math.abs(u - un) > SPAN / 2.0 + 60) continue;
				int role = role(f, fam, k, n, seed);
				if (role == NOTHING) continue;
				int d = (int) Math.round(u - pos(f, fam, k, n, seed));
				int g = ground(f, fam, k, n, seed);
				if (role == TOWER && Math.abs(c) <= W1 + 1 && Math.abs(d) <= 3)
					any |= towerColumn(w, m, x, z, g, c, d, fam == 0, extra(f, fam, k, n, seed));
				else if (role == SUB && Math.abs(c) <= SUB_HALF_C && Math.abs(d) <= SUB_HALF_U) {
					substation(w, m, x, z, g, c, d * subDir(f, fam, k, n, seed), fam == 0);
					any = true;
				}
			}
			// the conductors between two neighbouring towers of the same line
			if (Math.abs(c) == W1 || Math.abs(c) == W2 || c == 0) {
				for (long a = n0 - 2; a <= n0 + 1; a++) {
					if (role(f, fam, k, a, seed) == NOTHING) continue;
					long b = next(f, fam, k, a, seed, 1);
					if (b == Long.MIN_VALUE || role(f, fam, k, b, seed) == NOTHING) continue;
					double[] A = attach(f, fam, k, a, seed, c, 1), B = attach(f, fam, k, b, seed, c, -1);
					if (u <= A[0] + A[2] || u >= B[0] - B[2]) continue;
					double L = B[0] - A[0];
					double t = (u - A[0]) / L;
					double sag = 5 * L / SPAN;
					double y = A[1] + (B[1] - A[1]) * t - sag * 4 * t * (1 - t);
					BlockState wire = Blocks.CHAIN.getDefaultState().with(Properties.AXIS, fam == 0 ? Direction.Axis.Z : Direction.Axis.X);
					int yy = (int) Math.floor(y);
					BlockState old = w.getBlockState(m.set(x, yy, z));
					if (old.isAir() || old.isReplaceable() || old.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
						w.setBlockState(m, wire, 2);
						any = true;
					}
					break;
				}
			}
		}
		return any;
	}

	/** One column of a lattice tower: four legs closing in towards the top, bracing every 6, two cross-arms. */
	private static boolean towerColumn(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int g, int c, int d, boolean ns, int e) {
		int across = c, along = d;
		BlockState bar = Blocks.IRON_BARS.getDefaultState();
		BlockState barAcross = bar.with(ns ? Properties.EAST : Properties.NORTH, true).with(ns ? Properties.WEST : Properties.SOUTH, true);
		boolean built = false;
		int taper = 26 + e;
		for (int h = 0; h <= H + 1 + e; h++) {
			int y = g + 1 + h;
			double hw = 3 - 2.0 * Math.min(h, taper) / taper;      // half width of the tower body
			int r = (int) Math.round(hw);
			boolean leg = Math.abs(across) == r && Math.abs(along) == r;
			boolean brace = h % 6 == 5 && (Math.abs(across) == r && Math.abs(along) <= r || Math.abs(along) == r && Math.abs(across) <= r);
			boolean arm = along == 0 && ((h == ARM1 + e && Math.abs(across) <= W1) || (h == ARM2 + e && Math.abs(across) <= W2));
			boolean peak = across == 0 && along == 0 && h > 27 + e;
			if (leg || brace || peak) {
				w.setBlockState(m.set(x, y, z), bar, 2);
				built = true;
			} else if (arm) {
				w.setBlockState(m.set(x, y, z), barAcross, 2);
				built = true;
			}
			// insulators hanging from the ends of the arms
			if (along == 0 && ((h < ARM1 + e && h >= ARM1 + e - 2 && Math.abs(across) == W1) || (h < ARM2 + e && h >= ARM2 + e - 2 && Math.abs(across) == W2))) {
				w.setBlockState(m.set(x, y, z), Blocks.CHAIN.getDefaultState(), 2);
				built = true;
			}
		}
		if (Math.abs(across) == 3 && Math.abs(along) == 3)
			w.setBlockState(m.set(x, g, z), Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(), 2);   // foundations
		return built;
	}

	/**
	 * One column of a substation. du runs along the line towards it (the gantry where the wires come down stands
	 * at du = GANTRY), c across.
	 */
	private static void substation(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int g, int c, int du, boolean ns) {
		int ac = Math.abs(c), ad = Math.abs(du);
		BlockState bars = Blocks.IRON_BARS.getDefaultState();
		// level gravel yard; clear plants and fill dips
		for (int y = g + 1; y <= g + 16; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && (st.isReplaceable() || st.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || st.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
					|| st.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS) || st.isOf(Blocks.GRASS_BLOCK) || st.isOf(Blocks.DIRT)))
				w.setBlockState(m, Blocks.AIR.getDefaultState(), 2);
		}
		for (int y = g - 3; y < g; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (st.isAir() || !st.getFluidState().isEmpty()) w.setBlockState(m, Blocks.DIRT.getDefaultState(), 2);
		}
		boolean road = du <= -SUB_HALF_U + 1 || (ac <= 1 && du < -7);
		w.setBlockState(m.set(x, g, z), road ? Blocks.GRAY_CONCRETE.getDefaultState() : Blocks.GRAVEL.getDefaultState(), 2);
		// the fence round the yard, a gate on the road side
		if (ac == SUB_HALF_C || ad == SUB_HALF_U - 1) {
			if (!(du < 0 && ad == SUB_HALF_U - 1 && ac <= 2)) {
				w.setBlockState(m.set(x, g + 1, z), bars, 2);
				w.setBlockState(m.set(x, g + 2, z), bars, 2);
			}
			return;
		}
		if (ad >= SUB_HALF_U) return;
		// the gantry: two lattice posts with a beam where the conductors come down, a little mast for the earth wire
		if (du == GANTRY) {
			if (ac == 9) for (int y = g + 1; y <= g + 12; y++) w.setBlockState(m.set(x, y, z), bars, 2);
			if (ac <= 9) w.setBlockState(m.set(x, g + 12, z), bars.with(ns ? Properties.EAST : Properties.NORTH, true).with(ns ? Properties.WEST : Properties.SOUTH, true), 2);
			if (ac == 7 || ac == 5) w.setBlockState(m.set(x, g + 11, z), Blocks.CHAIN.getDefaultState(), 2);
			if (c == 0) {
				w.setBlockState(m.set(x, g + 13, z), bars, 2);
				w.setBlockState(m.set(x, g + 14, z), Blocks.LIGHTNING_ROD.getDefaultState(), 2);
			}
			return;
		}
		// busbars on posts from the gantry to the transformers
		if ((ac == 7 || ac == 5) && du >= -1 && du < GANTRY) {
			if (du % 4 == 0) for (int y = g + 1; y <= g + 6; y++) w.setBlockState(m.set(x, y, z), Blocks.ANDESITE_WALL.getDefaultState(), 2);
			w.setBlockState(m.set(x, g + 7, z), Blocks.LIGHTNING_ROD.getDefaultState().with(Properties.FACING, ns ? Direction.SOUTH : Direction.EAST), 2);
			return;
		}
		// two transformers with cooling fins
		if (ac >= 3 && ac <= 9 && du >= -5 && du <= -2) {
			int top = g + 4;
			for (int y = g + 1; y <= top; y++) w.setBlockState(m.set(x, y, z), Blocks.GRAY_CONCRETE.getDefaultState(), 2);
			if ((ac == 4 || ac == 8) && du == -3) w.setBlockState(m.set(x, top + 1, z), Blocks.LIGHTNING_ROD.getDefaultState(), 2);
			return;
		}
		if (ac >= 3 && ac <= 9 && (du == -6 || du == -1)) {
			for (int y = g + 1; y <= g + 3; y++) w.setBlockState(m.set(x, y, z), bars, 2);
			return;
		}
		// the control building by the gate
		if (ac >= 4 && ac <= 10 && du >= -12 && du <= -8) {
			boolean wall = ac == 4 || ac == 10 || du == -12 || du == -8;
			for (int y = g + 1; y <= g + 3; y++) {
				BlockState st = wall ? Blocks.BRICKS.getDefaultState() : Blocks.AIR.getDefaultState();
				if (wall && y == g + 2 && (ac == 7) && (du == -12 || du == -8)) st = ns ? VillageBuilder.paneEW(Blocks.GLASS_PANE) : VillageBuilder.paneNS(Blocks.GLASS_PANE);
				w.setBlockState(m.set(x, y, z), st, 2);
			}
			w.setBlockState(m.set(x, g + 4, z), Blocks.SMOOTH_STONE_SLAB.getDefaultState(), 2);
			if (!wall && ac == 7 && du == -10) w.setBlockState(m.set(x, g + 3, z), Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true), 2);
		}
		if (ac == SUB_HALF_C - 1 && ad == SUB_HALF_U - 2) {
			for (int y = g + 1; y <= g + 5; y++) w.setBlockState(m.set(x, y, z), Blocks.ANDESITE_WALL.getDefaultState(), 2);
			w.setBlockState(m.set(x, g + 6, z), Blocks.SEA_LANTERN.getDefaultState(), 2);
		}
	}

	/** The nearest substation to a point (for /findriver and the tests): {x, z, ground, fam}, or null. */
	static double[] findSubstation(RiverField f, long seed, int x, int z) {
		double[] best = null;
		double bd = Double.MAX_VALUE;
		for (int fam = 0; fam < 2; fam++) {
			double p = fam == 0 ? x : z, u = fam == 0 ? z : x;
			long k0 = Math.round((p - lineOff(seed, fam) - 0.5) / PS);
			for (long k = k0 - 1; k <= k0 + 1; k++) {
				double ph = phase(seed, fam, k);
				long n0 = (long) Math.floor((u - ph) / SPAN);
				for (long n = n0 - 120; n <= n0 + 120; n++) {
					if (role(f, fam, k, n, seed) != SUB) continue;
					double[] q = point(fam, k, pos(f, fam, k, n, seed), 0, seed);
					double d = Math.hypot(q[0] - x, q[1] - z);
					if (d < bd) {
						bd = d;
						best = new double[]{q[0], q[1], ground(f, fam, k, n, seed), fam, subDir(f, fam, k, n, seed)};
					}
				}
			}
		}
		return best;
	}

	/** Check for the tests: every tower of every line near this point is part of a line with a substation or tower at both ends. */
	static String checkLines(RiverField f, long seed, int x, int z) {
		int towers = 0, subs = 0, lines = 0;
		for (int fam = 0; fam < 2; fam++) {
			double p = fam == 0 ? x : z, u = fam == 0 ? z : x;
			long k = Math.round((p - lineOff(seed, fam) - 0.5) / PS);
			double ph = phase(seed, fam, k);
			long n0 = (long) Math.floor((u - ph) / SPAN);
			for (long n = n0 - 100; n <= n0 + 100; n++) {
				int r = role(f, fam, k, n, seed);
				if (r == NOTHING) continue;
				if (r == SUB) subs++;
				else towers++;
				// every tower and substation has a neighbour on at least one side, a tower on both
				boolean up = next(f, fam, k, n, seed, 1) != Long.MIN_VALUE, dn = next(f, fam, k, n, seed, -1) != Long.MIN_VALUE;
				if (r == TOWER && !(up && dn)) return "FOUT: mast " + n + " van lijn " + fam + "/" + k + " staat aan een kant los";
				if (r == SUB && !(up || dn)) return "FOUT: station " + n + " van lijn " + fam + "/" + k + " zonder lijn";
				if (r == SUB && up && dn) return "FOUT: station " + n + " midden in een lijn";
			}
			lines++;
		}
		return "OK: " + towers + " masten, " + subs + " stations op " + lines + " lijnen";
	}
}
