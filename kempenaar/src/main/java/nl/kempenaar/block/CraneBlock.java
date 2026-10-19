package nl.kempenaar.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import nl.kempenaar.Kempenaar;
import org.jetbrains.annotations.Nullable;

/**
 * Laadkraan voor op de kade. Stop er lading in (of laat een trechter dat doen) en de kraan
 * schept het in het dichtstbijzijnde open laadruim. Sluipend klikken: wisselen tussen laden en lossen.
 */
public class CraneBlock extends BlockWithEntity {
	public static final MapCodec<CraneBlock> CODEC = createCodec(CraneBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

	public CraneBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
	}

	/** Bij het neerzetten komen er vier opslagbunkers rond de voet (waar plek is). */
	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (world.isClient) return;
		placeStorage(world, pos);
	}

	public static int placeStorage(World world, BlockPos pos) {
		int n = 0, bin = 1;
		for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
			BlockPos p = pos.offset(d);
			if (world.getBlockState(p).isReplaceable()) {
				world.setBlockState(p, Kempenaar.CRANE_STORAGE.getDefaultState().with(CraneStorageBlock.BIN, bin));
				n++;
			}
			bin++;
		}
		return n;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CraneBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, Kempenaar.CRANE_BE, CraneBlockEntity::serverTick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof CraneBlockEntity crane) {
			if (player.isSneaking()) crane.toggleMode(player);
			else player.openHandledScreen(crane);
		}
		return ActionResult.CONSUME;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof CraneBlockEntity crane) crane.dropCarried();
		ItemScatterer.onStateReplaced(state, newState, world, pos);
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
