package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fairway buoys ("vaarwegboeien") along the rivers, as on the Dutch rivers: red can buoys along one side of the
 * fairway and green cone buoys along the other, in pairs about every 200 blocks, a quarter of the river width in
 * from the banks. Every third pair carries a light. A red and a green buoy mark the entrance of every harbour.
 * Left out near locks, harbour mouths (except their own pair), bridges and under the railway.
 */
final class Buoys {
	static final int CELL = 200;
	static final double IN = 0.72;            // buoys stand at 0.72 of the half width from the middle
	private static final int[][] EMPTY = new int[0][];
	private static final ConcurrentHashMap<Long, int[][]> CELLS = new ConcurrentHashMap<>();
	private static volatile RiverField owner;

	private Buoys() {
	}

	/** The buoys of a cell: {x, z, colour (0 red, 1 green), lit}. */
	static int[][] cell(RiverField f, int i, int j) {
		if (owner != f) {
			CELLS.clear();
			owner = f;
		}
		if (CELLS.size() > 100000) CELLS.clear();
		long key = ((long) i << 32) ^ (j & 0xffffffffL);
		return CELLS.computeIfAbsent(key, k -> make(f, i, j));
	}

	private static int[][] make(RiverField f, int i, int j) {
		long h = Harbours.mix(i * 0x9E3779B97F4A7C15L + j * 0x632BE59BD9B4E019L + 0xB00);
		double px = i * CELL + 40 + Math.floorMod(h, 120L), pz = j * CELL + 40 + Math.floorMod(h >>> 20, 120L);
		RiverField.Kind k = f.great;
		double d = k.distance(px, pz);
		if (d > 220) return EMPTY;
		double v = k.value(px, pz);
		double[] n = k.normal(px, pz);
		double sg = Math.signum(v);
		double cx = px - n[0] * d * sg, cz = pz - n[1] * d * sg;
		// walk a little closer to the real middle
		for (int it = 0; it < 3; it++) {
			double dd = k.distance(cx, cz), vv = k.value(cx, cz);
			double[] nn = k.normal(cx, cz);
			cx -= nn[0] * dd * Math.signum(vv);
			cz -= nn[1] * dd * Math.signum(vv);
		}
		if (k.distance(cx, cz) > 8) return EMPTY;
		// the middle must lie in this cell, so neighbouring cells do not put two pairs side by side
		if (Math.floorDiv((int) Math.floor(cx), CELL) != i || Math.floorDiv((int) Math.floor(cz), CELL) != j) return EMPTY;
		n = k.normal(cx, cz);
		double hw = k.halfWidth(cx, cz);
		int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
		if (!f.land(ix, iz)) return EMPTY;
		if (f.infra.motorwayDistance(cx, cz) < 70 || f.rails.distance(cx, cz) < 50) return EMPTY;
		if (f.infra.locks.near(ix, iz, 120)) return EMPTY;
		if (f.harbours.siteNear(ix, iz, 60) != null) return EMPTY;
		Villages.Town t = f.villages.at(ix, iz);
		if (t != null && t.river() && Math.hypot(cx - t.cx(), cz - t.cz()) < 60) return EMPTY;    // the ferry
		boolean lit = Math.floorMod(h >>> 40, 3L) == 0;
		int[][] out = new int[2][];
		for (int s = 0; s < 2; s++) {
			double o = (s == 0 ? 1 : -1) * hw * IN;
			int bx = (int) Math.floor(cx + n[0] * o), bz = (int) Math.floor(cz + n[1] * o);
			// red on the side where the river noise is positive, green on the other: the same all along the river
			int colour = k.value(bx + 0.5, bz + 0.5) > 0 ? 0 : 1;
			out[s] = new int[]{bx, bz, colour, lit ? 1 : 0};
		}
		return out;
	}

