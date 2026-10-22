package nl.greatrivers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The working countryside, so no two views look the same: every parcel between the ditches is a meadow, an arable
 * field (wheat, potatoes, carrots, beets in long rows), an orchard, or now and then a glasshouse complex
 * ("kassen", lit at night); and rows of big wind turbines along straight lines through the polders.
 * Everything is a pure function of the position, built one column at a time.
 */
final class Landscape {
	private Landscape() {
	}

	static final int MEADOW = 0, CROPS = 1, ORCHARD = 2, GLASS = 3;
	private static final ConcurrentHashMap<Long, Integer> KINDS = new ConcurrentHashMap<>();
	private static volatile RiverField kindsOwner;

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	/** What this parcel is used for (worked out once per parcel: its corners must be free countryside). */
	static int kind(RiverField f, long[] p) {
		if (kindsOwner != f) {
			KINDS.clear();
			kindsOwner = f;
		}
		long id = p[0];
		Integer k = KINDS.get(id);
		if (k != null) return k;
		long h = Harbours.mix(id ^ 0x4C414E44L);
		int roll = (int) Math.floorMod(h, 100L);
		int kind = roll < 52 ? MEADOW : roll < 80 ? CROPS : roll < 93 ? ORCHARD : GLASS;
		if (kind != MEADOW) {
			for (int i = 0; i <= 4 && kind != MEADOW; i++) {
				int la = i == 4 ? 70 : (i & 1) * 139, lb = i == 4 ? 70 : (i >> 1) * 139;
				int[] q = f.low.parcelPoint(p[3], p[4], la, lb);
				if (!free(f, q[0], q[1])) kind = MEADOW;
			}
		}
		KINDS.put(id, kind);
		return kind;
	}

	/** Open countryside: on land, away from the river, the towns, the harbours and the roads. */
	static boolean free(RiverField f, int x, int z) {
		if (f.coastal(x, z) < Lowlands.COAST + 0.08) return false;
		RiverField.Kind k = f.great;
		if (k.distance(x, z) - k.halfWidth(x, z) < Lowlands.foreland(k) + 60) return false;
		if (f.villages.site(x, z) != null || f.harbours.siteAt(x, z) != null) return false;
		return !f.infra.near(x, z, 12);
	}

