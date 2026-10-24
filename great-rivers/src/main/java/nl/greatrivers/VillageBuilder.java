package nl.greatrivers;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;

/**
 * Builds the villages and towns of {@link Villages}, one column at a time (so chunks never disagree).
 * Houses: 7 wide, 10 deep, brick, two floors and an attic under a steep gable roof with the gable to the street;
 * front door, big windows, kitchen, table and chairs, sofa, bookcase, lamp, rug downstairs; beds and a wardrobe
 * upstairs, a ladder between the floors. The church: brick nave with tall windows, pews and an altar, and a tower
 * with a spire.
 */
final class VillageBuilder {
	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	static final int G = Villages.GROUND;   // ground level: top block of the streets
	static final int Y0 = G + 1;      // ground floor
	private static final Block[] BRICK = {Blocks.BRICKS, Blocks.BRICKS, Blocks.MUD_BRICKS, Blocks.BRICKS, Blocks.BRICKS};
	private static final Block[][] ROOF = {
			{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES},
			{Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICKS},
			{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES},
			{Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICKS}};

	private VillageBuilder() {}

	/** A glass pane joined to its neighbours along x (a window in a wall running east-west). */
	static BlockState paneEW(Block b) {
		return b.getDefaultState().with(Properties.EAST, true).with(Properties.WEST, true);
	}

	/** A glass pane joined along z (a window in a wall running north-south). */
	static BlockState paneNS(Block b) {
		return b.getDefaultState().with(Properties.NORTH, true).with(Properties.SOUTH, true);
	}

	static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	static long hash(long a, long b, long c) {
		return Harbours.mix(a * 0x9E3779B97F4A7C15L + b * 0x632BE59BD9B4E019L + c * 0x2545F4914F6CDD1DL);
	}

	/** How far the quays and the boulevard reach from the water in a river city. */
	static final double QUAY = 12;

