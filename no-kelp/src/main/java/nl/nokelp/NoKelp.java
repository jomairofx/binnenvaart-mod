package nl.nokelp;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.OceanPlacedFeatures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Haalt kelp uit het spel.
 * 1) Nieuwe wereld: de kelp-features worden uit alle biomen gehaald, dus er groeit nergens meer kelp.
 * 2) Bestaande wereld: als een chunk laadt, wordt kelp die er al stond vervangen door water (zonder dat er iets valt).
 * 3) Losse kelp-items die in het water drijven worden opgeruimd (die kosten fps en hopen zich op rond schepen).
 */
public class NoKelp implements ModInitializer {
	public static final String MOD_ID = "nokelp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Hoeveel blokken per tick hooguit worden omgezet, zodat het spel nooit hapert. */
	private static final int PER_TICK = 4096;

	private static final Map<ServerWorld, ArrayDeque<LongArrayList>> QUEUE = new WeakHashMap<>();

	@Override
	public void onInitialize() {
		BiomeModifications.create(Identifier.of(MOD_ID, "remove_kelp"))
				.add(ModificationPhase.REMOVALS, BiomeSelectors.all(), ctx -> {
					ctx.getGenerationSettings().removeFeature(OceanPlacedFeatures.KELP_COLD);
					ctx.getGenerationSettings().removeFeature(OceanPlacedFeatures.KELP_WARM);
				});
		ServerChunkEvents.CHUNK_LOAD.register(NoKelp::scanChunk);
		ServerTickEvents.END_SERVER_TICK.register(NoKelp::process);
		nl.nokelp.SelfTest.register();
		LOGGER.info("No Kelp loaded: kelp is removed from world generation and from loaded chunks");
	}

	public static boolean isKelp(BlockState s) {
		return s.isOf(Blocks.KELP) || s.isOf(Blocks.KELP_PLANT);
	}

	/** Zoekt kelp in een net geladen chunk (snel: alleen secties die kelp bevatten worden doorzocht). */
	public static void scanChunk(ServerWorld world, WorldChunk chunk) {
		ChunkSection[] sections = chunk.getSectionArray();
		LongArrayList found = null;
		BlockPos.Mutable m = new BlockPos.Mutable();
		int baseX = chunk.getPos().getStartX(), baseZ = chunk.getPos().getStartZ();
		for (int i = 0; i < sections.length; i++) {
			ChunkSection sec = sections[i];
			if (sec == null || sec.isEmpty() || !sec.hasAny(NoKelp::isKelp)) continue;
			int baseY = chunk.sectionIndexToCoord(i) << 4;
			for (int y = 0; y < 16; y++)
				for (int z = 0; z < 16; z++)
					for (int x = 0; x < 16; x++) {
						if (!isKelp(sec.getBlockState(x, y, z))) continue;
						if (found == null) found = new LongArrayList();
						found.add(m.set(baseX + x, baseY + y, baseZ + z).asLong());
					}
		}
		if (found != null) QUEUE.computeIfAbsent(world, w -> new ArrayDeque<>()).add(found);
	}

	private static int tickCount;

	/** Ruimt drijvende kelp-items op. Kelp in inventarissen, kisten of op het droge blijft gewoon bestaan. */
	public static int clearFloatingKelp(ServerWorld world) {
		java.util.List<ItemEntity> remove = new java.util.ArrayList<>();
		for (Entity e : world.iterateEntities()) {
			if (e instanceof ItemEntity it && it.getStack().isOf(Items.KELP) && (it.isTouchingWater() || it.isSubmergedInWater()))
				remove.add(it);
		}
		for (ItemEntity it : remove) it.discard();
		return remove.size();
	}

	private static void process(MinecraftServer server) {
		if (++tickCount % 20 == 0) for (ServerWorld world : server.getWorlds()) clearFloatingKelp(world);
		for (ServerWorld world : server.getWorlds()) {
			ArrayDeque<LongArrayList> q = QUEUE.get(world);
			if (q == null || q.isEmpty()) continue;
			int budget = PER_TICK;
			BlockPos.Mutable m = new BlockPos.Mutable();
			while (budget > 0 && !q.isEmpty()) {
				LongArrayList list = q.poll();
				for (int i = 0; i < list.size(); i++) {
					m.set(list.getLong(i));
					if (!world.isChunkLoaded(m.getX() >> 4, m.getZ() >> 4)) continue;
					if (isKelp(world.getBlockState(m)))
						world.setBlockState(m, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS);
					budget--;
				}
			}
		}
	}

	/** Voor de test: alles wat in de wachtrij staat meteen verwerken. */
	static void flush(MinecraftServer server) {
		for (int k = 0; k < 1000; k++) process(server);
	}
}
