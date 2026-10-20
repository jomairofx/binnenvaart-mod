package nl.greatrivers;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
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

	@Override
	public void onInitialize() {
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
		for (RiverField.Kind kind : new RiverField.Kind[]{field.river, field.great}) {
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
		return 1;
	}
}
