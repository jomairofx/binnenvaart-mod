package nl.greatrivers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;

/**
 * Builds the business parks ("bedrijventerreinen") of {@link Roads.Park}: a main street through the middle with
 * pavements and street lights, plots on both sides, each with a company of its own: a distribution centre, a
 * factory, an office building, a car dealer, a builder's yard, a self-storage, or a data centre. A data centre
 * campus is one company: huge grey halls full of servers, the roof covered with cooling units, rows of emergency
 * generators, its own substation and a high fence.
 */
final class BusinessParks {
	private BusinessParks() {
	}

	static final int G = 63;                     // ground level of the park (top block)
	static final int Y0 = G + 1;
	static final int PLOT = 72;                  // plot length along the main street
	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState WALK = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState LINE = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.getDefaultState();
	private static final BlockState BARS = Blocks.IRON_BARS.getDefaultState();

	static final int DC = 0, DISTRIBUTION = 1, FACTORY = 2, OFFICES = 3, CARS = 4, YARD = 5, STORAGE = 6;

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private static long hash(long a, long b, long c) {
		return Harbours.mix(a * 0x9E3779B97F4A7C15L + b * 0x632BE59BD9B4E019L + c * 0x2545F4914F6CDD1DL);
	}

	/** The company on plot (side, index) of a park. */
	static int company(Roads.Park p, int side, int index) {
		if (p.type() == 1) return DC;
		long h = hash(p.seed(), side, index);
		int r = (int) Math.floorMod(h, 100L);
		return r < 12 ? DC : r < 38 ? DISTRIBUTION : r < 55 ? FACTORY : r < 68 ? OFFICES : r < 80 ? CARS : r < 90 ? YARD : STORAGE;
	}

