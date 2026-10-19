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
		BULK.put(Items.WHEAT, Blocks.HAY_BLOCK);
		BULK.put(Items.WHEAT_SEEDS, Blocks.HAY_BLOCK);
		BULK.put(Items.SNOWBALL, Blocks.SNOW_BLOCK);
		BULK.put(Items.CLAY_BALL, Blocks.CLAY);
		BULK.put(Items.FLINT, Blocks.GRAVEL);
	}

	/** Kostbare of verpakte blokken gaan in containers, niet los in het ruim. */
	private static final java.util.Set<Block> VALUABLE = java.util.Set.of(
			Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.IRON_BLOCK,
			Blocks.COPPER_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.QUARTZ_BLOCK,
			Blocks.ANCIENT_DEBRIS, Blocks.TNT, Blocks.BEACON, Blocks.GLASS, Blocks.HAY_BLOCK, Blocks.MELON, Blocks.PUMPKIN,
			Blocks.BOOKSHELF, Blocks.CRAFTING_TABLE, Blocks.HONEY_BLOCK, Blocks.SLIME_BLOCK, Blocks.SPONGE, Blocks.WET_SPONGE);

	/** Blok waarmee stortgoed getekend wordt, of null als het stukgoed (containers) is. */
	public static Block bulkBlock(Item item) {
		Block b = BULK.get(item);
		if (b != null) return b;
		if (item instanceof BlockItem bi) {
			Block block = bi.getBlock();
			BlockState st = block.getDefaultState();
			if (block instanceof BlockEntityProvider) return null;
			if (VALUABLE.contains(block)) return null;
			if (st.getRenderType() != BlockRenderType.MODEL) return null;
			try {
				if (!st.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) return null;
			} catch (RuntimeException e) {
				return null;   // blokken uit andere mods die een echte wereld nodig hebben
			}
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

	/** 0 = leeg, -1 = alleen containers, anders blok-id + 1 van het meest geladen stortgoed. */
	public static int material(Inventory inv) {
		Map<Block, Double> bulk = new HashMap<>();
		double total = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (s.isEmpty()) continue;
			double a = (double) s.getCount() / Math.max(1, s.getMaxCount());
			total += a;
			Block b = bulkBlock(s.getItem());
			if (b != null) bulk.merge(b, a, Double::sum);
		}
		if (total <= 0) return 0;
		Block best = null;
		double bestA = 0;
		for (Map.Entry<Block, Double> e : bulk.entrySet()) {
			if (e.getValue() > bestA) {
				bestA = e.getValue();
				best = e.getKey();
			}
		}
		if (best == null) return -1;
		return net.minecraft.registry.Registries.BLOCK.getRawId(best) + 1;
	}

	/** Welk deel van de lading stukgoed is (in containers), 0..1. */
	public static float containerShare(Inventory inv) {
		double total = 0, boxed = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (s.isEmpty()) continue;
			double a = (double) s.getCount() / Math.max(1, s.getMaxCount());
			total += a;
			if (bulkBlock(s.getItem()) == null) boxed += a;
		}
		return total <= 0 ? 0f : (float) (boxed / total);
	}
}
