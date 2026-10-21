package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Dresses the harbour terrain (made level by {@link RiverCarve}) with concrete quay walls, paving,
 * bollards, ladders, lamp posts and a warehouse. Runs once per chunk, last of all features.
 */
public final class HarbourFeature extends Feature<DefaultFeatureConfig> {
	private static final BlockState AIR = Blocks.AIR.getDefaultState();
	private static final BlockState PAVING = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState PAVING2 = Blocks.POLISHED_ANDESITE.getDefaultState();
	private static final BlockState WALL = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState WALL_LOW = Blocks.CYAN_TERRACOTTA.getDefaultState();
	private static final BlockState CAP = Blocks.SMOOTH_STONE_SLAB.getDefaultState();
	private static final BlockState FILL = Blocks.STONE.getDefaultState();
	private static final BlockState SHED = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
	private static final BlockState SHED_BAND = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState ROOF = Blocks.SMOOTH_STONE.getDefaultState();
	private static final BlockState BOLLARD = Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState();
	private static final BlockState POST = Blocks.ANDESITE_WALL.getDefaultState();
	private static final BlockState LAMP = Blocks.LANTERN.getDefaultState();
	private static final int Q = Harbours.QUAY_Y;

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
				Harbours.Site s = field.harbours.siteAt(x, z);
				if (s != null && Harbours.kind(s, x, z) == Harbours.FLAT) {
					any = true;
					column(w, m, s, x, z);
					continue;
				}
				if (s == null && field.polderWeight(x, z) > 0.8 && field.dikeRoad(x, z)) {
					any = true;
					dikeRoad(w, m, x, z);
				}
			}
		windmill(w, m, field, x0, z0);
		return any;
	}

	private static final BlockState ROAD = Blocks.GRAY_CONCRETE_POWDER.getDefaultState();

	/** Asphalt road along the top of the dike. */
	private static void dikeRoad(StructureWorldAccess w, BlockPos.Mutable m, int x, int z) {
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

	private static long mix(long h) {
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		h *= 0xc4ceb9fe1a85ec53L;
		return h ^ (h >>> 33);
	}

	/** Now and then a Dutch windmill in the polder. */
	private static void windmill(StructureWorldAccess w, BlockPos.Mutable m, RiverField field, int x0, int z0) {
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
				if (t != 64 || !w.getBlockState(m.set(mx + dx, t, mz + dz)).isOf(Blocks.GRASS_BLOCK)) return;
			}
		if (field.low.canalDistance(mx, mz) < 60) return;
		BlockState brick = Blocks.BRICKS.getDefaultState(), wood = Blocks.DARK_OAK_PLANKS.getDefaultState();
		BlockState cap = Blocks.DARK_OAK_PLANKS.getDefaultState(), white = Blocks.WHITE_WOOL.getDefaultState();
		BlockState frame = Blocks.SPRUCE_FENCE.getDefaultState();
		int base = 65;
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

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private void column(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z) {
		double a = s.a(x + 0.5, z + 0.5), v = s.v(x + 0.5, z + 0.5) - s.bank();
		// clear everything above the quay (trees, grass, snow)
		for (int y = Q + 1; y <= Q + 30; y++) if (!w.getBlockState(m.set(x, y, z)).isAir()) set(w, m, x, y, z, AIR);
		// solid quay body
		for (int y = Harbours.BASIN_FLOOR + 1; y < Q; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (st.isAir() || !st.getFluidState().isEmpty() || !st.isOpaqueFullCube(w, m)) set(w, m, x, y, z, FILL);
		}
		// which side touches the water?
		Direction waterSide = null;
		for (Direction d : Direction.Type.HORIZONTAL) {
			if (Harbours.kind(s, x + d.getOffsetX() + 0.5, z + d.getOffsetZ() + 0.5) == Harbours.WATER) {
				waterSide = d;
				break;
			}
		}
		if (waterSide != null) {
			// concrete quay wall with a dark lower band, smooth cap stone on top
			for (int y = Harbours.BASIN_FLOOR + 1; y <= Q - 1; y++) set(w, m, x, y, z, y < 59 ? WALL_LOW : WALL);
			set(w, m, x, Q, z, PAVING2);
			int idx = Math.floorMod((int) Math.round(a + v), 24);
			int wx = x + waterSide.getOffsetX(), wz = z + waterSide.getOffsetZ();
			if (idx % 8 == 0) set(w, m, x, Q + 1, z, BOLLARD);                              // bollards
			else if (idx == 4) {                                                            // ladder down the quay wall
				BlockState lad = Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, waterSide);
				for (int y = Harbours.BASIN_FLOOR + 3; y <= Q; y++) {
					boolean wet = y <= RiverField.WATER_TOP;
					set(w, m, wx, y, wz, lad.with(Properties.WATERLOGGED, wet));
				}
			}
			return;
		}
		boolean shed = Harbours.inShed(s, x + 0.5, z + 0.5);
		set(w, m, x, Q, z, (Math.floorMod((int) Math.floor(v), 7) == 0) ? PAVING2 : PAVING);
		if (shed) {
			boolean edge = !Harbours.inShed(s, x + 1.5, z + 0.5) || !Harbours.inShed(s, x - 0.5, z + 0.5)
					|| !Harbours.inShed(s, x + 0.5, z + 1.5) || !Harbours.inShed(s, x + 0.5, z - 0.5);
			boolean door = Math.abs(a) < 4.5 && v < Harbours.B + 17;
			if (edge) {
				for (int y = Q + 1; y <= Q + 7; y++) {
					if (door && y <= Q + 4) continue;
					set(w, m, x, y, z, y == Q + 6 ? SHED_BAND : SHED);
				}
			}
			set(w, m, x, Q + 8, z, ROOF);
			if (!edge && Math.floorMod(x * 7 + z * 3, 23) == 0) set(w, m, x, Q + 1, z, Blocks.BARREL.getDefaultState());
			if (!edge && Math.floorMod(x + z * 5, 31) == 0) set(w, m, x, Q + 7, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
			return;
		}
		// lamp posts in a row behind the quay edge
		if (Math.abs(v - (Harbours.B + 7)) < 0.5 && Math.floorMod((int) Math.round(a), 18) == 0) {
			for (int y = Q + 1; y <= Q + 4; y++) set(w, m, x, y, z, POST);
			set(w, m, x, Q + 5, z, LAMP);
		}
	}
}
