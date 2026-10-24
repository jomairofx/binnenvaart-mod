package nl.greatrivers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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

	private static final BlockState ROAD = Blocks.GRAY_CONCRETE.getDefaultState();

	/** Asphalt road along the top of the dike. */
	static void dikeRoad(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, int x, int z) {
		// the height from the terrain itself (a tree trunk on the dike would fool the height map and leave a gap)
		int top = (int) Math.floor(field.dutchHeight(x, z));
		if (top < 63 || top > 88) return;      // also where the dike runs into higher ground: the road goes on
		BlockState st = w.getBlockState(m.set(x, top, z));
		if (!st.getFluidState().isEmpty()) return;
		// fill any hole under the road
		for (int y = top - 1; y >= top - 4; y--) {
			BlockState b = w.getBlockState(m.set(x, y, z));
			if (b.isAir() || !b.getFluidState().isEmpty() || b.isReplaceable()) set(w, m, x, y, z, Blocks.DIRT.getDefaultState());
			else break;
		}
		// no trees on the road: take away any tree that grew here, trunk and crown
		boolean tree = false;
		for (int y = top + 1; y <= top + 10 && !tree; y++) tree = w.getBlockState(m.set(x, y, z)).isIn(net.minecraft.registry.tag.BlockTags.LOGS);
		if (tree) {
			for (int dx = -3; dx <= 3; dx++)
				for (int dz = -3; dz <= 3; dz++)
					for (int y = top + 1; y <= top + 16; y++) {
						BlockState b = w.getBlockState(m.set(x + dx, y, z + dz));
						if (b.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) set(w, m, x + dx, y, z + dz, AIR);
					}
		}
		set(w, m, x, top, z, ROAD);
		for (int y = top + 1; y <= top + 3; y++) {
			BlockState a = w.getBlockState(m.set(x, y, z));
			if (!a.isAir() && (a.isReplaceable() || a.isOf(Blocks.SNOW) || a.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)
					|| a.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || a.isOf(Blocks.SUGAR_CANE))) set(w, m, x, y, z, AIR);
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
		if (field.polderWeight(mx, mz) < 0.95 || field.harbours.siteAt(mx, mz) != null || field.villages.at(mx, mz) != null || field.infra.near(mx, mz, 40)) return;
		for (int dx = -4; dx <= 4; dx += 4)
			for (int dz = -4; dz <= 4; dz += 4) {
				int t = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, mx + dx, mz + dz) - 1;
				if (t != 62 || !w.getBlockState(m.set(mx + dx, t, mz + dz)).isOf(Blocks.GRASS_BLOCK)) return;
			}
		if (field.low.canalDistance(mx, mz) < 60) return;
		BlockState brick = Blocks.BRICKS.getDefaultState(), wood = Blocks.DARK_OAK_PLANKS.getDefaultState();
		BlockState cap = Blocks.DARK_OAK_PLANKS.getDefaultState(), white = Blocks.WHITE_WOOL.getDefaultState();
		BlockState frame = Blocks.SPRUCE_FENCE.getDefaultState();
		int base = 63;
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

	/**
	 * Now and then a house or farm on its own in the countryside, in many kinds: the brick farmhouse with a steep
	 * dark roof, the "stolpboerderij" under a pyramid of thatch, the "langgevelboerderij" (house and barn under one
	 * long roof), a modern white house with a flat roof, a bungalow with a hipped roof, a villa with a thatched roof,
	 * a little white cottage with a red roof.
	 */
	static void farm(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, int x0, int z0) {
		int cell = 288;
		int cx = Math.floorDiv(x0 + 8, cell), cz = Math.floorDiv(z0 + 8, cell);
		long h = mix(w.getSeed() ^ mix(cx * 0x2545F4914F6CDD1DL + cz * 977L + 5));
		if ((h & 1) == 0) return;   // half of the cells
		int fx = cx * cell + 40 + (int) ((h >>> 8 & 0xffff) % (cell - 80));
		int fz = cz * cell + 40 + (int) ((h >>> 24 & 0xffff) % (cell - 80));
		if (fx < x0 || fx >= x0 + 16 || fz < z0 || fz >= z0 + 16) return;
		int type = (int) Math.floorMod(h >>> 44, 7L);
		int[][] dims = {{15, 9, 3}, {13, 13, 2}, {15, 8, 3}, {10, 8, 6}, {12, 9, 3}, {11, 9, 3}, {9, 7, 3}};
		int len = dims[type][0], wid = dims[type][1], walls = dims[type][2];
		boolean alongX = ((h >>> 40) & 1) == 0;
		int lx = alongX ? len : wid, lz = alongX ? wid : len;
		int bx = x0 + 3, bz = z0 + 1;            // inside the area this chunk may build in
		if (field.polderWeight(bx + lx / 2, bz + lz / 2) < 0.95 || field.harbours.siteAt(bx, bz) != null || field.villages.at(bx, bz) != null) return;
		if (field.metros.near(bx, bz, 20)) return;
		if (field.low.canalDistance(bx, bz) < 50 || field.infra.near(bx + lx / 2, bz + lz / 2, 30)) return;
		for (int dx = 0; dx < lx; dx += 3)
			for (int dz = 0; dz < lz; dz += 3) {
				int t = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, bx + dx, bz + dz) - 1;
				if (t != 62 || !w.getBlockState(m.set(bx + dx, t, bz + dz)).isOf(Blocks.GRASS_BLOCK)) return;
				if (field.low.ditch(bx + dx, bz + dz)) return;
			}
		Direction pI = alongX ? Direction.EAST : Direction.SOUTH, pJ = alongX ? Direction.SOUTH : Direction.EAST;
		BlockState wall = switch (type) {
			case 3 -> Blocks.WHITE_CONCRETE.getDefaultState();
			case 4 -> Blocks.MUD_BRICKS.getDefaultState();
			case 6 -> Blocks.WHITE_TERRACOTTA.getDefaultState();
			default -> Blocks.BRICKS.getDefaultState();
		};
		BlockState trim = type == 3 ? Blocks.BLACK_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState();
		int base = 63, wallTop = base + walls;
		for (int i = 0; i < len; i++)
			for (int j = 0; j < wid; j++) {
				int x = bx + (alongX ? i : j), z = bz + (alongX ? j : i);
				boolean edge = i == 0 || i == len - 1 || j == 0 || j == wid - 1;
				boolean sideJ = j == 0 || j == wid - 1;     // a long wall
				BlockState glass = (sideJ == alongX) ? VillageBuilder.paneEW(Blocks.WHITE_STAINED_GLASS_PANE) : VillageBuilder.paneNS(Blocks.WHITE_STAINED_GLASS_PANE);
				set(w, m, x, base - 1, z, Blocks.SPRUCE_PLANKS.getDefaultState());
				boolean barn = type == 2 && i >= 7;          // the barn half of the langgevel farm
				for (int y = base; y <= wallTop; y++) {
					int rel = y - base;
					BlockState st = AIR;
					if (edge) {
						st = barn ? Blocks.DARK_OAK_PLANKS.getDefaultState() : wall;
						int along = sideJ ? i : j;
						boolean win;
						if (type == 3) win = rel % 3 != 0 && along % 4 != 0;                         // big windows
						else win = rel == 1 && along % 3 == 1 && !barn;
						if (win && !(sideJ && (i == 0 || i == len - 1))) st = glass;
						if (rel == walls && type != 3) st = barn ? Blocks.DARK_OAK_PLANKS.getDefaultState() : trim;
						if (type == 3 && rel == 3) st = trim;
						if (barn && sideJ && j == 0 && i >= 9 && i <= 12 && rel <= 2) st = Blocks.SPRUCE_PLANKS.getDefaultState();   // barn doors
					} else if (type == 3 && rel == 3) st = Blocks.BIRCH_PLANKS.getDefaultState();          // upper floor
					else if (rel == walls - 1 && !barn && i % 4 == 2 && j == wid / 2) st = Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
					set(w, m, x, y, z, st);
				}
				roof(w, m, type, x, z, i, j, len, wid, wallTop, pI, pJ, wall, barn);
			}
		// the front door in the middle of the long side
		int di = len / 2 - (type == 2 ? 4 : 0);
		int doorX = bx + (alongX ? di : 0), doorZ = bz + (alongX ? 0 : di);
		set(w, m, doorX, base, doorZ, AIR);
		set(w, m, doorX, base + 1, doorZ, AIR);
		// around it
		if (type == 3 || type == 4) {
			// a hedge along the front, a car on the drive
			for (int i = -1; i <= len; i++) {
				int x = bx + (alongX ? i : -3), z = bz + (alongX ? -3 : i);
				if (Math.abs(i - di) <= 1 || Math.abs(i - di - 3) <= 1) continue;
				set(w, m, x, base, z, Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
			}
			int cxp = bx + (alongX ? di + 3 : -2), czp = bz + (alongX ? -2 : di + 3);
			BlockState car = new BlockState[]{Blocks.BLACK_CONCRETE.getDefaultState(), Blocks.BLUE_CONCRETE.getDefaultState(), Blocks.WHITE_CONCRETE.getDefaultState()}[(int) ((h >>> 50) % 3)];
			set(w, m, cxp, base, czp, car);
			set(w, m, cxp, base + 1, czp, Blocks.BLACK_STAINED_GLASS.getDefaultState());
		} else {
			int sx = bx + (alongX ? -2 : lx + 1), sz = bz + 2;
			set(w, m, sx, base, sz, Blocks.HAY_BLOCK.getDefaultState());
			set(w, m, sx, base, sz + 1, Blocks.HAY_BLOCK.getDefaultState());
			set(w, m, sx, base + 1, sz, Blocks.HAY_BLOCK.getDefaultState());
			set(w, m, sx, base, sz + 3, Blocks.BARREL.getDefaultState());
		}
		for (int t = 0; t < 2; t++) {
			int tx = bx + (alongX ? 2 + t * (len - 4) : lx + 3), tz = bz + (alongX ? lz + 3 : 2 + t * (len - 4));
			tree(w, m, tx, base, tz, h >>> (t * 7));
		}
	}

	/** Places where a country house may stand near (x, z), for the tests: {x, z, type} (they may still be left out there). */
	static java.util.List<int[]> farmSpots(RiverField field, long seed, int x, int z, int n) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		int cell = 288;
		int c0 = Math.floorDiv(x, cell), r0 = Math.floorDiv(z, cell);
		for (int r = 0; r < 12 && out.size() < n; r++)
			for (int i = -r; i <= r && out.size() < n; i++)
				for (int j = -r; j <= r && out.size() < n; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
					int cx = c0 + i, cz = r0 + j;
					long h = mix(seed ^ mix(cx * 0x2545F4914F6CDD1DL + cz * 977L + 5));
					if ((h & 1) == 0) continue;
					int fx = cx * cell + 40 + (int) ((h >>> 8 & 0xffff) % (cell - 80));
					int fz = cz * cell + 40 + (int) ((h >>> 24 & 0xffff) % (cell - 80));
					int bx = (fx & ~15) + 3, bz = (fz & ~15) + 1;
					if (field.polderWeight(bx, bz) < 0.95 || field.villages.at(bx, bz) != null || field.infra.near(bx, bz, 30) || field.metros.near(bx, bz, 20)) continue;
					if (field.harbours.siteAt(bx, bz) != null) continue;
					out.add(new int[]{bx, bz, (int) Math.floorMod(h >>> 44, 7L)});
				}
		return out;
	}

	/** The roof of a country house, by type. */
	private static void roof(StructureWorldAccess w, BlockPos.Mutable m, int type, int x, int z, int i, int j, int len, int wid, int wallTop,
							 Direction pI, Direction pJ, BlockState wall, boolean barn) {
		BlockState thatch = Blocks.HAY_BLOCK.getDefaultState();
		if (type == 3) {
			// flat roof with a black edge and solar panels
			boolean edge = i == 0 || i == len - 1 || j == 0 || j == wid - 1;
			set(w, m, x, wallTop + 1, z, edge ? Blocks.BLACK_CONCRETE.getDefaultState() : Blocks.GRAY_CONCRETE.getDefaultState());
			if (!edge && i % 2 == 1 && j > 1 && j < wid - 2) set(w, m, x, wallTop + 2, z, Blocks.DAYLIGHT_DETECTOR.getDefaultState());
			return;
		}
		int ki = Math.min(i, len - 1 - i), kj = Math.min(j, wid - 1 - j);
		if (type == 1 || type == 4) {
			// pyramid of thatch (stolp) or a hipped roof of dark tiles (bungalow)
			int k = Math.min(ki, kj);
			int top = type == 1 ? 6 : 3;
			for (int r = 0; r <= Math.min(k, top); r++) {
				int y = wallTop + 1 + r;
				BlockState st = AIR;
				if (r == Math.min(k, top)) {
					if (type == 1) st = thatch;
					else if (r == top) st = Blocks.DEEPSLATE_TILES.getDefaultState();
					else {
						Direction d = ki < kj ? (i < len / 2 ? pI : pI.getOpposite()) : (j < wid / 2 ? pJ : pJ.getOpposite());
						st = Blocks.DEEPSLATE_TILE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, d);
					}
				}
				set(w, m, x, y, z, st);
			}
			if (type == 1 && ki == len / 2 && kj == wid / 2) set(w, m, x, wallTop + 8, z, Blocks.BRICKS.getDefaultState());   // chimney on top
			return;
		}
		// gable roofs, ridge along the length
		BlockState full, slab;
		Block stairs;
		if (type == 5) {
			full = thatch;
			slab = thatch;
			stairs = null;
		} else if (type == 6 || barn) {
			full = Blocks.POLISHED_GRANITE.getDefaultState();
			slab = Blocks.POLISHED_GRANITE_SLAB.getDefaultState();
			stairs = Blocks.POLISHED_GRANITE_STAIRS;
		} else {
			full = Blocks.DEEPSLATE_TILES.getDefaultState();
			slab = Blocks.DEEPSLATE_TILE_SLAB.getDefaultState();
			stairs = Blocks.DEEPSLATE_TILE_STAIRS;
		}
		for (int r = 0; r <= kj; r++) {
			int y = wallTop + 1 + r;
			BlockState st;
			if (r == kj) {
				if (wid % 2 == 1 && j == wid / 2) st = slab;
				else if (stairs == null) st = full;
				else st = stairs.getDefaultState().with(Properties.HORIZONTAL_FACING, j < wid / 2 ? pJ : pJ.getOpposite());
			} else st = (i == 0 || i == len - 1) ? wall : AIR;
			set(w, m, x, y, z, st);
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
			if (e < 3) continue;   // no sand: grass right down to the water
			// trees in the foreland and a row of pollard willows along the foot of the dike
			long hh = mix(x * 0x9E3779B97F4A7C15L + z * 0x632BE59BD9B4E019L);
			// stretches without trees, stretches with a few, and stretches with a regular row of willows
			double zone = field.low.treeZone(x, z);
			boolean scatter = zone > -0.25 && e > 8 && e < fore - 8 && (hh & 1023) < (zone > 0.3 ? 2 : 1);
			boolean row = false;
			if (!scatter && zone > 0.3 && Math.abs(e - (fore - 3)) < 0.5) {
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

	// ------------------------------------------------------------------ street lights on the dikes

	/**
	 * Modern street lights along the dike road, about every 40 blocks: a slim grey pole on the polder side of the
	 * road, an arm over the road and a flat lamp head; an invisible light under the head lights up the road.
	 */
	static boolean dikeLamp(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, int x, int z) {
		RiverField.Kind k = field.great;
		double[] dh = field.riverAt(x, z, true);
		double f = Lowlands.foreland(k);
		double kk = dh[0] - dh[1] - f;                 // across the dike: road on 15.5..19.5
		if (kk < 19 || kk >= 22) return false;
		double g = 3, gx = k.distance(x + g, z) - k.distance(x - g, z), gz = k.distance(x, z + g) - k.distance(x, z - g);
		double l = Math.hypot(gx, gz);
		if (l < 1e-9) return false;
		gx /= l;
		gz /= l;
		double tx = -gz, tz = gx;
		int cx = Math.round(x / 160f) * 160, cz = Math.round(z / 160f) * 160;
		double s = (x + 0.5 - cx) * tx + (z + 0.5 - cz) * tz;
		double kIdx = Math.round(s / 40.0);
		if (Math.abs(s - kIdx * 40) > 2) return false;
		// exactly one column per lamp: the one nearest to the ideal spot
		double px = x + 0.5 + (kIdx * 40 - s) * tx + (20.5 - kk) * gx, pz = z + 0.5 + (kIdx * 40 - s) * tz + (20.5 - kk) * gz;
		if ((int) Math.floor(px) != x || (int) Math.floor(pz) != z) return false;
		if (field.flatWeight(x, z) > 0.01) return false;
		int top = (int) Math.floor(field.dutchHeight(x, z));
		if (top < 64 || top > 88) return false;
		// the arm points towards the river, over the road
		net.minecraft.util.math.Direction toRoad = Math.abs(gx) > Math.abs(gz)
				? (gx > 0 ? net.minecraft.util.math.Direction.WEST : net.minecraft.util.math.Direction.EAST)
				: (gz > 0 ? net.minecraft.util.math.Direction.NORTH : net.minecraft.util.math.Direction.SOUTH);
		// slim grey mast, short arm, flat LED head; an invisible light under the head lights the road
		int poleTop = top + 6;
		set(w, m, x, top + 1, z, Blocks.POLISHED_ANDESITE_SLAB.getDefaultState());   // foot
		for (int y = top + 2; y <= poleTop; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
		int ax = x + toRoad.getOffsetX(), az = z + toRoad.getOffsetZ();
		set(w, m, ax, poleTop, az, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, toRoad.getAxis()));
		int hx = x + toRoad.getOffsetX() * 2, hz = z + toRoad.getOffsetZ() * 2;
		set(w, m, hx, poleTop, hz, Blocks.IRON_TRAPDOOR.getDefaultState()
				.with(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.TOP).with(Properties.HORIZONTAL_FACING, toRoad));
		set(w, m, hx, poleTop - 1, hz, Blocks.LIGHT.getDefaultState().with(Properties.LEVEL_15, 15));
		return true;
	}
}
