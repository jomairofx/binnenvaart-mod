package com.megatanker.client;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import com.megatanker.ship.ShipEntity;
import com.megatanker.ship.ShipTemplate;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShipRenderer extends EntityRenderer<ShipEntity> {
	private static final Block[] CONTAINER_COLORS = {
			Blocks.BLUE_CONCRETE, Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.GREEN_TERRACOTTA,
			Blocks.WHITE_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.CYAN_TERRACOTTA, Blocks.BROWN_CONCRETE
	};
	private final Map<BlockState, Sprite[]> spriteCache = new HashMap<>();
	private final Map<ShipEntity, DynCache> caches = new java.util.WeakHashMap<>();
	private Frustum lastFrustum;

	public ShipRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.shadowRadius = 0f;
	}

	@Override
	public Identifier getTexture(ShipEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}

	@Override
	public boolean shouldRender(ShipEntity entity, Frustum frustum, double x, double y, double z) {
		lastFrustum = frustum;
		return frustum.isVisible(entity.getBoundingBox().expand(2));
	}

	@Override
	public void render(ShipEntity ship, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		ShipMesh mesh = ship.renderCache instanceof ShipMesh m ? m : null;
		if (mesh == null || mesh.version != ship.structure.version) {
			mesh = ShipMesh.build(ship.structure);
			ship.renderCache = mesh;
		}
		double dy = MathHelper.wrapDegrees(Math.toDegrees(ship.shipYaw - ship.prevShipYaw));
		double y = ship.prevShipYaw + Math.toRadians(dy) * tickDelta;

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) -y));
		matrices.translate(-ShipTemplate.CX, 0, -ShipTemplate.CZ);
		Vec3d cam = this.dispatcher.camera.getPos();
		Vec3d shipPos = ship.getLerpedPos(tickDelta);
		boolean shadow = IrisCompat.isShadowPass();
		mesh.render(matrices, consumers, lastFrustum, shipPos, y, cam, shadow ? 2 : 0);

		DynCache dc = caches.computeIfAbsent(ship, k -> new DynCache());
		float hatch = MathHelper.lerp(tickDelta, ship.prevHatchProgress, ship.hatchProgress);
		boolean dirty = Math.abs(dc.hatch - hatch) > 1e-4f || dc.light != light;
		float[] fills = new float[4];
		for (int h = 0; h < 4; h++) {
			fills[h] = MathHelper.lerp(tickDelta, ship.prevDisplayFill[h], ship.displayFill[h]);
			if (Math.abs(dc.fill[h] - fills[h]) > 1e-4f || dc.mat[h] != ship.material(h)) dirty = true;
		}
		if (dirty) {
			dc.sink.n = 0;
			dc.hatch = hatch;
			dc.light = light;
			for (int h = 0; h < 4; h++) {
				dc.fill[h] = fills[h];
				dc.mat[h] = ship.material(h);
			}
			renderCargo(ship, fills, hatch, dc.sink, light);
			renderCovers(hatch, dc.sink, light);
			renderBeds(ship, dc.sink, light);
		}
		dc.sink.emit(consumers.getBuffer(TexturedRenderLayers.getEntityCutout()), matrices.peek());
		if (!shadow) renderName(ship, matrices, consumers, light);
		matrices.pop();
		super.render(ship, yaw, tickDelta, matrices, consumers, light);
	}

	// ---------------------------------------------------------------- lading

	private Sprite[] sprites(BlockState st) {
		return spriteCache.computeIfAbsent(st, s -> {
			BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(s);
			Random r = Random.create(42L);
			List<BakedQuad> up = model.getQuads(s, Direction.UP, r);
			List<BakedQuad> side = model.getQuads(s, Direction.NORTH, r);
			Sprite top = up.isEmpty() ? model.getParticleSprite() : up.get(0).getSprite();
			Sprite sd = side.isEmpty() ? top : side.get(0).getSprite();
			return new Sprite[]{top, sd};
		});
	}

	/** Tank level gauges next to every deck valve: a white frame with a black oil column. */
	private void renderCargo(ShipEntity ship, float[] fills, float hatch, Sink vc, int light) {
		ShipTemplate t = ship.template;
		Sprite frame = sprites(Blocks.WHITE_CONCRETE.getDefaultState())[0];
		Sprite oil = sprites(Blocks.BLACK_CONCRETE.getDefaultState())[0];
		Sprite empty = sprites(Blocks.LIGHT_GRAY_CONCRETE.getDefaultState())[0];
		for (int h = 0; h < t.holds.length; h++) {
			int cx = (t.holds[h][0] + t.holds[h][1]) / 2;
			for (float z0 : new float[]{4.15f, 25.15f}) {
				float x0 = cx + 0.3f, y0 = 2.0f, hgt = 2.6f;
				box(vc, frame, x0 - 0.08f, y0, z0 - 0.08f, x0 + 0.48f, y0 + 0.12f, z0 + 0.78f, light, 1f);
				box(vc, frame, x0 - 0.08f, y0 + hgt, z0 - 0.08f, x0 + 0.48f, y0 + hgt + 0.12f, z0 + 0.78f, light, 1f);
				float level = MathHelper.clamp(fills[h], 0f, 1f) * (hgt - 0.12f);
				if (level > 0.01f) box(vc, oil, x0, y0 + 0.12f, z0, x0 + 0.4f, y0 + 0.12f + level, z0 + 0.7f, light, 1f);
				if (level < hgt - 0.13f) box(vc, empty, x0 + 0.05f, y0 + 0.12f + level, z0 + 0.05f, x0 + 0.35f, y0 + hgt, z0 + 0.65f, light, 1f);
			}
		}
	}

	private void renderCargoBulk(ShipEntity ship, float[] fills, float hatch, Sink vc, int light) {
		ShipTemplate t = ship.template;
		for (int h = 0; h < t.holds.length; h++) {
			float f = fills[h];
			if (f < 0.002f) continue;
			int mat = ship.material(h);
			int a = t.holds[h][0], b = t.holds[h][1];
			if (mat > 0) {
				BlockState st = Registries.BLOCK.get(mat - 1).getDefaultState();
				renderPile(vc, sprites(st), a, b, t.holdZ0, t.holdZ1, (float) t.holdFloorY, f, hatch, light);
			} else {
				renderContainers(vc, h, a, b, (float) t.holdFloorY, f, light);
			}
		}
	}

	private static float pileHeight(int x, int z, int a, int b, float f, float cap) {
		float cx = (a + b + 1) / 2f, halfLen = (b - a + 1) / 2f;
		float dx = Math.abs(x + 0.5f - cx) / halfLen, dz = Math.abs(z + 0.5f - 5.5f) / 3.5f;
		long hsh = (x * 3129871L) ^ (z * 116129781L);
		hsh = hsh * hsh * 42317861L + hsh * 11L;
		float noise = ((hsh >> 16) & 15) / 15f - 0.5f;
		float hgt = f * (7.0f - 2.4f * dx * dx - 1.3f * dz * dz) + noise * 0.25f * f;
		float c = cap;
		if (x >= 89) c = Math.min(c, 5.4f);
		return MathHelper.clamp(hgt, 0.05f, c);
	}

	private void renderPile(Sink vc, Sprite[] sp, int a, int b, int z0, int z1, float floor,
							float f, float hatch, int light) {
		float cap = MathHelper.lerp(hatch, 7.0f, 5.4f);
		int w = b - a + 1, d = z1 - z0 + 1;
		float[][] hm = new float[w][d];
		for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) hm[i][k] = pileHeight(a + i, z0 + k, a, b, f, cap);
		for (int i = 0; i < w; i++) {
			for (int k = 0; k < d; k++) {
				float x = a + i, z = z0 + k, top = floor + hm[i][k];
				quadUp(vc, sp[0], x, top, z, x + 1, z + 1, 1f, light);
				// zijkanten waar de buur lager ligt
				float hxm = i == 0 ? 0 : hm[i - 1][k], hxp = i == w - 1 ? 0 : hm[i + 1][k];
				float hzm = k == 0 ? hm[i][k] : hm[i][k - 1], hzp = k == d - 1 ? hm[i][k] : hm[i][k + 1];
				sideX(vc, sp[1], x, z, z + 1, floor + hxm, top, false, floor, light);
				sideX(vc, sp[1], x + 1, z, z + 1, floor + hxp, top, true, floor, light);
				sideZ(vc, sp[1], z, x, x + 1, floor + hzm, top, false, floor, light);
				sideZ(vc, sp[1], z + 1, x, x + 1, floor + hzp, top, true, floor, light);
			}
		}
	}

	private void renderContainers(Sink vc, int hold, int a, int b, float floor, float f, int light) {
		float slots = f * 8f;
		int len = b - a + 1;
		for (int k = 0; k < 8; k++) {
			float p = MathHelper.clamp(slots - k, 0f, 1f);
			if (p <= 0) break;
			int tier = k / 4, idx = k % 4, row = idx % 2, col = idx / 2;
			float x0 = a + (len - 12.4f) / 2f + col * 6.4f;
			float zz = row == 0 ? 2.25f : 5.8f;
			float drop = (1 - p) * (1 - p) * 7f;
			float y0 = floor + tier * 2.62f + drop;
			Block color = CONTAINER_COLORS[(hold * 3 + k * 5) % CONTAINER_COLORS.length];
			Sprite s = sprites(color.getDefaultState())[0];
			box(vc, s, x0, y0, zz, x0 + 6.05f, y0 + 2.59f, zz + 2.44f, light, 1f);
			// deuren aan de kopse kant: iets donkerder strookje
			box(vc, s, x0 + 6.05f, y0 + 0.1f, zz + 0.1f, x0 + 6.1f, y0 + 2.49f, zz + 2.34f, light, 0.55f);
		}
	}

	// ---------------------------------------------------------------- luiken

	private void renderCovers(float t, Sink vc, int light) {
		Sprite s = sprites(Blocks.WHITE_CONCRETE.getDefaultState())[0];
		Sprite rim = sprites(Blocks.LIGHT_GRAY_CONCRETE.getDefaultState())[0];
		int n = ShipEntity.COVERS;
		float stagger = 0.06f, dur = 1f - stagger * (n - 1);
		for (int i = 0; i < n; i++) {
			float p = MathHelper.clamp((t - stagger * i) / dur, 0f, 1f);
			p = p * p * (3 - 2 * p);
			float stackY = 3.5f + ShipEntity.COVER_STACK * (n - 1 - i);
			float drop = MathHelper.clamp((p - 0.8f) / 0.2f, 0f, 1f);
			float x0 = MathHelper.lerp(p, 90f, 15f + 5f * i);
			float y = MathHelper.lerp(drop, stackY, 3.5f);
			if (p > 0 && p < 1) y += 0.15f * MathHelper.sin(p * MathHelper.PI);
			box(vc, rim, x0 + 0.03f, y, 0.95f, x0 + 4.97f, y + 0.25f, 10.05f, light, 0.95f);
			box(vc, s, x0 + 0.03f, y + 0.25f, 2.2f, x0 + 4.97f, y + 0.5f, 8.8f, light, 1f);
			box(vc, s, x0 + 0.03f, y + 0.5f, 3.8f, x0 + 4.97f, y + 0.62f, 7.2f, light, 1f);
		}
	}

	// ---------------------------------------------------------------- bedden

	/** Bedden worden in Minecraft apart getekend (geen blokmodel), dus hier zelf: frame, matras, dekbed en kussen. */
	private void renderBeds(ShipEntity ship, Sink vc, int light) {
		Sprite wool = sprites(Blocks.WHITE_WOOL.getDefaultState())[0];
		Sprite frame = sprites(Blocks.DARK_OAK_PLANKS.getDefaultState())[0];
		for (int[] h : ship.bedHeads()) {
			BlockState st = ship.structure.get(h[0], h[1], h[2]);
			if (!(st.getBlock() instanceof BedBlock bed)) continue;
			Block w = Registries.BLOCK.get(Identifier.ofVanilla(bed.getColor().getName() + "_wool"));
			Sprite cover = sprites(w.getDefaultState())[0];
			Direction f = st.get(BedBlock.FACING);
			// maten in het bed: u over de breedte (0..1), v van hoofdeinde (0) naar voeteneind (2)
			bedBox(vc, frame, h, f, 0.0f, 0.0f, 1.0f, 2.0f, 0.12f, 0.3f, light, 0.9f);           // frame
			for (float[] leg : new float[][]{{0f, 0f}, {0.85f, 0f}, {0f, 1.85f}, {0.85f, 1.85f}})
				bedBox(vc, frame, h, f, leg[0], leg[1], leg[0] + 0.15f, leg[1] + 0.15f, 0f, 0.12f, light, 0.8f);
			bedBox(vc, frame, h, f, 0.0f, 0.0f, 1.0f, 0.1f, 0.12f, 0.9f, light, 0.85f);           // hoofdeinde
			bedBox(vc, wool, h, f, 0.03f, 0.1f, 0.97f, 1.97f, 0.3f, 0.5625f, light, 1f);          // matras
			bedBox(vc, wool, h, f, 0.15f, 0.16f, 0.85f, 0.55f, 0.5625f, 0.7f, light, 1f);         // kussen
			bedBox(vc, cover, h, f, 0.0f, 0.7f, 1.0f, 2.0f, 0.5625f, 0.62f, light, 1f);           // dekbed
			bedBox(vc, cover, h, f, 0.0f, 1.97f, 1.0f, 2.02f, 0.28f, 0.62f, light, 0.9f);         // overslag voeteneind
		}
	}

	private static void bedBox(Sink vc, Sprite s, int[] h, Direction f, float u0, float v0, float u1, float v1, float y0, float y1, int light, float tint) {
		float[] a = map(f, u0, v0), b = map(f, u1, v1);
		float x0 = h[0] + Math.min(a[0], b[0]), x1 = h[0] + Math.max(a[0], b[0]);
		float z0 = h[2] + Math.min(a[1], b[1]), z1 = h[2] + Math.max(a[1], b[1]);
		box(vc, s, x0, h[1] + y0, z0, x1, h[1] + y1, z1, light, tint);
	}

	/** (u, v) in bedmaten naar (x, z) vanaf de hoek van het blok met het hoofdeinde. */
	private static float[] map(Direction f, float u, float v) {
		return switch (f) {
			case NORTH -> new float[]{u, v};            // hoofd naar -z, voeteneind richting +z
			case SOUTH -> new float[]{1 - u, 1 - v};
			case WEST -> new float[]{v, 1 - u};
			default -> new float[]{1 - v, u};           // EAST
		};
	}

	// ---------------------------------------------------------------- naam

	private void renderName(ShipEntity ship, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		TextRenderer tr = MinecraftClient.getInstance().textRenderer;
		String name = ship.template.name;
		float scale = 0.22f;
		float w = tr.getWidth(name) * scale;
		// stuurboord (z+)
		matrices.push();
		matrices.translate(138.0f, 1.9f, 30.01f);
		matrices.scale(scale, -scale, scale);
		draw(tr, name, matrices, consumers, light);
		matrices.pop();
		// bakboord (z-)
		matrices.push();
		matrices.translate(138.0f + w, 1.9f, -0.01f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
		matrices.scale(scale, -scale, scale);
		draw(tr, name, matrices, consumers, light);
		matrices.pop();
		// stern: smaller, just above the waterline
		float ss = 0.16f;
		float ws = tr.getWidth(name) * ss;
		matrices.push();
		matrices.translate(-0.01f + 0f, 1.6f, ShipTemplate.CZ - ws / 2f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90f));
		matrices.scale(ss, -ss, ss);
		draw(tr, name, matrices, consumers, light);
		matrices.pop();
	}

	private static void draw(TextRenderer tr, String s, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		tr.draw(s, 0, 0, 0xFFFFFFFF, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
	}

	// ---------------------------------------------------------------- hulpfuncties

	private static void v(Sink vc, float x, float y, float z, float u, float vv, float c, int light) {
		vc.add(x, y, z, u, vv, c, light);
	}

	/** Verzamelt hoekpunten in sjablooncoördinaten; wordt pas opnieuw opgebouwd als lading of luiken veranderen. */
	static final class Sink {
		float[] a = new float[8192];
		int n;

		void add(float x, float y, float z, float u, float v, float c, int light) {
			if (n + 7 > a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
			int ci = Math.min(255, (int) (c * 255));
			a[n++] = x;
			a[n++] = y;
			a[n++] = z;
			a[n++] = u;
			a[n++] = v;
			a[n++] = Float.intBitsToFloat(0xFF000000 | (ci << 16) | (ci << 8) | ci);
			a[n++] = Float.intBitsToFloat(light);
		}

		void emit(VertexConsumer vc, MatrixStack.Entry e) {
			Matrix4f m = e.getPositionMatrix();
			for (int i = 0; i < n; i += 7) {
				vc.vertex(m, a[i], a[i + 1], a[i + 2])
						.color(Float.floatToRawIntBits(a[i + 5]))
						.texture(a[i + 3], a[i + 4])
						.overlay(OverlayTexture.DEFAULT_UV)
						.light(Float.floatToRawIntBits(a[i + 6]))
						.normal(e, 0f, 1f, 0f);
			}
		}
	}

	/** Bewaarde lading/luiken per schip, met de waarden waarvoor ze gemaakt zijn. */
	static final class DynCache {
		final Sink sink = new Sink();
		float hatch = -1;
		final float[] fill = {-1, -1, -1, -1};
		final int[] mat = new int[4];
		int light = -1;
	}

	private static void quadUp(Sink vc, Sprite s, float x0, float y, float z0, float x1, float z1, float c, int light) {
		float u0 = s.getFrameU(0), u1 = s.getFrameU(1), v0 = s.getFrameV(0), v1 = s.getFrameV(1);
		v(vc, x0, y, z0, u0, v0, c, light);
		v(vc, x0, y, z1, u0, v1, c, light);
		v(vc, x1, y, z1, u1, v1, c, light);
		v(vc, x1, y, z0, u1, v0, c, light);
	}

	private static void quadDown(Sink vc, Sprite s, float x0, float y, float z0, float x1, float z1, float c, int light) {
		float u0 = s.getFrameU(0), u1 = s.getFrameU(1), v0 = s.getFrameV(0), v1 = s.getFrameV(1);
		v(vc, x0, y, z0, u0, v0, c, light);
		v(vc, x1, y, z0, u1, v0, c, light);
		v(vc, x1, y, z1, u1, v1, c, light);
		v(vc, x0, y, z1, u0, v1, c, light);
	}

	/** Verticaal vlak loodrecht op x, opgedeeld per blokhoogte zodat de textuur niet uitrekt. */
	private static void sideX(Sink vc, Sprite s, float x, float z0, float z1, float yLow, float yHigh,
							  boolean positive, float base, int light) {
		if (yHigh - yLow < 0.01f) return;
		float u0 = s.getFrameU(0), u1 = s.getFrameU(1);
		for (float k = (float) Math.floor(yLow - base) + base; k < yHigh; k += 1f) {
			float a = Math.max(yLow, k), b = Math.min(yHigh, k + 1);
			if (b - a < 0.005f) continue;
			float va = s.getFrameV(1 - (a - k)), vb = s.getFrameV(1 - (b - k));
			if (positive) {
				v(vc, x, a, z0, u0, va, 0.6f, light);
				v(vc, x, b, z0, u0, vb, 0.6f, light);
				v(vc, x, b, z1, u1, vb, 0.6f, light);
				v(vc, x, a, z1, u1, va, 0.6f, light);
			} else {
				v(vc, x, a, z0, u0, va, 0.6f, light);
				v(vc, x, a, z1, u1, va, 0.6f, light);
				v(vc, x, b, z1, u1, vb, 0.6f, light);
				v(vc, x, b, z0, u0, vb, 0.6f, light);
			}
		}
	}

	private static void sideZ(Sink vc, Sprite s, float z, float x0, float x1, float yLow, float yHigh,
							  boolean positive, float base, int light) {
		if (yHigh - yLow < 0.01f) return;
		float u0 = s.getFrameU(0), u1 = s.getFrameU(1);
		for (float k = (float) Math.floor(yLow - base) + base; k < yHigh; k += 1f) {
			float a = Math.max(yLow, k), b = Math.min(yHigh, k + 1);
			if (b - a < 0.005f) continue;
			float va = s.getFrameV(1 - (a - k)), vb = s.getFrameV(1 - (b - k));
			if (positive) {
				v(vc, x0, a, z, u0, va, 0.8f, light);
				v(vc, x1, a, z, u1, va, 0.8f, light);
				v(vc, x1, b, z, u1, vb, 0.8f, light);
				v(vc, x0, b, z, u0, vb, 0.8f, light);
			} else {
				v(vc, x0, a, z, u0, va, 0.8f, light);
				v(vc, x0, b, z, u0, vb, 0.8f, light);
				v(vc, x1, b, z, u1, vb, 0.8f, light);
				v(vc, x1, a, z, u1, va, 0.8f, light);
			}
		}
	}

	/** Doos met tegels van 1 blok, zodat texturen niet uitrekken. */
	private static void box(Sink vc, Sprite s, float x0, float y0, float z0, float x1, float y1, float z1, int light, float tint) {
		for (float x = x0; x < x1 - 1e-4f; x += 1f) {
			float xe = Math.min(x1, x + 1);
			for (float z = z0; z < z1 - 1e-4f; z += 1f) {
				float ze = Math.min(z1, z + 1);
				quadUp(vc, s, x, y1, z, xe, ze, tint, light);
			}
		}
		for (float z = z0; z < z1 - 1e-4f; z += 1f) {
			float ze = Math.min(z1, z + 1);
			sideXTile(vc, s, x0, z, ze, y0, y1, false, light, tint);
			sideXTile(vc, s, x1, z, ze, y0, y1, true, light, tint);
		}
		for (float x = x0; x < x1 - 1e-4f; x += 1f) {
			float xe = Math.min(x1, x + 1);
			sideZTile(vc, s, z0, x, xe, y0, y1, false, light, tint);
			sideZTile(vc, s, z1, x, xe, y0, y1, true, light, tint);
		}
	}

	private static void sideXTile(Sink vc, Sprite s, float x, float z0, float z1, float y0, float y1, boolean pos, int light, float tint) {
		float u0 = s.getFrameU(0), u1 = s.getFrameU(Math.min(1f, z1 - z0));
		for (float y = y0; y < y1 - 1e-4f; y += 1f) {
			float ye = Math.min(y1, y + 1);
			float v0 = s.getFrameV(0), v1 = s.getFrameV(Math.min(1f, ye - y));
			float c = 0.6f * tint;
			if (pos) {
				v(vc, x, y, z0, u0, v1, c, light);
				v(vc, x, ye, z0, u0, v0, c, light);
				v(vc, x, ye, z1, u1, v0, c, light);
				v(vc, x, y, z1, u1, v1, c, light);
			} else {
				v(vc, x, y, z0, u0, v1, c, light);
				v(vc, x, y, z1, u1, v1, c, light);
				v(vc, x, ye, z1, u1, v0, c, light);
				v(vc, x, ye, z0, u0, v0, c, light);
			}
		}
	}

	private static void sideZTile(Sink vc, Sprite s, float z, float x0, float x1, float y0, float y1, boolean pos, int light, float tint) {
		float u0 = s.getFrameU(0), u1 = s.getFrameU(Math.min(1f, x1 - x0));
		for (float y = y0; y < y1 - 1e-4f; y += 1f) {
			float ye = Math.min(y1, y + 1);
			float v0 = s.getFrameV(0), v1 = s.getFrameV(Math.min(1f, ye - y));
			float c = 0.8f * tint;
			if (pos) {
				v(vc, x0, y, z, u0, v1, c, light);
				v(vc, x1, y, z, u1, v1, c, light);
				v(vc, x1, ye, z, u1, v0, c, light);
				v(vc, x0, ye, z, u0, v0, c, light);
			} else {
				v(vc, x0, y, z, u0, v1, c, light);
				v(vc, x0, ye, z, u0, v0, c, light);
				v(vc, x1, ye, z, u1, v0, c, light);
				v(vc, x1, y, z, u1, v1, c, light);
			}
		}
	}
}
