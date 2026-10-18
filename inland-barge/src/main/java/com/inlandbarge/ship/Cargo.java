package com.inlandbarge.ship;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.HashMap;
import java.util.Map;

/** Bepaalt hoe vol een laadruim is en hoe de lading eruitziet (stortgoed of containers). */
public final class Cargo {
	private Cargo() {}

	private static final Map<Item, Block> BULK = new HashMap<>();

	static {
		BULK.put(Items.COAL, Blocks.COAL_BLOCK);
		BULK.put(Items.CHARCOAL, Blocks.COAL_BLOCK);
		BULK.put(Items.RAW_IRON, Blocks.RAW_IRON_BLOCK);
		BULK.put(Items.RAW_COPPER, Blocks.RAW_COPPER_BLOCK);
		BULK.put(Items.RAW_GOLD, Blocks.RAW_GOLD_BLOCK);
		BULK.put(Items.IRON_INGOT, Blocks.IRON_BLOCK);
		BULK.put(Items.GOLD_INGOT, Blocks.GOLD_BLOCK);
		BULK.put(Items.COPPER_INGOT, Blocks.COPPER_BLOCK);
		BULK.put(Items.WHEAT, Blocks.HAY_BLOCK);
		BULK.put(Items.WHEAT_SEEDS, Blocks.HAY_BLOCK);
		BULK.put(Items.REDSTONE, Blocks.REDSTONE_BLOCK);
		BULK.put(Items.LAPIS_LAZULI, Blocks.LAPIS_BLOCK);
		BULK.put(Items.DIAMOND, Blocks.DIAMOND_BLOCK);
		BULK.put(Items.EMERALD, Blocks.EMERALD_BLOCK);
		BULK.put(Items.BONE_MEAL, Blocks.BONE_BLOCK);
		BULK.put(Items.BONE, Blocks.BONE_BLOCK);
		BULK.put(Items.SNOWBALL, Blocks.SNOW_BLOCK);
		BULK.put(Items.CLAY_BALL, Blocks.CLAY);
		BULK.put(Items.QUARTZ, Blocks.QUARTZ_BLOCK);
		BULK.put(Items.FLINT, Blocks.GRAVEL);
		BULK.put(Items.MELON_SLICE, Blocks.MELON);
		BULK.put(Items.DRIED_KELP, Blocks.DRIED_KELP_BLOCK);
		BULK.put(Items.GLOWSTONE_DUST, Blocks.GLOWSTONE);
		BULK.put(Items.AMETHYST_SHARD, Blocks.AMETHYST_BLOCK);
		BULK.put(Items.SUGAR, Blocks.WHITE_CONCRETE_POWDER);
		BULK.put(Items.GUNPOWDER, Blocks.GRAY_CONCRETE_POWDER);
		BULK.put(Items.POTATO, Blocks.BROWN_TERRACOTTA);
		BULK.put(Items.CARROT, Blocks.ORANGE_TERRACOTTA);
		BULK.put(Items.BEETROOT, Blocks.RED_TERRACOTTA);
		BULK.put(Items.NETHER_WART, Blocks.NETHER_WART_BLOCK);
		BULK.put(Items.SLIME_BALL, Blocks.SLIME_BLOCK);
	}

	/** Blok waarmee stortgoed getekend wordt, of null als het stukgoed (containers) is. */
	public static Block bulkBlock(Item item) {
		Block b = BULK.get(item);
		if (b != null) return b;
		if (item instanceof BlockItem bi) {
			Block block = bi.getBlock();
			BlockState st = block.getDefaultState();
			if (block instanceof BlockEntityProvider) return null;
			if (st.getRenderType() != BlockRenderType.MODEL) return null;
			if (!st.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) return null;
			return block;
		}
		return null;
	}

	/** Vullingsgraad 0..1: aantal stapels-equivalent gedeeld door het aantal vakken. */
	public static float fill(Inventory inv) {
		double sum = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (!s.isEmpty()) sum += (double) s.getCount() / Math.max(1, s.getMaxCount());
		}
		return (float) Math.min(1.0, sum / inv.size());
	}

	/** 0 = leeg, -1 = containers, anders blok-id + 1 van het stortgoed. */
	public static int material(Inventory inv) {
		Map<Item, Double> amount = new HashMap<>();
		double total = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (s.isEmpty()) continue;
			double a = (double) s.getCount() / Math.max(1, s.getMaxCount());
			amount.merge(s.getItem(), a, Double::sum);
			total += a;
		}
		if (total <= 0) return 0;
		Item best = null;
		double bestA = -1;
		for (Map.Entry<Item, Double> e : amount.entrySet()) {
			if (e.getValue() > bestA) {
				bestA = e.getValue();
				best = e.getKey();
			}
		}
		Block b = best == null ? null : bulkBlock(best);
		if (b == null || bestA < total * 0.5) return -1;
		return net.minecraft.registry.Registries.BLOCK.getRawId(b) + 1;
	}
}
