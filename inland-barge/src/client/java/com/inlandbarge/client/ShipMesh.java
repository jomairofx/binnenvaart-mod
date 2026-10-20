package com.inlandbarge.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import com.inlandbarge.ship.ShipStructure;
import com.inlandbarge.ship.ShipTemplate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Een eenmalig opgebouwd model van alle scheepsblokken.
 * Voor snelheid is het opgedeeld in plakken van 8 blokken (alleen wat in beeld is wordt getekend)
 * en in drie detailniveaus: kleine details alleen van dichtbij, binnenkant tot middellange afstand,
 * en van ver weg alleen de buitenkant.
 */
public final class ShipMesh {
	private static final int STRIDE = 7; // x y z u v kleur licht
	/** Per vlak: normaal (3) en afstand (1), daarna 4 hoekpunten. */
	private static final int QUAD = 4 + 4 * STRIDE;
	private static final int SEC = 8;
	/** Sommige schepen (tanker) hebben dichte tanks: vlakken daarheen zijn nooit te zien. */
	private static final boolean SEALED_CULL = false;
	/** Afstanden (blokken) waaronder detailniveau 0 en 1 nog getekend worden. */
	public static final double NEAR = 200, MID = 400;

	public final int version;
	public final boolean lights;
	private final int sections;
	private final List<RenderLayer> layers = new ArrayList<>();
	/** [laag][sectie * 3 + niveau] */
	private final List<float[][]> data = new ArrayList<>();

	private ShipMesh(int version, boolean lights) {
		this.version = version;
		this.lights = lights;
		this.sections = (ShipTemplate.SIZE_X + SEC - 1) / SEC;
	}