	/** Builds a buoy if one stands in this column. */
	static boolean column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		double[] rv = f.riverAt(x, z, true);
		if (rv[0] - rv[1] > -6) return harbourMouth(w, m, f, x, z);
		int i = Math.floorDiv(x, CELL), j = Math.floorDiv(z, CELL);
		for (int di = -1; di <= 1; di++)
			for (int dj = -1; dj <= 1; dj++)
				for (int[] b : cell(f, i + di, j + dj))
					if (b[0] == x && b[1] == z) {
						buoy(w, m, x, z, b[2], b[3] == 1);
						return true;
					}
		return false;
	}

	/** The pair of buoys at the mouth of a harbour basin, a little out in the river. */
	private static boolean harbourMouth(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		Harbours.Site s = f.harbours.siteNear(x, z, 40);
		if (s == null || s.size() == Harbours.PORT) return false;
		double a = s.a(x + 0.5, z + 0.5), v = s.v(x + 0.5, z + 0.5) - s.bank();
		double half = s.A();
		for (int sd = -1; sd <= 1; sd += 2) {
			double ba = sd * (half + 6), bv = -12;
			int bx = (int) Math.floor(s.wx(ba, s.bank() + bv)), bz = (int) Math.floor(s.wz(ba, s.bank() + bv));
			if (bx == x && bz == z) {
				double[] rv = f.riverAt(x, z, true);
				if (rv[0] - rv[1] > -2) return false;
				int colour = f.great.value(x + 0.5, z + 0.5) > 0 ? 0 : 1;
				buoy(w, m, x, z, colour, true);
				return true;
			}
		}
		return false;
	}

	/** A red can buoy or a green cone buoy, anchored with a chain, a light on top where it is lit. */
	private static void buoy(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int colour, boolean lit) {
		int y0 = RiverField.WATER_TOP;
		BlockState body = colour == 0 ? Blocks.RED_CONCRETE.getDefaultState() : Blocks.GREEN_CONCRETE.getDefaultState();
		BlockState band = colour == 0 ? Blocks.RED_TERRACOTTA.getDefaultState() : Blocks.GREEN_TERRACOTTA.getDefaultState();
		// the anchor chain down to the river bed
		for (int y = y0 - 1; y > y0 - 24; y--) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (st.getFluidState().isEmpty() && !st.isAir()) break;
			w.setBlockState(m, Blocks.CHAIN.getDefaultState().with(Properties.WATERLOGGED, true), 2);
		}
		w.setBlockState(m.set(x, y0, z), body, 2);
		w.setBlockState(m.set(x, y0 + 1, z), band, 2);
		if (colour == 0) {
			// the can: flat top
			w.setBlockState(m.set(x, y0 + 2, z), body, 2);
			if (lit) w.setBlockState(m.set(x, y0 + 3, z), Blocks.RED_CANDLE.getDefaultState().with(Properties.LIT, true), 2);
		} else {
			// the cone: narrowing to a point
			w.setBlockState(m.set(x, y0 + 2, z), Blocks.GREEN_STAINED_GLASS_PANE.getDefaultState(), 2);
			if (lit) w.setBlockState(m.set(x, y0 + 3, z), Blocks.GREEN_CANDLE.getDefaultState().with(Properties.LIT, true), 2);
		}
	}

	/** The nearest buoy to a point, for /findriver and the tests: {x, z, colour}, or null. */
	static int[] nearest(RiverField f, int x, int z, int cells) {
		int i0 = Math.floorDiv(x, CELL), j0 = Math.floorDiv(z, CELL);
		int[] best = null;
		double bd = Double.MAX_VALUE;
		for (int i = -cells; i <= cells; i++)
			for (int j = -cells; j <= cells; j++)
				for (int[] b : cell(f, i0 + i, j0 + j)) {
					double d = Math.hypot(b[0] - x, b[1] - z);
					if (d < bd) {
						bd = d;
						best = b;
					}
				}
		return best;
	}
}
