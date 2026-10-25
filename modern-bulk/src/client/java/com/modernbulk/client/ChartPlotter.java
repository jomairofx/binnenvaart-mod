package com.modernbulk.client;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import com.modernbulk.ship.ShipEntity;
import com.modernbulk.ship.ShipHolder;
import org.joml.Matrix4f;

/**
 * The chart plotter at the helm: a live map of the world round the ship, drawn like a Minecraft map (block colours,
 * relief shading, deeper water darker), heading up with the ship itself in the middle at its real size, other ships,
 * a range ring every 100 blocks and a red mark to the north. About 380 x 285 blocks are on the screen.
 * The map is read from the world a few thousand columns per tick into a buffer that stays fixed to the world, so it
 * scrolls smoothly however fast the ship goes.
 */
final class ChartPlotter {
	static final int W = 256, H = 192;
	/** Blocks per pixel. */
	static final float SCALE = 1.5f;
	private static final int RING = 512, MASK = RING - 1;
	private static final int SCAN = 200;            // columns read round the ship (each side)
	private static final int PER_TICK = 4200;
	private static final int UNKNOWN = 0xFF2A1C12;  // dark navy (ABGR)

	private static final int[] colour = new int[RING * RING];
	private static final int[] keyX = new int[RING * RING], keyZ = new int[RING * RING];
	private static boolean cleared;
	private static NativeImageBackedTexture texture;
	private static Identifier id;
	private static World lastWorld;
	private static int cursor;
	private static int lastAge = -1, lastShip = -1;

	private ChartPlotter() {
	}

	private static void clear() {
		java.util.Arrays.fill(keyX, Integer.MIN_VALUE);
		cleared = true;
	}

	/** Reads the next stretch of the world round the ship into the buffer. */
	private static void scan(World w, ShipEntity ship) {
		int cx = (int) Math.floor(ship.getX()), cz = (int) Math.floor(ship.getZ());
		int side = SCAN * 2 + 1, total = side * side;
		BlockPos.Mutable m = new BlockPos.Mutable();
		// the cells nearest the ship first after a jump, then round and round
		for (int n = 0; n < PER_TICK; n++) {
			int i = cursor;
			cursor = (cursor + 1) % total;
			int x = cx - SCAN + i % side, z = cz - SCAN + i / side;
			int k = (x & MASK) * RING + (z & MASK);
			if (!w.isChunkLoaded(x >> 4, z >> 4)) {
				if (keyX[k] != x || keyZ[k] != z) {
					keyX[k] = x;
					keyZ[k] = z;
					colour[k] = UNKNOWN;
				}
				continue;
			}
			colour[k] = sample(w, m, x, z);
			keyX[k] = x;
			keyZ[k] = z;
		}
	}

	private static int top(World w, int x, int z) {
		return w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
	}

	/** The map colour of one column, as on a Minecraft map. */
	private static int sample(World w, BlockPos.Mutable m, int x, int z) {
		int y = top(w, x, z);
		int bottom = w.getBottomY();
		BlockState st = w.getBlockState(m.set(x, y, z));
		while (y > bottom && st.getMapColor(w, m) == MapColor.CLEAR) st = w.getBlockState(m.set(x, --y, z));
		FluidState fl = st.getFluidState();
		if (fl.isIn(FluidTags.WATER) || st.getMapColor(w, m) == MapColor.WATER_BLUE) {
			int depth = 0;
			while (depth < 14 && y - depth > bottom && w.getBlockState(m.set(x, y - depth - 1, z)).getFluidState().isIn(FluidTags.WATER)) depth++;
			double f = depth * 0.1 + ((x + z) & 1) * 0.2;
			MapColor.Brightness b = f < 0.5 ? MapColor.Brightness.HIGH : f > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
			return MapColor.WATER_BLUE.getRenderColor(b);
		}
		MapColor mc = st.getMapColor(w, m.set(x, y, z));
		if (mc == MapColor.CLEAR) return UNKNOWN;
		int north = top(w, x, z - 1);
		double d = (y - north) * 0.8 + (((x + z) & 1) - 0.5) * 0.4;
		MapColor.Brightness b = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
		return mc.getRenderColor(b);
	}

