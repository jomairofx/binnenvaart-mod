package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

/** Dutch countryside: dike roads, windmills, farmhouses, pollard willows. */
final class Countryside {
	private static final BlockState AIR = Blocks.AIR.getDefaultState();

	static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	static long mix(long h) {
		return Harbours.mix(h);
	}

	private static final BlockState ROAD = Blocks.GRAY_CONCRETE_POWDER.getDefaultState();

	/** Asphalt road along the top of the dike. */
	static void dikeRoad(StructureWorldAccess w, BlockPos.Mutable m, int x, int z) {
		int top = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
		if (top < 66 || top > 72) return;
		BlockState st = w.getBlockState(m.set(x, top, z));
		if (!st.isOf(Blocks.GRASS_BLOCK) && !st.isOf(Blocks.DIRT) && !st.isOf(Blocks.SNOW_BLOCK) && !st.isOf(Blocks.STONE)) return;
		set(w, m, x, top, z, ROAD);
		for (int y = top + 1; y <= top + 2; y++) {
			BlockState a = w.getBlockState(m.set(x, y, z));
			if (!a.isAir() && (a.isReplaceable() || a.isOf(Blocks.SNOW))) set(w, m, x, y, z, AIR);
		}
	}


	/** Now and then a Dutch windmill in the polder. */
	static void windmill(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, int x0, int z0) {
		int cell = 352;
		int cx = Math.floorDiv(x0 + 8, cell), cz = Math.floorDiv(z0 + 8, cell);
		long h = mix(w.getSeed() ^ mix(cx * 0x632BE59BD9B4E019L + cz * 31L));
		if ((h & 3) == 0) return;   // three in four cells
		int mx = cx * cell + 40 + (int) ((h >>> 8 & 0xffff) % (cell - 80));
		int mz = cz * cell + 40 + (int) ((h >>> 24 & 0xffff) % (cell - 80));
		if (mx < x0 || mx >= x0 + 16 || mz < z0 || mz >= z0 + 16) return;
		mx = x0 + 8;
		mz = z0 + 8;   // keep the whole mill inside the area this chunk may build in
		if (field.polderWeight(mx, mz) < 0.95 || field.harbours.siteAt(mx, mz) != null) return;
		for (int dx = -4; dx <= 4; dx += 4)
			for (int dz = -4; dz <= 4; dz += 4) {
				int t = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, mx + dx, mz + dz) - 1;
				if (t != 63 || !w.getBlockState(m.set(mx + dx, t, mz + dz)).isOf(Blocks.GRASS_BLOCK)) return;
			}
		if (field.low.canalDistance(mx, mz) < 60) return;
		BlockState brick = Blocks.BRICKS.getDefaultState(), wood = Blocks.DARK_OAK_PLANKS.getDefaultState();
		BlockState cap = Blocks.DARK_OAK_PLANKS.getDefaultState(), white = Blocks.WHITE_WOOL.getDefaultState();
		BlockState frame = Blocks.SPRUCE_FENCE.getDefaultState();
		int base = 64;
		for (int y = base; y <= base + 14; y++) {
			double r = y < base + 3 ? 3.6 : (y < base + 9 ? 3.1 : (y < base + 13 ? 2.4 : 1.6));
			for (int dx = -4; dx <= 4; dx++)
				for (int dz = -4; dz <= 4; dz++) {
					double d = Math.hypot(dx, dz);
					if (d > r) continue;
					boolean shell = d > r - 1.1;
					BlockState st = !shell ? AIR : (y < base + 3 ? brick : (y >= base + 13 ? cap : wood));
					if (y == base + 14) st = cap;
					set(w, m, mx + dx, y, mz + dz, st);
				}
		}
		// gallery (stelling) around the body
		for (int dx = -4; dx <= 4; dx++)
			for (int dz = -4; dz <= 4; dz++) {
				double d = Math.hypot(dx, dz);
				if (d > 3.2 && d <= 4.4) set(w, m, mx + dx, base + 4, mz + dz, Blocks.SPRUCE_SLAB.getDefaultState());
			}
		// door, a few windows, inside floors
		for (int y = base; y <= base + 1; y++) set(w, m, mx - 3, y, mz, AIR);
		set(w, m, mx - 3, base + 6, mz, AIR);
		set(w, m, mx + 3, base + 6, mz, AIR);
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++) if (Math.hypot(dx, dz) <= 2.2) set(w, m, mx + dx, base + 5, mz + dz, Blocks.SPRUCE_PLANKS.getDefaultState());
		set(w, m, mx + 1, base + 1, mz, Blocks.SPRUCE_LOG.getDefaultState());
		set(w, m, mx + 1, base + 6, mz, Blocks.SPRUCE_LOG.getDefaultState());
		// sails on the east side, a cross of four arms with white cloth
		int hx = mx + 3, hy = base + 12;
		set(w, m, hx, hy, mz, Blocks.SPRUCE_LOG.getDefaultState());
		set(w, m, hx + 1, hy, mz, Blocks.SPRUCE_LOG.getDefaultState());
		int ax = hx + 2;
		for (int k = 1; k <= 10; k++) {
			int[][] arms = {{0, k}, {0, -k}, {k, 0}, {-k, 0}};   // (dy, dz)
			for (int[] a : arms) {
				int yy = hy + a[0], zz = mz + a[1];
				if (yy < base - 0) continue;
				set(w, m, ax, yy, zz, frame);
			}
			if (k >= 3) {
				if (hy + k > 0) set(w, m, ax, hy + k, mz + 1, white);
				set(w, m, ax, hy - k, mz - 1, white);
				set(w, m, ax, hy - 1, mz + k, white);
				set(w, m, ax, hy + 1, mz - k, white);
			}
		}
	}


	// ------------------------------------------------------------------ farmhouses

	/** Now and then a Dutch farmhouse (brick, steep dark roof, green shutters) with a few trees and hay. */
	static void farm(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, int x0, int z0) {
		int cell = 288;
		int cx = Math.floorDiv(x0 + 8, cell), cz = Math.floorDiv(z0 + 8, cell);
		long h = mix(w.getSeed() ^ mix(cx * 0x2545F4914F6CDD1DL + cz * 977L + 5));
		if ((h & 1) == 0) return;   // half of the cells
		int fx = cx * cell + 40 + (int) ((h >>> 8 & 0xffff) % (cell - 80));
		int fz = cz * cell + 40 + (int) ((h >>> 24 & 0xffff) % (cell - 80));
		if (fx < x0 || fx >= x0 + 16 || fz < z0 || fz >= z0 + 16) return;
		int bx = x0 + 3, bz = z0 + 1;            // footprint 9 x 15 inside the area this chunk may build in
		boolean alongX = ((h >>> 40) & 1) == 0;
		int lx = alongX ? 15 : 9, lz = alongX ? 9 : 15;
		if (field.polderWeight(bx + lx / 2, bz + lz / 2) < 0.95 || field.harbours.siteAt(bx, bz) != null) return;
		if (field.low.canalDistance(bx, bz) < 50) return;
		for (int dx = 0; dx < lx; dx += 4)
			for (int dz = 0; dz < lz; dz += 4) {
				int t = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, bx + dx, bz + dz) - 1;
				if (t != 63 || !w.getBlockState(m.set(bx + dx, t, bz + dz)).isOf(Blocks.GRASS_BLOCK)) return;
				if (field.low.ditch(bx + dx, bz + dz)) return;
			}
		BlockState brick = Blocks.BRICKS.getDefaultState(), white = Blocks.WHITE_CONCRETE.getDefaultState();
		BlockState glass = Blocks.WHITE_STAINED_GLASS_PANE.getDefaultState();
		int base = 64, wallTop = base + 3;
		int len = alongX ? lx : lz, wid = alongX ? lz : lx;
		for (int i = 0; i < len; i++)
			for (int j = 0; j < wid; j++) {
				int x = bx + (alongX ? i : j), z = bz + (alongX ? j : i);
				boolean edge = i == 0 || i == len - 1 || j == 0 || j == wid - 1;
				set(w, m, x, base - 1, z, Blocks.SPRUCE_PLANKS.getDefaultState());
				for (int y = base; y <= wallTop; y++) {
					BlockState st = AIR;
					if (edge) {
						st = brick;
						boolean window = y == base + 1 && ((i % 3 == 1 && (j == 0 || j == wid - 1)) || (j % 3 == 1 && (i == 0 || i == len - 1)));
						if (window) st = glass;
						if (y == wallTop) st = Blocks.WHITE_CONCRETE.getDefaultState();
					}
					set(w, m, x, y, z, st);
				}
				// steep gable roof along the long side, dark tiles
				int k = Math.min(j, wid - 1 - j);   // distance from the eaves
				for (int r = 0; r <= k; r++) {
					int y = wallTop + 1 + r;
					BlockState roof;
					if (r == k) {
						if (wid % 2 == 1 && j == wid / 2) roof = Blocks.DEEPSLATE_TILE_SLAB.getDefaultState();
						else {
							net.minecraft.util.math.Direction f = alongX ? (j < wid / 2 ? net.minecraft.util.math.Direction.SOUTH : net.minecraft.util.math.Direction.NORTH)
									: (j < wid / 2 ? net.minecraft.util.math.Direction.EAST : net.minecraft.util.math.Direction.WEST);
							roof = Blocks.DEEPSLATE_TILE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, f);
						}
					} else roof = (i == 0 || i == len - 1) ? brick : AIR;
					set(w, m, x, y, z, roof);
				}
			}
		// front door with green shutters beside the windows
		int dx = alongX ? len / 2 : 0, dz = alongX ? 0 : len / 2;
		int doorX = bx + (alongX ? dx : 0), doorZ = bz + (alongX ? 0 : dz);
		set(w, m, doorX, base, doorZ, AIR);
		set(w, m, doorX, base + 1, doorZ, AIR);
		// a barrel, hay bales and a fence by the farm
		int sx = bx + (alongX ? -2 : lx + 1), sz = bz + (alongX ? 2 : 2);
		set(w, m, sx, base, sz, Blocks.HAY_BLOCK.getDefaultState());
		set(w, m, sx, base, sz + 1, Blocks.HAY_BLOCK.getDefaultState());
		set(w, m, sx, base + 1, sz, Blocks.HAY_BLOCK.getDefaultState());
		set(w, m, sx, base, sz + 3, Blocks.BARREL.getDefaultState());
		for (int t = 0; t < 2; t++) {
			int tx = bx + (alongX ? 3 + t * 8 : lx + 3), tz = bz + (alongX ? lz + 3 : 3 + t * 8);
			tree(w, m, tx, base, tz, h >>> (t * 7));
		}
	}

	/** A simple broad-leaved tree (oak). */
	static void tree(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, long h) {
		int trunk = 4 + (int) (h & 1);
		BlockState log = Blocks.OAK_LOG.getDefaultState();
		BlockState leaf = Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
		for (int dy = -2; dy <= 2; dy++)
			for (int dx = -2; dx <= 2; dx++)
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dz * dz + dy * dy * 1.4 > 6.5) continue;
					int yy = y + trunk + dy;
					if (w.getBlockState(m.set(x + dx, yy, z + dz)).isAir()) set(w, m, x + dx, yy, z + dz, leaf);
				}
		for (int dy = 0; dy < trunk + 1; dy++) set(w, m, x, y + dy, z, log);
	}

	/** A pollard willow ("knotwilg"): a short thick trunk with a ball of grey-green twigs. */
	static void willow(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z) {
		BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
		BlockState leaf = Blocks.AZALEA_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
		for (int dy = 0; dy < 3; dy++) set(w, m, x, y + dy, z, log);
		for (int dy = 0; dy <= 2; dy++)
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++) {
					if (dy == 2 && Math.abs(dx) + Math.abs(dz) == 2) continue;
					if (w.getBlockState(m.set(x + dx, y + 3 + dy, z + dz)).isAir()) set(w, m, x + dx, y + 3 + dy, z + dz, leaf);
				}
	}

	// ------------------------------------------------------------------ river banks: groynes and beaches

	private static final BlockState[] STONES = {Blocks.COBBLESTONE.getDefaultState(), Blocks.STONE.getDefaultState(),
			Blocks.ANDESITE.getDefaultState(), Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()};

	/**
	 * Groynes ("kribben"): short stone dams from the bank into the river, every 50 (Maas) or 75 (great river) blocks,
	 * with small sand beaches in between. Trees in the forelands. Returns true when it did something.
	 */
	static boolean riverBank(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, int x, int z) {
		boolean any = false;
		for (RiverField.Kind k : new RiverField.Kind[]{field.river, field.great}) {
			double[] dh = field.riverAt(x, z, k == field.great);
			double d = dh[0], hw = dh[1], e = d - hw;
			double fore = Lowlands.foreland(k);
			if (e > fore + 2 || e < -5) continue;
			if (e < 3) {
				// sand beach along the water line
				int y = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
				BlockState st = w.getBlockState(m.set(x, y, z));
				if (y >= RiverField.WATER_TOP - 4 && y <= RiverField.WATER_TOP + 2
						&& (st.isOf(Blocks.GRASS_BLOCK) || st.isOf(Blocks.DIRT) || st.isOf(Blocks.GRAVEL) || st.isOf(Blocks.CLAY) || st.isOf(Blocks.MUD))) {
					set(w, m, x, y, z, Blocks.SAND.getDefaultState());
					BlockState up = w.getBlockState(m.set(x, y + 1, z));
					if (!up.isAir() && up.isReplaceable() && up.getFluidState().isEmpty()) set(w, m, x, y + 1, z, AIR);
					any = true;
				}
				continue;
			}
			// trees in the foreland and a row of pollard willows along the foot of the dike
			long hh = mix(x * 0x9E3779B97F4A7C15L + z * 0x632BE59BD9B4E019L);
			boolean scatter = e > 8 && e < fore - 8 && (hh & 1023) < 3;
			boolean row = false;
			if (!scatter && Math.abs(e - (fore - 3)) < 0.5) {
				// along-river coordinate (only worked out here, it costs a few noise samples)
				double g = 3, gx = k.distance(x + g, z) - k.distance(x - g, z), gz = k.distance(x, z + g) - k.distance(x, z - g);
				double l = Math.hypot(gx, gz);
				if (l < 1e-9) continue;
				int cx = Math.round(x / 160f) * 160, cz = Math.round(z / 160f) * 160;
				double s = (x - cx) * (-gz / l) + (z - cz) * (gx / l);
				row = Math.floorMod((long) Math.floor(s), 9L) == 0;
			}
			if (row || scatter) {
				int y = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z);
				if (y < RiverField.WATER_TOP + 1 || y > RiverField.WATER_TOP + 4) continue;
				if (!w.getBlockState(m.set(x, y - 1, z)).isOf(Blocks.GRASS_BLOCK)) continue;
				if (row || ((hh >>> 12) & 1) == 0) willow(w, m, x, y, z);
				else tree(w, m, x, y, z, hh >>> 20);
				any = true;
			}
		}
		return any;
	}
}