	/** Builds this column of the town. Returns true if anything was placed. */
	static boolean column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Villages.Town t, int x, int z) {
		double e = Double.MAX_VALUE;
		if (t.river()) {
			double[] rv = f.riverAt(x, z, true);
			e = rv[0] - rv[1];
			if (e < 0) return ferry(w, m, f, t, x, z, e);
		}
		// clear trees and plants above the ground
		for (int y = G + 1; y <= G + 40; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && (st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS) || st.isReplaceable() || st.isIn(BlockTags.FLOWERS))) set(w, m, x, y, z, AIR);
		}
		if (e < QUAY) {
			quay(w, m, f, x, z, e);
			return true;
		}
		int u = Math.floorMod(x - t.cx(), Villages.PU), wv = Math.floorMod(z - t.cz(), Villages.PW);
		if (Villages.street(t, x, z)) {
			street(w, m, t, x, z, u, wv);
			return true;
		}
		if (!Villages.blockIn(t, x, z)) return edge(w, m, f, t, x, z);
		int bu = u - Villages.STREET, bw = wv - Villages.STREET;   // 0..28, 0..20
		int bx = Math.floorDiv(x - t.cx(), Villages.PU), bz = Math.floorDiv(z - t.cz(), Villages.PW);
		if (t.river() && waterfront(w, m, f, t, x, z, bu, bw, t.cx() + bx * Villages.PU, t.cz() + bz * Villages.PW, t.city())) return true;
		if (bx == 0 && bz == 0) {
			church(w, m, t, x, z, bu, bw);
			return true;
		}
		// the water tower and the football pitch of the town, in two blocks towards the edge
		int[] wt = special(t, 1), sp = special(t, 2);
		if (bx == wt[0] && bz == wt[1]) {
			waterTower(w, m, x, z, bu, bw, t.seed());
			return true;
		}
		if (bx == sp[0] && bz == sp[1]) {
			sportsField(w, m, x, z, bu, bw);
			return true;
		}
		double zone = Math.hypot(t.cx() + bx * Villages.PU + Villages.PU / 2.0 - t.cx(), t.cz() + bz * Villages.PW + Villages.PW / 2.0 - t.cz()) / t.radius();
		long bh = hash(t.seed(), bx * 31L + bz, 99);
		if (t.dike() && t.city()) {
			// a town behind the dike: apartment towers and flats along the dike, with a view over the river
			double ed = Villages.dikeBank(t, bx, bz) - Lowlands.foreland(f.great) - Villages.DIKE_KEEP;
			if (ed < 60 && (bh & 3) != 0) {
				tower(w, m, x, z, bu, bw, bh, 0.55 + ed / 120);
				return true;
			}
			if (ed < 130 && (bh & 3) != 0) {
				flats(w, m, x, z, bu, bw, bh);
				return true;
			}
			if ((bh & 15) == 1) {
				park(w, m, x, z, bu, bw);
				return true;
			}
		} else if (t.city()) {
			double tall = t.river() ? 0.62 : 0.3, flats = t.river() ? 0.8 : 0.62;
			if (zone < tall && (bh & 7) != 0) {
				tower(w, m, x, z, bu, bw, bh, t.river() ? Math.max(0, zone - 0.3) / 0.32 : zone / tall);
				return true;
			}
			if (zone < flats && (bh & 3) != 0) {
				flats(w, m, x, z, bu, bw, bh);
				return true;
			}
			if ((bh & 15) == 1) {
				park(w, m, x, z, bu, bw);
				return true;
			}
		} else if ((bh & 15) == 3 && zone > 0.3) {
			park(w, m, x, z, bu, bw);
			return true;
		}
		// two rows of houses back to back: the front row faces north (-z), the back row south (+z)
		if (bu >= 28) {                                   // a narrow alley at the end of the block
			set(w, m, x, G, z, Blocks.DIRT_PATH.getDefaultState());
			return true;
		}
		int hx = bu % 7, house = bu / 7;
		boolean front = bw <= 9;
		if (bw == 10) {                                    // back garden fence between the rows
			set(w, m, x, G + 1, z, Blocks.OAK_FENCE.getDefaultState());
			return true;
		}
		int pw = front ? bw : 20 - bw;                     // 0 at the street facade, 9 at the back
		long hh = hash(t.seed(), bx * 31L + bz, house * 2L + (front ? 0 : 1));
		house(w, m, t, x, z, hx, pw, front, hh, !t.city());
		if (Math.abs(bx) <= 1 && Math.abs(bz) <= 1) shopFront(w, m, x, z, hx, pw, front, hh);
		return true;
	}

	/** A block towards the edge of the town for something special (n = 1 water tower, 2 football pitch). */
	static int[] special(Villages.Town t, int n) {
		double a = (hash(t.seed(), n, 17) >>> 11) * 0x1.0p-53 * 2 * Math.PI;
		double d = t.radius() * 0.62;
		int bx = (int) Math.floor((Math.cos(a) * d) / Villages.PU), bz = (int) Math.floor((Math.sin(a) * d) / Villages.PW);
		if (bx == 0 && bz == 0) bx = 1;
		return new int[]{bx, bz};
	}

	/** A Dutch water tower: an octagonal brick shaft, the tank storey with small windows, a pointed roof. */
	static void waterTower(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long seed) {
		int dx = bu - 14, dz = bw - 10;
		double r8 = Math.max(Math.abs(dx), Math.abs(dz)) + 0.42 * Math.min(Math.abs(dx), Math.abs(dz));   // octagon "radius"
		set(w, m, x, G, z, r8 <= 7 ? Blocks.STONE_BRICKS.getDefaultState() : ((bu % 9 == 4 || bw % 7 == 3) ? Blocks.GRAVEL.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState()));
		if (r8 > 6.6) {
			if ((bu == 2 || bu == 26) && (bw == 2 || bw == 18)) Countryside.tree(w, m, x, G + 1, z, hash(x, z, 5));
			return;
		}
		BlockState brick = (seed & 1) == 0 ? Blocks.BRICKS.getDefaultState() : Blocks.MUD_BRICKS.getDefaultState();
		BlockState white = Blocks.WHITE_CONCRETE.getDefaultState();
		int top = Y0 + 26;
		for (int y = Y0; y <= top + 12; y++) {
			int rel = y - Y0;
			BlockState st = AIR;
			if (rel < 24) {
				// the shaft
				if (r8 <= 3.6 && r8 > 2.6) {
					st = brick;
					if (rel % 6 == 3 && (dx == 0 || dz == 0)) st = paneEW(Blocks.GLASS_PANE);
					if (dz == -3 && dx == 0 && rel <= 2) st = AIR;                         // door
				} else if (r8 <= 2.6 && rel == 0) st = Blocks.OAK_PLANKS.getDefaultState();
				else if (dx == 0 && dz == 2 && rel >= 1) st = Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
				if (rel >= 20 && r8 <= 3.6 + (rel - 20) * 0.6 && r8 > 2.6 + (rel - 20) * 0.6) st = brick;   // corbels out to the tank
			} else if (rel <= 31) {
				// the tank storey: white band, windows, brick
				if (r8 <= 6.0 && r8 > 5.0) {
					st = rel == 24 || rel == 31 ? white : brick;
					if (rel >= 26 && rel <= 28 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1)) st = paneEW(Blocks.GLASS_PANE);
				} else if (r8 <= 5.0 && (rel == 24 || rel == 31)) st = Blocks.SPRUCE_PLANKS.getDefaultState();
			} else {
				// the roof: dark tiles to a point, a weather vane on top
				int k = rel - 32;
				double rr = 6.0 - k * 1.0;
				if (r8 <= rr && r8 > rr - 1.2) st = Blocks.DEEPSLATE_TILES.getDefaultState();
				if (rr < 0.8 && dx == 0 && dz == 0) st = k <= 7 ? Blocks.IRON_BARS.getDefaultState() : AIR;
				if (k == 7 && dx == 0 && dz == 0) st = Blocks.LIGHTNING_ROD.getDefaultState();
			}
			if (y > top + 12) break;
			set(w, m, x, y, z, st);
		}
	}

	/** A football pitch with white lines, goals and floodlights. */
	static void sportsField(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw) {
		boolean line = ((bu == 1 || bu == 27) && bw >= 1 && bw <= 19) || ((bw == 1 || bw == 19) && bu >= 1 && bu <= 27) || (bu == 14 && bw >= 1 && bw <= 19);
		int dx = bu - 14, dz = bw - 10;
		double r = Math.sqrt(dx * dx + dz * dz);
		if (Math.abs(r - 4) < 0.5) line = true;
		if ((bu == 5 || bu == 23) && bw >= 6 && bw <= 14) line = true;                                  // penalty areas
		if ((bw == 6 || bw == 14) && (bu <= 5 || bu >= 23) && bu >= 1 && bu <= 27) line = true;
		set(w, m, x, G, z, line ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState());
		// goals
		if ((bu == 0 || bu == 28) && bw >= 8 && bw <= 12) {
			if (bw == 8 || bw == 12) for (int y = G + 1; y <= G + 2; y++) set(w, m, x, y, z, Blocks.BIRCH_FENCE.getDefaultState());
			set(w, m, x, G + 3, z, Blocks.BIRCH_SLAB.getDefaultState());
		}
		// floodlights at the corners
		if ((bu == 0 || bu == 28) && (bw == 0 || bw == 20)) {
			for (int y = G + 1; y <= G + 12; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, G + 13, z, Blocks.SEA_LANTERN.getDefaultState());
		}
		// a low fence around it
		if (bu == 0 && (bw < 8 || bw > 12) || bu == 28 && (bw < 8 || bw > 12)) set(w, m, x, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
	}

	/** Is any part of this block of a river city on the quays or in the water? */
	static boolean blockNearWater(RiverField f, Villages.Town t, int bx, int bz) {
		int x0 = t.cx() + bx * Villages.PU, z0 = t.cz() + bz * Villages.PW;
		for (int i = 0; i <= 2; i++)
			for (int j = 0; j <= 2; j++) {
				int x = x0 + i * Villages.PU / 2, z = z0 + j * Villages.PW / 2;
				double[] rv = f.riverAt(x, z, true);
				if (rv[0] - rv[1] < QUAY + 3) return true;
			}
		return false;
	}

	// ------------------------------------------------------------------ the waterfront of a river town

	/** Buildings keep at least this far from the water: a green promenade in between. */
	static final double PROMENADE = QUAY + 22, FRONT = QUAY + 80;
	private static final java.util.concurrent.ConcurrentHashMap<Long, Double> WATER_DIST = new java.util.concurrent.ConcurrentHashMap<>();

	/** Least distance of the block (bx, bz) of a river town from the water (cached per block). */
	static double waterDist(RiverField f, int x0, int z0) {
		if (WATER_DIST.size() > 200000) WATER_DIST.clear();
		long key = (x0 * 0x9E3779B97F4A7C15L) ^ (z0 * 0x632BE59BD9B4E019L) ^ 0x3A7E5L;
		return WATER_DIST.computeIfAbsent(key, k -> {
			double e = Double.MAX_VALUE;
			for (int i = 0; i <= 2; i++)
				for (int j = 0; j <= 2; j++) {
					double[] rv = f.riverAt(x0 + i * Villages.PU / 2, z0 + j * Villages.PW / 2, true);
					e = Math.min(e, rv[0] - rv[1]);
				}
			return e;
		});
	}

	/**
	 * The blocks along the river in a river town or city: first a green promenade, then a varied front of
	 * buildings: apartment towers, terraced apartments stepping down to the water, old brick warehouses turned
	 * into lofts, tall narrow houses, modern town houses, a restaurant pavilion with a terrace. Returns false
	 * where the block is too far from the water to be part of the waterfront.
	 */
	static boolean waterfront(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Villages.Town t, int x, int z, int bu, int bw, int x0, int z0, boolean big) {
		double e = waterDist(f, x0, z0);
		if (e < PROMENADE) {
			promenade(w, m, x, z, bu, bw, hash(t.seed(), x0 * 31L + z0, 444));
			return true;
		}
		if (e >= FRONT) return false;
		long bh = hash(t.seed(), x0 * 31L + z0, 555);
		int kind = (int) Math.floorMod(bh >>> 7, big ? 7L : 5L);
		switch (kind) {
			case 0 -> terraced(w, m, x, z, bu, bw, bh);
			case 1 -> lofts(w, m, x, z, bu, bw, bh);
			case 2 -> MetroBuilder.canalHouses(w, m, x, z, bu, bw, bh);
			case 3 -> pavilion(w, m, x, z, bu, bw, bh);
			case 4 -> MetroBuilder.modernRows(w, m, x, z, bu, bw, bh);
			case 5 -> tower(w, m, x, z, bu, bw, bh, 0.6);
			default -> flats(w, m, x, z, bu, bw, bh);
		}
		return true;
	}

	/** Green promenade along the water: lawns, trees, paths, benches, a playground. */
	static void promenade(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		long h = hash(x, z, 41);
		boolean path = bu == 14 || bw == 10 || bu == 0 || bw == 0;
		set(w, m, x, G, z, path ? (((x + z) & 3) == 0 ? Blocks.POLISHED_ANDESITE.getDefaultState() : Blocks.SMOOTH_STONE.getDefaultState()) : Blocks.GRASS_BLOCK.getDefaultState());
		if (path) {
			if ((bu == 14 && bw % 6 == 3) || (bw == 10 && bu % 8 == 4)) set(w, m, x, G + 1, z, stairs(Blocks.SPRUCE_STAIRS, bu == 14 ? Direction.EAST : Direction.NORTH));
			if (bu == 0 && bw % 10 == 5) {
				for (int y = G + 1; y <= G + 3; y++) set(w, m, x, y, z, Blocks.DARK_OAK_FENCE.getDefaultState());
				set(w, m, x, G + 4, z, Blocks.LANTERN.getDefaultState());
			}
			return;
		}
		if ((bh & 3) == 0 && bu >= 3 && bu <= 11 && bw >= 2 && bw <= 8) {
			// a playground: sand, a climbing frame, a slide
			set(w, m, x, G, z, Blocks.SAND.getDefaultState());
			if ((bu == 5 || bu == 9) && (bw == 3 || bw == 7)) for (int y = G + 1; y <= G + 3; y++) set(w, m, x, y, z, Blocks.OAK_FENCE.getDefaultState());
			if (bu >= 5 && bu <= 9 && (bw == 3 || bw == 7) && !(bu == 5 || bu == 9)) set(w, m, x, G + 4, z, Blocks.OAK_SLAB.getDefaultState());
			if (bu == 7 && bw == 5) set(w, m, x, G + 1, z, Blocks.RED_CONCRETE.getDefaultState());
			return;
		}
		if ((h % 23) == 0) Countryside.tree(w, m, x, G + 1, z, h >>> 8);
		else if ((h % 13) == 0) set(w, m, x, G + 1, z, Blocks.SHORT_GRASS.getDefaultState());
		else if ((h % 41) == 0) set(w, m, x, G + 1, z, (h & 64) == 0 ? Blocks.POPPY.getDefaultState() : Blocks.CORNFLOWER.getDefaultState());
	}

	/** Terraced apartments: white blocks stepping up from 3 to 9 floors, roof terraces with glass balustrades. */
	static void terraced(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean body = bu >= 2 && bu <= 26 && bw >= 3 && bw <= 17;
		if (!body) {
			set(w, m, x, G, z, (bw == 1 || bw == 19) ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState());
			if ((bu == 0 || bu == 28) && bw % 6 == 3) Countryside.tree(w, m, x, G + 1, z, bh + bw);
			return;
		}
		boolean up = ((bh >>> 3) & 1) == 0;
		int step = (up ? bu - 2 : 26 - bu) / 5;                 // 0..4
		int floors = 3 + step * 2 - (step == 4 ? 1 : 0);
		int H = floors * 3;
		boolean stepEdge = (up ? bu - 2 : 26 - bu) % 5 == 4 && step < 4;
		boolean edge = bu == 2 || bu == 26 || bw == 3 || bw == 17;
		BlockState wall = ((bh >>> 5) & 1) == 0 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.SMOOTH_SANDSTONE.getDefaultState();
		BlockState glass = bw == 3 || bw == 17 ? paneEW(Blocks.GLASS_PANE) : paneNS(Blocks.GLASS_PANE);
		set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
		for (int y = Y0; y <= Y0 + H; y++) {
			int rel = y - Y0;
			BlockState st;
			if (edge || stepEdge) {
				if (rel % 3 == 0) st = wall;
				else st = ((bu + bw) % 3 != 0) ? glass : wall;
				if (rel <= 2 && bw == 3 && bu % 8 == 4) st = AIR;                    // front doors
			} else {
				st = rel % 3 == 0 ? Blocks.SMOOTH_STONE.getDefaultState() : AIR;
				boolean lit = Math.floorMod(Harbours.mix(bh + bu / 4 * 31L + rel / 3 * 7L), 10L) < 5;
				if (rel % 3 == 2 && bu % 4 == 1 && bw % 5 == 0 && lit) st = Blocks.SEA_LANTERN.getDefaultState();
				if (rel % 3 == 1 && bu % 4 == 3 && bw % 5 == 2) st = Blocks.SPRUCE_STAIRS.getDefaultState();
			}
			set(w, m, x, y, z, st);
		}
		// roof terrace: glass balustrade round the edge, a few plants
		int roof = Y0 + H + 1;
		set(w, m, x, roof - 1, z, wall);
		if (edge || stepEdge) set(w, m, x, roof, z, glass);
		else if (((bu * 7 + bw * 3) % 11) == 0) set(w, m, x, roof, z, Blocks.POTTED_AZALEA_BUSH.getDefaultState());
		for (int y = roof + 1; y <= Y0 + 40; y++) {
			BlockState o = w.getBlockState(m.set(x, y, z));
			if (o.isIn(BlockTags.LEAVES) || o.isIn(BlockTags.LOGS)) set(w, m, x, y, z, AIR);
		}
	}

	/** Old brick warehouses on the quay ("pakhuizen") turned into lofts: five storeys, rows of arched windows, a name band. */
	static void lofts(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean body = bu >= 1 && bu <= 27 && bw >= 2 && bw <= 18;
		if (!body) {
			set(w, m, x, G, z, Blocks.BRICKS.getDefaultState());
			if (bw == 0 && bu % 7 == 3) set(w, m, x, G + 1, z, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());
			return;
		}
		boolean split = bu == 14;                                    // two warehouses side by side
		boolean edge = bu == 1 || bu == 27 || bw == 2 || bw == 18 || split;
		Block brick = new Block[]{Blocks.BRICKS, Blocks.MUD_BRICKS, Blocks.BRICKS, Blocks.DEEPSLATE_BRICKS}[(int) ((bh >>> 9) & 3)];
		BlockState b = brick.getDefaultState();
		int H = 20;
		boolean sideNS = bu == 1 || bu == 27 || split;
		set(w, m, x, G, z, Blocks.STONE_BRICKS.getDefaultState());
		for (int y = Y0; y <= Y0 + H; y++) {
			int rel = y - Y0;
			BlockState st;
			int along = sideNS ? bw : bu;
			if (edge) {
				st = b;
				if (!split && along % 3 == 1 && rel % 4 >= 1 && rel % 4 <= 2 && rel > 0) st = sideNS ? paneNS(Blocks.GLASS_PANE) : paneEW(Blocks.GLASS_PANE);
				if (!split && along % 3 == 1 && rel % 4 == 3) st = stairs(brick == Blocks.MUD_BRICKS ? Blocks.MUD_BRICK_STAIRS : brick == Blocks.DEEPSLATE_BRICKS ? Blocks.DEEPSLATE_BRICK_STAIRS : Blocks.BRICK_STAIRS,
						sideNS ? (bu == 1 ? Direction.EAST : Direction.WEST) : (bw == 2 ? Direction.SOUTH : Direction.NORTH)).with(Properties.BLOCK_HALF, BlockHalf.TOP);
				if (rel == H - 2 && !split) st = Blocks.WHITE_CONCRETE.getDefaultState();          // the painted name band
				if (bw == 2 && rel <= 2 && bu % 7 == 3) st = AIR;                                    // the big doors
			} else {
				st = rel % 4 == 0 ? Blocks.SPRUCE_PLANKS.getDefaultState() : AIR;
				if (rel % 4 == 0 && bu % 7 == 5 && bw == 10) st = AIR;
				if (bu % 7 == 5 && bw == 10) st = Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
				boolean lit = Math.floorMod(Harbours.mix(bh + bu / 5 * 31L + rel / 4 * 7L), 10L) < 6;
				if (rel % 4 == 3 && bu % 5 == 2 && bw % 5 == 0 && lit) st = Blocks.SEA_LANTERN.getDefaultState();
			}
			set(w, m, x, y, z, st);
		}
		// gabled top of the facade at both ends and a hoisting beam
		set(w, m, x, Y0 + H + 1, z, edge ? Blocks.STONE_BRICK_SLAB.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState());
		if (bw == 2 && (bu == 7 || bu == 21)) set(w, m, x, Y0 + H - 3, z - 1, Blocks.DARK_OAK_FENCE.getDefaultState());
	}

	/** A restaurant pavilion on the waterfront: a glass box with a flat white roof and a terrace with parasols. */
	static void pavilion(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean hall = bu >= 7 && bu <= 21 && bw >= 7 && bw <= 13;
		boolean roof = bu >= 6 && bu <= 22 && bw >= 6 && bw <= 14;
		set(w, m, x, G, z, ((bu + bw) & 1) == 0 ? Blocks.SPRUCE_PLANKS.getDefaultState() : Blocks.BIRCH_PLANKS.getDefaultState());
		if (bu <= 1 || bu >= 27 || bw <= 1 || bw >= 19) {
			set(w, m, x, G, z, Blocks.GRASS_BLOCK.getDefaultState());
			if (bw <= 1 && bu % 4 == 2) set(w, m, x, G + 1, z, Blocks.FLOWER_POT.getDefaultState());
			return;
		}
		if (hall) {
			boolean edge = bu == 7 || bu == 21 || bw == 7 || bw == 13;
			for (int y = Y0; y <= Y0 + 3; y++) {
				BlockState st = AIR;
				if (edge) st = (bw == 7 || bw == 13) ? paneEW(Blocks.GLASS_PANE) : paneNS(Blocks.GLASS_PANE);
				if (edge && y == Y0 && bw == 7 && bu == 14) st = AIR;
				if (!edge && y == Y0 && bu % 3 == 1 && bw % 3 == 1) st = Blocks.SPRUCE_FENCE.getDefaultState();
				if (!edge && y == Y0 + 1 && bu % 3 == 1 && bw % 3 == 1) st = Blocks.SPRUCE_PRESSURE_PLATE.getDefaultState();
				if (!edge && y == Y0 + 3 && bu % 4 == 2 && bw == 10) st = Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
				set(w, m, x, y, z, st);
			}
		}
		if (roof) {
			set(w, m, x, Y0 + 4, z, Blocks.WHITE_CONCRETE.getDefaultState());
			return;
		}
		// the terrace: tables with parasols
		if (bu % 5 == 2 && bw % 5 == 2) {
			set(w, m, x, Y0, z, Blocks.SPRUCE_FENCE.getDefaultState());
			set(w, m, x, Y0 + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
			Block cloth = new Block[]{Blocks.WHITE_WOOL, Blocks.RED_WOOL, Blocks.GREEN_WOOL}[(int) ((bh >>> 4) % 3)];
			set(w, m, x, Y0 + 2, z, cloth.getDefaultState());
			for (Direction d : Direction.Type.HORIZONTAL) set(w, m, x + d.getOffsetX(), Y0 + 2, z + d.getOffsetZ(), cloth.getDefaultState());
		} else if ((bu % 5 == 2 && (bw % 5 == 1 || bw % 5 == 3)) || (bw % 5 == 2 && (bu % 5 == 1 || bu % 5 == 3))) {
			set(w, m, x, Y0, z, stairs(Blocks.SPRUCE_STAIRS, bu % 5 == 1 ? Direction.EAST : bu % 5 == 3 ? Direction.WEST : bw % 5 == 1 ? Direction.SOUTH : Direction.NORTH));
		}
	}

	// ------------------------------------------------------------------ the edge of the town

	/** Just outside the outer streets: a hedge with trees, so the town ends in green, not in stone. */
	static boolean edge(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Villages.Town t, int x, int z) {
		// roads coming in from outside run on to the first street: the provincial roads and the dike road
		Object[] rh = f.roads.hit(x + 0.5, z + 0.5, Roads.OUTER + 1);
		if (rh != null) {
			double[] p = (double[]) rh[0];
			if (Roads.build(w, m, (Roads.Seg) rh[1], p[0], p[1], x, z)) return true;
		}
		if (f.dikeRoad(x, z)) {
			Countryside.dikeRoad(w, m, f, x, z);
			return true;
		}
		if ((int) Math.floor(f.dutchHeight(x, z)) != G) return false;      // on the dike slope: no hedge
		int k = 0;
		for (int d = 1; d <= 3 && k == 0; d++)
			if (Villages.street(t, x + d, z) || Villages.street(t, x - d, z) || Villages.street(t, x, z + d) || Villages.street(t, x, z - d)) k = d;
		if (k == 0) return false;
		if (k == 2) {
			long h = hash(x, z, 11);
			if ((h & 7) == 0) Countryside.tree(w, m, x, G + 1, z, h >>> 3);
			else set(w, m, x, G + 1, z, Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ river city: quays, boulevard, ferry

	static void quay(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z, double e) {
		long h = hash(x, z, 21);
		if (e < 1.5) {
			// the quay wall: brick-faced, standing on the river bed, smooth stone coping
			int g = G - 1;
			while (g > 30 && !w.getBlockState(m.set(x, g, z)).isOpaqueFullCube(w, m)) g--;
			for (int y = Math.min(g, G - 1) - 1; y < G; y++) set(w, m, x, y, z, y < RiverField.WATER_TOP - 2 ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.BRICKS.getDefaultState());
			set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
			if ((h & 31) == 0) set(w, m, x, G + 1, z, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());   // bollard
			return;
		}
		// the boulevard: paving, a row of trees, benches and lights
		set(w, m, x, G, z, ((x + z) & 3) == 0 ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.POLISHED_ANDESITE.getDefaultState());
		if (e >= 6 && e < 7 && (h % 9) == 0) Countryside.tree(w, m, x, G + 1, z, h >>> 8);
		else if (e >= 3 && e < 4 && (h % 23) == 0) set(w, m, x, G + 1, z, Blocks.SPRUCE_STAIRS.getDefaultState());
		else if (e >= 10 && e < 11 && (h % 17) == 0) {
			for (int y = G + 1; y <= G + 4; y++) set(w, m, x, y, z, Blocks.DARK_OAK_FENCE.getDefaultState());
			set(w, m, x, G + 5, z, Blocks.LANTERN.getDefaultState());
		}
	}

	/** The ferry ("pont") across the river in the middle of the city: a landing stage on both banks and the ferry. */
	private static boolean ferry(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Villages.Town t, int x, int z, double e) {
		if (e < -16) return false;
		double[] n = f.great.normal(t.cx(), t.cz());
		double tx = -n[1], tz = n[0];
		double a = (x + 0.5 - t.cx()) * tx + (z + 0.5 - t.cz()) * tz;
		double aa = Math.abs(a);
		if (aa < 3 && e > -12) {
			// landing stage of timber on piles, at quay height
			int g = ground(w, m, x, z);
			if (aa > 2 && ((x + z) & 3) == 0) for (int y = g; y < G; y++) set(w, m, x, y, z, Blocks.DARK_OAK_LOG.getDefaultState());
			set(w, m, x, G, z, Blocks.SPRUCE_PLANKS.getDefaultState());
			if (aa > 2) set(w, m, x, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
			return true;
		}
		// the ferry itself, moored at the stage on one bank: white hull with a blue band, a small wheelhouse
		double side = (n[0] * (x - t.cx()) + n[1] * (z - t.cz())) >= 0 ? 1 : -1;
		if (side > 0 && a >= 3.5 && a < 9.5 && e > -14 && e < -1) {
			boolean rim = a < 4.5 || a >= 8.5 || e < -13 || e >= -2;
			set(w, m, x, RiverField.WATER_TOP - 1, z, Blocks.BLUE_CONCRETE.getDefaultState());
			set(w, m, x, RiverField.WATER_TOP, z, Blocks.WHITE_CONCRETE.getDefaultState());
			set(w, m, x, RiverField.WATER_TOP + 1, z, rim ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.GRAY_CONCRETE.getDefaultState());
			if (rim) set(w, m, x, RiverField.WATER_TOP + 2, z, Blocks.IRON_BARS.getDefaultState());
			if (!rim && e >= -9 && e < -6 && a >= 5.5 && a < 7.5) {
				for (int y = RiverField.WATER_TOP + 2; y <= RiverField.WATER_TOP + 3; y++) set(w, m, x, y, z, Blocks.WHITE_CONCRETE.getDefaultState());
				set(w, m, x, RiverField.WATER_TOP + 4, z, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
				set(w, m, x, RiverField.WATER_TOP + 5, z, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
			}
			return true;
		}
		return false;
	}

	private static int ground(StructureWorldAccess w, BlockPos.Mutable m, int x, int z) {
		int g = G;
		while (g > 30 && (w.getBlockState(m.set(x, g, z)).isAir() || !w.getBlockState(m).getFluidState().isEmpty())) g--;
		return g;
	}

	// ------------------------------------------------------------------ parks

	static void park(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw) {
		long h = hash(x, z, 31);
		boolean path = bu == 14 || bw == 10;
		if (path) {
			set(w, m, x, G, z, Blocks.GRAVEL.getDefaultState());
			if ((h % 19) == 0) set(w, m, x, G + 1, z, Blocks.SPRUCE_STAIRS.getDefaultState());
			return;
		}
		set(w, m, x, G, z, Blocks.GRASS_BLOCK.getDefaultState());
		if (bu > 0 && bu < 28 && bw > 0 && bw < 20) {
			if ((h % 37) == 0) Countryside.tree(w, m, x, G + 1, z, h >>> 8);
			else if ((h % 11) == 0) set(w, m, x, G + 1, z, Blocks.SHORT_GRASS.getDefaultState());
			else if ((h % 29) == 0) set(w, m, x, G + 1, z, Blocks.POPPY.getDefaultState());
		}
	}

	// ------------------------------------------------------------------ office towers and flats

	private static final Block[][] TOWER = {
			{Blocks.CYAN_TERRACOTTA, Blocks.LIGHT_GRAY_CONCRETE}, {Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.WHITE_CONCRETE},
			{Blocks.BLACK_CONCRETE, Blocks.GRAY_CONCRETE}, {Blocks.BLUE_TERRACOTTA, Blocks.LIGHT_GRAY_CONCRETE},
			{Blocks.GRAY_TERRACOTTA, Blocks.SMOOTH_QUARTZ}};

	/** An office or apartment tower filling the block, 25 to 75 high: taller towards the middle of the city. */
	static void tower(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh, double zone) {
		int x0 = 3, x1 = 25, z0 = 3, z1 = 17;
		boolean in = bu >= x0 && bu <= x1 && bw >= z0 && bw <= z1;
		if (!in) {
			// plaza around the tower
			set(w, m, x, G, z, ((bu + bw) & 1) == 0 ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.POLISHED_ANDESITE.getDefaultState());
			if ((bu == 1 || bu == 27) && (bw == 1 || bw == 19)) Countryside.tree(w, m, x, G + 1, z, bh >>> 5);
			return;
		}
		Block[] pal = TOWER[(int) ((bh >>> 8) % TOWER.length)];
		BlockState win = pal[0].getDefaultState(), frame = pal[1].getDefaultState();
		int H = (int) Math.round((24 + ((bh >>> 16) % 7) * 8) * (1.35 - 0.6 * zone));
		H = Math.max(22, Math.min(112, H)) / 4 * 4;
		boolean edge = bu == x0 || bu == x1 || bw == z0 || bw == z1;
		boolean corner = (bu == x0 || bu == x1) && (bw == z0 || bw == z1);
		int along = (bw == z0 || bw == z1) ? bu : bw;
		set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
		set(w, m, x, Y0, z, edge ? frame : Blocks.SMOOTH_STONE.getDefaultState());
		for (int y = Y0 + 1; y <= Y0 + H; y++) {
			int rel = y - Y0;
			BlockState st;
			int floor = rel / 4, fr = rel % 4;
			// most floors are lit, so the tower glows at night like a real office tower
			boolean lit = Math.floorMod(Harbours.mix(bh + floor * 7919L), 10L) < 6;
			if (edge) {
				if (corner || fr == 0) st = frame;                                             // floor bands and corners
				else if (rel <= 3) st = Blocks.GLASS.getDefaultState();                          // glass lobby
				else st = along % 3 == 0 ? frame : Blocks.GLASS.getDefaultState();             // glass curtain wall, mullions
				if (fr == 3 && along % 3 != 0 && !corner && rel > 3) st = win;                  // the spandrel under the next floor
				if (rel <= 2 && bw == z0 && (bu == 13 || bu == 14 || bu == 15)) st = AIR;      // entrance
			} else {
				boolean core = bu >= 12 && bu <= 16 && bw >= 8 && bw <= 12;
				boolean coreWall = core && (bu == 12 || bu == 16 || bw == 8 || bw == 12);
				if (fr == 0) st = core && !coreWall ? AIR : Blocks.SMOOTH_STONE.getDefaultState();
				else if (coreWall) st = (bu == 12 && bw == 10 && fr <= 2) ? AIR : Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();   // lift and stairs
				else if (core) st = (bu == 14 && bw == 10) ? Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST) : AIR;
				else if (fr == 3) st = lit && Math.floorMod(bu, 4) == 1 && Math.floorMod(bw, 4) == 1 ? Blocks.SEA_LANTERN.getDefaultState() : AIR;
				else if (fr == 1 && rel > 4) {
					// offices: rows of desks with chairs, plants in the corners
					boolean inner = bu >= x0 + 2 && bu <= x1 - 2 && bw >= z0 + 2 && bw <= z1 - 2;
					if (inner && Math.floorMod(bw, 4) == 1 && Math.floorMod(bu, 3) != 0) st = Blocks.SPRUCE_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.TOP);
					else if (inner && Math.floorMod(bw, 4) == 2 && Math.floorMod(bu, 3) == 1) st = stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH);
					else if ((bu == x0 + 1 || bu == x1 - 1) && (bw == z0 + 1 || bw == z1 - 1)) st = Blocks.POTTED_FERN.getDefaultState();
					else st = AIR;
				} else st = AIR;
				if (rel == 3 && bu % 5 == 0 && bw % 5 == 0 && !core) st = Blocks.SEA_LANTERN.getDefaultState();
			}
			set(w, m, x, y, z, st);
		}
		// roof: parapet, plant rooms, a mast on the tallest
		int roof = Y0 + H + 1;
		set(w, m, x, roof, z, edge ? frame : Blocks.SMOOTH_STONE.getDefaultState());
		if (edge) set(w, m, x, roof + 1, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
		else if (bu >= 9 && bu <= 15 && bw >= 7 && bw <= 12) {
			for (int y = roof + 1; y <= roof + 3; y++) set(w, m, x, y, z, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
		}
		if (H >= 56 && bu == 12 && bw == 9) {
			for (int y = roof + 4; y <= roof + 12; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, roof + 13, z, Blocks.REDSTONE_LAMP.getDefaultState());
		}
		for (int y = roof + 2; y <= Y0 + 120; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS)) set(w, m, x, y, z, AIR);
		}
	}

	/** Two "galerijflats": long blocks of flats of 4 to 8 floors with open galleries, gardens between them. */
	static void flats(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int bu, int bw, long bh) {
		boolean front = bw <= 9;
		int pw = front ? bw : 20 - bw;                          // 0 at the street
		int floors = 4 + (int) ((bh >>> 12) % 5);
		int H = floors * 3;
		boolean body = bu >= 1 && bu <= 27 && pw >= 2 && pw <= 8;
		boolean gallery = bu >= 1 && bu <= 27 && pw == 1;
		if (!body && !gallery) {
			set(w, m, x, G, z, pw == 0 ? Blocks.SMOOTH_STONE.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState());
			if (pw == 10 && ((bh + bu) % 9) == 0) Countryside.tree(w, m, x, G + 1, z, bh >>> 3);
			return;
		}
		BlockState wall = (((bh >>> 4) & 1) == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).getDefaultState();
		BlockState brick = (((bh >>> 5) & 1) == 0 ? Blocks.BRICKS : Blocks.WHITE_CONCRETE).getDefaultState();
		BlockState win = paneEW(Blocks.GLASS_PANE);
		set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
		if (gallery) {
			// open galleries along the street side, one per floor, with a concrete balustrade
			for (int f = 1; f < floors; f++) {
				int y = Y0 + f * 3;
				set(w, m, x, y, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.TOP));
				set(w, m, x, y + 1, z, wall);
			}
			return;
		}
		boolean edge = bu == 1 || bu == 27 || pw == 2 || pw == 8;
		set(w, m, x, Y0, z, edge ? brick : Blocks.SMOOTH_STONE.getDefaultState());
		for (int y = Y0 + 1; y <= Y0 + H; y++) {
			int rel = y - Y0;
			BlockState st;
			if (edge) {
				if (rel % 3 == 0) st = wall;                                      // floor slabs show on the facade
				else if (bu == 1 || bu == 27) st = brick;                         // closed end walls
				else if (rel <= 2) st = brick;                                    // brick plinth
				else st = (bu % 4 == 2 || bu % 4 == 3) ? win : brick;
				if (pw == 2 && rel % 3 != 0 && bu % 4 == 0) st = AIR;             // front doors from the gallery
				if (rel <= 2 && pw == 2 && bu == 14) st = AIR;                    // main entrance
			} else {
				st = rel % 3 == 0 ? Blocks.SMOOTH_STONE.getDefaultState() : AIR;
				if (bu % 4 == 0 && rel % 3 != 0) st = Blocks.BIRCH_PLANKS.getDefaultState();    // walls between the flats
				boolean lit = Math.floorMod(Harbours.mix(bh + bu / 4 * 31L + rel / 3 * 7L + (front ? 1 : 2)), 10L) < 5;
				if (rel % 3 == 2 && bu % 4 == 2 && pw == 5 && lit) st = Blocks.SEA_LANTERN.getDefaultState();
				if (rel % 3 == 1 && bu % 4 == 3 && pw == 6) st = Blocks.SPRUCE_STAIRS.getDefaultState();   // a chair by the window
			}
			set(w, m, x, y, z, st);
		}
		set(w, m, x, Y0 + H + 1, z, edge ? wall : Blocks.GRAY_CONCRETE.getDefaultState());
	}

	// ------------------------------------------------------------------ streets

	static void street(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z, int u, int wv) {
		boolean alongX = wv < Villages.STREET && u >= Villages.STREET;    // street running east-west
		boolean alongZ = u < Villages.STREET && wv >= Villages.STREET;
		int across = alongX ? wv : alongZ ? u : -1;
		BlockState pave = Blocks.BRICKS.getDefaultState();
		if (across == 0 || across == Villages.STREET - 1) pave = Blocks.SMOOTH_STONE.getDefaultState();   // pavements
		if (across < 0) pave = Blocks.STONE_BRICKS.getDefaultState();                                    // crossings
		set(w, m, x, G, z, pave);
		// street lamps on the pavement
		int along = alongX ? x : z;
		if ((across == 0) && Math.floorMod(along, 12) == 0) {
			for (int y = G + 1; y <= G + 3; y++) set(w, m, x, y, z, Blocks.DARK_OAK_FENCE.getDefaultState());
			set(w, m, x, G + 4, z, Blocks.LANTERN.getDefaultState());
		}
		streetLife(w, m, x, z, across, along, alongX);
	}

	private static final Block[] CAR_PAINT = {Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.BLUE_CONCRETE,
			Blocks.RED_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.GREEN_CONCRETE};

	/**
	 * The small things on a street: green litter bins and bike racks on the pavement, a parked car now and then
	 * along one side, a post box. across = 0..STREET-1 across the street, along = coordinate along it.
	 */
	static void streetLife(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int across, int along, boolean alongX) {
		if (across < 0) return;
		if (across == 0 && (Math.floorMod(along, 12) == 0 || Math.floorMod(along, 16) == 0)) return;     // the street lamps
		long seg = Math.floorDiv(along, 31);
		int ph = Math.floorMod(along, 31);
		long h = hash(seg, alongX ? z : x, alongX ? 1 : 2);
		if (across == 0) {
			if (ph == 17 && (h & 3) == 0) set(w, m, x, G + 1, z, Blocks.GREEN_TERRACOTTA.getDefaultState());           // litter bin
			else if (ph >= 20 && ph <= 23 && (h & 12) == 4) set(w, m, x, G + 1, z, Blocks.IRON_BARS.getDefaultState());  // bike rack
			else if (ph == 26 && (h & 63) == 9) {
				set(w, m, x, G + 1, z, Blocks.ORANGE_CONCRETE.getDefaultState());                                         // post box
				set(w, m, x, G + 2, z, Blocks.ORANGE_TERRACOTTA.getDefaultState());
			}
		} else if (across == Villages.STREET - 2 && ph >= 3 && ph <= 6 && (h & 3) == 1) {
			// a parked car along the kerb
			BlockState body = CAR_PAINT[(int) Math.floorMod(h >>> 8, (long) CAR_PAINT.length)].getDefaultState();
			set(w, m, x, G + 1, z, body);
			if (ph == 4 || ph == 5) set(w, m, x, G + 2, z, Blocks.BLACK_STAINED_GLASS.getDefaultState());
		}
	}

	// ------------------------------------------------------------------ houses

	/** Direction towards the street (out of the front door) for this row. */
	private static Direction out(boolean front) {
		return front ? Direction.NORTH : Direction.SOUTH;
	}

	static BlockState stairs(Block b, Direction f) {
		return b.getDefaultState().with(Properties.HORIZONTAL_FACING, f);
	}

	static void house(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z, int hx, int pw, boolean front, long hh, boolean residents) {
		Block wall = BRICK[(int) (hh & 3)];
		Block[] roof = ROOF[(int) ((hh >>> 3) & 3)];
		Direction out = out(front), in = out.getOpposite();
		boolean edge = hx == 0 || hx == 6 || pw == 0 || pw == 9;
		BlockState brick = wall.getDefaultState();
		BlockState glass = paneEW(Blocks.WHITE_STAINED_GLASS_PANE);
		int eave = Y0 + 8;
		// foundation and floors
		set(w, m, x, G, z, Blocks.STONE_BRICKS.getDefaultState());
		set(w, m, x, Y0, z, edge ? brick : Blocks.OAK_PLANKS.getDefaultState());
		for (int y = Y0 + 1; y < eave; y++) set(w, m, x, y, z, AIR);
		if (!edge && !(hx == 1 && (pw == 7 || pw == 8))) set(w, m, x, Y0 + 4, z, Blocks.SPRUCE_PLANKS.getDefaultState());
		if (edge) {
			for (int y = Y0 + 1; y < eave; y++) {
				BlockState st = brick;
				int fl = y - Y0;   // 1..7
				if (pw == 0) {                                                       // the street facade
					if (hx == 1 && fl <= 2) st = AIR;                                   // door (placed below)
					else if (hx >= 3 && hx <= 5 && (fl == 1 || fl == 2)) st = glass;   // big front window
					else if ((hx == 2 || hx == 4) && (fl == 5 || fl == 6)) st = glass; // upstairs windows
					else if (fl == 3 && hx >= 3 && hx <= 5) st = Blocks.WHITE_CONCRETE.getDefaultState();   // white lintel
				} else if (pw == 9) {                                                // the back
					if (hx == 5 && fl <= 2) st = AIR;
					else if ((hx == 3) && (fl == 1 || fl == 2)) st = glass;
					else if ((hx == 2 || hx == 4) && (fl == 5 || fl == 6)) st = glass;
				}
				set(w, m, x, y, z, st);
			}
			if (pw == 0 && hx == 1) door(w, m, x, z, out, Blocks.SPRUCE_DOOR);
			if (pw == 9 && hx == 5) door(w, m, x, z, in, Blocks.OAK_DOOR);
			if (pw == 0 && hx >= 3 && hx <= 5) {                                  // flower box under the window
				int fx = x + out.getOffsetX(), fz = z + out.getOffsetZ();
				if (hx == 4) set(w, m, fx, Y0 + 1, fz, Blocks.FLOWER_POT.getDefaultState());
			}
		}
		// steep gable roof, ridge from front to back, the gables stepped up in brick on the street side
		int k = Math.min(hx, 6 - hx);
		for (int r = 0; r <= 3; r++) {
			int y = eave + r;
			if (r < k) {
				// inside the roof: gable walls front and back, attic air in between
				set(w, m, x, y, z, (pw == 0 || pw == 9) ? brick : AIR);
			} else if (r == k) {
				BlockState st;
				if (hx == 3) st = roof[2].getDefaultState();
				else st = stairs(roof[0], hx < 3 ? Direction.EAST : Direction.WEST);
				set(w, m, x, y, z, st);
			} else {
				// stepped gable ("trapgevel") above the roof line at the front
				if (pw == 0 && r == k + 1 && hx != 3 && (hx == 1 || hx == 5)) set(w, m, x, y, z, brick);
				else set(w, m, x, y, z, AIR);
			}
		}
		if (pw == 0 && hx == 3) set(w, m, x, eave + 4, z, roof[1].getDefaultState().with(Properties.SLAB_TYPE, SlabType.BOTTOM));
		if (edge) return;
		// ------------------------------------------------ interior
		int fl0 = Y0 + 1, fl1 = Y0 + 5;
		// ladder between the floors, against the back wall
		if (hx == 1 && pw == 8) for (int y = fl0; y <= Y0 + 4; y++) set(w, m, x, y, z, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, out));
		// downstairs
		if (pw == 2 && hx == 2) {                                              // table with chairs
			set(w, m, x, fl0, z, Blocks.OAK_FENCE.getDefaultState());
			set(w, m, x, fl0 + 1, z, Blocks.OAK_PRESSURE_PLATE.getDefaultState());
		}
		if (pw == 2 && (hx == 1 || hx == 3)) set(w, m, x, fl0, z, stairs(Blocks.OAK_STAIRS, hx == 1 ? Direction.WEST : Direction.EAST));
		if (pw >= 3 && pw <= 4 && hx >= 3 && hx <= 4) set(w, m, x, fl0, z, Blocks.RED_CARPET.getDefaultState());
		if (pw == 4 && hx == 5) set(w, m, x, fl0, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));   // armchair
		if (pw == 5 && hx == 5) set(w, m, x, fl0, z, Blocks.BOOKSHELF.getDefaultState());
		if (pw == 5 && hx == 5) set(w, m, x, fl0 + 1, z, Blocks.BOOKSHELF.getDefaultState());
		if (pw == 8) {                                                         // kitchen along the back wall
			if (hx == 2) set(w, m, x, fl0, z, Blocks.SMOKER.getDefaultState().with(Properties.HORIZONTAL_FACING, out));
			if (hx == 3) set(w, m, x, fl0, z, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
			if (hx == 4) set(w, m, x, fl0, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
			if (hx == 4) set(w, m, x, fl0 + 1, z, Blocks.FLOWER_POT.getDefaultState());
		}
		if (pw == 4 && hx == 3) set(w, m, x, Y0 + 3, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
		// upstairs: double bed, wardrobe, lamp
		if (pw == 6 || pw == 7) {
			if (hx == 4 || hx == 5) {
				BedPart part = pw == 7 ? BedPart.HEAD : BedPart.FOOT;
				Block bed = ((hx + (int) (bw(t))) & 1) == 0 ? Blocks.WHITE_BED : Blocks.LIGHT_BLUE_BED;
				set(w, m, x, fl1, z, bed.getDefaultState().with(BedBlock.FACING, in).with(BedBlock.PART, part));
			}
		}
		if (pw == 2 && hx == 5) {
			set(w, m, x, fl1, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
			set(w, m, x, fl1 + 1, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
		}
		if (pw == 2 && hx == 3) set(w, m, x, fl1, z, Blocks.CRAFTING_TABLE.getDefaultState());
		if (pw == 4 && hx == 3) set(w, m, x, Y0 + 7, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
		// someone lives here
		if (residents && pw == 3 && hx == 2 && (hh & 7) == 0) {
			VillagerEntity v = EntityType.VILLAGER.create(w.toServerWorld());
			if (v != null) {
				v.refreshPositionAndAngles(x + 0.5, fl0, z + 0.5, 0, 0);
				v.setPersistent();
				v.initialize(w, w.getLocalDifficulty(m.set(x, fl0, z)), SpawnReason.STRUCTURE, null);
				w.spawnEntityAndPassengers(v);
			}
		}
	}

	private static final Block[] AWNING = {Blocks.RED_WOOL, Blocks.GREEN_WOOL, Blocks.BLUE_WOOL, Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL};

	/** In the middle of the town the houses have shops downstairs: a big shop window and an awning over the pavement. */
	static void shopFront(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int hx, int pw, boolean front, long hh) {
		if (pw != 0 || hx == 0 || hx == 6) return;
		if (hx >= 2) {
			set(w, m, x, Y0 + 1, z, paneEW(Blocks.GLASS_PANE));
			set(w, m, x, Y0 + 2, z, paneEW(Blocks.GLASS_PANE));
		}
		Direction out = out(front);
		int fx = x + out.getOffsetX(), fz = z + out.getOffsetZ();
		set(w, m, fx, Y0 + 3, fz, AWNING[(int) Math.floorMod(hh >>> 9, (long) AWNING.length)].getDefaultState());
		if (hx == 3) set(w, m, x, Y0 + 3, z, Blocks.SEA_LANTERN.getDefaultState());
	}

	private static long bw(Villages.Town t) {
		return t.seed() >>> 7;
	}

	static void door(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, Direction facing, Block door) {
		BlockState d = door.getDefaultState().with(DoorBlock.FACING, facing.getOpposite());
		set(w, m, x, Y0 + 1, z, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(w, m, x, Y0 + 2, z, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	// ------------------------------------------------------------------ the church and its square

	static void church(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z, int bu, int bw) {
		// square paving everywhere in the middle block, the church on it
		set(w, m, x, G, z, ((bu + bw) & 3) == 0 ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.BRICKS.getDefaultState());
		boolean nave = bu >= 9 && bu <= 25 && bw >= 5 && bw <= 15;
		boolean tower = bu >= 4 && bu <= 8 && bw >= 8 && bw <= 12;
		BlockState brick = Blocks.BRICKS.getDefaultState();
		if (!nave && !tower) {
			// trees and benches on the square
			if ((bu == 2 || bu == 26) && (bw == 2 || bw == 18)) Countryside.tree(w, m, x, G + 1, z, hash(x, z, 3));
			if (bw == 18 && bu >= 10 && bu <= 13) set(w, m, x, G + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
			if (bw == 2 && bu >= 15 && bu <= 18) set(w, m, x, G + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
			return;
		}
		int wallTop = Y0 + 9;
		if (nave) {
			int nu = bu - 9, nw = bw - 5;                   // 0..16 along, 0..10 across
			boolean edge = nu == 0 || nu == 16 || nw == 0 || nw == 10;
			set(w, m, x, Y0, z, edge ? brick : Blocks.POLISHED_ANDESITE.getDefaultState());
			for (int y = Y0 + 1; y <= wallTop; y++) {
				BlockState st = AIR;
				if (edge) {
					st = brick;
					boolean window = (nw == 0 || nw == 10) && nu % 3 == 2 && y >= Y0 + 3 && y <= Y0 + 7;
					if (window) st = paneEW(y == Y0 + 7 ? Blocks.LIGHT_BLUE_STAINED_GLASS_PANE : Blocks.WHITE_STAINED_GLASS_PANE);
					if (nu == 16 && nw == 5 && y >= Y0 + 4 && y <= Y0 + 7) st = paneNS(Blocks.YELLOW_STAINED_GLASS_PANE);
					if (nu == 0 && nw == 5 && y <= Y0 + 3) st = AIR;   // from the tower into the nave
				}
				set(w, m, x, y, z, st);
			}
			// steep roof along the nave
			int k = Math.min(nw, 10 - nw);
			for (int r = 0; r <= 6; r++) {
				int y = wallTop + 1 + r;
				BlockState st;
				if (r < k) st = (nu == 0 || nu == 16) ? brick : AIR;
				else if (r == k) st = nw == 5 ? Blocks.DEEPSLATE_TILES.getDefaultState()
						: stairs(Blocks.DEEPSLATE_TILE_STAIRS, nw < 5 ? Direction.SOUTH : Direction.NORTH);
				else st = AIR;
				set(w, m, x, y, z, st);
			}
			if (edge) return;
			// pews facing the altar (east), aisle in the middle
			if (nu >= 2 && nu <= 12 && nu % 2 == 0 && nw != 5) set(w, m, x, Y0 + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
			if (nu == 15 && nw >= 4 && nw <= 6) set(w, m, x, Y0 + 1, z, Blocks.QUARTZ_BLOCK.getDefaultState());
			if (nu == 15 && (nw == 4 || nw == 6)) set(w, m, x, Y0 + 2, z, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 3).with(Properties.LIT, true));
			if (nu == 14 && nw >= 3 && nw <= 7) set(w, m, x, Y0 + 1, z, Blocks.RED_CARPET.getDefaultState());
			if ((nu == 4 || nu == 10) && nw == 5) {
				set(w, m, x, wallTop + 2, z, Blocks.CHAIN.getDefaultState());
				set(w, m, x, wallTop + 1, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
			}
			return;
		}
		// the tower with its spire (west end, door into the nave)
		int tu = bu - 4, tw = bw - 8;                         // 0..4
		boolean edge = tu == 0 || tu == 4 || tw == 0 || tw == 4;
		int top = Y0 + 22;
		set(w, m, x, Y0, z, edge ? brick : Blocks.POLISHED_ANDESITE.getDefaultState());
		for (int y = Y0 + 1; y <= top; y++) {
			BlockState st = edge ? brick : AIR;
			if (edge && tu == 0 && tw == 2 && y <= Y0 + 3) st = AIR;                                   // west door
			if (edge && tu == 4 && tw == 2 && y <= Y0 + 3) st = AIR;                                   // into the nave
			if (edge && (tw == 2 || tu == 2) && y >= top - 4 && y <= top - 2) st = AIR;                // belfry openings
			if (edge && (tw == 2 || tu == 2) && y == top - 9) st = Blocks.WHITE_CONCRETE.getDefaultState();   // clock faces
			set(w, m, x, y, z, st);
		}
		if (!edge && tu == 2 && tw == 2) set(w, m, x, top - 3, z, Blocks.BELL.getDefaultState());
		if (!edge && tu == 2 && tw == 2) set(w, m, x, top - 2, z, Blocks.OAK_PLANKS.getDefaultState());
		if (tu == 0 && tw == 2) {
			set(w, m, x, Y0 + 1, z, Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.EAST).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			set(w, m, x, Y0 + 2, z, Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.EAST).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		}
		// spire: a slender pyramid of dark tiles
		int d = Math.max(Math.abs(tu - 2), Math.abs(tw - 2));     // 0..2
		for (int y = top + 1; y <= top + 14; y++) {
			int ring = (int) Math.round(2 - (y - top) / 14.0 * 2.2);
			set(w, m, x, y, z, d <= ring ? Blocks.DEEPSLATE_TILES.getDefaultState() : AIR);
		}
		if (d == 0) {
			set(w, m, x, top + 15, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, top + 16, z, Blocks.LIGHTNING_ROD.getDefaultState());
		}
	}
}
