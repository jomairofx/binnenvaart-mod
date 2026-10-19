package com.modernbulk.ship;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/** De blokken van één schip (sjabloon + wijzigingen zoals open deuren), als BlockView in sjablooncoördinaten. */
public class ShipStructure implements BlockView {
	public final ShipTemplate template;
	private final BlockState[] states;
	/** Wijzigingen ten opzichte van het sjabloon (voor opslag en synchronisatie). */
	public final Long2ObjectMap<BlockState> changes = new Long2ObjectOpenHashMap<>();
	public int version = 0;

	public ShipStructure(ShipTemplate template) {
		this.template = template;
		this.states = template.states.clone();
	}

	public BlockState get(int x, int y, int z) {
		int i = ShipTemplate.index(x, y, z);
		if (i < 0) return Blocks.AIR.getDefaultState();
		BlockState s = states[i];
		return s == null ? Blocks.AIR.getDefaultState() : s;
	}

	public void set(int x, int y, int z, BlockState state) {
		int i = ShipTemplate.index(x, y, z);
		if (i < 0) return;
		states[i] = state;
		long key = BlockPos.asLong(x, y, z);
		if (state == template.getState(x, y, z)) changes.remove(key);
		else changes.put(key, state);
		version++;
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return get(pos.getX(), pos.getY(), pos.getZ());
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return Fluids.EMPTY.getDefaultState();
	}

	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	@Override
	public int getHeight() {
		return ShipTemplate.SIZE_Y;
	}

	@Override
	public int getBottomY() {
		return ShipTemplate.MIN_Y;
	}
}
