package nl.greatrivers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Dresses the land after the terrain is made: harbours (quay walls, paving, cranes, goods, buildings),
 * groynes and beaches along the rivers, dike roads, water in the ditches, windmills and farms.
 * Runs once per chunk, last of all features.
 */
public final class HarbourFeature extends Feature<DefaultFeatureConfig> {
	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState PAVING = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState PAVING2 = Blocks.POLISHED_ANDESITE.getDefaultState();
	private static final BlockState WALL = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState WALL_LOW = Blocks.CYAN_TERRACOTTA.getDefaultState();
	private static final BlockState FILL = Blocks.STONE.getDefaultState();
	private static final BlockState BOLLARD = Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState();
	private static final BlockState POST = Blocks.ANDESITE_WALL.getDefaultState();
	private static final BlockState LAMP = Blocks.LANTERN.getDefaultState();
	private static final int Q = Harbours.QUAY_Y;

	private static final Block[][] CONTAINER = {
			{Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_TERRACOTTA}, {Blocks.BLUE_CONCRETE, Blocks.BLUE_TERRACOTTA},
			{Blocks.RED_CONCRETE, Blocks.RED_TERRACOTTA}, {Blocks.GREEN_CONCRETE, Blocks.GREEN_TERRACOTTA},
			{Blocks.ORANGE_CONCRETE, Blocks.ORANGE_TERRACOTTA}, {Blocks.WHITE_CONCRETE, Blocks.WHITE_TERRACOTTA},
			{Blocks.GRAY_CONCRETE, Blocks.GRAY_TERRACOTTA}, {Blocks.BROWN_CONCRETE, Blocks.BROWN_TERRACOTTA},
			{Blocks.CYAN_CONCRETE, Blocks.CYAN_TERRACOTTA}, {Blocks.YELLOW_CONCRETE, Blocks.YELLOW_TERRACOTTA}};

	private static volatile boolean warned;

