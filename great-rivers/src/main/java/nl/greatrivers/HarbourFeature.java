package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
				if (s == null || Harbours.kind(s, x, z) != Harbours.FLAT) continue;
				any = true;
				column(w, m, s, x, z);
			}
		return any;
	}

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private void column(StructureWorldAccess w, BlockPos.Mutable m, Harbours.Site s, int x, int z) {
		double a = s.a(x + 0.5, z + 0.5), v = s.v(x + 0.5, z + 0.5) - s.bank;
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