	/**
	 * Tekent de zichtbare plakken. {@code frustum} mag null zijn (dan alles), {@code shipPos}/{@code yaw}
	 * zijn de wereldpositie en draaiing van het schip in dit beeld. Het detailniveau wordt per plak gekozen
	 * aan de hand van de afstand tot de camera; {@code minLevelAll} dwingt een grover niveau af (schaduwen).
	 */
	public void render(MatrixStack matrices, VertexConsumerProvider consumers, @Nullable Frustum frustum, Vec3d shipPos, double yaw, Vec3d cam, int minLevelAll) {
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f m = entry.getPositionMatrix();
		boolean[] visible = new boolean[sections];
		int[] level = new int[sections];
		double c = Math.cos(yaw), s = Math.sin(yaw);
		for (int k = 0; k < sections; k++) {
			double x0 = ShipTemplate.MIN_X + k * SEC - ShipTemplate.CX, x1 = x0 + SEC;
			double z0 = ShipTemplate.MIN_Z - ShipTemplate.CZ, z1 = z0 + ShipTemplate.SIZE_Z;
			double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
			for (double lx : new double[]{x0, x1})
				for (double lz : new double[]{z0, z1}) {
					double wx = shipPos.x + lx * c - lz * s, wz = shipPos.z + lx * s + lz * c;
					minX = Math.min(minX, wx);
					maxX = Math.max(maxX, wx);
					minZ = Math.min(minZ, wz);
					maxZ = Math.max(maxZ, wz);
				}
			Box b = new Box(minX, shipPos.y + ShipTemplate.MIN_Y, minZ, maxX, shipPos.y + ShipTemplate.MIN_Y + ShipTemplate.SIZE_Y, maxZ);
			visible[k] = frustum == null || frustum.isVisible(b);
			double dx = Math.max(0, Math.max(b.minX - cam.x, cam.x - b.maxX));
			double dy = Math.max(0, Math.max(b.minY - cam.y, cam.y - b.maxY));
			double dz = Math.max(0, Math.max(b.minZ - cam.z, cam.z - b.maxZ));
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			level[k] = Math.max(minLevelAll, d < NEAR ? 0 : d < MID ? 1 : 2);
		}
		// camera in sjablooncoördinaten, om afgewende vlakken over te slaan
		double rx = cam.x - shipPos.x, rz = cam.z - shipPos.z;
		float cx = (float) (rx * c + rz * s + ShipTemplate.CX), cy = (float) (cam.y - shipPos.y), cz = (float) (-rx * s + rz * c + ShipTemplate.CZ);
		float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
		float m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
		org.joml.Vector3f nrm = entry.getNormalMatrix().transform(new org.joml.Vector3f(0f, 1f, 0f));
		float nx = nrm.x, ny = nrm.y, nz = nrm.z;
		int overlay = OverlayTexture.DEFAULT_UV;
		for (int l = 0; l < layers.size(); l++) {
			float[][] parts = data.get(l);
			VertexConsumer vc = null;
			for (int k = 0; k < sections; k++) {
				if (!visible[k]) continue;
				for (int lev = level[k]; lev < 3; lev++) {
					float[] d = parts[k * 3 + lev];
					if (d == null || d.length == 0) continue;
					if (vc == null) vc = consumers.getBuffer(layers.get(l));
					for (int q = 0; q < d.length; q += QUAD) {
						// achterkant naar de camera: wordt toch niet getekend
						if (d[q] * cx + d[q + 1] * cy + d[q + 2] * cz - d[q + 3] < -0.6f) continue;
						for (int i = q + 4, e = q + QUAD; i < e; i += STRIDE) {
							float x = d[i], y = d[i + 1], z = d[i + 2];
							vc.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
									Float.floatToRawIntBits(d[i + 5]), d[i + 3], d[i + 4], overlay, Float.floatToRawIntBits(d[i + 6]), nx, ny, nz);
						}
					}
				}
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

	/** Kleine onderdelen (relingen, tapijt, lampjes, hendels...) zijn van ver niet te zien. */
	private static boolean isDetail(BlockState st, ShipStructure s, BlockPos pos) {
		if (st.getBlock() instanceof net.minecraft.block.DoorBlock || st.getBlock() instanceof net.minecraft.block.CarpetBlock) return false;
		VoxelShape shape = st.getOutlineShape(s, pos);
		double v = 0;
		for (Box b : shape.getBoundingBoxes()) v += b.getLengthX() * b.getLengthY() * b.getLengthZ();
		return v < 0.2;
	}

	public static ShipMesh build(ShipStructure s) {
		return build(s, 0, true);
	}

	/** part: 0 = alles, 1 = alles behalve de hefbare stuurhut, 2 = alleen de stuurhut. */
	public static ShipMesh build(ShipStructure s, int part, boolean lightsOn) {
		ShipMesh mesh = new ShipMesh(s.version, lightsOn);
		MinecraftClient mc = MinecraftClient.getInstance();
		var brm = mc.getBlockRenderManager();
		var colors = mc.getBlockColors();
		int[] sky = new int[ShipTemplate.SIZE_X * ShipTemplate.SIZE_Y * ShipTemplate.SIZE_Z];
		int[] blk = new int[sky.length];
		boolean[] opaque = new boolean[sky.length];
		computeLight(s, sky, blk, opaque, lightsOn);
		boolean[] outside = floodOutside(opaque);

		Map<RenderLayer, FloatList[]> buffers = new HashMap<>();
		List<RenderLayer> order = new ArrayList<>();
		Random random = Random.create();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = ShipTemplate.MIN_Y; y < ShipTemplate.MIN_Y + ShipTemplate.SIZE_Y; y++) {
			for (int z = ShipTemplate.MIN_Z; z < ShipTemplate.MIN_Z + ShipTemplate.SIZE_Z; z++) {
				for (int x = ShipTemplate.MIN_X; x < ShipTemplate.MIN_X + ShipTemplate.SIZE_X; x++) {
					BlockState st = s.get(x, y, z);
					if (st.isAir() || st.getRenderType() != BlockRenderType.MODEL) continue;
					if (part == 1 && s.template.inLift(x, y, z)) continue;
					if (part == 2 && !s.template.inLift(x, y, z)) continue;
					pos.set(x, y, z);
					BakedModel model = brm.getModel(st);
					RenderLayer layer = RenderLayers.getEntityBlockLayer(st, false);
					FloatList[] buf = buffers.computeIfAbsent(layer, k -> {
						order.add(k);
						FloatList[] a = new FloatList[mesh.sections * 3];
						for (int i = 0; i < a.length; i++) a[i] = new FloatList();
						return a;
					});
					int sec = Math.min(mesh.sections - 1, Math.max(0, (x - ShipTemplate.MIN_X) / SEC));
					boolean detail = isDetail(st, s, pos);
					for (Direction dir : Direction.values()) {
						random.setSeed(42L);
						List<BakedQuad> quads = model.getQuads(st, dir, random);
						if (quads.isEmpty()) continue;
						boolean border = part != 0 && s.template.inLift(x, y, z) != s.template.inLift(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());
						if (!border && !Block.shouldDrawSide(st, s, pos, dir, pos.offset(dir))) continue;
						int ni = ShipTemplate.index(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());
						if (SEALED_CULL && ni >= 0 && !outside[ni] && !opaque[ni]) continue;
						int lev = detail ? 0 : (ni < 0 || outside[ni]) ? 2 : 1;
						if (dir == Direction.DOWN && !detail && x >= 0 && x < s.template.length && y <= s.template.hullBottom[x]) lev = 1;
						if (border) lev = 0;
						for (BakedQuad q : quads) emit(buf[sec * 3 + lev], q, st, x, y, z, border ? null : dir, sky, blk, opaque, colors, border);
					}
					random.setSeed(42L);
					int own = ShipTemplate.index(x, y, z);
					int levFree = detail ? 0 : (own < 0 || outside[own]) ? 2 : 1;
					for (BakedQuad q : model.getQuads(st, null, random))
						emit(buf[sec * 3 + levFree], q, st, x, y, z, null, sky, blk, opaque, colors, false);
				}
			}
		}
		for (RenderLayer layer : order) {
			FloatList[] b = buffers.get(layer);
			float[][] parts = new float[b.length][];
			for (int i = 0; i < b.length; i++) parts[i] = b[i].n == 0 ? null : b[i].toArray();
			mesh.layers.add(layer);
			mesh.data.add(parts);
		}
		return mesh;
	}

	private static void emit(FloatList buf, BakedQuad q, BlockState st, int x, int y, int z, Direction cull,
							 int[] sky, int[] blk, boolean[] opaque, net.minecraft.client.color.block.BlockColors colors, boolean border) {
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
		if (border) skyL = 15;   // face between the raised wheelhouse and the roof: open air when lifted
		int light = LightmapTextureManager.pack(blkL, skyL);
		// echte normaal van het vlak (uit de hoekpunten), voor het overslaan van afgewende vlakken
		float ax = Float.intBitsToFloat(v[0]) + x, ay = Float.intBitsToFloat(v[1]) + y, az = Float.intBitsToFloat(v[2]) + z;
		float ux = Float.intBitsToFloat(v[stride]) + x - ax, uy = Float.intBitsToFloat(v[stride + 1]) + y - ay, uz = Float.intBitsToFloat(v[stride + 2]) + z - az;
		float wx = Float.intBitsToFloat(v[2 * stride]) + x - ax, wy = Float.intBitsToFloat(v[2 * stride + 1]) + y - ay, wz = Float.intBitsToFloat(v[2 * stride + 2]) + z - az;
		float qx = uy * wz - uz * wy, qy = uz * wx - ux * wz, qz = ux * wy - uy * wx;
		float ql = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
		if (ql < 1e-6f) {
			// ontaard vlak: nooit overslaan
			buf.add(0); buf.add(0); buf.add(0); buf.add(-1000f);
		} else {
			qx /= ql; qy /= ql; qz /= ql;
			if (qx * face.getOffsetX() + qy * face.getOffsetY() + qz * face.getOffsetZ() < 0) { qx = -qx; qy = -qy; qz = -qz; }
			buf.add(qx); buf.add(qy); buf.add(qz); buf.add(qx * ax + qy * ay + qz * az);
		}
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

	/** Alle niet-dichte cellen die van buiten het schip bereikbaar zijn. */
	private static boolean[] floodOutside(boolean[] opaque) {
		int sx = ShipTemplate.SIZE_X, sy = ShipTemplate.SIZE_Y, sz = ShipTemplate.SIZE_Z;
		boolean[] out = new boolean[opaque.length];
		int[] q = new int[opaque.length];
		int head = 0, tail = 0;
		for (int iy = 0; iy < sy; iy++)
			for (int iz = 0; iz < sz; iz++)
				for (int ix = 0; ix < sx; ix++) {
					if (ix == 0 || iy == 0 || iz == 0 || ix == sx - 1 || iy == sy - 1 || iz == sz - 1) {
						int i = (iy * sz + iz) * sx + ix;
						if (!opaque[i] && !out[i]) {
							out[i] = true;
							q[tail++] = i;
						}
					}
				}
		while (head < tail) {
			int i = q[head++];
			int ix = i % sx, iz = (i / sx) % sz, iy = i / (sx * sz);
			int[][] n = {{ix + 1, iy, iz}, {ix - 1, iy, iz}, {ix, iy + 1, iz}, {ix, iy - 1, iz}, {ix, iy, iz + 1}, {ix, iy, iz - 1}};
			for (int[] c : n) {
				if (c[0] < 0 || c[1] < 0 || c[2] < 0 || c[0] >= sx || c[1] >= sy || c[2] >= sz) continue;
				int j = (c[1] * sz + c[2]) * sx + c[0];
				if (opaque[j] || out[j]) continue;
				out[j] = true;
				q[tail++] = j;
			}
		}
		return out;
	}

	/** Eenvoudige lichtberekening binnen het schip: daglicht van boven en licht van lampen. */
	private static void computeLight(ShipStructure s, int[] sky, int[] blk, boolean[] opaque, boolean lightsOn) {
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
					int lum = lightsOn ? st.getLuminance() : 0;
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
		float[] a = new float[1024];
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
