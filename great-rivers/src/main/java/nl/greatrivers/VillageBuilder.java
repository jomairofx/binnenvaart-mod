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
	static final int G = 62;          // ground level: top block of the polder
	static final int Y0 = G + 1;      // ground floor
	private static final Block[] BRICK = {Blocks.BRICKS, Blocks.BRICKS, Blocks.MUD_BRICKS, Blocks.BRICKS, Blocks.BRICKS};
	private static final Block[][] ROOF = {
			{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES},
			{Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.BRICKS},
			{Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.DEEPSLATE_TILES},
			{Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICKS}};

	private VillageBuilder() {}

	static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private static long hash(long a, long b, long c) {
		return Harbours.mix(a * 0x9E3779B97F4A7C15L + b * 0x632BE59BD9B4E019L + c * 0x2545F4914F6CDD1DL);
	}

	/** Builds this column of the town. Returns true if anything was placed. */
	static boolean column(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z) {
		// clear trees and plants above the ground
		for (int y = G + 1; y <= G + 40; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && (st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS) || st.isReplaceable())) set(w, m, x, y, z, AIR);
		}
		int u = Math.floorMod(x - t.cx(), Villages.PU), wv = Math.floorMod(z - t.cz(), Villages.PW);
		if (Villages.street(t, x, z)) {
			street(w, m, t, x, z, u, wv);
			return true;
		}
		if (!Villages.blockIn(t, x, z)) return false;
		int bu = u - Villages.STREET, bw = wv - Villages.STREET;   // 0..28, 0..20
		int bx = Math.floorDiv(x - t.cx(), Villages.PU), bz = Math.floorDiv(z - t.cz(), Villages.PW);
		if (bx == 0 && bz == 0) {
			church(w, m, t, x, z, bu, bw);
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
		house(w, m, t, x, z, hx, pw, front, hh);
		return true;
	}

	// ------------------------------------------------------------------ streets

	private static void street(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z, int u, int wv) {
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
		// lime trees along the main streets of a town
		if (t.city() && across == Villages.STREET - 1 && Math.floorMod(along + 6, 12) == 0) {
			Countryside.tree(w, m, x, G + 1, z, hash(x, z, 5));
		}
	}

	// ------------------------------------------------------------------ houses

	/** Direction towards the street (out of the front door) for this row. */
	private static Direction out(boolean front) {
		return front ? Direction.NORTH : Direction.SOUTH;
	}

	private static BlockState stairs(Block b, Direction f) {
		return b.getDefaultState().with(Properties.HORIZONTAL_FACING, f);
	}

	private static void house(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z, int hx, int pw, boolean front, long hh) {
		Block wall = BRICK[(int) (hh & 3)];
		Block[] roof = ROOF[(int) ((hh >>> 3) & 3)];
		Direction out = out(front), in = out.getOpposite();
		boolean edge = hx == 0 || hx == 6 || pw == 0 || pw == 9;
		BlockState brick = wall.getDefaultState();
		BlockState glass = Blocks.WHITE_STAINED_GLASS_PANE.getDefaultState();
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
		if (pw == 3 && hx == 2 && (t.seed() + hx * 7 + pw) % 3 == 0) {
			VillagerEntity v = EntityType.VILLAGER.create(w.toServerWorld());
			if (v != null) {
				v.refreshPositionAndAngles(x + 0.5, fl0, z + 0.5, 0, 0);
				v.setPersistent();
				v.initialize(w, w.getLocalDifficulty(m.set(x, fl0, z)), SpawnReason.STRUCTURE, null);
				w.spawnEntityAndPassengers(v);
			}
		}
	}

	private static long bw(Villages.Town t) {
		return t.seed() >>> 7;
	}

	private static void door(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, Direction facing, Block door) {
		BlockState d = door.getDefaultState().with(DoorBlock.FACING, facing.getOpposite());
		set(w, m, x, Y0 + 1, z, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(w, m, x, Y0 + 2, z, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	// ------------------------------------------------------------------ the church and its square

	private static void church(StructureWorldAccess w, BlockPos.Mutable m, Villages.Town t, int x, int z, int bu, int bw) {
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
					if (window) st = (y == Y0 + 7 ? Blocks.LIGHT_BLUE_STAINED_GLASS_PANE : Blocks.WHITE_STAINED_GLASS_PANE).getDefaultState();
					if (nu == 16 && nw == 5 && y >= Y0 + 4 && y <= Y0 + 7) st = Blocks.YELLOW_STAINED_GLASS_PANE.getDefaultState();
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
