package nl.kempenaar.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Opslagbunker van de laadkraan (vier stuks rond de voet). Hier stort de kraan bij het lossen de lading in. */
public class CraneStorageBlock extends BlockWithEntity {
	public static final MapCodec<CraneStorageBlock> CODEC = createCodec(CraneStorageBlock::new);
	public static final IntProperty BIN = IntProperty.of("bin", 1, 4);

	public CraneStorageBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(BIN, 1));
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(BIN);
	}

	/** Groot: 1,8 x 1,8 blokken en 2 hoog (de bunker steekt aan alle kanten iets buiten het blok uit). */
	private static final net.minecraft.util.shape.VoxelShape SHAPE = net.minecraft.block.Block.createCuboidShape(-6.4, 0, -6.4, 22.4, 24, 22.4);

	@Override
	protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos, net.minecraft.block.ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new CraneStorageBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(World world, BlockState state, net.minecraft.block.entity.BlockEntityType<T> type) {
		return world.isClient ? null : validateTicker(type, nl.kempenaar.Kempenaar.CRANE_STORAGE_BE, CraneStorageBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient) return ActionResult.SUCCESS;
		if (world.getBlockEntity(pos) instanceof CraneStorageBlockEntity bin) player.openHandledScreen(bin);
		return ActionResult.CONSUME;
	}

	@Override
	protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		ItemScatterer.onStateReplaced(state, newState, world, pos);
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return net.minecraft.screen.ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
	}
}