	/** One column of the countryside. Returns true if something was built. */
	static boolean column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		boolean any = turbine(w, m, f, x, z);
		long[] p = f.low.parcel(x, z);
		int la = (int) p[1], lb = (int) p[2];
		if (la < 4 || la > 135 || lb < 4 || lb > 135) return any;     // grass strips along the ditches
		int kind = kind(f, p);
		if (kind == MEADOW) return any;
		int top = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
		BlockState g = w.getBlockState(m.set(x, top, z));
		if (!g.isOf(Blocks.GRASS_BLOCK) && !g.isOf(Blocks.DIRT)) return any;
		long h = Harbours.mix(p[0] * 31 + la * 1009L + lb);
		switch (kind) {
			case CROPS -> crops(w, m, p, x, top, z, la, lb, h);
			case ORCHARD -> orchard(w, m, x, top, z, la, lb, h);
			case GLASS -> glasshouse(w, m, x, top, z, la, lb, h);
			default -> {
			}
		}
		return true;
	}

	private static void clear(StructureWorldAccess w, BlockPos.Mutable m, int x, int top, int z, int h) {
		for (int y = top + 1; y <= top + h; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && st.getFluidState().isEmpty() && (st.isReplaceable() || st.isIn(BlockTags.FLOWERS) || st.isIn(BlockTags.LEAVES)
					|| st.isIn(BlockTags.LOGS) || st.isOf(Blocks.SUGAR_CANE) || st.isIn(BlockTags.SAPLINGS)))
				set(w, m, x, y, z, Blocks.AIR.getDefaultState());
		}
	}

	private static final Block[] CROP = {Blocks.WHEAT, Blocks.POTATOES, Blocks.CARROTS, Blocks.BEETROOTS, Blocks.WHEAT};

	/** An arable field: long rows of one crop, a tractor track every 12 rows. */
	private static void crops(StructureWorldAccess w, BlockPos.Mutable m, long[] p, int x, int top, int z, int la, int lb, long h) {
		clear(w, m, x, top, z, 12);
		Block crop = CROP[(int) Math.floorMod(Harbours.mix(p[0]), (long) CROP.length)];
		boolean rowsAlong = (Harbours.mix(p[0] + 7) & 1) == 0;
		int across = rowsAlong ? la : lb;
		if (across % 12 == 0) {
			set(w, m, x, top, z, Blocks.COARSE_DIRT.getDefaultState());      // track
			return;
		}
		set(w, m, x, top, z, Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7));
		int maxAge = crop == Blocks.BEETROOTS ? 3 : 7;
		int age = Math.max(0, Math.min(maxAge, maxAge - (int) Math.floorMod(Harbours.mix(p[0] + 3), 3L) - (int) (h & 1)));
		BlockState c = crop.getDefaultState();
		c = crop == Blocks.BEETROOTS ? c.with(Properties.AGE_3, Math.min(3, age)) : c.with(Properties.AGE_7, age);
		set(w, m, x, top + 1, z, c);
	}

	/** An orchard: low fruit trees in straight rows, in blossom here and there. */
	private static void orchard(StructureWorldAccess w, BlockPos.Mutable m, int x, int top, int z, int la, int lb, long h) {
		if (la % 6 != 3 || lb % 6 != 3) return;
		clear(w, m, x, top, z, 6);
		BlockState log = Blocks.OAK_LOG.getDefaultState();
		set(w, m, x, top + 1, z, log);
		set(w, m, x, top + 2, z, log);
		boolean blossom = (h & 3) == 0;
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++)
				for (int dy = 2; dy <= 4; dy++) {
					if (dy == 2 && dx == 0 && dz == 0) continue;
					if (dy == 4 && (dx != 0 && dz != 0)) continue;
					long hh = Harbours.mix(h + dx * 7 + dz * 13 + dy);
					Block leaf = blossom && (hh & 3) == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : (hh & 7) == 1 ? Blocks.AZALEA_LEAVES : Blocks.OAK_LEAVES;
					BlockState cur = w.getBlockState(m.set(x + dx, top + dy, z + dz));
					if (cur.isAir() || cur.isReplaceable())
						set(w, m, x + dx, top + dy, z + dz, leaf.getDefaultState().with(Properties.PERSISTENT, true));
				}
	}

	/** Glasshouses: a Venlo glasshouse of 120 x 120 with glass walls and a ridged glass roof, plants inside, lit. */
	private static void glasshouse(StructureWorldAccess w, BlockPos.Mutable m, int x, int top, int z, int la, int lb, long h) {
		int a = la - 10, b = lb - 10;      // 0..119 inside the parcel
		if (a < 0 || a > 119 || b < 0 || b > 119) {
			if (a >= -6 && a < 0 || b >= -6 && b < 0) set(w, m, x, top, z, Blocks.GRAVEL.getDefaultState());   // yard
			return;
		}
		clear(w, m, x, top, z, 10);
		boolean wall = a == 0 || a == 119 || b == 0 || b == 119;
		int floor = Math.max(top, 62);
		for (int y = top; y < floor; y++) set(w, m, x, y, z, Blocks.DIRT.getDefaultState());
		BlockState glass = Blocks.GLASS.getDefaultState(), frame = Blocks.SMOOTH_QUARTZ.getDefaultState();
		boolean post = a % 8 == 0 && b % 8 == 0;
		set(w, m, x, floor, z, (a % 4 == 2) ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.PODZOL.getDefaultState());
		for (int y = floor + 1; y <= floor + 4; y++) {
			BlockState st = Blocks.AIR.getDefaultState();
			if (wall) st = (post || y == floor + 4) ? frame : glass;
			else if (post) st = frame;
			set(w, m, x, y, z, st);
		}
		if (wall && b == 0 && a >= 58 && a <= 61) {
			set(w, m, x, floor + 1, z, Blocks.AIR.getDefaultState());
			set(w, m, x, floor + 2, z, Blocks.AIR.getDefaultState());
		}
		// ridged roof (each ridge 4 wide), white gutters between
		int r = a % 4;
		set(w, m, x, floor + 5, z, r == 0 ? frame : glass);
		if (r == 2) set(w, m, x, floor + 6, z, glass);
		// plants in rows, grow lights under the roof
		if (!wall && a % 4 != 2 && !post) {
			Block plant = (h & 1) == 0 ? Blocks.AZALEA : Blocks.FLOWERING_AZALEA;
			if (((x * 3 + z) & 1) == 0) set(w, m, x, floor + 1, z, (b % 5 == 0 ? Blocks.SWEET_BERRY_BUSH : plant).getDefaultState());
		}
		if (!wall && a % 8 == 4 && b % 8 == 4) set(w, m, x, floor + 4, z, Blocks.LIGHT.getDefaultState().with(Properties.LEVEL_15, 12));
	}

	// ------------------------------------------------------------------ wind turbines

	private static final int TCELL = 2400, TCOUNT = 5, TSPACE = 150, HUB = 58, BLADE = 22;

	/** Hub position and blade angle of turbine i of the row in this cell, or null when the cell has none. */
	private static double[] turbineAt(RiverField f, long seed, int cx, int cz, int i) {
		long h = Harbours.mix(seed * 13 + cx * 0x9E3779B97F4A7C15L + cz * 0x632BE59BD9B4E019L + 4242);
		if ((h & 1) == 0) return null;
		double ang = ((h >>> 8) & 0xffff) / 65536.0 * Math.PI;
		double sx = cx * TCELL + 400 + ((h >>> 24) & 0xffff) % (TCELL - 1400), sz = cz * TCELL + 400 + ((h >>> 40) & 0xffff) % (TCELL - 1400);
		double px = sx + Math.cos(ang) * TSPACE * i, pz = sz + Math.sin(ang) * TSPACE * i;
		int ix = (int) Math.floor(px), iz = (int) Math.floor(pz);
		if (!free(f, ix, iz)) return null;
		return new double[]{ix, iz, ((Harbours.mix(h + i) >>> 10) & 0xffff) / 65536.0 * 2 * Math.PI / 3};
	}

	private static final ConcurrentHashMap<Long, double[][]> ROWS = new ConcurrentHashMap<>();
	private static volatile RiverField rowsOwner;

	private static double[][] row(RiverField f, long seed, int cx, int cz) {
		if (rowsOwner != f) {
			ROWS.clear();
			rowsOwner = f;
		}
		long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
		return ROWS.computeIfAbsent(key, k -> {
			double[][] r = new double[TCOUNT][];
			for (int i = 0; i < TCOUNT; i++) r[i] = turbineAt(f, seed, cx, cz, i);
			return r;
		});
	}

	/** Tall white wind turbines in rows; the rotor faces west (into the wind off the sea). */
	private static boolean turbine(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		int cx = Math.floorDiv(x, TCELL), cz = Math.floorDiv(z, TCELL);
		long seed = w.getSeed();
		boolean any = false;
		double[][] rw = row(f, seed, cx, cz);
		for (int i = 0; i < TCOUNT; i++) {
			double[] t = rw[i];
			if (t == null) continue;
			int tx = (int) t[0], tz = (int) t[1];
			if (Math.abs(x - tx) > 4 || Math.abs(z - tz) > BLADE + 1) continue;
			int ground = (int) Math.floor(f.dutchHeight(tx, tz));   // from the terrain function: the tower may stand in another chunk
			int hub = ground + HUB;
			BlockState white = Blocks.WHITE_CONCRETE.getDefaultState();
			int dx = x - tx, dz = z - tz;
			// the tower: a wide foot (plus-shaped), slim above
			if (Math.abs(dx) + Math.abs(dz) <= 1) {
				int topY = (dx == 0 && dz == 0) ? hub - 1 : ground + 24;
				for (int y = ground - 1; y <= topY; y++) set(w, m, x, y, z, y <= ground ? Blocks.LIGHT_GRAY_CONCRETE.getDefaultState() : white);
				if (dx == 0 && dz == 0) set(w, m, x, ground + 1, z, Blocks.IRON_DOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.EAST)
						.with(Properties.DOUBLE_BLOCK_HALF, net.minecraft.block.enums.DoubleBlockHalf.LOWER));
				if (dx == 0 && dz == 0) set(w, m, x, ground + 2, z, Blocks.IRON_DOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.EAST)
						.with(Properties.DOUBLE_BLOCK_HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER));
				any = true;
			}
			// the nacelle: a long box on top, the hub in front (west)
			if (dz >= 0 && dz <= 1 && dx >= -2 && dx <= 3) {
				for (int y = hub; y <= hub + 1; y++) set(w, m, x, y, z, white);
				any = true;
			}
			if (dx == -3 && dz == 0) {
				set(w, m, x, hub, z, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
				any = true;
			}
			// three blades in the plane just in front of the hub
			if (dx == -3) {
				double hz = tz + 0.5, hy = hub + 0.5;
				for (int b = 0; b < 3; b++) {
					double th = t[2] + b * 2 * Math.PI / 3, c = Math.cos(th), s = Math.sin(th);
					double r0, r1;
					if (Math.abs(c) < 1e-3) {
						if (z + 0.5 < hz - 0.5 || z + 0.5 > hz + 0.5) continue;
						r0 = 1.5;
						r1 = BLADE;
					} else {
						double ra = (z - hz) / c, rb = (z + 1 - hz) / c;
						r0 = Math.max(1.5, Math.min(ra, rb));
						r1 = Math.min(BLADE, Math.max(ra, rb));
					}
					if (r0 > r1) continue;
					double y0 = hy + r0 * s, y1 = hy + r1 * s;
					for (int y = (int) Math.floor(Math.min(y0, y1)); y <= (int) Math.floor(Math.max(y0, y1)); y++)
						if (y > ground + 2) set(w, m, x, y, z, white);
					any = true;
				}
			}
		}
		return any;
	}
}
