package nl.greatrivers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;

/**
 * Builds the big ports of {@link Harbours} (size PORT): three or four basins of 90 x 560 cut into the land, with
 * 150 wide piers between them. Every pier is a terminal of its own:
 * - a container terminal: tall ship-to-shore gantry cranes along both quays, their booms out over the water,
 *   stacks of containers in rows behind them;
 * - a dry bulk terminal: long heaps of coal or ore under a conveyor gallery, bulldozers' lanes;
 * - a liquid bulk terminal: white and grey storage tanks in rows, pipelines along the quay.
 * Behind the basins: distribution centres, the port office tower, truck parking. Red and green harbour lights
 * stand on the pier heads at the river.
 */
final class PortBuilder {
	private PortBuilder() {
	}

	static final int Q = Harbours.QUAY_Y;
	static final int CONTAINER = 0, BULK = 1, LIQUID = 2;
	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState PAVING = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState PAVING2 = Blocks.POLISHED_ANDESITE.getDefaultState();
	private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState WALL = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState WALL_LOW = Blocks.CYAN_TERRACOTTA.getDefaultState();
	private static final BlockState FILL = Blocks.STONE.getDefaultState();
	private static final Block[][] BOX = {
			{Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_TERRACOTTA}, {Blocks.BLUE_CONCRETE, Blocks.BLUE_TERRACOTTA},
			{Blocks.RED_CONCRETE, Blocks.RED_TERRACOTTA}, {Blocks.GREEN_CONCRETE, Blocks.GREEN_TERRACOTTA},
			{Blocks.ORANGE_CONCRETE, Blocks.ORANGE_TERRACOTTA}, {Blocks.WHITE_CONCRETE, Blocks.WHITE_TERRACOTTA},
			{Blocks.GRAY_CONCRETE, Blocks.GRAY_TERRACOTTA}, {Blocks.BROWN_CONCRETE, Blocks.BROWN_TERRACOTTA},
			{Blocks.CYAN_CONCRETE, Blocks.CYAN_TERRACOTTA}, {Blocks.YELLOW_CONCRETE, Blocks.YELLOW_TERRACOTTA}};

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private static long hash(long a, long b, long c) {
		return Harbours.mix(a * 0x9E3779B97F4A7C15L + b * 0x632BE59BD9B4E019L + c * 0x2545F4914F6CDD1DL);
	}

	/** What kind of terminal pier p is (0 .. basins: pier p lies between basin p-1 and p; 0 and basins are the outer quays). */
	static int terminal(Harbours.Site s, int p) {
		if (p == 0 || p == s.basins()) return CONTAINER;
		return (int) Math.floorMod(hash(s.cx(), s.cz(), p), 3L);
	}

	// ------------------------------------------------------------------ one column of the quay

