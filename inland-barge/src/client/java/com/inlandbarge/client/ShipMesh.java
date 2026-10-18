package com.inlandbarge.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import com.inlandbarge.ship.ShipStructure;
import com.inlandbarge.ship.ShipTemplate;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Een eenmalig opgebouwd model van alle scheepsblokken, dat elk beeld alleen nog gedraaid hoeft te worden. */
public final class ShipMesh {
	private static final int STRIDE = 7; // x y z u v kleur licht
	public final int version;
	private final List<RenderLayer> layers = new ArrayList<>();
	private final List<float[]> data = new ArrayList<>();

	private ShipMesh(int version) {
		this.version = version;
	}

	public void render(MatrixStack matrices, VertexConsumerProvider consumers) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f m = entry.getPositionMatrix();
		for (int l = 0; l < layers.size(); l++) {
			VertexConsumer vc = consumers.getBuffer(layers.get(l));
			float[] d = data.get(l);
			for (int i = 0; i < d.length; i += STRIDE) {
				vc.vertex(m, d[i], d[i + 1], d[i + 2])
						.color(Float.floatToRawIntBits(d[i + 5]))
						.texture(d[i + 3], d[i + 4])
						.overlay(OverlayTexture.DEFAULT_UV)
						.light(Float.floatToRawIntBits(d[i + 6]))
						.normal(entry, 0f, 1f, 0f);
			}
		}
	}

	// -------------------------------------------------------------------------

	private static float shade(Direction d) {
		return switch (d) {
			case DOWN -> 0.5f;
			case UP -> 1.0f;
			case NORTH, SOUTH -> 0.8f;
			case EAST, WEST -> 0.6f;
		};
	}

	public static ShipMesh build(ShipStructure s) {
		ShipMesh mesh = new ShipMesh(s.version);
		MinecraftClient mc = MinecraftClient.getInstance();
		var brm = mc.getBlockRenderManager();
		var colors = mc.getBlockColors();
		int[] sky = new int[ShipTemplate.SIZE_X * ShipTemplate.SIZE_Y * ShipTemplate.SIZE_Z];
		int[] blk = new int[sky.length];
		boolean[] opaque = new boolean[sky.length];
		computeLight(s, sky, blk, opaque);

		Map<RenderLayer, FloatList> buffers = new LinkedHashMap<>();
		Random random = Random.create();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = ShipTemplate.MIN_Y; y < ShipTemplate.MIN_Y + ShipTemplate.SIZE_Y; y++) {
			for (int z = ShipTemplate.MIN_Z; z < ShipTemplate.MIN_Z + ShipTemplate.SIZE_Z; z++) {
				for (int x = ShipTemplate.MIN_X; x < ShipTemplate.MIN_X + ShipTemplate.SIZE_X; x++) {
					BlockState st = s.get(x, y, z);
					if (st.isAir() || st.getRenderType() != BlockRenderType.MODEL) continue;
					pos.set(x, y, z);
					BakedModel model = brm.getModel(st);
					RenderLayer layer = RenderLayers.getEntityBlockLayer(st, false);
					FloatList buf = buffers.computeIfAbsent(layer, k -> new FloatList());
					for (Direction dir : Direction.values()) {
						random.setSeed(42L);
						List<BakedQuad> quads = model.getQuads(st, dir, random);
						if (quads.isEmpty()) continue;
						if (!Block.shouldDrawSide(st, s, pos, dir, pos.offset(dir))) continue;
						for (BakedQuad q : quads) emit(buf, q, st, x, y, z, dir, sky, blk, opaque, colors);
					}
					random.setSeed(42L);
					for (BakedQuad q : model.getQuads(st, null, random)) emit(buf, q, st, x, y, z, null, sky, blk, opaque, colors);
				}
			}
		}
		for (Map.Entry<RenderLayer, FloatList> e : buffers.entrySet()) {
			mesh.layers.add(e.getKey());
			mesh.data.add(e.getValue().toArray());
		}
		return mesh;
	}

	private static void emit(FloatList buf, BakedQuad q, BlockState st, int x, int y, int z, Direction cull,
							 int[] sky, int[] blk, boolean[] opaque, net.minecraft.client.color.block.BlockColors colors) {
		int[] v = q.getVertexData();
		int stride = v.length / 4;
		Direction face = q.getFace();
		float r = 1, g = 1, b = 1;
		if (q.hasColor()) {
			int c;
			if (st.isOf(Blocks.WATER_CAULDRON)) c = 0x3F76E4;
			else c = colors.getColor(st, null, null, q.getColorIndex());
			if (c == -1) c = 0xFFFFFF;
			r = ((c >> 16) & 255) / 255f;
			g = ((c >> 8) & 255) / 255f;
			b = (c & 255) / 255f;
		}
		float sh = q.hasShade() ? shade(face) : 1f;
		int color = ((int) (r * sh * 255) << 16) | ((int) (g * sh * 255) << 8) | (int) (b * sh * 255) | 0xFF000000;
		// licht: van het blok waar het vlak naartoe kijkt
		int lx = x, ly = y, lz = z;
		Direction ld = cull != null ? cull : face;
		int own = ShipTemplate.index(x, y, z);
		if (cull != null || (own >= 0 && opaque[own])) {
			lx += ld.getOffsetX();
			ly += ld.getOffsetY();
			lz += ld.getOffsetZ();
		}
		int li = ShipTemplate.index(lx, ly, lz);
		int skyL = li < 0 ? 15 : sky[li];
		int blkL = li < 0 ? 0 : blk[li];
		int light = LightmapTextureManager.pack(blkL, skyL);
		for (int i = 0; i < 4; i++) {
			int o = i * stride;
			buf.add(Float.intBitsToFloat(v[o]) + x);
			buf.add(Float.intBitsToFloat(v[o + 1]) + y);
			buf.add(Float.intBitsToFloat(v[o + 2]) + z);
			buf.add(Float.intBitsToFloat(v[o + 4]));
			buf.add(Float.intBitsToFloat(v[o + 5]));
			buf.add(Float.intBitsToFloat(color));
			buf.add(Float.intBitsToFloat(light));
		}
	}

	/** Eenvoudige lichtberekening binnen het schip: daglicht van boven en licht van lampen. */
	private static void computeLight(ShipStructure s, int[] sky, int[] blk, boolean[] opaque) {
		int sx = ShipTemplate.SIZE_X, sy = ShipTemplate.SIZE_Y, sz = ShipTemplate.SIZE_Z;
		BlockPos.Mutable p = new BlockPos.Mutable();
		ArrayDeque<Integer> qs = new ArrayDeque<>();
		ArrayDeque<Integer> qb = new ArrayDeque<>();
		for (int iy = 0; iy < sy; iy++)
			for (int iz = 0; iz < sz; iz++)
				for (int ix = 0; ix < sx; ix++) {
					int i = (iy * sz + iz) * sx + ix;
					int x = ix + ShipTemplate.MIN_X, y = iy + ShipTemplate.MIN_Y, z = iz + ShipTemplate.MIN_Z;
					BlockState st = s.get(x, y, z);
					p.set(x, y, z);
					opaque[i] = !st.isAir() && st.isOpaqueFullCube(s, p);
					int lum = st.getLuminance();
					if (lum > 0) {
						blk[i] = lum;
						qb.add(i);
					}
				}
		for (int iz = 0; iz < sz; iz++)
			for (int ix = 0; ix < sx; ix++) {
				for (int iy = sy - 1; iy >= 0; iy--) {
					int i = (iy * sz + iz) * sx + ix;
					if (opaque[i]) break;
					sky[i] = 15;
					qs.add(i);
				}
			}
		spread(qs, sky, opaque);
		spread(qb, blk, opaque);
	}

	private static void spread(ArrayDeque<Integer> q, int[] light, boolean[] opaque) {
		int sx = ShipTemplate.SIZE_X, sy = ShipTemplate.SIZE_Y, sz = ShipTemplate.SIZE_Z;
		while (!q.isEmpty()) {
			int i = q.poll();
			int l = light[i] - 1;
			if (l <= 0) continue;
			int ix = i % sx, iz = (i / sx) % sz, iy = i / (sx * sz);
			int[][] n = {{ix + 1, iy, iz}, {ix - 1, iy, iz}, {ix, iy + 1, iz}, {ix, iy - 1, iz}, {ix, iy, iz + 1}, {ix, iy, iz - 1}};
			for (int[] c : n) {
				if (c[0] < 0 || c[1] < 0 || c[2] < 0 || c[0] >= sx || c[1] >= sy || c[2] >= sz) continue;
				int j = (c[1] * sz + c[2]) * sx + c[0];
				if (opaque[j] || light[j] >= l) continue;
				light[j] = l;
				q.add(j);
			}
		}
	}

	static final class FloatList {
		float[] a = new float[4096];
		int n;

		void add(float f) {
			if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
			a[n++] = f;
		}

		float[] toArray() {
			return java.util.Arrays.copyOf(a, n);
		}
	}
}