	/** Builds the picture: heading up, the ship a little below the middle so you see further ahead. */
	private static void compose(World w, ShipEntity ship) {
		NativeImage img = texture.getImage();
		if (img == null) return;
		double c = Math.cos(ship.shipYaw), s = Math.sin(ship.shipYaw);
		double ox = ship.getX(), oz = ship.getZ();
		float cy = H * 0.6f;
		float ring1 = 100f / SCALE, ring2 = 200f / SCALE;
		java.util.List<ShipEntity> ships = ShipHolder.of(w);
		// north on the screen: the world -z direction seen from the ship
		double nf = -s, nr = -c;     // forward and right parts of north
		for (int py = 0; py < H; py++) {
			for (int px = 0; px < W; px++) {
				double right = (px + 0.5 - W / 2.0) * SCALE, fwd = (cy - py - 0.5) * SCALE;
				double wx = ox + fwd * c - right * s, wz = oz + fwd * s + right * c;
				int x = (int) Math.floor(wx), z = (int) Math.floor(wz);
				int k = (x & MASK) * RING + (z & MASK);
				int col = keyX[k] == x && keyZ[k] == z ? colour[k] : UNKNOWN;
				// ships: this one white, others orange, at their real size
				for (ShipEntity o : ships) {
					if (Math.abs(o.getX() - wx) > 130 || Math.abs(o.getZ() - wz) > 130) continue;
					net.minecraft.util.math.Vec3d l = o.toLocal(new net.minecraft.util.math.Vec3d(wx, o.getY(), wz));
					if (o.template.insideOutline(l.x, l.z, 0.4)) {
						col = o == ship ? 0xFFFFFFFF : 0xFF2A8CFF;
						break;
					}
				}
				// range rings at 100 and 200 blocks
				double dx = px + 0.5 - W / 2.0, dy = py + 0.5 - cy, r = Math.sqrt(dx * dx + dy * dy);
				if (Math.abs(r - ring1) < 0.55 || Math.abs(r - ring2) < 0.55) col = blend(col, 0xFFE0E0E0, 0.45f);
				// heading line straight ahead
				if (Math.abs(dx) < 0.6 && dy < 0 && ((int) -dy / 4) % 2 == 0) col = blend(col, 0xFFFFFFFF, 0.5f);
				img.setColor(px, py, col);
			}
		}
		// the north mark on the outer ring
		double len = Math.hypot(nf, nr);
		int nx = (int) Math.round(W / 2.0 + nr / len * ring2), ny = (int) Math.round(cy - nf / len * ring2);
		for (int a = -2; a <= 2; a++)
			for (int b = -2; b <= 2; b++) {
				int qx = nx + a, qy = ny + b;
				if (qx >= 0 && qy >= 0 && qx < W && qy < H && a * a + b * b <= 5) img.setColor(qx, qy, 0xFF2020E0);
			}
		// a frame
		for (int px = 0; px < W; px++) {
			img.setColor(px, 0, 0xFF303030);
			img.setColor(px, H - 1, 0xFF303030);
		}
		for (int py = 0; py < H; py++) {
			img.setColor(0, py, 0xFF303030);
			img.setColor(W - 1, py, 0xFF303030);
		}
		texture.upload();
	}

	private static int blend(int a, int b, float t) {
		int r = (int) (((a) & 255) * (1 - t) + ((b) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		return 0xFF000000 | (bl << 16) | (g << 8) | r;
	}

	/**
	 * Draws the screen on a vertical plane at x = fx (template coordinates, facing aft towards -x), from y0 to y1 and
	 * z0 to z1. Only the ship nearest the camera keeps its map up to date.
	 */
	static void draw(ShipEntity ship, MatrixStack matrices, VertexConsumerProvider consumers, float fx, float y0, float y1, float z0, float z1) {
		MinecraftClient mc = MinecraftClient.getInstance();
		World w = ship.getWorld();
		if (mc.player == null || ship.squaredDistanceTo(mc.player) > 70 * 70) return;
		if (texture == null) {
			texture = new NativeImageBackedTexture(W, H, true);
			id = Identifier.of("modernbulk", "chart_plotter");
			mc.getTextureManager().registerTexture(id, texture);
		}
		if (w != lastWorld || !cleared) {
			clear();
			lastWorld = w;
			cursor = 0;
		}
		// the nearest ship with a screen takes the plotter; it refreshes once per tick
		if (lastShip != ship.getId()) {
			ShipEntity cur = lastShip < 0 ? null : (w.getEntityById(lastShip) instanceof ShipEntity se ? se : null);
			if (cur == null || cur.isRemoved() || cur.squaredDistanceTo(mc.player) > ship.squaredDistanceTo(mc.player) + 4) {
				lastShip = ship.getId();
				lastAge = -1;
			} else return;
		}
		if (ship.age != lastAge) {
			lastAge = ship.age;
			scan(w, ship);
			compose(w, ship);
		}
		float sw = z1 - z0, sh = y1 - y0, a = sw / sh, ia = (float) W / H;
		float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
		if (a > ia) {
			float span = ia / a;
			v0 = 0.5f - span / 2;
			v1 = 0.5f + span / 2;
		} else {
			float span = a / ia;
			u0 = 0.5f - span / 2;
			u1 = 0.5f + span / 2;
		}
		VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(id));
		MatrixStack.Entry e = matrices.peek();
		Matrix4f m = e.getPositionMatrix();
		int light = 0xF000F0;
		vc.vertex(m, fx, y1, z0).color(0xFFFFFFFF).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, -1f, 0f, 0f);
		vc.vertex(m, fx, y0, z0).color(0xFFFFFFFF).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, -1f, 0f, 0f);
		vc.vertex(m, fx, y0, z1).color(0xFFFFFFFF).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, -1f, 0f, 0f);
		vc.vertex(m, fx, y1, z1).color(0xFFFFFFFF).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, -1f, 0f, 0f);
	}
}