	static void column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Harbours.Site s, int x, int z) {
		double cx = x + 0.5, cz = z + 0.5;
		for (int y = Q + 1; y <= Q + 60; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && (st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS) || st.isReplaceable() || st.isIn(BlockTags.FLOWERS)))
				set(w, m, x, y, z, AIR);
		}
		for (int y = Harbours.BASIN_FLOOR + 1; y < Q; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (st.isAir() || !st.getFluidState().isEmpty() || !st.isOpaqueFullCube(w, m)) set(w, m, x, y, z, FILL);
		}
		// the quay wall where it touches the water
		Direction waterSide = null;
		for (Direction d : Direction.Type.HORIZONTAL)
			if (Harbours.kind(s, cx + d.getOffsetX(), cz + d.getOffsetZ()) == Harbours.WATER) {
				waterSide = d;
				break;
			}
		double[] pf = Harbours.portFrame(s, cx, cz);
		int basin = (int) pf[0];
		double e = pf[1], v = pf[2], da = pf[3];
		if (waterSide != null) {
			for (int y = Harbours.BASIN_FLOOR + 1; y <= Q - 1; y++) set(w, m, x, y, z, y < 59 ? WALL_LOW : WALL);
			set(w, m, x, Q, z, PAVING2);
			int idx = Math.floorMod((int) Math.round(v + s.a(cx, cz)), 30);
			if (idx % 10 == 0) set(w, m, x, Q + 1, z, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());   // bollards
			else if (idx == 5) {
				int wx = x + waterSide.getOffsetX(), wz = z + waterSide.getOffsetZ();
				BlockState lad = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, waterSide);
				for (int y = Harbours.BASIN_FLOOR + 3; y <= Q; y++) set(w, m, wx, y, wz, lad.with(Properties.WATERLOGGED, y <= RiverField.WATER_TOP));
			}
			return;
		}
		set(w, m, x, Q, z, PAVING);
		// harbour lights on the pier heads at the river: red on one side of each basin mouth, green on the other
		if (v >= 2 && v < 6 && e >= 2 && e < 6) {
			light(w, m, x, z, da < 0 ? 0 : 1, v, e);
			return;
		}
		if (v <= Harbours.P_BL - 4) {
			// along the basins: which pier, and how far from each of its two quays
			int pier = da > 0 ? basin + 1 : basin;
			double pierW = (pier == 0 || pier == s.basins()) ? Harbours.P_END : Harbours.P_PW;
			int kind = terminal(s, pier);
			// on the outer quays the far side (away from the basin) is just the edge of the port
			boolean outer = pier == 0 || pier == s.basins();
			double e2 = outer ? Double.MAX_VALUE : pierW - e;     // from the other quay of the pier
			double ee = Math.min(e, e2);
			boolean nearSide = e <= e2;
			// tall light masts along the middle of the pier
			if (Math.abs(e - pierW / 2) < 0.6 && Math.floorMod((int) Math.floor(v), 80) == 40 && !outer) {
				for (int y = Q + 1; y <= Q + 24; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
				set(w, m, x, Q + 25, z, Blocks.SEA_LANTERN.getDefaultState());
				return;
			}
			switch (kind) {
				case CONTAINER -> containerPier(w, m, s, x, z, ee, v, nearSide ? 1 : -1, pier, outer ? e : ee, outer, pierW);
				case BULK -> bulkPier(w, m, s, x, z, ee, v, pier, pierW, e);
				default -> liquidPier(w, m, s, x, z, ee, v, pier, pierW, e);
			}
			return;
		}
		// behind the basins: the port's land side
		backArea(w, m, s, x, z, s.a(cx, cz), v - Harbours.P_BL);
	}

	/** Columns over the water of the basins: the booms of the container cranes. */
	static void water(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Harbours.Site s, int x, int z) {
		double[] pf = Harbours.portFrame(s, x + 0.5, z + 0.5);
		double e = pf[1], v = pf[2], da = pf[3];
		if (e >= 0 || e < -40 || v < 0 || v > Harbours.P_BL - 4) return;
		int basin = (int) pf[0];
		int pier = da > 0 ? basin + 1 : basin;
		if (terminal(s, pier) != CONTAINER) return;
		boom(w, m, s, x, z, e, v, pier);
	}

	// ------------------------------------------------------------------ container terminal

	static final int CRANE_PITCH = 70, LEG_W = 4, LEG_L = 22, BOOM_Y = Q + 38;

	/** Ship-to-shore gantry crane along the quay: where is the crane nearest to this v along the basin? */
	private static double craneV(double v) {
		return Math.floorDiv((long) Math.floor(v), (long) CRANE_PITCH) * (double) CRANE_PITCH + CRANE_PITCH / 2.0;
	}

	private static BlockState craneColour(Harbours.Site s, int pier) {
		return ((hash(s.cx(), pier, 5) & 1) == 0) ? Blocks.LIGHT_BLUE_CONCRETE.getDefaultState() : Blocks.RED_CONCRETE.getDefaultState();
	}

	private static void containerPier(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double ee, double v, int side, int pier,
									  double e, boolean outer, double pierW) {
		double vc = craneV(v), dv = v - vc;
		BlockState col = craneColour(s, pier);
		// the crane rails along the quay
		if (Math.abs(ee - LEG_W) < 0.5 || Math.abs(ee - LEG_L) < 0.5) set(w, m, x, Q, z, Blocks.IRON_BLOCK.getDefaultState());
		if (ee < LEG_L + 6 && vc > 20 && vc < Harbours.P_BL - 30) {
			// the gantry: four legs, portal beams, the machinery house, the boom (its landward back reach) and the A-frame
			boolean leg = (Math.abs(ee - LEG_W) < 1 || Math.abs(ee - LEG_L) < 1) && Math.abs(Math.abs(dv) - 8) < 1;
			if (leg) for (int y = Q + 1; y <= BOOM_Y - 4; y++) set(w, m, x, y, z, col);
			if (ee > LEG_W - 1 && ee < LEG_L + 1 && Math.abs(Math.abs(dv) - 8) < 1) {
				set(w, m, x, Q + 14, z, col);                      // low portal beam (the straddle carriers pass under it)
				set(w, m, x, BOOM_Y - 4, z, col);
			}
			if (ee > LEG_W - 1 && ee < LEG_L + 1 && Math.abs(dv) < 9 && (ee < LEG_W + 1 || ee > LEG_L - 1)) set(w, m, x, BOOM_Y - 4, z, col);
			if (Math.abs(dv) < 2) {
				// the boom and its back reach over the quay
				if (ee < LEG_L + 6) set(w, m, x, BOOM_Y, z, col);
				if (ee > LEG_L - 3 && ee < LEG_L + 6 && Math.abs(dv) < 1.5) for (int y = BOOM_Y - 3; y < BOOM_Y; y++) set(w, m, x, y, z, Blocks.WHITE_CONCRETE.getDefaultState());   // machinery house
				// the A-frame over the legs, with the stays down to the boom
				if (Math.abs(ee - LEG_W) < 1 || Math.abs(ee - LEG_L) < 1) for (int y = BOOM_Y + 1; y <= BOOM_Y + 12; y++) set(w, m, x, y, z, col);
				if (Math.abs(ee - LEG_W) < 1 && Math.abs(dv) < 0.6) set(w, m, x, BOOM_Y + 13, z, Blocks.REDSTONE_LAMP.getDefaultState());
			}
			if (Math.abs(dv) < 1 && ee >= LEG_W && ee <= LEG_L) set(w, m, x, BOOM_Y + 12, z, col);
			// the driver's cabin under the boom at the water side
			if (Math.abs(dv) < 1 && ee < LEG_W - 1) set(w, m, x, BOOM_Y - 1, z, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
			if (leg || Math.abs(dv) < 2) return;
		}
		// container stacks behind the cranes, in rows with lanes between
		double from = LEG_L + 10, to = outer ? pierW - 20 : pierW / 2 - 4;
		if (ee >= from && ee < to) stacks(w, m, s, x, z, v, ee - from, pier * 2 + (side > 0 ? 0 : 1));
		else if (Math.abs(ee - (LEG_L + 6)) < 1 && Math.floorMod((int) Math.floor(v), 16) < 12 && Math.floorMod((int) Math.floor(v), 160) < 40) {
			// a straddle carrier waiting in the lane
			int k = Math.floorMod((int) Math.floor(v), 16);
			if (k == 0 || k == 3 || k == 8 || k == 11) for (int y = Q + 1; y <= Q + 8; y++) set(w, m, x, y, z, Blocks.YELLOW_CONCRETE.getDefaultState());
		}
	}

	/** The boom of a gantry crane out over the water. */
	private static void boom(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double e, double v, int pier) {
		double vc = craneV(v), dv = v - vc;
		if (vc <= 20 || vc >= Harbours.P_BL - 30) return;
		BlockState col = craneColour(s, pier);
		if (Math.abs(dv) < 2 && e > -38) {
			set(w, m, x, BOOM_Y, z, col);
			// the stays from the A-frame down to the boom
			double t = (-e) / 38.0;
			int sy = BOOM_Y + 12 - (int) Math.round(11 * t);
			if (Math.abs(dv) < 1 && sy > BOOM_Y) set(w, m, x, sy, z, Blocks.IRON_BARS.getDefaultState());
			// the trolley with the spreader and a container hanging from it
			if (Math.abs(e + 18) < 2) {
				set(w, m, x, BOOM_Y - 1, z, Blocks.GRAY_CONCRETE.getDefaultState());
				if (Math.abs(dv) < 1) for (int y = BOOM_Y - 8; y < BOOM_Y - 1; y++) set(w, m, x, y, z, Blocks.CHAIN.getDefaultState());
				Block[] c = BOX[(int) Math.floorMod(hash(s.cx(), (long) vc, pier), (long) BOX.length)];
				for (int y = BOOM_Y - 11; y < BOOM_Y - 8; y++) set(w, m, x, y, z, c[0].getDefaultState());
			}
		}
	}

	private static void stacks(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double v, double e, long row0) {
		int ei = (int) Math.floor(e), row = ei / 4, across = ei % 4;
		if (across == 3) return;                                          // lane between the rows
		long rowKind = hash(row, row0, s.cx());
		int len = (rowKind & 1) == 0 ? 12 : 6;
		int si = (int) Math.floor(v + 1000);
		int pitch = len + 1, slot = si / pitch, along = si % pitch;
		if (along == len || slot % 8 == 7) return;
		long hh = hash(row * 31 + row0, slot, s.cz());
		int levels = 1 + (int) ((hh >>> 3) % 5);
		for (int k = 0; k < levels; k++) {
			Block[] c = BOX[(int) ((hash(row * 31 + row0, slot, k) >>> 5) % BOX.length)];
			for (int dy = 0; dy < 3; dy++) {
				boolean end = along == 0 || along == len - 1, side = across == 0 || across == 2;
				set(w, m, x, Q + 1 + k * 3 + dy, z, (end && side ? c[1] : c[0]).getDefaultState());
			}
		}
	}

	// ------------------------------------------------------------------ dry bulk terminal

	private static void bulkPier(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double ee, double v, int pier, double pierW, double e) {
		BlockState mat = (hash(s.cx(), pier, 9) & 1) == 0 ? Blocks.COAL_BLOCK.getDefaultState() : Blocks.RAW_IRON_BLOCK.getDefaultState();
		// unloaders on rails along the quays: grab cranes with a long boom
		if (Math.abs(ee - 6) < 0.5 || Math.abs(ee - 18) < 0.5) set(w, m, x, Q, z, Blocks.IRON_BLOCK.getDefaultState());
		double vc = Math.floorDiv((long) Math.floor(v), 120L) * 120.0 + 60, dv = v - vc;
		if (ee < 22 && Math.abs(dv) < 6) {
			boolean leg = (Math.abs(ee - 6) < 1 || Math.abs(ee - 18) < 1) && Math.abs(Math.abs(dv) - 5) < 1;
			if (leg) for (int y = Q + 1; y <= Q + 22; y++) set(w, m, x, y, z, Blocks.ORANGE_CONCRETE.getDefaultState());
			if (Math.abs(dv) < 6 && ee > 5 && ee < 19) set(w, m, x, Q + 22, z, Blocks.ORANGE_CONCRETE.getDefaultState());
			if (Math.abs(dv) < 1.5 && ee >= 0) set(w, m, x, Q + 26, z, Blocks.ORANGE_CONCRETE.getDefaultState());
			if (Math.abs(dv) < 1.5 && ee > 8 && ee < 16) for (int y = Q + 23; y < Q + 26; y++) set(w, m, x, y, z, Blocks.WHITE_CONCRETE.getDefaultState());
			return;
		}
		// the conveyor gallery along the middle of the pier
		double mid = pierW / 2;
		if (Math.abs(e - mid) < 1.5) {
			set(w, m, x, Q + 12, z, Blocks.IRON_BLOCK.getDefaultState());
			if (Math.abs(e - mid) > 0.5) set(w, m, x, Q + 13, z, Blocks.IRON_BARS.getDefaultState());
			else set(w, m, x, Q + 13, z, Blocks.BLACK_CARPET.getDefaultState());
			if (Math.floorMod((int) Math.floor(v), 24) == 0) for (int y = Q + 1; y < Q + 12; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
			return;
		}
		// long heaps on both sides of the conveyor
		double heapE = Math.abs(e - mid) - 3, depth = mid - 26;
		if (heapE > 0 && heapE < depth) {
			double bay = 110, si = v - 20;
			double ph = si - Math.floor(si / bay) * bay;
			if (si < 0 || ph < 6) return;
			double ds = (ph - bay / 2 - 3) / (bay / 2 - 4), de = (heapE - depth / 2) / (depth / 2);
			double d = Math.max(Math.abs(ds), Math.abs(de) * 0.9);
			if (d >= 1) return;
			int top = (int) Math.round(14 * (1 - Math.pow(d, 1.5)) + 0.3);
			for (int y = Q + 1; y <= Q + top; y++) set(w, m, x, y, z, mat);
		}
	}

	// ------------------------------------------------------------------ liquid bulk terminal

	private static void liquidPier(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double ee, double v, int pier, double pierW, double e) {
		// pipelines along the quays, loading arms
		if (ee >= 4 && ee < 7) {
			set(w, m, x, Q + 1, z, Blocks.IRON_BLOCK.getDefaultState());
			if (ee >= 5 && ee < 6) set(w, m, x, Q + 2, z, Blocks.IRON_BARS.getDefaultState());
			if (Math.floorMod((int) Math.floor(v), 90) == 45 && ee < 5) for (int y = Q + 2; y <= Q + 9; y++) set(w, m, x, y, z, Blocks.YELLOW_CONCRETE.getDefaultState());
			return;
		}
		// rows of round tanks
		double pitchV = 40, pitchE = 36;
		double eo = e - 22, vo = v - 20;
		if (eo < 0 || vo < 0 || eo > pierW - 44) return;
		double ce = Math.floor(eo / pitchE) * pitchE + pitchE / 2, cv = Math.floor(vo / pitchV) * pitchV + pitchV / 2;
		if (cv > Harbours.P_BL - 50) return;
		double r = Math.hypot(eo - ce, vo - cv);
		long th = hash((long) ce, (long) cv, pier + s.cx());
		double R = 13 + (th & 3);
		int H = 16 + (int) ((th >>> 3) % 8);
		BlockState shell = (th >>> 6 & 1) == 0 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
		if (r <= R) {
			boolean wall = r > R - 1.1;
			for (int y = Q + 1; y <= Q + H; y++) set(w, m, x, y, z, wall ? shell : AIR);
			// slightly domed roof
			int ry = Q + H + (r < R * 0.5 ? 1 : 0);
			set(w, m, x, Q + H, z, shell);
			if (ry > Q + H) set(w, m, x, ry, z, shell);
			if (wall && Math.floorMod((int) Math.floor(eo + vo), 9) == 0 && Q + H - 3 > Q) set(w, m, x, Q + H - 2, z, shell);
			return;
		}
		// the bund wall round each tank
		if (Math.abs(Math.max(Math.abs(eo - ce), Math.abs(vo - cv)) - (pitchE / 2 - 1)) < 0.6) set(w, m, x, Q + 1, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
	}

	// ------------------------------------------------------------------ the land side behind the basins

	private static void backArea(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double a, double b) {
		// b = 0 at the heads of the basins; a road along the heads, then warehouses, the port office, truck parking
		if (b < 14) {
			set(w, m, x, Q, z, b < 2 || b >= 12 ? PAVING : ASPHALT);
			if (b >= 6.5 && b < 7.5 && Math.floorMod((int) Math.floor(a), 10) < 5) set(w, m, x, Q, z, Blocks.WHITE_CONCRETE.getDefaultState());
			if (b < 1 && Math.floorMod((int) Math.floor(a), 32) == 0) InfraBuilder.lamp(w, m, x, Q, z, Direction.NORTH, 8);
			return;
		}
		double along = s.alongExtent();
		// the port office tower in the middle
		if (Math.abs(a) < 18 && b >= 30 && b < 60) {
			int bu = (int) Math.floor(a + 18) % 40, bw = (int) Math.floor(b - 30);
			VillageBuilder.tower(w, m, x, z, Math.min(28, Math.max(0, bu - 4)), Math.min(20, Math.max(0, (int) Math.round(bw * 20 / 30.0))), hash(s.cx(), s.cz(), 77), -0.2);
			return;
		}
		// distribution centres in a row: big pale halls with docks and trailers
		double hallA = 120, gap = 30;
		double ap = a + along + 1000;
		double ph = ap - Math.floor(ap / (hallA + gap)) * (hallA + gap);
		if (b >= 40 && b < 140 && ph < hallA && Math.abs(a) > 24) {
			int hu = (int) Math.floor(ph), hw = (int) Math.floor(b - 40);
			boolean edge = hu == 0 || hu == (int) hallA - 1 || hw == 0 || hw == 99;
			int top = Q + 16;
			long hh = hash((long) Math.floor(ap / (hallA + gap)), s.cx(), 3);
			BlockState wall = (hh & 1) == 0 ? Blocks.LIGHT_GRAY_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState();
			BlockState band = new BlockState[]{Blocks.BLUE_CONCRETE.getDefaultState(), Blocks.GREEN_CONCRETE.getDefaultState(), Blocks.ORANGE_CONCRETE.getDefaultState(), Blocks.RED_CONCRETE.getDefaultState()}[(int) ((hh >>> 2) & 3)];
			set(w, m, x, Q, z, PAVING);
			for (int y = Q + 1; y <= top; y++) {
				BlockState st = AIR;
				if (edge) {
					st = y == top - 2 ? band : wall;
					if (hw == 0 && y <= Q + 5 && hu % 8 >= 2 && hu % 8 <= 5) st = Blocks.GRAY_CONCRETE.getDefaultState();   // dock doors
				} else if (y == top - 1 && hu % 10 == 5 && hw % 10 == 5) st = Blocks.SEA_LANTERN.getDefaultState();
				else if (y <= Q + 7 && hu % 9 == 4 && hw >= 6 && hw <= 93) st = (y - Q) % 3 == 0 ? Blocks.SPRUCE_SLAB.getDefaultState() : (hw % 4 == 1 ? Blocks.IRON_BARS.getDefaultState() : AIR);
				set(w, m, x, y, z, st);
			}
			set(w, m, x, top + 1, z, edge ? wall : Blocks.GRAY_CONCRETE.getDefaultState());
			return;
		}
		// the yard in front of the halls: trailers at the docks
		set(w, m, x, Q, z, ASPHALT);
		if (b >= 26 && b < 38 && ph < hallA && Math.abs(a) > 24) {
			int hu = (int) Math.floor(ph);
			if (hu % 8 >= 3 && hu % 8 <= 4 && ((hash(hu / 8, (long) Math.floor(ap / (hallA + gap)), 7) & 1) == 0)) {
				set(w, m, x, Q + 1, z, b < 27 ? Blocks.BLACK_CONCRETE.getDefaultState() : Blocks.GRAY_CONCRETE.getDefaultState());
				set(w, m, x, Q + 2, z, b < 28 ? Blocks.LIGHT_BLUE_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
				set(w, m, x, Q + 3, z, b < 28 ? Blocks.LIGHT_BLUE_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
			}
		}
		// the fence round the port
		if (b > s.inland() - Harbours.P_BL - 6 || Math.abs(a) > along - 4) {
			set(w, m, x, Q + 1, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, Q + 2, z, Blocks.IRON_BARS.getDefaultState());
		} else if (b >= 160 && Math.floorMod((int) Math.floor(a), 5) == 0 && Math.floorMod((int) Math.floor(b), 18) < 12) {
			set(w, m, x, Q, z, Blocks.WHITE_CONCRETE.getDefaultState());       // truck parking bays
		}
	}

	/** A small harbour light on a pier head: a round tower, red or green, with the light on top. */
	private static void light(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, int colour, double v, double e) {
		BlockState c = colour == 0 ? Blocks.RED_CONCRETE.getDefaultState() : Blocks.GREEN_CONCRETE.getDefaultState();
		boolean core = v >= 3 && v < 5 && e >= 3 && e < 5;
		for (int y = Q + 1; y <= Q + 8; y++) set(w, m, x, y, z, core || y == Q + 1 ? ((y - Q) % 4 == 0 ? Blocks.WHITE_CONCRETE.getDefaultState() : c) : AIR);
		if (core) {
			set(w, m, x, Q + 9, z, Blocks.SEA_LANTERN.getDefaultState());
			set(w, m, x, Q + 10, z, colour == 0 ? Blocks.RED_STAINED_GLASS.getDefaultState() : Blocks.GREEN_STAINED_GLASS.getDefaultState());
		}
	}
}
