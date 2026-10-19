"""Mixed cargo: bulk goods are dumped loose, everything else (food, weapons, tools, valuables...) goes in sea containers."""
import sys, re
root, pkg = sys.argv[1:3]
P = pkg.replace(".", "/")

# ---------------- Cargo.java ----------------
f = f"{root}/main/java/{P}/ship/Cargo.java"
s = open(f).read()
# valuables and food are not bulk any more
for item in ["IRON_INGOT", "GOLD_INGOT", "COPPER_INGOT", "DIAMOND", "EMERALD", "LAPIS_LAZULI", "QUARTZ", "AMETHYST_SHARD",
             "MELON_SLICE", "POTATO", "CARROT", "BEETROOT", "SLIME_BALL", "GUNPOWDER", "SUGAR", "NETHER_WART", "GLOWSTONE_DUST",
             "REDSTONE", "BONE", "BONE_MEAL", "DRIED_KELP"]:
    s2 = re.sub(r"\t\tBULK\.put\(Items\." + item + r", [^;]+;\n", "", s)
    s = s2
a = "			if (block instanceof BlockEntityProvider) return null;\n"
assert a in s
s = s.replace(a, a + "			if (VALUABLE.contains(block)) return null;\n")
a = "	/** Blok waarmee stortgoed getekend wordt"
s = s.replace(a, """	/** Kostbare of verpakte blokken gaan in containers, niet los in het ruim. */
	private static final java.util.Set<Block> VALUABLE = java.util.Set.of(
			Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.IRON_BLOCK,
			Blocks.COPPER_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.QUARTZ_BLOCK,
			Blocks.ANCIENT_DEBRIS, Blocks.TNT, Blocks.BEACON, Blocks.GLASS, Blocks.HAY_BLOCK, Blocks.MELON, Blocks.PUMPKIN,
			Blocks.BOOKSHELF, Blocks.CRAFTING_TABLE, Blocks.HONEY_BLOCK, Blocks.SLIME_BLOCK, Blocks.SPONGE, Blocks.WET_SPONGE);

""" + a, 1)
s = s.replace("			if (!st.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) return null;",
              """			try {
				if (!st.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) return null;
			} catch (RuntimeException e) {
				return null;   // blokken uit andere mods die een echte wereld nodig hebben
			}""")
i = s.index("	/** 0 = leeg, -1 = containers")
s = s[:i] + """	/** 0 = leeg, -1 = alleen containers, anders blok-id + 1 van het meest geladen stortgoed. */
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
"""
open(f, "w").write(s)

# ---------------- ShipEntity.java ----------------
f = f"{root}/main/java/{P}/ship/ShipEntity.java"
s = open(f).read()
m = re.search(r"	private static final TrackedData<Integer>\[\] MATERIAL = new TrackedData\[(\w+)\];\n", s)
n = m.group(1)
s = s.replace(m.group(0), m.group(0) + f"	@SuppressWarnings(\"unchecked\")\n	private static final TrackedData<Float>[] BOXED = new TrackedData[{n}];\n")
a = "			MATERIAL[i] = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.INTEGER);\n"
assert s.count(a) == 1
s = s.replace(a, a + "			BOXED[i] = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);\n")
a = "			builder.add(MATERIAL[i], 0);\n"
assert s.count(a) == 1
s = s.replace(a, a + "			builder.add(BOXED[i], 0f);\n")
a = "	public int material(int hold) {"
s = s.replace(a, """	/** Deel van de lading in dit ruim dat in containers zit (0..1). */
	public float containerShare(int hold) {
		return dataTracker.get(BOXED[hold]);
	}

""" + a)
a = "		dataTracker.set(MATERIAL[hold], Cargo.material(holds[hold]));\n"
assert s.count(a) == 1
s = s.replace(a, a + "		dataTracker.set(BOXED[hold], Cargo.containerShare(holds[hold]));\n")
a = "			dataTracker.set(MATERIAL[i], Cargo.material(holds[i]));\n"
assert s.count(a) == 1
s = s.replace(a, a + "			dataTracker.set(BOXED[i], Cargo.containerShare(holds[i]));\n")
open(f, "w").write(s)

