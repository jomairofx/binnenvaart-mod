package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

/** Puts the blocks of the motorways, bridges, viaducts, locks, weirs and lock roads, one column at a time. */
final class InfraBuilder {
	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState WATER = Blocks.WATER.getDefaultState();
	private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState LINE = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState CONCRETE = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
	private static final BlockState CONCRETE_DARK = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState UNDERSIDE = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState BARRIER = Blocks.SMOOTH_STONE_SLAB.getDefaultState();
	private static final BlockState STEEL = Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState();
	private static final BlockState HANGER = Blocks.CHAIN.getDefaultState();
	private static final BlockState COPING = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState PAVING = Blocks.POLISHED_ANDESITE.getDefaultState();
	private static final BlockState GATE = Blocks.CYAN_TERRACOTTA.getDefaultState();
	private static final BlockState LEAF = Blocks.BLACK_CONCRETE.getDefaultState();
	private static final BlockState TOWER = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState GLASS = Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();
	private static final BlockState PILE = Blocks.DARK_OAK_LOG.getDefaultState();
	private static final BlockState JETTY = Blocks.SPRUCE_PLANKS.getDefaultState();
	private static final BlockState BOLLARD = Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState();
	private static final BlockState LIGHT = Blocks.LIGHT.getDefaultState().with(Properties.LEVEL_15, 15);