	static void column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Roads.Park p, int x, int z) {
		// clear plants and trees, fill the ground up to the park level
		for (int y = G + 1; y <= G + 40; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && (st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS) || st.isReplaceable() || st.isIn(BlockTags.FLOWERS)))
				set(w, m, x, y, z, AIR);
		}
		for (int y = G - 3; y < G; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (st.isAir() || !st.getFluidState().isEmpty()) set(w, m, x, y, z, Blocks.DIRT.getDefaultState());
		}
		int L = p.alongX() ? p.x1() - p.x0() : p.z1() - p.z0();
		int D = p.alongX() ? p.z1() - p.z0() : p.x1() - p.x0();
		int u = p.alongX() ? x - p.x0() : z - p.z0();          // along the main street
		int v = p.alongX() ? z - p.z0() : x - p.x0();          // across
		int mid = D / 2;
		int across = v - mid;
		// the edge: a hedge round the park
		if (u <= 1 || u >= L - 1 || v <= 1 || v >= D - 1) {
			set(w, m, x, G, z, GRASS);
			if ((u == 0 || u == L || v == 0 || v == D) && !(Math.abs(u - L / 2) <= 9))
				set(w, m, x, G + 1, z, Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
			return;
		}
		// the main street: two lanes, pavements, street lights, trees
		if (Math.abs(across) <= 7) {
			int a = Math.abs(across);
			BlockState st = a <= 4 ? ASPHALT : a == 5 ? GRASS : WALK;
			if (a == 0 && Math.floorMod(u, 10) < 4) st = LINE;
			set(w, m, x, G, z, st);
			if (a == 5 && Math.floorMod(u, 30) == 15) InfraBuilder.lamp(w, m, x, G, z, p.alongX() ? (across < 0 ? Direction.SOUTH : Direction.NORTH) : (across < 0 ? Direction.EAST : Direction.WEST), 7);
			else if (a == 5 && Math.floorMod(u, 15) == 7) Countryside.tree(w, m, x, G + 1, z, hash(x, z, 3));
			return;
		}
		// the plots on both sides: pu along the street, pv from the street back
		int side = across < 0 ? 0 : 1;
		int pv = Math.abs(across) - 8;
		int depth = mid - 10;
		int start = 4;
		int index = Math.floorDiv(u - start, PLOT), pu = Math.floorMod(u - start, PLOT);
		int nPlots = (L - 2 * start) / PLOT;
		// where the access road comes in: keep free
		if (Math.abs(u - L / 2) <= 9 || index < 0 || index >= nPlots) {
			set(w, m, x, G, z, GRASS);
			if ((hash(x, z, 9) % 41) == 0) Countryside.tree(w, m, x, G + 1, z, hash(x, z, 4));
			return;
		}
		// the direction from a plot towards the street, and the facade orientation
		Direction toStreet = p.alongX() ? (side == 0 ? Direction.SOUTH : Direction.NORTH) : (side == 0 ? Direction.EAST : Direction.WEST);
		boolean facadeX = p.alongX();
		long ph = hash(p.seed(), side * 1000L + index, 17);
		switch (company(p, side, index)) {
			case DC -> dataCentre(w, m, x, z, pu, pv, depth, ph, facadeX);
			case DISTRIBUTION -> distribution(w, m, x, z, pu, pv, depth, ph, facadeX);
			case FACTORY -> factory(w, m, x, z, pu, pv, depth, ph, facadeX);
			case OFFICES -> offices(w, m, x, z, pu, pv, depth, ph, facadeX, toStreet);
			case CARS -> carDealer(w, m, x, z, pu, pv, depth, ph, facadeX);
			case YARD -> yard(w, m, x, z, pu, pv, depth, ph, facadeX);
			default -> storage(w, m, x, z, pu, pv, depth, ph, facadeX);
		}
	}

	/** A glass pane along the facade line (or across it). */
	private static BlockState pane(boolean alongFacade, boolean facadeX) {
		return (alongFacade == facadeX) ? VillageBuilder.paneEW(Blocks.GLASS_PANE) : VillageBuilder.paneNS(Blocks.GLASS_PANE);
	}

	/** A plain box hall: walls of wall, a band near the top, a flat roof. Returns true if (pu, pv) is inside it. */
	private static boolean hall(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int pu, int pv, int u0, int u1, int v0, int v1, int h,
								BlockState wall, BlockState band, BlockState roof) {
		if (pu < u0 || pu > u1 || pv < v0 || pv > v1) return false;
		boolean edge = pu == u0 || pu == u1 || pv == v0 || pv == v1;
		set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
		for (int y = Y0; y < Y0 + h; y++) set(w, m, x, y, z, edge ? (y == Y0 + h - 2 ? band : wall) : AIR);
		set(w, m, x, Y0 + h, z, edge ? wall : roof);
		return true;
	}

	// ------------------------------------------------------------------ the companies

	/**
	 * A data centre: a huge grey windowless hall with vertical ribs, a small glass office at the street, the roof full
	 * of cooling units, a row of emergency generators behind it, its own transformers, a high fence all round.
	 */
	private static void dataCentre(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int pu, int pv, int depth, long h, boolean facadeX) {
		set(w, m, x, G, z, ASPHALT);
		int u0 = 4, u1 = PLOT - 6, v0 = 10, v1 = depth - 18;
		// the fence
		if ((pu == 1 || pu == PLOT - 3) && pv >= 2 && pv <= depth - 2 || (pv == 2 || pv == depth - 2) && pu >= 1 && pu <= PLOT - 3) {
			if (!(pv == 2 && pu >= 30 && pu <= 36)) {
				set(w, m, x, Y0, z, BARS);
				set(w, m, x, Y0 + 1, z, BARS);
				set(w, m, x, Y0 + 2, z, BARS);
			}
			return;
		}
		int H = 16;
		if (pu >= u0 && pu <= u1 && pv >= v0 && pv <= v1) {
			boolean edge = pu == u0 || pu == u1 || pv == v0 || pv == v1;
			boolean office = pv <= v0 + 6 && pu >= u0 + 4 && pu <= u0 + 20;
			set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
			for (int y = Y0; y < Y0 + H; y++) {
				BlockState st = AIR;
				int rel = y - Y0;
				if (edge) {
					boolean rib = (pv == v0 || pv == v1 ? pu : pv) % 4 == 0;
					st = rib ? Blocks.GRAY_CONCRETE.getDefaultState() : Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
					if (office && pv == v0 && rel >= 1 && rel <= 8 && rel % 4 != 0) st = pane(true, facadeX);
					if (office && pv == v0 && pu >= u0 + 10 && pu <= u0 + 12 && rel <= 2) st = AIR;
				} else if (!office && rel <= 6 && (pu - u0) % 5 == 2 && pv > v0 + 8) {
					// rows of server racks, their lights blinking blue and green
					st = rel <= 5 ? (((pv + rel) % 7 == 0) ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.BLACK_CONCRETE.getDefaultState()) : AIR;
				} else if (rel == H - 1 && (pu % 6 == 3) && (pv % 6 == 3)) st = Blocks.SEA_LANTERN.getDefaultState();
				else if (office && rel == 4) st = Blocks.SMOOTH_STONE.getDefaultState();
				set(w, m, x, y, z, st);
			}
			set(w, m, x, Y0 + H, z, edge ? Blocks.GRAY_CONCRETE.getDefaultState() : Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
			// cooling units on the roof: boxes with a fan grille on top
			if (!edge && pu > u0 + 2 && pu < u1 - 2 && pv > v0 + 2 && pv < v1 - 2) {
				int cu = (pu - u0) % 6, cv = (pv - v0) % 7;
				if (cu >= 1 && cu <= 4 && cv >= 1 && cv <= 4) {
					set(w, m, x, Y0 + H + 1, z, Blocks.GRAY_CONCRETE.getDefaultState());
					set(w, m, x, Y0 + H + 2, z, (cu == 2 || cu == 3) && (cv == 2 || cv == 3) ? Blocks.IRON_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.BOTTOM) : Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
				}
			}
			return;
		}
		// emergency generators behind the hall: container-like boxes with exhausts
		if (pv >= v1 + 4 && pv <= v1 + 7 && pu >= u0 && pu <= u1 && (pu - u0) % 8 < 6) {
			set(w, m, x, Y0, z, Blocks.WHITE_CONCRETE.getDefaultState());
			set(w, m, x, Y0 + 1, z, Blocks.WHITE_CONCRETE.getDefaultState());
			set(w, m, x, Y0 + 2, z, (pu - u0) % 8 == 2 && pv == v1 + 5 ? Blocks.IRON_BARS.getDefaultState() : Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
			if ((pu - u0) % 8 == 2 && pv == v1 + 5) set(w, m, x, Y0 + 3, z, Blocks.IRON_BARS.getDefaultState());
			return;
		}
		// the data centre's own transformers by the fence
		if (pv >= v1 + 10 && pv <= v1 + 13 && pu >= u1 - 14 && pu <= u1 && (pu - (u1 - 14)) % 5 < 3) {
			for (int y = Y0; y <= Y0 + 3; y++) set(w, m, x, y, z, Blocks.GRAY_CONCRETE.getDefaultState());
			if (pv == v1 + 11) set(w, m, x, Y0 + 4, z, Blocks.LIGHTNING_ROD.getDefaultState());
			return;
		}
		// car park at the front
		if (pv >= 3 && pv < v0 - 1 && pu % 3 == 0) set(w, m, x, G, z, LINE);
	}

	/** A distribution centre: a big pale box with loading docks and lorries at them. */
	private static void distribution(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int pu, int pv, int depth, long h, boolean facadeX) {
		set(w, m, x, G, z, ASPHALT);
		BlockState wall = (h & 1) == 0 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
		BlockState band = new BlockState[]{Blocks.BLUE_CONCRETE.getDefaultState(), Blocks.ORANGE_CONCRETE.getDefaultState(), Blocks.GREEN_CONCRETE.getDefaultState(), Blocks.RED_CONCRETE.getDefaultState()}[(int) ((h >>> 2) & 3)];
		int v0 = 22, v1 = depth - 6;
		if (hall(w, m, x, z, pu, pv, 3, PLOT - 5, v0, v1, 14, wall, band, Blocks.GRAY_CONCRETE.getDefaultState())) {
			if (pv == v0 && (pu - 3) % 6 >= 2 && (pu - 3) % 6 <= 4) for (int y = Y0; y <= Y0 + 3; y++) set(w, m, x, y, z, Blocks.GRAY_CONCRETE.getDefaultState());   // dock doors
			int rel;
			if (pv > v0 && pv < v1 && pu % 9 == 4) for (int y = Y0; y <= Y0 + 7; y++) set(w, m, x, y, z, (y - Y0) % 3 == 0 ? Blocks.SPRUCE_SLAB.getDefaultState() : Blocks.BARREL.getDefaultState());   // racks
			return;
		}
		// lorries backed up to the docks
		if (pv >= v0 - 14 && pv < v0 - 1 && (pu - 3) % 6 >= 2 && (pu - 3) % 6 <= 3 && ((hash(h, pu / 6, 1) & 1) == 0)) {
			boolean cab = pv < v0 - 11;
			set(w, m, x, Y0, z, Blocks.BLACK_CONCRETE.getDefaultState());
			set(w, m, x, Y0 + 1, z, cab ? band : Blocks.WHITE_CONCRETE.getDefaultState());
			set(w, m, x, Y0 + 2, z, cab ? Blocks.BLACK_STAINED_GLASS.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
			if (!cab) set(w, m, x, Y0 + 3, z, Blocks.WHITE_CONCRETE.getDefaultState());
		}
	}

	/** A factory: brick halls with a sawtooth roof, a tall chimney, silos and a pipe bridge. */
	private static void factory(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int pu, int pv, int depth, long h, boolean facadeX) {
		set(w, m, x, G, z, ASPHALT);
		int cu = PLOT - 12, cv = depth - 12;
		if (Math.abs(pu - cu) <= 1 && Math.abs(pv - cv) <= 1) {
			int top = Y0 + 40;
			for (int y = Y0; y <= top; y++) {
				BlockState st = Blocks.BRICKS.getDefaultState();
				if (y > top - 5) st = ((y - top) & 2) == 0 ? Blocks.RED_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState();
				if (pu == cu && pv == cv && y > Y0) st = AIR;
				set(w, m, x, y, z, st);
			}
			return;
		}
		// silos
		for (int s = 0; s < 3; s++) {
			int su = 8 + s * 7, sv = depth - 10;
			int du = pu - su, dv = pv - sv;
			if (du * du + dv * dv <= 6) {
				for (int y = Y0; y <= Y0 + 18; y++) set(w, m, x, y, z, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
				set(w, m, x, Y0 + 19, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
				return;
			}
		}
		int v0 = 14, v1 = depth - 20;
		if (pu >= 4 && pu <= PLOT - 6 && pv >= v0 && pv <= v1) {
			boolean edge = pu == 4 || pu == PLOT - 6 || pv == v0 || pv == v1;
			int top = Y0 + 9;
			set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
			for (int y = Y0; y <= top; y++) {
				BlockState st = AIR;
				if (edge) {
					st = Blocks.BRICKS.getDefaultState();
					if ((pv == v0 || pv == v1) && pu % 4 == 2 && y >= Y0 + 3 && y <= Y0 + 6) st = pane(true, facadeX);
					if (pv == v0 && pu >= 30 && pu <= 35 && y <= Y0 + 5) st = Blocks.GRAY_CONCRETE.getDefaultState();
				} else if (y == Y0 && pu % 6 == 3 && pv % 4 == 1) st = Blocks.IRON_BLOCK.getDefaultState();
				else if (y == Y0 + 1 && pu % 6 == 3 && pv % 4 == 1) st = Blocks.BLAST_FURNACE.getDefaultState();
				else if (y == top - 1 && pu % 8 == 4 && pv % 6 == 3) st = Blocks.SEA_LANTERN.getDefaultState();
				set(w, m, x, y, z, st);
			}
			// sawtooth roof
			int saw = (pv - v0) % 4;
			int ry = top + 1 + (saw == 3 ? 0 : saw);
			for (int y = top + 1; y < ry; y++) set(w, m, x, y, z, edge && (pu == 4 || pu == PLOT - 6) ? Blocks.BRICKS.getDefaultState() : AIR);
			set(w, m, x, ry, z, saw == 3 ? Blocks.GLASS.getDefaultState() : Blocks.GRAY_CONCRETE.getDefaultState());
			if (saw == 3) for (int y = top + 2; y <= top + 3; y++) set(w, m, x, y, z, Blocks.GLASS.getDefaultState());
			return;
		}
		if (pv == v1 + 4 && pu >= 4 && pu <= 26) set(w, m, x, Y0 + 7, z, Blocks.IRON_BARS.getDefaultState());   // pipe bridge
	}

	/** An office building: glass and white bands, five to eight floors, with a car park. */
	private static void offices(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int pu, int pv, int depth, long h, boolean facadeX, Direction toStreet) {
		int u0 = 12, u1 = PLOT - 14, v0 = 12, v1 = Math.min(depth - 10, 40);
		int floors = 5 + (int) ((h >>> 3) % 4), H = floors * 4;
		if (pu >= u0 && pu <= u1 && pv >= v0 && pv <= v1) {
			boolean edge = pu == u0 || pu == u1 || pv == v0 || pv == v1;
			set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
			for (int y = Y0; y < Y0 + H; y++) {
				int rel = y - Y0;
				BlockState st = AIR;
				if (edge) {
					boolean longWall = pv == v0 || pv == v1;
					st = rel % 4 == 0 ? Blocks.WHITE_CONCRETE.getDefaultState() : pane(longWall, facadeX);
					if ((pu == u0 || pu == u1) && (pv == v0 || pv == v1)) st = Blocks.WHITE_CONCRETE.getDefaultState();
					if (pv == v0 && pu >= (u0 + u1) / 2 - 1 && pu <= (u0 + u1) / 2 + 1 && rel <= 2) st = AIR;
				} else if (rel % 4 == 0) st = Blocks.SMOOTH_STONE.getDefaultState();
				else if (rel % 4 == 3 && pu % 4 == 1 && pv % 4 == 1 && Math.floorMod(Harbours.mix(h + rel / 4), 10L) < 6) st = Blocks.SEA_LANTERN.getDefaultState();
				else if (rel % 4 == 1 && pu % 4 == 2 && pv % 3 == 1) st = Blocks.SPRUCE_SLAB.getDefaultState().with(Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.TOP);
				set(w, m, x, y, z, st);
			}
			set(w, m, x, Y0 + H, z, Blocks.GRAY_CONCRETE.getDefaultState());
			if (!edge && pu == (u0 + u1) / 2 && pv == (v0 + v1) / 2) for (int y = Y0 + H + 1; y <= Y0 + H + 4; y++) set(w, m, x, y, z, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
			return;
		}
		// car park with trees
		set(w, m, x, G, z, ASPHALT);
		if (pv > v1 + 2 || pv < v0 - 2) {
			if (pu % 3 == 0) set(w, m, x, G, z, LINE);
			else if ((hash(h, pu / 3, pv) & 3) == 0) {
				set(w, m, x, Y0, z, new BlockState[]{Blocks.WHITE_CONCRETE.getDefaultState(), Blocks.BLACK_CONCRETE.getDefaultState(), Blocks.BLUE_CONCRETE.getDefaultState(), Blocks.GRAY_CONCRETE.getDefaultState()}[(int) ((hash(h, pu / 3, pv / 5) >>> 4) & 3)]);
			}
		} else {
			set(w, m, x, G, z, GRASS);
			if ((hash(x, z, 5) % 29) == 0) Countryside.tree(w, m, x, Y0, z, hash(x, z, 6));
		}
	}

	private static final Block[] CAR = {Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE};

	/** A car dealer: a glass showroom with cars inside, rows of cars outside, flags. */
	private static void carDealer(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int pu, int pv, int depth, long h, boolean facadeX) {
		set(w, m, x, G, z, Blocks.SMOOTH_STONE.getDefaultState());
		int u0 = 8, u1 = 44, v0 = 18, v1 = 38;
		if (pu >= u0 && pu <= u1 && pv >= v0 && pv <= v1) {
			boolean edge = pu == u0 || pu == u1 || pv == v0 || pv == v1;
			for (int y = Y0; y < Y0 + 7; y++) {
				BlockState st = AIR;
				if (edge) st = y == Y0 + 6 ? Blocks.BLACK_CONCRETE.getDefaultState() : pane(pv == v0 || pv == v1, facadeX);
				if (edge && (pu == u0 || pu == u1) && (pv == v0 || pv == v1)) st = Blocks.BLACK_CONCRETE.getDefaultState();
				set(w, m, x, y, z, st);
			}
			set(w, m, x, Y0 + 7, z, Blocks.WHITE_CONCRETE.getDefaultState());
			if (!edge && (pu - u0) % 8 >= 2 && (pu - u0) % 8 <= 5 && (pv - v0) % 6 >= 2 && (pv - v0) % 6 <= 3) {
				set(w, m, x, Y0, z, CAR[(int) Math.floorMod(hash(h, (pu - u0) / 8, (pv - v0) / 6), (long) CAR.length)].getDefaultState());
				if ((pu - u0) % 8 >= 3 && (pu - u0) % 8 <= 4) set(w, m, x, Y0 + 1, z, Blocks.BLACK_STAINED_GLASS.getDefaultState());
			}
			return;
		}
		// cars for sale in rows
		if (pv >= 4 && pv <= depth - 6 && !(pv >= v0 - 2 && pv <= v1 + 2 && pu <= u1 + 2)) {
			int cu = pu % 6, cv = pv % 4;
			if (cu >= 1 && cu <= 4 && cv >= 1 && cv <= 2) {
				set(w, m, x, Y0, z, CAR[(int) Math.floorMod(hash(h, pu / 6, pv / 4), (long) CAR.length)].getDefaultState());
				if (cu == 2 || cu == 3) set(w, m, x, Y0 + 1, z, Blocks.BLACK_STAINED_GLASS.getDefaultState());
			}
		}
		// flag poles at the street
		if (pv == 1 && pu % 8 == 4 && pu < u1) {
			for (int y = Y0; y <= Y0 + 6; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, Y0 + 7, z, new BlockState[]{Blocks.BLUE_WOOL.getDefaultState(), Blocks.RED_WOOL.getDefaultState(), Blocks.WHITE_WOOL.getDefaultState()}[(int) ((h >>> 5) % 3)]);
		}
	}

	/** A builder's yard / agricultural contractor: a steel shed, machines, heaps of sand and gravel. */
	private static void yard(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int pu, int pv, int depth, long h, boolean facadeX) {
		set(w, m, x, G, z, Blocks.GRAVEL.getDefaultState());
		int v0 = depth - 34, v1 = depth - 8;
		if (hall(w, m, x, z, pu, pv, 6, 40, v0, v1, 10, Blocks.GREEN_TERRACOTTA.getDefaultState(), Blocks.WHITE_CONCRETE.getDefaultState(), Blocks.GRAY_CONCRETE.getDefaultState())) {
			if (pv == v0 && pu >= 14 && pu <= 22) for (int y = Y0; y <= Y0 + 6; y++) set(w, m, x, y, z, AIR);    // open front
			return;
		}
		// heaps
		for (int k = 0; k < 2; k++) {
			double cu = 52 + k * 0, cv = 12 + k * 14;
			double d = Math.hypot((pu - cu) / 8.0, (pv - cv) / 6.0);
			if (d < 1) {
				int top = (int) Math.round(5 * (1 - d * d));
				for (int y = Y0; y < Y0 + top; y++) set(w, m, x, y, z, k == 0 ? Blocks.SAND.getDefaultState() : Blocks.GRAVEL.getDefaultState());
				return;
			}
		}
		// a tractor and a digger
		if (pu >= 10 && pu <= 12 && pv >= 8 && pv <= 9) {
			set(w, m, x, Y0, z, Blocks.BLACK_CONCRETE.getDefaultState());
			set(w, m, x, Y0 + 1, z, (h & 1) == 0 ? Blocks.GREEN_CONCRETE.getDefaultState() : Blocks.RED_CONCRETE.getDefaultState());
			if (pu == 10) set(w, m, x, Y0 + 2, z, Blocks.GLASS.getDefaultState());
		}
		if (pu >= 20 && pu <= 22 && pv >= 8 && pv <= 10) {
			set(w, m, x, Y0, z, Blocks.BLACK_CONCRETE.getDefaultState());
			set(w, m, x, Y0 + 1, z, Blocks.YELLOW_CONCRETE.getDefaultState());
			if (pu == 21 && pv == 9) set(w, m, x, Y0 + 2, z, Blocks.YELLOW_CONCRETE.getDefaultState());
		}
	}

	/** Self-storage: rows of low boxes with roller doors in bright colours. */
	private static void storage(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int pu, int pv, int depth, long h, boolean facadeX) {
		set(w, m, x, G, z, ASPHALT);
		int row = (pv - 8) / 10, rv = (pv - 8) % 10;
		if (pv < 8 || pv > depth - 6 || pu < 4 || pu > PLOT - 6) return;
		if (rv >= 6) return;                                   // lanes between the rows
		boolean edge = rv == 0 || rv == 5 || pu == 4 || pu == PLOT - 6;
		BlockState door = (h & 1) == 0 ? Blocks.ORANGE_CONCRETE.getDefaultState() : Blocks.BLUE_CONCRETE.getDefaultState();
		for (int y = Y0; y <= Y0 + 3; y++) {
			BlockState st = edge ? Blocks.LIGHT_GRAY_CONCRETE.getDefaultState() : AIR;
			if ((rv == 0 || rv == 5) && pu % 4 != 0 && y <= Y0 + 2) st = door;
			set(w, m, x, y, z, st);
		}
		set(w, m, x, Y0 + 4, z, Blocks.GRAY_CONCRETE.getDefaultState());
	}
}