# ---------------- ShipRenderer.java ----------------
f = f"{root}/client/java/{P}/client/ShipRenderer.java"
s = open(f).read()
i = s.index("	private void renderCargo(")
j = s.index("	private static float pileHeight(")
s = s[:i] + """	private void renderCargo(ShipEntity ship, float[] fills, float hatch, Sink vc, int light) {
		ShipTemplate t = ship.template;
		for (int h = 0; h < t.holds.length; h++) {
			float f = fills[h];
			if (f < 0.002f) continue;
			int mat = ship.material(h);
			int a = t.holds[h][0], b = t.holds[h][1];
			float floor = (float) t.holdFloorY;
			int len = b - a + 1;
			int cols = Math.max(1, (int) ((len - 0.4f) / 6.2f));
			int rows = Math.max(1, (int) ((t.holdZ1 - t.holdZ0 + 1 - 0.2f) / 2.5f));
			int tiers = Math.max(1, (int) ((t.coamingTopY - floor - 0.3f) / 2.62f));
			float share = mat > 0 ? ship.containerShare(h) : 1f;
			if (mat > 0 && (share < 0.02f || cols < 2)) share = share >= 0.5f && cols < 2 ? 1f : 0f;
			if (share >= 0.999f || mat <= 0) {
				renderContainers(vc, h, a, cols, rows, tiers, t.holdZ0, t.holdZ1, floor, f, len, light);
			} else if (share <= 0.001f) {
				BlockState st = Registries.BLOCK.get(mat - 1).getDefaultState();
				renderPile(vc, sprites(st), a, b, t.holdZ0, t.holdZ1, floor, (float) t.coamingTopY, f, hatch, light);
			} else {
				// gemengd: containers achterin het ruim, stortgoed in de rest
				int cc = MathHelper.clamp(Math.round(cols * share), 1, cols - 1);
				float fc = MathHelper.clamp(f * share * cols / cc, 0.02f, 1f);
				renderContainers(vc, h, a, cc, rows, tiers, t.holdZ0, t.holdZ1, floor, fc, cc * 6.2f + 0.4f, light);
				int a2 = Math.min(b - 2, a + (int) Math.ceil(cc * 6.2f + 0.4f));
				float fp = MathHelper.clamp(f * (1 - share) * len / (b - a2 + 1), 0.02f, 1f);
				BlockState st = Registries.BLOCK.get(mat - 1).getDefaultState();
				renderPile(vc, sprites(st), a2, b, t.holdZ0, t.holdZ1, floor, (float) t.coamingTopY, fp, hatch, light);
			}
		}
	}

""" + s[j:]
i = s.index("	private void renderContainers(")
j = s.index("	// ---------------------------------------------------------------- luiken")
s = s[:i] + """	/** Zeecontainers (20 ft) netjes in rijen en lagen; ze zakken van boven in het ruim als ze geladen worden. */
	private void renderContainers(Sink vc, int hold, int a, int cols, int rows, int tiers, int z0, int z1, float floor,
								  float f, float span, int light) {
		int per = cols * rows, cap = per * tiers;
		float slots = f * cap;
		float xs = a + (span - cols * 6.2f) / 2f + 0.07f;
		float zs = z0 + ((z1 - z0 + 1) - rows * 2.5f) / 2f + 0.03f;
		Sprite dark = sprites(Blocks.GRAY_CONCRETE.getDefaultState())[0];
		for (int k = 0; k < cap; k++) {
			float p = MathHelper.clamp(slots - k, 0f, 1f);
			if (p <= 0) break;
			int tier = k / per, idx = k % per, col = idx / rows, row = idx % rows;
			float x0 = xs + col * 6.2f, zz = zs + row * 2.5f;
			float drop = (1 - p) * (1 - p) * 7f;
			float y0 = floor + tier * 2.62f + drop;
			Block color = CONTAINER_COLORS[(hold * 7 + k * 5 + (k / 3)) % CONTAINER_COLORS.length];
			Sprite s = sprites(color.getDefaultState())[0];
			box(vc, s, x0, y0 + 0.08f, zz, x0 + 6.05f, y0 + 2.51f, zz + 2.44f, light, 1f);
			// ribbels in de zijwanden
			for (float rx = x0 + 0.45f; rx < x0 + 5.7f; rx += 0.7f) {
				box(vc, s, rx, y0 + 0.2f, zz - 0.04f, rx + 0.3f, y0 + 2.4f, zz, light, 0.82f);
				box(vc, s, rx, y0 + 0.2f, zz + 2.44f, rx + 0.3f, y0 + 2.4f, zz + 2.48f, light, 0.82f);
			}
			// hoekstijlen, onder- en bovenregels
			box(vc, dark, x0, y0, zz, x0 + 6.05f, y0 + 0.08f, zz + 2.44f, light, 0.7f);
			box(vc, s, x0, y0 + 2.51f, zz, x0 + 6.05f, y0 + 2.59f, zz + 2.44f, light, 0.75f);
			for (float cx : new float[]{x0, x0 + 5.9f})
				for (float cz : new float[]{zz, zz + 2.29f}) box(vc, dark, cx, y0, cz, cx + 0.15f, y0 + 2.59f, cz + 0.15f, light, 0.8f);
			// deuren aan de kopse kant met vergrendelstangen
			box(vc, s, x0 + 6.05f, y0 + 0.1f, zz + 0.1f, x0 + 6.1f, y0 + 2.49f, zz + 2.34f, light, 0.55f);
			for (float bz : new float[]{zz + 0.5f, zz + 0.9f, zz + 1.5f, zz + 1.9f})
				box(vc, dark, x0 + 6.1f, y0 + 0.2f, bz, x0 + 6.14f, y0 + 2.4f, bz + 0.06f, light, 0.6f);
		}
	}

""" + s[j:]
open(f, "w").write(s)
print("ok", root)
