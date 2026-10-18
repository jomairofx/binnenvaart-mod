package nl.kempenaar.ship;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import nl.kempenaar.Kempenaar;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Vaste vorm van het schip, geladen uit /kempenaar/johanna.json.
 * Coördinaten zijn "sjablooncoördinaten": x = lengte (0 = achtersteven), z = breedte, y = hoogte.
 */
public final class ShipTemplate {
	public static final int MIN_X = -3, MIN_Y = -6, MIN_Z = -3;
	public static final int SIZE_X = 76, SIZE_Y = 22, SIZE_Z = 14;

	/** Draaipunt (midden van het schip) in sjablooncoördinaten. */
	public static final double CX = 35.0, CZ = 4.5;

	public final String name;
	public final int length;
	public final BlockState[] states = new BlockState[SIZE_X * SIZE_Y * SIZE_Z];
	public final int[] hullZ0, hullZ1, hullBottom;
	public final int[][] holds;
	public final int holdZ0, holdZ1;
	public final double holdFloorY, coamingTopY;
	public final double[] seatAft, seatBow;
	public final int[] helmAft, helmBow;

	private static ShipTemplate instance;

	public static ShipTemplate get() {
		if (instance == null) instance = load();
		return instance;
	}

	public static int index(int x, int y, int z) {
		int ix = x - MIN_X, iy = y - MIN_Y, iz = z - MIN_Z;
		if (ix < 0 || iy < 0 || iz < 0 || ix >= SIZE_X || iy >= SIZE_Y || iz >= SIZE_Z) return -1;
		return (iy * SIZE_Z + iz) * SIZE_X + ix;
	}

	public BlockState getState(int x, int y, int z) {
		int i = index(x, y, z);
		if (i < 0) return Blocks.AIR.getDefaultState();
		BlockState s = states[i];
		return s == null ? Blocks.AIR.getDefaultState() : s;
	}

	/** Ligt (tx, tz) binnen de omtrek van de romp? */
	public boolean insideOutline(double tx, double tz, double margin) {
		if (tx < -margin || tx >= length + margin) return false;
		int xi = Math.max(0, Math.min(length - 1, (int) Math.floor(tx)));
		return tz >= hullZ0[xi] - margin && tz < hullZ1[xi] + 1 + margin;
	}

	private ShipTemplate(JsonObject json) {
		this.name = json.get("name").getAsString();
		this.length = json.get("length").getAsInt();
		JsonArray pal = json.getAsJsonArray("palette");
		BlockState[] palette = new BlockState[pal.size()];
		for (int i = 0; i < pal.size(); i++) palette[i] = parse(pal.get(i).getAsString());
		JsonArray blocks = json.getAsJsonArray("blocks");
		for (int i = 0; i + 3 < blocks.size(); i += 4) {
			int x = blocks.get(i).getAsInt(), y = blocks.get(i + 1).getAsInt(), z = blocks.get(i + 2).getAsInt();
			int idx = index(x, y, z);
			if (idx >= 0) states[idx] = palette[blocks.get(i + 3).getAsInt()];
		}
		JsonArray hull = json.getAsJsonArray("hull");
		hullZ0 = new int[length];
		hullZ1 = new int[length];
		hullBottom = new int[length];
		for (int i = 0; i < hull.size() && i < length; i++) {
			JsonArray h = hull.get(i).getAsJsonArray();
			hullZ0[i] = h.get(1).getAsInt();
			hullZ1[i] = h.get(2).getAsInt();
			hullBottom[i] = h.get(3).getAsInt();
		}
		JsonArray hs = json.getAsJsonArray("holds");
		holds = new int[hs.size()][2];
		for (int i = 0; i < hs.size(); i++) {
			holds[i][0] = hs.get(i).getAsJsonArray().get(0).getAsInt();
			holds[i][1] = hs.get(i).getAsJsonArray().get(1).getAsInt();
		}
		holdZ0 = json.getAsJsonArray("holdZ").get(0).getAsInt();
		holdZ1 = json.getAsJsonArray("holdZ").get(1).getAsInt();
		holdFloorY = json.get("holdFloorY").getAsDouble();
		coamingTopY = json.get("coamingTopY").getAsDouble();
		JsonObject seats = json.getAsJsonObject("seats");
		seatAft = vec(seats.getAsJsonObject("aft").getAsJsonArray("pos"));
		seatBow = vec(seats.getAsJsonObject("bow").getAsJsonArray("pos"));
		JsonObject zones = json.getAsJsonObject("helmZones");
		helmAft = ints(zones.getAsJsonArray("aft"));
		helmBow = ints(zones.getAsJsonArray("bow"));
	}

	private static double[] vec(JsonArray a) {
		return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
	}

	private static int[] ints(JsonArray a) {
		int[] r = new int[a.size()];
		for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt();
		return r;
	}

	private static BlockState parse(String s) {
		try {
			return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), s, false).blockState();
		} catch (Exception e) {
			Kempenaar.LOGGER.warn("Onbekend blok in scheepssjabloon: {}", s);
			return Blocks.AIR.getDefaultState();
		}
	}

	private static ShipTemplate load() {
		try (InputStream in = ShipTemplate.class.getResourceAsStream("/kempenaar/johanna.json")) {
			if (in == null) throw new IllegalStateException("hendrika.json ontbreekt");
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return new ShipTemplate(json);
		} catch (Exception e) {
			throw new RuntimeException("Kon scheepssjabloon niet laden", e);
		}
	}
}