	private InfraBuilder() {
	}

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private static boolean natural(BlockState st) {
		return st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS) || st.isReplaceable() || st.isOf(Blocks.SNOW)
				|| st.isOf(Blocks.GRASS_BLOCK) || st.isOf(Blocks.DIRT) || st.isOf(Blocks.COARSE_DIRT) || st.isOf(Blocks.PODZOL)
				|| st.isOf(Blocks.MOSS_BLOCK) || st.isOf(Blocks.BEE_NEST) || st.isOf(Blocks.PUMPKIN) || st.isOf(Blocks.MELON)
				|| st.isOf(Blocks.SAND) || st.isOf(Blocks.GRAVEL) || st.isOf(Blocks.STONE) || st.isOf(Blocks.CLAY) || st.isOf(Blocks.MUD)
				|| st.isIn(BlockTags.FLOWERS) || st.isIn(BlockTags.SAPLINGS) || st.isOf(Blocks.SUGAR_CANE) || st.isOf(Blocks.BAMBOO)
				|| st.isOf(Blocks.SWEET_BERRY_BUSH) || st.isOf(Blocks.CACTUS) || st.isOf(Blocks.BROWN_MUSHROOM) || st.isOf(Blocks.RED_MUSHROOM)
				|| st.isOf(Blocks.LILY_PAD);
	}

	/** Takes away trees, grass and loose earth above y (up to y + h). */
	private static void clearAbove(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, int h) {
		for (int yy = y + 1; yy <= y + h; yy++) {
			BlockState st = w.getBlockState(m.set(x, yy, z));
			if (!st.isAir() && natural(st) && st.getFluidState().isEmpty()) set(w, m, x, yy, z, AIR);
		}
	}

	/** First solid block under the water / air below y. */
	private static int ground(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z) {
		for (int yy = y; yy > w.getBottomY() + 1; yy--) {
			BlockState st = w.getBlockState(m.set(x, yy, z));
			if (st.isAir() || !st.getFluidState().isEmpty() || st.isReplaceable() || st.isIn(BlockTags.LEAVES)) continue;
			return yy;
		}
		return w.getBottomY() + 1;
	}

	private static Direction cardinal(double dx, double dz) {
		if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** A modern street light: slim grey mast, short arm, flat head with an invisible light under it. */
	static void lamp(StructureWorldAccess w, BlockPos.Mutable m, int x, int base, int z, Direction arm, int height) {
		int top = base + height;
		set(w, m, x, base + 1, z, Blocks.POLISHED_ANDESITE_SLAB.getDefaultState());
		for (int y = base + 2; y <= top; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
		int ax = x + arm.getOffsetX(), az = z + arm.getOffsetZ();
		set(w, m, ax, top, az, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, arm.getAxis()));
		int hx = x + arm.getOffsetX() * 2, hz = z + arm.getOffsetZ() * 2;
		set(w, m, hx, top, hz, Blocks.IRON_TRAPDOOR.getDefaultState()
				.with(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.TOP).with(Properties.HORIZONTAL_FACING, arm));
		if (w.getBlockState(m.set(hx, top - 1, hz)).isAir()) set(w, m, hx, top - 1, hz, LIGHT);
	}

	/**
	 * The column at x, z. Returns 0 when there is nothing here, 1 when something was built but the countryside may
	 * still do its things (under a bridge), 2 when the column belongs to the road or the lock.
	 */
	static int column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		int res = 0;
		// the road at ground level first, then what goes over it
		Infra.Hit[] hits = new Infra.Hit[2];
		for (int fam = 1; fam >= 0; fam--) hits[fam] = f.infra.hit(fam, x + 0.5, z + 0.5, Infra.EDGE + 1.5);
		for (int fam = 1; fam >= 0; fam--) {
			Infra.Hit h = hits[fam];
			if (h == null || h.kind == Infra.BRIDGE || h.kind == Infra.VIADUCT) continue;
			if (groundRoad(w, m, f, h, x, z)) res = 2;
		}
		for (int fam = 1; fam >= 0; fam--) {
			Infra.Hit h = hits[fam];
			if (h == null) continue;
			if (h.kind == Infra.BRIDGE) {
				if (bridge(w, m, f, h, x, z)) res = Math.max(res, 1);
			} else if (h.kind == Infra.VIADUCT) {
				if (viaduct(w, m, h, x, z)) res = Math.max(res, 1);
			}
		}
		if (Junctions.build(w, m, f, x, z)) res = 2;
		for (Locks.Site s : f.infra.locks.around(x, z)) {
			int r = lock(w, m, f, s, x, z);
			if (r > res) res = r;
		}
		// provincial roads between the villages
		if (res < 2) {
			Object[] rh = f.roads.hit(x + 0.5, z + 0.5, Roads.OUTER + 17);
			if (rh != null) {
				double[] p = (double[]) rh[0];
				if (Math.abs(p[0]) <= Roads.OUTER + 1) {
					if (Roads.build(w, m, (Roads.Seg) rh[1], p[0], p[1], x, z)) res = 2;
				} else if (Roads.station(w, m, (Roads.Seg) rh[1], p[0], p[1], x, z)) res = 2;
			}
		}
		return res;
	}

	// ------------------------------------------------------------------ motorways

	/** Road surface across the motorway: a = distance from the middle, u = along. */
	private static BlockState surface(double a, double u) {
		if (a < 1.0) return COPING;                                              // central reservation
		if (a < 2.2) return LINE;
		if (a < 5.2) return ASPHALT;
		if (a < 6.4) return Math.floorMod((long) Math.floor(u), 12L) < 4 ? LINE : ASPHALT;   // lane line
		if (a < 9.4) return ASPHALT;
		if (a < 10.6) return LINE;                                               // edge line
		return ASPHALT;                                                          // hard shoulder
	}

	private static boolean groundRoad(StructureWorldAccess w, BlockPos.Mutable m, RiverField f0, Infra.Hit h, int x, int z) {
		double a = Math.abs(h.c);
		int y = h.y;
		if (a > Infra.EDGE + 1.5) return false;
		// no trees on or over the road
		clearAbove(w, m, x, y, z, 14);
		if (a >= Infra.HALF) return true;   // grass verge
		set(w, m, x, y, z, surface(a, h.u));
		// where the motorway ends (before the open sea) a crash barrier closes it off
		if (f0 != null && Math.abs(h.u - Math.round(h.u)) < 0.6 && f0.infra.endsNear(h.fam, h.k, h.u, 2)) {
			set(w, m, x, y + 1, z, Blocks.RED_CONCRETE.getDefaultState());
			set(w, m, x, y + 2, z, Blocks.WHITE_CONCRETE.getDefaultState());
		}
		BlockState below = w.getBlockState(m.set(x, y - 1, z));
		if (below.isAir() || !below.getFluidState().isEmpty()) set(w, m, x, y - 1, z, Blocks.STONE.getDefaultState());
		if (a < 1.0) set(w, m, x, y + 1, z, BARRIER);
		return true;
	}

	/** Deck of a bridge or viaduct at y (road surface): surface, deck slab, parapets, median, lights. */
	private static void deck(StructureWorldAccess w, BlockPos.Mutable m, Infra.Hit h, int x, int y, int z) {
		double a = Math.abs(h.c);
		if (a >= Infra.EDGE) return;
		set(w, m, x, y - 1, z, CONCRETE);
		set(w, m, x, y - 2, z, UNDERSIDE);
		if (a >= Infra.HALF) {
			set(w, m, x, y, z, CONCRETE);                 // edge beam
			set(w, m, x, y + 1, z, CONCRETE);             // parapet
			set(w, m, x, y + 2, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
			return;
		}
		set(w, m, x, y, z, surface(a, h.u));
		if (a < 1.0) set(w, m, x, y + 1, z, BARRIER);
		// box girders under the deck
		if ((a >= 2 && a < 4) || (a >= 8.5 && a < 10.5)) set(w, m, x, y - 3, z, CONCRETE);
		// street lights on the central reservation, both sides lit
		long along = Math.round(h.u);
		if (a < 1.0 && Math.floorMod(along, 36L) == 0) {
			for (int yy = y + 1; yy <= y + 8; yy++) set(w, m, x, yy, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, y + 9, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
		}
		if (a >= 2.5 && a < 3.5 && Math.floorMod(along, 36L) == 0) {
			set(w, m, x, y + 8, z, Blocks.IRON_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.TOP));
			set(w, m, x, y + 7, z, LIGHT);
		}
		if (a >= 1.0 && a < 2.5 && Math.floorMod(along, 36L) == 0) set(w, m, x, y + 8, z, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
		clearAbove(w, m, x, y + 1, z, 10);
	}

	/** Concrete from the ground (or the river bed) up to y. */
	private static void pier(StructureWorldAccess w, BlockPos.Mutable m, int x, int top, int z) {
		int g = ground(w, m, x, top, z);
		for (int yy = g - 1; yy <= top; yy++) set(w, m, x, yy, z, yy < RiverField.WATER_TOP - 1 ? CONCRETE_DARK : CONCRETE);
	}

	private static boolean bridge(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Infra.Hit h, int x, int z) {
		double a = Math.abs(h.c);
		if (a >= Infra.EDGE) return false;
		int y = Infra.DECK;
		deck(w, m, h, x, y, z);
		long along = Math.round(h.u);
		double hw = f.great.halfWidth(h.cx, h.cz);
		double dc = f.great.distance(h.cx, h.cz);
		double clear = Math.max(40, Math.min(75, hw * 0.6));     // the fairway: one free span, the arch over it
		// abutment at the end of the bridge
		if (h.e > Infra.BR_END - 2.5) {
			for (int yy = ground(w, m, x, y - 3, z); yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
		} else if (dc >= clear && Math.floorMod(along, 50L) < 3 && a < Infra.HALF) {
			// piers: two columns under the deck, with a cross beam
			if ((a >= 3.5 && a < 8.5)) pier(w, m, x, y - 3, z);
			else set(w, m, x, y - 3, z, CONCRETE);
		}
		if (dc < clear) {
			// bowstring arch over the fairway, steel painted green-blue, hung from it with chains
			double ah = Math.min(34, clear * 0.48);
			double hc = ah * (1 - (dc / clear) * (dc / clear));
			// how steep the arch is here: from the change of the distance to the river along the road
			double[] p1 = f.infra.centre(h.fam, h.k, h.u + 1);
			double d1 = f.great.distance(p1[0], p1[1]);
			double hc1 = ah * (1 - Math.min(1, (d1 / clear) * (d1 / clear)));
			int yc = y + 1 + (int) Math.round(hc);
			if (a >= Infra.HALF) {
				int lo = Math.min(yc, y + 1 + (int) Math.round((hc + hc1) / 2)), hi = Math.max(yc, y + 1 + (int) Math.round((hc + hc1) / 2));
				for (int yy = Math.max(y + 2, lo - 1); yy <= hi + 1; yy++) set(w, m, x, yy, z, STEEL);
			} else {
				if (a >= Infra.HALF - 1 && Math.floorMod(along, 6L) == 3 && hc > 3) {
					for (int yy = y + 1; yy < yc; yy++) set(w, m, x, yy, z, HANGER);   // hangers along the deck edge
				}
				if (hc > ah * 0.62 && Math.floorMod(along, 12L) == 0) set(w, m, x, yc + 1, z, STEEL);   // wind bracing
			}
		}
		return true;
	}

	private static boolean viaduct(StructureWorldAccess w, BlockPos.Mutable m, Infra.Hit h, int x, int z) {
		double a = Math.abs(h.c);
		if (a >= Infra.EDGE) return false;
		int y = h.y;
		deck(w, m, h, x, y, z);
		double dv = Math.abs(h.dv);
		if (dv > Infra.VIA_SPAN - 2.2) {
			// abutment walls just beside the road below
			for (int yy = h.underY - 2; yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
		} else if (dv < 1.0 && a >= 3.5 && a < 8.5) {
			for (int yy = h.underY + 1; yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);   // pier on the central reservation
		}
		return true;
	}

	// ------------------------------------------------------------------ locks

	private static void wall(StructureWorldAccess w, BlockPos.Mutable m, int x, int z, BlockState top) {
		int g = ground(w, m, x, Locks.WALL_TOP, z);
		for (int yy = Math.min(g - 2, Locks.WALL_TOP - 1); yy < Locks.WALL_TOP; yy++) set(w, m, x, yy, z, yy < 58 ? CONCRETE_DARK : CONCRETE);
		set(w, m, x, Locks.WALL_TOP, z, top);
		clearAbove(w, m, x, Locks.WALL_TOP, z, 12);
	}

	private static int lock(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Locks.Site s, int x, int z) {
		double px = x + 0.5, pz = z + 0.5;
		double a = s.a(px, pz), wv = s.w(px, pz), aa = Math.abs(a);
		long al = Math.round(a);
		Direction toBank = cardinal(s.gx() * s.side(), s.gz() * s.side());   // from the middle of the river to the lock bank
		int res = 0;
		if (s.inArea(a, wv, 2)) {
			// outer wall (with the waiting quay beyond the lock heads)
			if (wv >= s.outer0() && wv < s.hw() + 1 && aa <= Locks.WALL_L + Locks.GUIDE) {
				boolean face = wv < s.outer0() + 1;
				boolean gate = face && aa >= Locks.GATE0 && aa < Locks.GATE1;
				if (gate) {
					int g = ground(w, m, x, Locks.WALL_TOP, z);
					for (int yy = g - 2; yy < Locks.WALL_TOP; yy++) set(w, m, x, yy, z, LEAF);
					set(w, m, x, Locks.WALL_TOP, z, Blocks.DARK_OAK_PLANKS.getDefaultState());
				} else wall(w, m, x, z, face ? COPING : PAVING);
				if (face && !gate && Math.floorMod(al, 20L) == 10) set(w, m, x, Locks.WALL_TOP + 1, z, BOLLARD);
				if (wv >= s.hw() - 2.5 && wv < s.hw() - 1.5 && aa <= Locks.WALL_L && Math.floorMod(al, 40L) == 20)
					lamp(w, m, x, Locks.WALL_TOP, z, toBank, 7);
				return 2;
			}
			// the lock chamber and its approaches: open water, ladders against the walls
			if (wv >= s.ch0() && wv < s.outer0() && aa <= Locks.WALL_L + Locks.GUIDE) {
				for (int yy = RiverField.WATER_TOP + 1; yy <= RiverField.WATER_TOP + 30; yy++) {
					BlockState st = w.getBlockState(m.set(x, yy, z));
					if (!st.isAir() && natural(st)) set(w, m, x, yy, z, AIR);
				}
				boolean ladderSpot = aa <= Locks.WALL_L - 10 && Math.floorMod(al, 30L) == 15;
				if (ladderSpot && (wv >= s.outer0() - 1 || wv < s.ch0() + 1)) {
					Direction facing = wv >= s.outer0() - 1 ? toBank.getOpposite() : toBank;
					int g = ground(w, m, x, RiverField.WATER_TOP, z);
					for (int yy = Math.max(g + 1, 50); yy <= Locks.WALL_TOP; yy++)
						set(w, m, x, yy, z, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, facing)
								.with(Properties.WATERLOGGED, yy <= RiverField.WATER_TOP));
				}
				return 2;
			}
			// middle wall with the control tower, and the guide jetty beyond it
			if (wv >= s.mid0() && wv < s.ch0()) {
				if (aa <= Locks.WALL_L) {
					boolean face = wv >= s.ch0() - 1;
					boolean gate = face && aa >= Locks.GATE0 && aa < Locks.GATE1;
					if (gate) {
						int g = ground(w, m, x, Locks.WALL_TOP, z);
						for (int yy = g - 2; yy < Locks.WALL_TOP; yy++) set(w, m, x, yy, z, LEAF);
						set(w, m, x, Locks.WALL_TOP, z, Blocks.DARK_OAK_PLANKS.getDefaultState());
					} else wall(w, m, x, z, face ? COPING : PAVING);
					if (face && !gate && Math.floorMod(al, 20L) == 10) set(w, m, x, Locks.WALL_TOP + 1, z, BOLLARD);
					controlTower(w, m, s, x, z, a, wv);
					// green signal lights at both ends: the lock is open
					if (aa >= Locks.WALL_L - 1.5 && wv >= s.mid0() + 3 && wv < s.mid0() + 4) {
						for (int yy = Locks.WALL_TOP + 1; yy <= Locks.WALL_TOP + 4; yy++) set(w, m, x, yy, z, Blocks.IRON_BARS.getDefaultState());
						set(w, m, x, Locks.WALL_TOP + 5, z, Blocks.LIME_STAINED_GLASS.getDefaultState());
						set(w, m, x, Locks.WALL_TOP + 6, z, LIGHT);
					}
					// the weir road reaches the tower over the middle wall
					if (aa <= 3.5 && wv < s.mid0() + 1) {
						set(w, m, x, Locks.WEIR_DECK - 1, z, CONCRETE);
						set(w, m, x, Locks.WEIR_DECK, z, COPING);
					}
					return 2;
				}
				if (aa <= Locks.WALL_L + Locks.GUIDE && wv >= s.ch0() - 3) {
					int g = ground(w, m, x, Locks.WALL_TOP, z);
					boolean pile = Math.floorMod(al, 8L) < 2;
					if (pile) for (int yy = g - 1; yy < Locks.WALL_TOP; yy++) set(w, m, x, yy, z, PILE);
					set(w, m, x, Locks.WALL_TOP, z, JETTY);
					if (pile && wv >= s.ch0() - 1) set(w, m, x, Locks.WALL_TOP + 1, z, Blocks.DARK_OAK_FENCE.getDefaultState());
					return 2;
				}
			}
			// the weir
			if (aa <= Locks.WEIR_A && wv >= s.far() - 2 && wv < s.mid0()) {
				weir(w, m, s, x, z, a, wv);
				return 2;
			}
			// paved strip behind the outer wall
			if (wv >= s.hw() + 1 && wv < s.hw() + Locks.LAND && aa <= Locks.WALL_L) {
				int y = Locks.WALL_TOP;
				set(w, m, x, y, z, Math.floorMod((long) Math.floor(wv), 4L) == 0 ? COPING : PAVING);
				BlockState below = w.getBlockState(m.set(x, y - 1, z));
				if (below.isAir() || !below.getFluidState().isEmpty()) set(w, m, x, y - 1, z, Blocks.STONE.getDefaultState());
				clearAbove(w, m, x, y, z, 14);
				if (wv >= s.hw() + Locks.LAND - 2 && wv < s.hw() + Locks.LAND - 1 && Math.floorMod(al, 40L) == 0)
					lamp(w, m, x, y, z, toBank.getOpposite(), 7);
				return 2;
			}
			// the service road down the ramp on the far bank
			if (aa <= 3.5 && wv < s.far() - 2 && wv > s.far() - 150) {
				int top = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
				if (top >= RiverField.WATER_TOP) {
					clearAbove(w, m, x, top, z, 12);
					set(w, m, x, top, z, aa >= 2.5 ? LINE : ASPHALT);
					res = 2;
				}
			}
		}
		// the provincial road to the village
		double[] rd = s.road(px, pz);
		if (rd != null && Math.abs(rd[0]) <= Roads.OUTER + 1) {
			Roads.Seg seg = new Roads.Seg(s.rx0(), s.rz0(), s.rx1(), s.rz1(), s.side());
			if (Roads.build(w, m, seg, rd[0], rd[1], x, z)) return 2;
		}
		return res;
	}

	private static void controlTower(StructureWorldAccess w, BlockPos.Mutable m, Locks.Site s, int x, int z, double a, double wv) {
		double w0 = s.mid0() + 1, w1 = s.ch0() - 1;     // 6 wide, on the middle wall
		if (Math.abs(a) > 5.5 || wv < w0 || wv >= w1) return;
		boolean edge = Math.abs(a) > 4.5 || wv < w0 + 1 || wv >= w1 - 1;
		int y0 = Locks.WALL_TOP + 1;
		for (int y = y0; y <= 80; y++) {
			BlockState st;
			if (y == 80) st = Blocks.SMOOTH_STONE.getDefaultState();
			else if (!edge) st = y == Locks.WEIR_DECK ? Blocks.SMOOTH_STONE.getDefaultState() : AIR;
			else if (y >= 76 && y <= 78) st = GLASS;
			else st = TOWER;
			set(w, m, x, y, z, st);
		}
		// doors: below on the lock side, above to the weir road
		if (edge && Math.abs(a) <= 1 && wv < w0 + 1) {
			set(w, m, x, Locks.WEIR_DECK + 1, z, AIR);
			set(w, m, x, Locks.WEIR_DECK + 2, z, AIR);
		}
		if (edge && a < -4.5 && wv >= w0 + 2 && wv < w0 + 3) {
			set(w, m, x, y0, z, AIR);
			set(w, m, x, y0 + 1, z, AIR);
		}
		// a ladder inside from the ground floor to the control room
		if (!edge && a > 3 && a <= 4.5 && wv >= w1 - 2) {
			for (int y = y0; y <= Locks.WEIR_DECK + 1; y++) set(w, m, x, y, z, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
		}
		if (!edge && Math.abs(a) < 0.5 && wv >= (w0 + w1) / 2 - 0.5 && wv < (w0 + w1) / 2 + 0.5) {
			set(w, m, x, Locks.WEIR_DECK + 4, z, Blocks.SEA_LANTERN.getDefaultState());
			for (int y = 81; y <= 84; y++) set(w, m, x, y, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, 85, z, Blocks.LIGHTNING_ROD.getDefaultState());
		}
	}

	private static void weir(StructureWorldAccess w, BlockPos.Mutable m, Locks.Site s, int x, int z, double a, double wv) {
		double aa = Math.abs(a);
		double q = s.mid0() - wv;
		boolean abut = wv < -s.hw() + 2;
		boolean pier = abut || Math.floorMod((long) Math.floor(q), (long) Locks.PIER_EVERY) < 4;
		int g = ground(w, m, x, RiverField.WATER_TOP, z);
		int deck = Locks.WEIR_DECK;
		if (pier) {
			for (int yy = g - 2; yy < deck; yy++) set(w, m, x, yy, z, yy < 58 ? CONCRETE_DARK : CONCRETE);
			if (aa <= 3.5) set(w, m, x, deck, z, COPING);
			else set(w, m, x, deck, z, CONCRETE);
			if (!abut) {
				// hoist towers on both sides of the road, joined by a portal beam
				for (int yy = deck + 1; yy <= 82; yy++) {
					BlockState st = AIR;
					if (aa >= 4) st = yy >= 81 ? CONCRETE : TOWER;
					else if (yy >= 81) st = CONCRETE;
					set(w, m, x, yy, z, st);
				}
				set(w, m, x, 83, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
			} else clearAbove(w, m, x, deck, z, 12);
			return;
		}
		// a bay: concrete sill, closed steel gate, the service road over it
		for (int yy = g - 2; yy <= 54; yy++) set(w, m, x, yy, z, CONCRETE_DARK);
		if (aa < 1.5) for (int yy = 55; yy <= RiverField.WATER_TOP + 2; yy++) set(w, m, x, yy, z, GATE);
		if (aa <= 4.5) {
			set(w, m, x, deck - 1, z, CONCRETE);
			set(w, m, x, deck, z, aa > 3.5 ? CONCRETE : COPING);
			if (aa > 3.5) set(w, m, x, deck + 1, z, CONCRETE);   // parapet
		}
		// cables of the gate hoists
		if (aa < 1 && Math.floorMod((long) Math.floor(q), (long) Locks.PIER_EVERY) == 6)
			for (int yy = RiverField.WATER_TOP + 3; yy < deck - 1; yy++) set(w, m, x, yy, z, HANGER);
	}
}
