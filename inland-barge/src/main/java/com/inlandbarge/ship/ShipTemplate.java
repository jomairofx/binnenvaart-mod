package com.inlandbarge.ship;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import com.inlandbarge.InlandBarge;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Vaste vorm van het schip, geladen uit /inlandbarge/stella_maris.json.
 * Coördinaten zijn "sjablooncoördinaten": x = lengte (0 = achtersteven), z = breedte, y = hoogte.
 */
public final class ShipTemplate {
	public static final int MIN_X = -2, MIN_Y = -6, MIN_Z = -2;
	public static final int SIZE_X = 116, SIZE_Y = 24, SIZE_Z = 16;

	/** Draaipunt (midden van het schip) in sjablooncoördinaten. */
	public static final double CX = 48.0, CZ = 5.5;

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
		readExtras(json);
	}

	/** Hefbare stuurhut: {x0,y0,z0,x1,y1,z1} (sjablooncoördinaten) of null als het schip er geen heeft. */
	public int[] liftRegion;
	public double liftMax;
	/** Hydraulische kolom onder de stuurhut: {x0,z0,x1,z1}. */
	public int[] liftColumn;
	/** Posities (BlockPos.asLong) van alle lampen, voor het licht aan/uit. */
	public long[] lightPositions = new long[0];

	/** Kolommen (x, z) binnen het gebied die niet meebewegen, bijvoorbeeld een ladder door de vloer. */
	public int[][] liftExclude = new int[0][];

	public boolean inLift(int x, int y, int z) {
		int[] r = liftRegion;
		if (r == null || x < r[0] || x > r[3] || y < r[1] || y > r[4] || z < r[2] || z > r[5]) return false;
		for (int[] e : liftExclude) if (e[0] == x && e[1] == z) return false;
		return true;
	}

	public boolean inLiftXZ(double x, double z) {
		int[] r = liftRegion;
		if (r == null || x < r[0] || x >= r[3] + 1 || z < r[2] || z >= r[5] + 1) return false;
		int fx = (int) Math.floor(x), fz = (int) Math.floor(z);
		for (int[] e : liftExclude) if (e[0] == fx && e[1] == fz) return false;
		return true;
	}

	/** Hoe een lamp eruitziet als het licht uit staat. */
	public static BlockState lightOff(BlockState s) {
		if (s.contains(net.minecraft.state.property.Properties.LIT)) return s.with(net.minecraft.state.property.Properties.LIT, false);
		if (s.isOf(Blocks.SEA_LANTERN) || s.isOf(Blocks.GLOWSTONE) || s.isOf(Blocks.SHROOMLIGHT)) return Blocks.WHITE_CONCRETE.getDefaultState();
		return s;
	}

	private void readExtras(JsonObject json) {
		if (json.has("lift")) {
			JsonObject lift = json.getAsJsonObject("lift");
			liftRegion = ints(lift.getAsJsonArray("region"));
			liftMax = lift.get("max").getAsDouble();
			liftColumn = ints(lift.getAsJsonArray("column"));
			if (lift.has("exclude")) {
				JsonArray ex = lift.getAsJsonArray("exclude");
				liftExclude = new int[ex.size()][];
				for (int i = 0; i < ex.size(); i++) liftExclude[i] = ints(ex.get(i).getAsJsonArray());
			}
		}
		it.unimi.dsi.fastutil.longs.LongArrayList lp = new it.unimi.dsi.fastutil.longs.LongArrayList();
		for (int x = MIN_X; x < MIN_X + SIZE_X; x++)
			for (int y = MIN_Y; y < MIN_Y + SIZE_Y; y++)
				for (int z = MIN_Z; z < MIN_Z + SIZE_Z; z++) {
					BlockState s = getState(x, y, z);
					if (s == null || s.isAir()) continue;
					boolean lit = s.contains(net.minecraft.state.property.Properties.LIT) && s.get(net.minecraft.state.property.Properties.LIT);
					if (s.getLuminance() > 0 || lit) lp.add(net.minecraft.util.math.BlockPos.asLong(x, y, z));
				}
		lightPositions = lp.toLongArray();
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
			InlandBarge.LOGGER.warn("Onbekend blok in scheepssjabloon: {}", s);
			return Blocks.AIR.getDefaultState();
		}
	}

	private static ShipTemplate load() {
		try (InputStream in = ShipTemplate.class.getResourceAsStream("/inlandbarge/stella_maris.json")) {
			if (in == null) throw new IllegalStateException("hendrika.json ontbreekt");
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			return new ShipTemplate(json);
		} catch (Exception e) {
			throw new RuntimeException("Kon scheepssjabloon niet laden", e);
		}
	}
}
