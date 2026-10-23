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
 * east-west at a fixed z) about 8 km apart, a lattice tower every 240 blocks with two cross-arms, insulators, and the
 * conductors sagging between the towers. Towers are left out in towns, cities, harbours, near the river and the
 * roads; the cables only hang between two towers that both stand.
 */
final class Pylons {
	static final int PS = 8000, SPAN = 240, H = 34;
	static final int ARM1 = 24, ARM2 = 31, W1 = 7, W2 = 5;
	private static final ConcurrentHashMap<Long, Integer> TOWERS = new ConcurrentHashMap<>();
	private static volatile RiverField owner;

	private Pylons() {
	}

	/** Ground height of tower n of line (fam, k), or Integer.MIN_VALUE when it does not stand. */
	private static int tower(RiverField f, int fam, long k, long n, long seed) {
		if (owner != f) {
			TOWERS.clear();
			owner = f;
		}
		if (TOWERS.size() > 100000) TOWERS.clear();
		long key = ((long) fam << 62) ^ ((k & 0xfffff) << 40) ^ (n & 0xffffffffffL);
		return TOWERS.computeIfAbsent(key, kk -> {
			double[] p = point(fam, k, n * (double) SPAN + phase(seed, fam, k), 0, seed);
			int x = (int) Math.floor(p[0]), z = (int) Math.floor(p[1]);
			for (int dx = -8; dx <= 8; dx += 8)
				for (int dz = -8; dz <= 8; dz += 8) if (!Landscape.free(f, x + dx, z + dz)) return Integer.MIN_VALUE;
			return (int) Math.floor(f.dutchHeight(x, z));
		});
	}

	private static double lineOff(long seed, int fam) {
		return Math.floorMod(Harbours.mix(seed + 0xE1EC + fam), (long) PS);
	}

	private static double phase(long seed, int fam, long k) {
		return Math.floorMod(Harbours.mix(seed * 3 + fam * 977L + k * 31L), (long) SPAN);
	}

	private static double[] point(int fam, long k, double u, double c, long seed) {
		double line = lineOff(seed, fam) + k * (double) PS + 0.5;
		return fam == 0 ? new double[]{line + c, u} : new double[]{u, line + c};
	}

	/** Builds this column of a high-voltage line. Returns true if it built something. */
	static boolean column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		long seed = w.getSeed();
		boolean any = false;
		for (int fam = 0; fam < 2; fam++) {
			double p = fam == 0 ? x + 0.5 : z + 0.5, u = fam == 0 ? z + 0.5 : x + 0.5;
			long k = Math.round((p - lineOff(seed, fam) - 0.5) / PS);
			int c = (int) Math.round(p - (lineOff(seed, fam) + k * (double) PS + 0.5));
			if (Math.abs(c) > W1 + 1) continue;
			double ph = phase(seed, fam, k);
			long n0 = (long) Math.floor((u - ph) / SPAN);
			double u0 = n0 * (double) SPAN + ph;
			int du = (int) Math.round(u - u0);            // 0..SPAN from tower n0
			for (int which = 0; which <= 1; which++) {
				long n = n0 + which;
				int d = which == 0 ? du : du - SPAN;
				if (Math.abs(d) > 3) continue;
				int g = tower(f, fam, k, n, seed);
				if (g == Integer.MIN_VALUE) continue;
				any |= towerColumn(w, m, x, z, g, c, d, fam == 0);
			}
			// the conductors between two standing towers
			if (du > 3 && du < SPAN - 3 && (Math.abs(c) == W1 || Math.abs(c) == W2 || c == 0)) {
				int g0 = tower(f, fam, k, n0, seed), g1 = tower(f, fam, k, n0 + 1, seed);
				if (g0 == Integer.MIN_VALUE || g1 == Integer.MIN_VALUE) continue;
				double t = du / (double) SPAN;
				int arm = Math.abs(c) == W1 ? ARM1 : Math.abs(c) == W2 ? ARM2 : H + 1;
				double hang = c == 0 ? 0 : 3;
				double y = (g0 + (g1 - g0) * t) + arm - hang - 6 * 4 * t * (1 - t);
				BlockState wire = Blocks.CHAIN.getDefaultState().with(Properties.AXIS, fam == 0 ? Direction.Axis.Z : Direction.Axis.X);
				w.setBlockState(m.set(x, (int) Math.floor(y), z), wire, 2);
				any = true;
			}
		}
		return any;
	}

	/** One column of a lattice tower: four legs closing in towards the top, bracing every 6, two cross-arms. */
	private static boolean towerColumn(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int g, int c, int d, boolean ns) {
		int across = c, along = d;
		BlockState bar = Blocks.IRON_BARS.getDefaultState();
		BlockState barAcross = bar.with(ns ? Properties.EAST : Properties.NORTH, true).with(ns ? Properties.WEST : Properties.SOUTH, true);
		boolean built = false;
		for (int h = 0; h <= H + 1; h++) {
			int y = g + 1 + h;
			double hw = 3 - 2.0 * Math.min(h, 26) / 26;      // half width of the tower body
			int r = (int) Math.round(hw);
			boolean leg = Math.abs(across) == r && Math.abs(along) == r;
			boolean brace = h % 6 == 5 && (Math.abs(across) == r && Math.abs(along) <= r || Math.abs(along) == r && Math.abs(across) <= r);
			boolean arm = along == 0 && ((h == ARM1 && Math.abs(across) <= W1) || (h == ARM2 && Math.abs(across) <= W2));
			boolean peak = across == 0 && along == 0 && h > 27;
			if (leg || brace || peak) {
				w.setBlockState(m.set(x, y, z), bar, 2);
				built = true;
			} else if (arm) {
				w.setBlockState(m.set(x, y, z), barAcross, 2);
				built = true;
			}
			// insulators hanging from the ends of the arms
			if (along == 0 && ((h < ARM1 && h >= ARM1 - 2 && Math.abs(across) == W1) || (h < ARM2 && h >= ARM2 - 2 && Math.abs(across) == W2))) {
				w.setBlockState(m.set(x, y, z), Blocks.CHAIN.getDefaultState(), 2);
				built = true;
			}
		}
		if (built && Math.abs(across) <= 3 && Math.abs(along) <= 3 && Math.abs(across) == 3 && Math.abs(along) == 3)
			w.setBlockState(m.set(x, g, z), Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(), 2);   // foundations
		return built;
	}
}
