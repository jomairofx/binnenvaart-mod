package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

/**
 * The woods, grown by hand instead of by the vanilla features: trees stand at random, in thickets and loose groups
 * with clearings between them, never in rows. Oak woods with some birch, tall birch woods, and tall pine woods on
 * the sandy hills; every tree its own height and crown; ferns, bushes and a fallen trunk here and there.
 */
final class Forest {
	private Forest() {
	}

	private static long hash(long x, long z, long s) {
		return Harbours.mix(x * 0x9E3779B97F4A7C15L + z * 0x632BE59BD9B4E019L + s * 0x2545F4914F6CDD1DL);
	}

	private static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}

	/** Chance of a trunk at this column. */
	private static double chance(RiverField f, int x, int z, int kind) {
		double d = f.low.woodDensity(x, z);
		double base = kind == 3 ? 0.060 : kind == 2 ? 0.050 : 0.040;
		return base * (0.08 + 1.8 * Math.pow(d, 1.6));
	}

	/** Does a tree start at this column (and no stronger one right next to it)? */
	private static boolean trunk(RiverField f, int x, int z, int kind) {
		double u = unit(hash(x, z, 1));
		if (u >= chance(f, x, z, kind)) return false;
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++) {
				if ((dx == 0 && dz == 0) || Math.abs(dx) + Math.abs(dz) > 3) continue;
				double v = unit(hash(x + dx, z + dz, 1));
				if (v < u && v < chance(f, x + dx, z + dz, kind)) return false;
			}
		return true;
	}

	static boolean column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		int kind = f.low.wood(x, z, f.low.lowland(x, z));
		if (kind == 0) return false;
		if (!Landscape.free(f, x, z)) return false;
		int top = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
		if (top < 62 || top > 120) return false;
		BlockState g = w.getBlockState(m.set(x, top, z));
		if (!g.isOf(Blocks.GRASS_BLOCK) && !g.isOf(Blocks.DIRT) && !g.isOf(Blocks.PODZOL) && !g.isOf(Blocks.COARSE_DIRT)) return false;
		long h = hash(x, z, 7);
		if (trunk(f, x, z, kind)) {
			int y = top + 1;
			if (kind == 3) pine(w, m, x, y, z, h);
			else if (kind == 2 || (h & 7) == 0) birch(w, m, x, y, z, h);
			else oak(w, m, x, y, z, h);
			return true;
		}
		// the ground of the wood
		double d = f.low.woodDensity(x, z);
		if (!w.getBlockState(m.set(x, top + 1, z)).isAir()) return false;
		if ((h % 1000) < 30 * d) set(w, m, x, top + 1, z, Blocks.FERN.getDefaultState());
		else if ((h % 1000) < 40 * d) set(w, m, x, top + 1, z, Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
		else if ((h % 5000) == 7 && kind != 3) {
			// a fallen trunk
			boolean ax = (h & 1) == 0;
			for (int i = 0; i < 4; i++) {
				int lx = x + (ax ? i : 0), lz = z + (ax ? 0 : i);
				if (w.getBlockState(m.set(lx, top + 1, lz)).isAir())
					set(w, m, lx, top + 1, lz, Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS, ax ? net.minecraft.util.math.Direction.Axis.X : net.minecraft.util.math.Direction.Axis.Z));
			}
		}
		return false;
	}

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private static void leaf(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState leaves) {
		BlockState o = w.getBlockState(m.set(x, y, z));
		if (o.isAir() || o.isReplaceable()) set(w, m, x, y, z, leaves);
	}

	/** An oak: its own height and an irregular, round crown. */
	private static void oak(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, long h) {
		int trunk = 4 + (int) (h % 4);
		double r = 2.2 + (h >>> 8 & 3) * 0.45;
		BlockState log = Blocks.OAK_LOG.getDefaultState(), leaves = Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
		for (int i = 0; i < trunk; i++) set(w, m, x, y + i, z, log);
		int cy = y + trunk;
		int R = (int) Math.ceil(r);
		for (int dy = -2; dy <= R; dy++)
			for (int dx = -R; dx <= R; dx++)
				for (int dz = -R; dz <= R; dz++) {
					double dd = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.4);
					if (dd > r + 0.4) continue;
					if (dd > r - 0.8 && (hash(x + dx, (y + dy) * 31L + z + dz, 3) & 3) == 0) continue;   // ragged edge
					if (dx == 0 && dz == 0 && dy < 1) continue;
					leaf(w, m, x + dx, cy + dy, z + dz, leaves);
				}
		set(w, m, x, cy, z, log);
	}

	/** A tall birch with a narrow crown at the top. */
	private static void birch(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, long h) {
		int trunk = 7 + (int) (h % 5);
		BlockState log = Blocks.BIRCH_LOG.getDefaultState(), leaves = Blocks.BIRCH_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
		for (int i = 0; i < trunk; i++) set(w, m, x, y + i, z, log);
		int top = y + trunk;
		for (int dy = -4; dy <= 1; dy++) {
			int r = dy >= 0 ? 1 : 2;
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) == r && Math.abs(dz) == r && (hash(x + dx, top + dy, z + dz) & 1) == 0) continue;
					if (dx == 0 && dz == 0 && dy < 0) continue;
					leaf(w, m, x + dx, top + dy, z + dz, leaves);
				}
		}
	}

	/** A tall Scots pine: a long bare trunk, a flat, uneven crown at the top. */
	private static void pine(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, long h) {
		int trunk = 10 + (int) (h % 7);
		BlockState log = Blocks.SPRUCE_LOG.getDefaultState(), leaves = Blocks.SPRUCE_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
		for (int i = 0; i < trunk; i++) set(w, m, x, y + i, z, log);
		int top = y + trunk;
		for (int dy = -3; dy <= 1; dy++) {
			int r = dy == 1 ? 1 : dy == -3 ? 1 : 2;
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) + Math.abs(dz) > r + 1) continue;
					if ((hash(x + dx, (top + dy) * 7L + z + dz, 5) % 5) == 0) continue;
					if (dx == 0 && dz == 0 && dy < 1) continue;
					leaf(w, m, x + dx, top + dy, z + dz, leaves);
				}
		}
		set(w, m, x, top, z, log);
	}
}
