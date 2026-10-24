package nl.greatrivers;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GreatRivers implements ModInitializer {
	public static final String MOD_ID = "greatrivers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final HarbourFeature HARBOUR = Registry.register(Registries.FEATURE, Identifier.of(MOD_ID, "harbour"), new HarbourFeature());

	public static final String[] NO_ICE = {"iceberg_packed", "iceberg_blue", "blue_ice", "ice_spike", "ice_patch", "freeze_top_layer"};

	@Override
	public void onInitialize() {
		// no ravines and cave mouths cut through the flat land (the caves deep underground stay)
		BiomeModifications.create(Identifier.of(MOD_ID, "no_carvers")).add(net.fabricmc.fabric.api.biome.v1.ModificationPhase.REMOVALS,
				BiomeSelectors.foundInOverworld(), ctx -> {
					ctx.getGenerationSettings().removeCarver(net.minecraft.world.gen.carver.ConfiguredCarvers.CAVE);
					ctx.getGenerationSettings().removeCarver(net.minecraft.world.gen.carver.ConfiguredCarvers.CAVE_EXTRA_UNDERGROUND);
					ctx.getGenerationSettings().removeCarver(net.minecraft.world.gen.carver.ConfiguredCarvers.CANYON);
					// no lava pools on the land (or anywhere else)
					ctx.getGenerationSettings().removeFeature(net.minecraft.world.gen.feature.MiscPlacedFeatures.LAKE_LAVA_SURFACE);
					ctx.getGenerationSettings().removeFeature(net.minecraft.world.gen.feature.MiscPlacedFeatures.LAKE_LAVA_UNDERGROUND);
					// no icebergs, blue ice, ice spikes or frozen water anywhere: this is the Netherlands, not the arctic
					for (String id : NO_ICE)
						ctx.getGenerationSettings().removeFeature(RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.ofVanilla(id)));
				});
		// far fewer animals: only cows and sheep in the meadows, and only now and then
		BiomeModifications.create(Identifier.of(MOD_ID, "fewer_animals")).add(net.fabricmc.fabric.api.biome.v1.ModificationPhase.REMOVALS,
				BiomeSelectors.foundInOverworld(), ctx -> {
					for (net.minecraft.entity.EntityType<?> t : new net.minecraft.entity.EntityType<?>[]{
							net.minecraft.entity.EntityType.PIG, net.minecraft.entity.EntityType.CHICKEN, net.minecraft.entity.EntityType.HORSE,
							net.minecraft.entity.EntityType.DONKEY, net.minecraft.entity.EntityType.RABBIT, net.minecraft.entity.EntityType.GOAT,
							net.minecraft.entity.EntityType.LLAMA, net.minecraft.entity.EntityType.FOX, net.minecraft.entity.EntityType.WOLF,
							net.minecraft.entity.EntityType.PANDA, net.minecraft.entity.EntityType.PARROT, net.minecraft.entity.EntityType.OCELOT,
							net.minecraft.entity.EntityType.POLAR_BEAR, net.minecraft.entity.EntityType.MOOSHROOM, net.minecraft.entity.EntityType.CAMEL,
							net.minecraft.entity.EntityType.ARMADILLO, net.minecraft.entity.EntityType.TURTLE, net.minecraft.entity.EntityType.FROG})
						ctx.getSpawnSettings().removeSpawnsOfEntityType(t);
					ctx.getSpawnSettings().setCreatureSpawnProbability(0.015f);
				});
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.TOP_LAYER_MODIFICATION,
				RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(MOD_ID, "harbour")));
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
				dispatcher.register(CommandManager.literal("findriver").executes(GreatRivers::findRiver)));
		SelfTest.register();
		LOGGER.info("Great Rivers loaded");
	}

	/** Nearest point in the middle of a river of this kind, searching outwards from (x, z). Null if none within reach. */
	public static int[] nearest(RiverField.Kind kind, int x, int z, int maxRadius) {
		int step = 32;
		for (int r = 0; r <= maxRadius; r += step) {
			int n = Math.max(1, (int) Math.round(2 * Math.PI * r / step));
			double best = Double.MAX_VALUE;
			int bx = 0, bz = 0;
			for (int i = 0; i < n; i++) {
				double a = 2 * Math.PI * i / n;
				int px = x + (int) Math.round(r * Math.cos(a)), pz = z + (int) Math.round(r * Math.sin(a));
				double d = kind.distance(px, pz) / kind.halfWidth(px, pz);
				if (d < 0.6 && d < best) {
					best = d;
					bx = px;
					bz = pz;
				}
			}
			if (best < Double.MAX_VALUE) return centre(kind, bx, bz);
		}
		return null;
	}

	/** Walks to the middle of the fairway. */
	public static int[] centre(RiverField.Kind kind, int x, int z) {
		for (int s = 16; s >= 1; s /= 2) {
			boolean moved = true;
			while (moved) {
				moved = false;
				double d = kind.distance(x, z);
				int[][] dirs = {{s, 0}, {-s, 0}, {0, s}, {0, -s}};
				for (int[] dd : dirs) {
					if (kind.distance(x + dd[0], z + dd[1]) < d - 1e-6) {
						x += dd[0];
						z += dd[1];
						moved = true;
						break;
					}
				}
			}
		}
		return new int[]{x, z};
	}

	private static int findRiver(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		RiverField field = RiverField.current;
		if (field == null) {
			src.sendError(Text.literal("This world has no Great Rivers terrain (only the normal overworld, in a world made with the mod)."));
			return 0;
		}
		Vec3d p = src.getPosition();
		int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
		for (RiverField.Kind kind : new RiverField.Kind[]{field.great}) {
			int[] c = nearest(kind, x, z, 24000);
			if (c == null) {
				src.sendFeedback(() -> Text.literal("No " + kind.name() + " found within 24000 blocks.").formatted(Formatting.GRAY), false);
				continue;
			}
			int dist = (int) Math.round(Math.hypot(c[0] - x, c[1] - z));
			int width = (int) Math.round(kind.halfWidth(c[0], c[1]) * 2);
			String tp = "/tp @s " + c[0] + " 70 " + c[1];
			Text coords = Text.literal("[" + c[0] + ", " + c[1] + "]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
					.styled(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
							.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to teleport"))));
			src.sendFeedback(() -> Text.literal("Nearest " + kind.name() + " (about " + width + " blocks wide): ").append(coords)
					.append(Text.literal(", " + dist + " blocks away")), false);
		}
		for (int kind = 2; kind >= 0; kind--) {
			Villages.Town t = findTown(field, x, z, 14, kind >= 1, kind == 2 ? Boolean.TRUE : kind == 1 ? Boolean.FALSE : null);
			if (t == null) continue;
			int dist = (int) Math.round(Math.hypot(t.cx() - x, t.cz() - z));
			String tp = "/tp @s " + t.cx() + " 80 " + t.cz();
			Text coords = Text.literal("[" + t.cx() + ", " + t.cz() + "]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
					.styled(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
							.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to teleport"))));
			src.sendFeedback(() -> Text.literal("Nearest " + t.kind() + ": ").append(coords).append(Text.literal(", " + dist + " blocks away")), false);
		}
		for (int c = 0; c <= 1; c++) {
			Villages.Town t = findDikeTown(field, x, z, 10, c == 1);
			if (t != null) report(src, "Nearest " + t.kind(), t.cx(), 80, t.cz(), x, z);
		}
		Harbours.Site port = field.harbours.nearestPort(x, z, 2);
		if (port != null) report(src, "Nearest big port (" + port.basins() + " basins: containers, bulk, tanks)",
				(int) Math.floor(port.wx(0, port.bank() + Harbours.P_BL + 20)), Harbours.QUAY_Y + 2, (int) Math.floor(port.wz(0, port.bank() + Harbours.P_BL + 20)), x, z);
		for (int size = 0; size <= 2; size++) {
		Harbours.Site h = findHarbour(field, x, z, 14, size);
		if (h == null) continue;
		{
			int[] q = quaySpot(h);
			int dist = (int) Math.round(Math.hypot(q[0] - x, q[1] - z));
			String tp = "/tp @s " + q[0] + " " + (Harbours.QUAY_Y + 1) + " " + q[1];
			Text coords = Text.literal("[" + q[0] + ", " + q[1] + "]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
					.styled(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
							.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to teleport"))));
			src.sendFeedback(() -> Text.literal("Nearest " + h.sizeName() + " harbour (" + h.goods().replace('_', ' ') + "): ").append(coords)
					.append(Text.literal(", " + dist + " blocks away")), false);
		}
		}
		Metros.Metro big = field.metros.nearestNormal(x, z, 3);
		if (big != null) report(src, "Nearest big city (" + (int) Math.round(big.radius() * 2) + " blocks across" + (big.river() ? ", on the river" : "") + ")",
				big.cx(), 95, big.cz(), x, z);
		Metros.Metro mega = field.metros.nearestBig(x, z, Metros.MEGA, 3);
		if (mega != null) report(src, "Nearest metropolis (" + Math.round(mega.radius() * 2 / 100) / 10.0 + " km across" + (mega.river() ? ", on the river" : "") + ")",
				mega.cx(), 130, mega.cz(), x, z);
		Metros.Metro low = field.metros.nearestBig(x, z, Metros.LOW, 3);
		if (low != null) report(src, "Nearest big low-rise town (" + Math.round(low.radius() * 2 / 100) / 10.0 + " km across)",
				low.cx(), 90, low.cz(), x, z);
		double[] cs = field.rails.findCentralStation(x, z);
		if (cs != null) report(src, "Nearest central station", (int) cs[0] + 10, (int) cs[2] + 2, (int) cs[1], x, z);
		int[] sea = findSea(field, x, z);
		if (sea != null) report(src, "Nearest open sea (big water)", sea[0], 64, sea[1], x, z);
		double[] br = field.infra.findBridge(x, z, 40000);
		if (br != null) report(src, "Nearest river bridge (motorway)", (int) br[0], Infra.DECK + 1, (int) br[1], x, z);
		double[] vd = field.infra.findViaduct(x, z, 3);
		if (vd != null) report(src, "Nearest viaduct", (int) vd[0], 80, (int) vd[1], x, z);
		Locks.Site lk = field.infra.locks.nearest(x, z, 12);
		if (lk != null) {
			double la = 0, lw = lk.hw() + Locks.LAND - 4;
			report(src, "Nearest lock and weir" + (lk.hasRoad() ? " (road to a " + lk.town() + ")" : ""),
					(int) Math.floor(lk.wx(la, lw)), Locks.WALL_TOP + 1, (int) Math.floor(lk.wz(la, lw)), x, z);
		}
		double[] ss = Pylons.findSubstation(field, src.getWorld().getSeed(), x, z);
		if (ss != null) report(src, "Nearest high-voltage substation", (int) ss[0], (int) ss[2] + 1, (int) ss[1], x, z);
		return 1;
	}

	/** Nearest fairway buoy {x, z, colour} (for the tests). */
	public static int[] buoy(RiverField f, int x, int z) {
		return Buoys.nearest(f, x, z, 10);
	}

	/** Places of country houses (for the client test). */
	public static java.util.List<int[]> farmSpots(RiverField f, long seed, int x, int z, int n) {
		return Countryside.farmSpots(f, seed, x, z, n);
	}

	/** Nearest high-voltage substation {x, z, ground, fam, dir} (for the tests). */
	public static double[] substation(RiverField f, long seed, int x, int z) {
		return Pylons.findSubstation(f, seed, x, z);
	}

	public static double[] cityPylon(RiverField f, Metros.Metro mt) {
		return MetroBuilder.findPylon(f, mt);
	}

	/** Middle of a bridge over the river in a big city (for the tests). */
	public static double[] cityBridge(RiverField f, Metros.Metro mt) {
		return MetroBuilder.findBridge(f, mt);
	}

	/** A point out on the open sea, at least 600 blocks from any land, nearest to x, z. */
	public static int[] findSea(RiverField field, int x, int z) {
		for (int r = 0; r <= 40000; r += 250) {
			int n = Math.max(1, (int) Math.round(2 * Math.PI * r / 250));
			for (int i = 0; i < n; i++) {
				double a = 2 * Math.PI * i / n;
				int px = x + (int) Math.round(r * Math.cos(a)), pz = z + (int) Math.round(r * Math.sin(a));
				if (field.coastal(px, pz) > Lowlands.COAST - 0.12) continue;
				boolean open = true;
				for (int k = 0; k < 8 && open; k++) {
					double b = k * Math.PI / 4;
					open = field.coastal(px + (int) (600 * Math.cos(b)), pz + (int) (600 * Math.sin(b))) < Lowlands.COAST;
				}
				if (open) return new int[]{px, pz};
			}
		}
		return null;
	}

	private static void report(ServerCommandSource src, String what, int tx, int ty, int tz, int x, int z) {
		int dist = (int) Math.round(Math.hypot(tx - x, tz - z));
		String tp = "/tp @s " + tx + " " + ty + " " + tz;
		Text coords = Text.literal("[" + tx + ", " + tz + "]").formatted(Formatting.AQUA, Formatting.UNDERLINE)
				.styled(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to teleport"))));
		src.sendFeedback(() -> Text.literal(what + ": ").append(coords).append(Text.literal(", " + dist + " blocks away")), false);
	}

	/** Middle of the quay behind the basin. */
	public static int[] quaySpot(Harbours.Site h) {
		double a = h.size() == Harbours.LARGE ? h.A() + 9 : 0, v = h.bank() + (h.size() == Harbours.LARGE ? h.B() * 0.5 : h.B() + 9);
		return new int[]{(int) Math.floor(h.wx(a, v)), (int) Math.floor(h.wz(a, v))};
	}

	/** Nearest harbour of a given size (or any size with -1). */
	public static Harbours.Site findHarbour(RiverField field, int x, int z, int cells, int size) {
		int cx = Math.floorDiv(x, Harbours.CELL), cz = Math.floorDiv(z, Harbours.CELL);
		Harbours.Site best = null;
		double bd = Double.MAX_VALUE;
		for (int r = 0; r <= cells; r++) {
			for (int i = -r; i <= r; i++)
				for (int j = -r; j <= r; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
					Harbours.Site s = field.harbours.site(cx + i, cz + j);
					if (s == null || (size >= 0 && s.size() != size)) continue;
					double d = Math.hypot(s.cx() - x, s.cz() - z);
					if (d < bd) {
						bd = d;
						best = s;
					}
				}
			if (best != null && bd < r * Harbours.CELL) return best;
		}
		return best;
	}

	/** Nearest town (city true) or village. */
	public static Villages.Town findTown(RiverField field, int x, int z, int cells, boolean city) {
		return findTown(field, x, z, cells, city, null);
	}

	/** Nearest town of a kind; river null = with or without a river through it. */
	public static Villages.Town findTown(RiverField field, int x, int z, int cells, boolean city, Boolean river) {
		int cx = Math.floorDiv(x, Villages.CELL), cz = Math.floorDiv(z, Villages.CELL);
		Villages.Town best = null;
		double bd = Double.MAX_VALUE;
		for (int r = 0; r <= cells; r++) {
			for (int i = -r; i <= r; i++)
				for (int j = -r; j <= r; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
					Villages.Town t = field.villages.town(cx + i, cz + j);
					if (t == null || t.dike() || t.city() != city || (river != null && t.river() != river)) continue;
					double d = Math.hypot(t.cx() - x, t.cz() - z);
					if (d < bd) {
						bd = d;
						best = t;
					}
				}
			if (best != null && bd < r * Villages.CELL) return best;
		}
		return best;
	}

	/** Nearest village or town right behind the river dike. */
	public static Villages.Town findDikeTown(RiverField field, int x, int z, int cells, Boolean city) {
		int cx = Math.floorDiv(x, Villages.CELL), cz = Math.floorDiv(z, Villages.CELL);
		Villages.Town best = null;
		double bd = Double.MAX_VALUE;
		for (int i = -cells; i <= cells; i++)
			for (int j = -cells; j <= cells; j++) {
				Villages.Town t = field.villages.town(cx + i, cz + j);
				if (t == null || !t.dike() || (city != null && t.city() != city)) continue;
				double d = Math.hypot(t.cx() - x, t.cz() - z);
				if (d < bd) {
					bd = d;
					best = t;
				}
			}
		return best;
	}

	public static Harbours.Site nearestHarbour(RiverField field, int x, int z, int cells) {
		int cx = Math.floorDiv(x, Harbours.CELL), cz = Math.floorDiv(z, Harbours.CELL);
		Harbours.Site best = null;
		double bd = Double.MAX_VALUE;
		for (int r = 0; r <= cells; r++) {
			for (int i = -r; i <= r; i++)
				for (int j = -r; j <= r; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
					Harbours.Site s = field.harbours.site(cx + i, cz + j);
					if (s == null) continue;
					double d = Math.hypot(s.cx() - x, s.cz() - z);
					if (d < bd) {
						bd = d;
						best = s;
					}
				}
			if (best != null && bd < (r) * Harbours.CELL) return best;
		}
		return best;
	}
}