	public HarbourFeature() {
		super(DefaultFeatureConfig.CODEC);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
		RiverField field = RiverField.current;
		if (field == null) return false;
		StructureWorldAccess w = ctx.getWorld();
		BlockPos o = ctx.getOrigin();
		int x0 = o.getX() & ~15, z0 = o.getZ() & ~15;
		boolean any = false;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = 0; dx < 16; dx++)
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx, z = z0 + dz;
				try {
					if (dress(w, m, field, x, z)) any = true;
					// the high-voltage lines last: their wires hang over roads and fields alike
					if (Pylons.column(w, m, field, x, z)) any = true;
					if (Buoys.column(w, m, field, x, z)) any = true;
				} catch (RuntimeException ex) {
					// never let one column stop the world from generating
					if (!warned) {
						warned = true;
						GreatRivers.LOGGER.error("Great Rivers: error while dressing column " + x + ", " + z, ex);
					}
				}
			}
		Countryside.windmill(w, m, field, x0, z0);
		Countryside.farm(w, m, field, x0, z0);
		return any;
	}

	/** Everything but the high-voltage lines, for one column. */
	private boolean dress(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, int x, int z) {
		boolean any = false;
		Metros.Metro metro = field.metros.at(x, z);
		if (metro != null) {
			boolean b = MetroBuilder.column(w, m, field, metro, x, z);
			// the railway viaduct through a metropolis
			if (metro.mega() && field.rails.near(x, z, 16) && Rails.build(w, m, field, x, z) > 0) b = true;
			if (b) return true;
		}
		Villages.Town town = field.villages.at(x, z);
		if (town != null && VillageBuilder.column(w, m, field, town, x, z)) {
			return true;
		}
		Harbours.Site s = field.harbours.siteAt(x, z);
		if (s != null) {
			int k = Harbours.kind(s, x + 0.5, z + 0.5);
			if (k == Harbours.FLAT) {
				any = true;
				column(w, m, field, s, x, z);
			}
			if (k != Harbours.NO) return any;
		}
		int inf = InfraBuilder.column(w, m, field, x, z);
		if (inf > 0) any = true;
		if (inf == 2) return any;
		if (Countryside.riverBank(w, m, field, x, z)) any = true;
		if (Landscape.column(w, m, field, x, z)) any = true;
		double p = field.polderWeight(x, z);
		if (p > 0.8 && field.dikeRoad(x, z)) {
			any = true;
			Countryside.dikeRoad(w, m, field, x, z);
		}
		if (p > 0.8 && Countryside.dikeLamp(w, m, field, x, z)) any = true;
		// ditches and lakes can be too narrow for the aquifers to fill them: put the water in
		double t = field.dutchHeight(x, z);
		if (t < RiverField.WATER_TOP && field.land(x, z)) {
			for (int y = Math.max((int) Math.floor(t), RiverField.WATER_TOP - 12); y <= RiverField.WATER_TOP; y++)
				if (w.getBlockState(m.set(x, y, z)).isAir()) set(w, m, x, y, z, Blocks.WATER.getDefaultState());
		}
		return any;
	}

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private static boolean natural(BlockState st) {
		return st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS) || st.isReplaceable() || st.isOf(Blocks.GRASS_BLOCK)
				|| st.isIn(BlockTags.FLOWERS) || st.isIn(BlockTags.SAPLINGS) || st.isOf(Blocks.SUGAR_CANE) || st.isOf(Blocks.SWEET_BERRY_BUSH)
				|| st.isOf(Blocks.DIRT) || st.isOf(Blocks.STONE) || st.isOf(Blocks.COARSE_DIRT) || st.isOf(Blocks.SNOW_BLOCK)
				|| st.isOf(Blocks.SAND) || st.isOf(Blocks.GRAVEL) || st.isOf(Blocks.CLAY) || st.isOf(Blocks.MUD) || st.isOf(Blocks.MOSS_BLOCK)
				|| st.isOf(Blocks.BROWN_MUSHROOM_BLOCK) || st.isOf(Blocks.RED_MUSHROOM_BLOCK) || st.isOf(Blocks.MUSHROOM_STEM)
				|| st.isOf(Blocks.PUMPKIN) || st.isOf(Blocks.MELON) || st.isOf(Blocks.BEE_NEST) || st.isOf(Blocks.ANDESITE)
				|| st.isOf(Blocks.DIORITE) || st.isOf(Blocks.GRANITE) || st.isOf(Blocks.TUFF) || st.isOf(Blocks.CALCITE);
	}

	private static long hash(long a, long b, long c) {
		return Harbours.mix(a * 0x9E3779B97F4A7C15L + b * 0x632BE59BD9B4E019L + c * 0x2545F4914F6CDD1DL);
	}

	private void column(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, Harbours.Site s, int x, int z) {
		double cxp = x + 0.5, czp = z + 0.5;
		// clear the natural things above the quay (trees, grass, snow), keep what other columns of the harbour placed
		for (int y = Q + 1; y <= Q + 40; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && natural(st)) set(w, m, x, y, z, AIR);
		}
		for (int y = Harbours.BASIN_FLOOR + 1; y < Q; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (st.isAir() || !st.getFluidState().isEmpty() || !st.isOpaqueFullCube(w, m)) set(w, m, x, y, z, FILL);
		}
		// the quay wall where it touches the water
		Direction waterSide = null;
		for (Direction d : Direction.Type.HORIZONTAL) {
			if (Harbours.kind(s, cxp + d.getOffsetX(), czp + d.getOffsetZ()) == Harbours.WATER) {
				waterSide = d;
				break;
			}
		}
		double a = s.a(cxp, czp), v = s.v(cxp, czp) - s.bank();
		if (waterSide != null) {
			for (int y = Harbours.BASIN_FLOOR + 1; y <= Q - 1; y++) set(w, m, x, y, z, y < 59 ? WALL_LOW : WALL);
			set(w, m, x, Q, z, PAVING2);
			int idx = Math.floorMod((int) Math.round(a + v), 24);
			if (idx % 8 == 0) set(w, m, x, Q + 1, z, BOLLARD);
			else if (idx == 4) {
				int wx = x + waterSide.getOffsetX(), wz = z + waterSide.getOffsetZ();
				BlockState lad = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, waterSide);
				for (int y = Harbours.BASIN_FLOOR + 3; y <= Q; y++)
					set(w, m, wx, y, wz, lad.with(Properties.WATERLOGGED, y <= RiverField.WATER_TOP));
			}
			return;
		}
		set(w, m, x, Q, z, (Math.floorMod((int) Math.floor(v), 8) == 0 || Math.floorMod((int) Math.floor(a), 8) == 0) ? PAVING2 : PAVING);
		// cranes and their four storage bins
		for (int[] c : field.harbours.cranes(s)) {
			int dx = x - c[0], dz = z - c[1];
			if (dx == 0 && dz == 0) {
				crane(w, m, s, x, z, c[2]);
				return;
			}
			if ((Math.abs(dx) == 2 && dz == 0) || (dx == 0 && Math.abs(dz) == 2)) {
				bin(w, m, s, x, z, dz == -2 ? 1 : dx == 2 ? 2 : dz == 2 ? 3 : 4);
				return;
			}
		}
		if (Harbours.inBuilding(s, cxp, czp)) {
			building(w, m, s, x, z);
			return;
		}
		double[] f = Harbours.quayFrame(s, cxp, czp);
		if (f != null) {
			double sAlong = f[0], e = f[1];
			// light masts behind the crane track
			if (Math.abs(e - (Harbours.YARD_FROM - 2.5)) < 0.5 && Math.floorMod((int) Math.floor(sAlong) + 7, 40) == 0) {
				for (int y = Q + 1; y <= Q + 11; y++) set(w, m, x, y, z, POST);
				set(w, m, x, Q + 12, z, Blocks.SEA_LANTERN.getDefaultState());
				return;
			}
			double yardEnd = s.Q() - 8;
			if (e >= Harbours.YARD_FROM && e < yardEnd) {
				goods(w, m, s, x, z, sAlong, e - Harbours.YARD_FROM, yardEnd - Harbours.YARD_FROM);
				return;
			}
			// security fence at the back of the yard
			if (Math.abs(e - (s.Q() - 3.5)) < 0.5) {
				set(w, m, x, Q + 1, z, Blocks.IRON_BARS.getDefaultState());
				set(w, m, x, Q + 2, z, Blocks.IRON_BARS.getDefaultState());
				return;
			}
		}
		// lamp posts on the rest of the quay
		if (Math.floorMod((int) Math.floor(a) + 3, 30) == 0 && Math.floorMod((int) Math.floor(v) + 3, 30) == 0) {
			for (int y = Q + 1; y <= Q + 4; y++) set(w, m, x, y, z, POST);
			set(w, m, x, Q + 5, z, LAMP);
		}
	}

	// ------------------------------------------------------------------ cranes (from the Kempenaar mod, if it is installed)

	private static Block block(String id) {
		Identifier i = Identifier.of(id);
		return Registries.BLOCK.containsId(i) ? Registries.BLOCK.get(i) : null;
	}

	private static Direction cardinal(double x, double z) {
		return Math.abs(x) > Math.abs(z) ? (x > 0 ? Direction.EAST : Direction.WEST) : (z > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	private void crane(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, int side) {
		Block crane = block("kempenaar:laadkraan");
		if (crane == null) return;
		// facing the water
		Direction face = s.size() == Harbours.LARGE ? cardinal(-side * s.tx(), -side * s.tz()) : cardinal(-s.gx(), -s.gz());
		BlockState st = crane.getDefaultState();
		Property<?> p = crane.getStateManager().getProperty("facing");
		if (p != null && p.getValues().contains(face)) st = with(st, p, face);
		set(w, m, x, Q, z, PAVING2);
		set(w, m, x, Q + 1, z, st);
		BlockEntity be = w.getBlockEntity(m.set(x, Q + 1, z));
		if (be instanceof Inventory inv) {
			fill(inv, s.goods(), hash(x, z, 7));
			// ready to work: switched on, loading
			NbtCompound nbt = be.createNbt(w.getRegistryManager());
			nbt.putBoolean("Enabled", true);
			nbt.putBoolean("Unloading", false);
			be.read(nbt, w.getRegistryManager());
			be.markDirty();
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> BlockState with(BlockState st, Property<?> p, Object v) {
		return st.with((Property<T>) p, (T) v);
	}

	private void bin(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, int n) {
		Block bin = block("kempenaar:kraanopslag");
		if (bin == null) return;
		BlockState st = bin.getDefaultState();
		Property<?> p = bin.getStateManager().getProperty("bin");
		if (p != null && p.getValues().contains(n)) st = with(st, p, n);
		set(w, m, x, Q + 1, z, st);
		// two bins are full of the goods, two are empty so a ship can be unloaded straight away
		if (n <= 2 && w.getBlockEntity(m.set(x, Q + 1, z)) instanceof Inventory inv) {
			fill(inv, s.goods(), hash(x, z, 13));
			w.getBlockEntity(m).markDirty();
		}
	}

	/** The goods of this harbour, as the crane loads them. */
	static void fill(Inventory inv, String kind, long h) {
		Item[] items = switch (kind) {
			case "sand" -> new Item[]{Items.SAND};
			case "gravel" -> new Item[]{Items.GRAVEL};
			case "coal" -> new Item[]{Items.COAL};
			case "grain" -> new Item[]{Items.WHEAT};
			case "iron_ore" -> new Item[]{Items.RAW_IRON};
			case "timber" -> new Item[]{Items.SPRUCE_LOG, Items.OAK_LOG};
			case "clay" -> new Item[]{Items.CLAY_BALL};
			default -> new Item[]{Items.IRON_INGOT, Items.BREAD, Items.PAPER, Items.LEATHER, Items.BOOK, Items.COPPER_INGOT,
					Items.WHITE_WOOL, Items.BRICK, Items.GLASS_BOTTLE, Items.STRING};
		};
		for (int i = 0; i < inv.size(); i++) {
			Item it = items[(int) Math.floorMod(Harbours.mix(h + i), (long) items.length)];
			inv.setStack(i, new ItemStack(it, it.getMaxCount()));
		}
	}

	// ------------------------------------------------------------------ the goods yard

	private void goods(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double sAlong, double e, double depth) {
		switch (s.goods()) {
			case "containers" -> containers(w, m, s, x, z, sAlong, e, depth);
			case "grain" -> silos(w, m, s, x, z, sAlong, e, depth);
			case "timber" -> timber(w, m, s, x, z, sAlong, e);
			default -> pile(w, m, s, x, z, sAlong, e, depth);
		}
	}

	/** Stacks of 20 ft and 40 ft containers in rows, with lanes for the straddle carriers. */
	private void containers(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double sAlong, double e, double depth) {
		int ei = (int) Math.floor(e), row = ei / 4, across = ei % 4;
		if (across == 3 || ei >= (int) (depth / 4) * 4) return;          // aisle between the rows
		long rowKind = hash(row, 11, s.cx());
		int len = (rowKind & 1) == 0 ? 12 : 6;                           // 40 ft or 20 ft rows
		int si = (int) Math.floor(sAlong + 1000);
		int pitch = len + 1, slot = si / pitch, along = si % pitch;
		if (along == len || slot % 7 == 6) return;                       // gap between containers, cross lane
		long hh = hash(row, slot, s.cz());
		int levels = 1 + (int) ((hh >>> 3) % 4);
		for (int k = 0; k < levels; k++) {
			Block[] c = CONTAINER[(int) ((hash(row, slot, k) >>> 5) % CONTAINER.length)];
			for (int dy = 0; dy < 3; dy++) {
				int y = Q + 1 + k * 3 + dy;
				// a clean box: one colour, with darker corner posts so every container stands out on its own
				boolean end = along == 0 || along == len - 1, side = across == 0 || across == 2;
				Block b = end && side ? c[1] : c[0];
				set(w, m, x, y, z, b.getDefaultState());
			}
		}
	}

	/** A heap of bulk goods in bays separated by concrete block walls. */
	private void pile(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double sAlong, double e, double depth) {
		BlockState mat = switch (s.goods()) {
			case "sand" -> Blocks.SAND.getDefaultState();
			case "gravel" -> Blocks.GRAVEL.getDefaultState();
			case "coal" -> Blocks.COAL_BLOCK.getDefaultState();
			case "iron_ore" -> Blocks.RAW_IRON_BLOCK.getDefaultState();
			default -> Blocks.CLAY.getDefaultState();
		};
		double bay = 30, si = sAlong + 1000;
		double ph = si - Math.floor(si / bay) * bay;
		if (ph < 2) {                                                    // concrete block wall between the bays
			for (int y = Q + 1; y <= Q + 4; y++) set(w, m, x, y, z, Blocks.SMOOTH_STONE.getDefaultState());
			return;
		}
		double ds = (ph - 16) / 13.5, de = (e - depth / 2) / (depth / 2 + 0.5);
		double d = Math.sqrt(ds * ds + de * de);
		if (d >= 1) return;
		long hh = hash((long) Math.floor(si / bay), 3, s.cx());
		double peak = 7 + (hh & 3);
		int top = (int) Math.round(peak * (1 - Math.pow(d, 1.3)) + 0.3);
		for (int y = Q + 1; y <= Q + top; y++) set(w, m, x, y, z, mat);
	}

	/** Grain silos in two rows with a conveyor gallery on top. */
	private void silos(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double sAlong, double e, double depth) {
		double pitch = 11, si = sAlong + 1000;
		double ph = si - Math.floor(si / pitch) * pitch - pitch / 2;
		int rows = depth >= 24 ? 2 : 1;
		for (int r = 0; r < rows; r++) {
			double ec = 6 + r * 11;
			double d = Math.hypot(ph, e - ec);
			int height = 22;
			if (d <= 4.6) {
				boolean wall = d > 3.4;
				for (int y = Q + 1; y <= Q + height; y++) if (wall) set(w, m, x, y, z, Blocks.WHITE_CONCRETE.getDefaultState());
				set(w, m, x, Q + height + 1, z, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
				if (d < 2) set(w, m, x, Q + height + 2, z, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
				return;
			}
			if (Math.abs(e - ec) < 1 && d > 4.6) {                     // gallery between the silos
				set(w, m, x, Q + height + 2, z, Blocks.IRON_BLOCK.getDefaultState());
				set(w, m, x, Q + height + 3, z, Blocks.IRON_BARS.getDefaultState());
				return;
			}
		}
	}

	/** Stacks of logs, five deep and ten long, three high. */
	private void timber(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z, double sAlong, double e) {
		int ei = (int) Math.floor(e), si = (int) Math.floor(sAlong + 1000);
		if (ei % 7 >= 5 || si % 12 >= 10) return;
		// logs lie along the quay
		double ax = s.size() == Harbours.LARGE ? s.gx() : s.tx(), az = s.size() == Harbours.LARGE ? s.gz() : s.tz();
		Direction.Axis axis = Math.abs(ax) > Math.abs(az) ? Direction.Axis.X : Direction.Axis.Z;
		long hh = hash(ei / 7, si / 12, s.cz());
		int levels = 2 + (int) (hh & 1);
		BlockState log = ((hh >>> 4) & 1) == 0 ? Blocks.SPRUCE_LOG.getDefaultState() : Blocks.OAK_LOG.getDefaultState();
		for (int y = Q + 1; y <= Q + levels; y++) set(w, m, x, y, z, log.with(Properties.AXIS, axis));
	}

	// ------------------------------------------------------------------ buildings

	/** Warehouse with a white band, roller doors on the quay side, flat roof. */
	private void building(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z) {
		double cx = x + 0.5, cz = z + 0.5;
		boolean edge = !Harbours.inBuilding(s, cx + 1, cz) || !Harbours.inBuilding(s, cx - 1, cz)
				|| !Harbours.inBuilding(s, cx, cz + 1) || !Harbours.inBuilding(s, cx, cz - 1);
		int top = Q + 9;
		if (edge) {
			double a = s.a(cx, cz);
			boolean door = Math.floorMod((int) Math.floor(a) + 2, 9) < 4;
			for (int y = Q + 1; y < top; y++) {
				BlockState st = y == Q + 7 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
				if (door && y <= Q + 4) st = Blocks.GRAY_CONCRETE.getDefaultState();
				if (y == Q + 6 && !door) st = Blocks.LIGHT_GRAY_STAINED_GLASS.getDefaultState();
				set(w, m, x, y, z, st);
			}
			set(w, m, x, top, z, Blocks.GRAY_CONCRETE.getDefaultState());
			set(w, m, x, top + 1, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
		} else {
			set(w, m, x, top, z, Blocks.GRAY_CONCRETE.getDefaultState());
			long hh = hash(x, z, 1);
			if ((hh & 31) == 0) set(w, m, x, Q + 1, z, Blocks.BARREL.getDefaultState());
			else if ((hh & 31) == 1) set(w, m, x, Q + 1, z, Blocks.HAY_BLOCK.getDefaultState());
			if ((hh >>> 8 & 63) == 0) set(w, m, x, top - 1, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
		}
	}
}
