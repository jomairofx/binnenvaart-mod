package com.modernbulk.client;

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
import com.modernbulk.ship.ShipEntity;
import com.modernbulk.ship.ShipTemplate;
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
		// eigen kopie van de matrixstapel: als er iets misgaat blijft die van Minecraft heel
		MatrixStack own = new MatrixStack();
		own.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
		own.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
		try {
			renderSafe(ship, yaw, tickDelta, own, consumers, light);
		} catch (RuntimeException | StackOverflowError ex) {
			ShipEntity.reportError("render", ex);
		}
	}

	private void renderSafe(ShipEntity ship, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		ShipMesh[] meshes = ship.renderCache instanceof ShipMesh[] mm ? mm : null;
		boolean lightsOn = ship.lightsOn();
		if (meshes == null || meshes[0].version != ship.structure.version || meshes[0].lights != lightsOn) {
			meshes = ship.template.liftRegion != null
					? new ShipMesh[]{ShipMesh.build(ship.structure, 1, lightsOn), ShipMesh.build(ship.structure, 2, lightsOn)}
					: new ShipMesh[]{ShipMesh.build(ship.structure, 0, lightsOn)};
			ship.renderCache = meshes;
		}
		ShipMesh mesh = meshes[0];
		double dy = MathHelper.wrapDegrees(Math.toDegrees(ship.shipYaw - ship.prevShipYaw));
		double y = ship.prevShipYaw + Math.toRadians(dy) * tickDelta;

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float) -y));
		matrices.translate(-ShipTemplate.CX, 0, -ShipTemplate.CZ);
		Vec3d cam = this.dispatcher.camera.getPos();
		Vec3d shipPos = ship.getLerpedPos(tickDelta);
		boolean shadow = IrisCompat.isShadowPass();
		mesh.render(matrices, consumers, lastFrustum, shipPos, y, cam, shadow ? 2 : 0);
		if (meshes.length > 1) {
			float lift = ship.liftLerp(tickDelta);
			matrices.push();
			matrices.translate(0, lift, 0);
			meshes[1].render(matrices, consumers, lastFrustum, shipPos.add(0, lift, 0), y, cam, shadow ? 2 : 0);
			matrices.pop();
			if (lift > 0.01f) {
				int[] r = ship.template.liftRegion, c = ship.template.liftColumn;
				Sink ps = new Sink();
				box(ps, sprites(Blocks.GRAY_CONCRETE.getDefaultState())[0], c[0] + 0.1f, r[1], c[1] + 0.1f, c[2] + 0.9f, r[1] + lift, c[3] + 0.9f, light, 0.9f);
				box(ps, sprites(Blocks.IRON_BLOCK.getDefaultState())[0], c[0] + 0.35f, r[1], c[1] + 0.35f, c[2] + 0.65f, r[1] + lift + 0.01f, c[3] + 0.65f, light, 1f);
				ps.emit(consumers.getBuffer(TexturedRenderLayers.getEntityCutout()), matrices.peek());
			}
		}

		DynCache dc = caches.computeIfAbsent(ship, k -> new DynCache());
		float hatch = MathHelper.lerp(tickDelta, ship.prevHatchProgress, ship.hatchProgress);
		boolean dirty = Math.abs(dc.hatch - hatch) > 1e-4f || dc.light != light;
		float[] fills = new float[ShipEntity.HOLD_COUNT];
		for (int h = 0; h < ShipEntity.HOLD_COUNT; h++) {
			fills[h] = MathHelper.lerp(tickDelta, ship.prevDisplayFill[h], ship.displayFill[h]);
			if (Math.abs(dc.fill[h] - fills[h]) > 1e-4f || dc.mat[h] != ship.material(h)) dirty = true;
		}
		if (dirty) {
			dc.sink.n = 0;
			dc.hatch = hatch;
			dc.light = light;
			for (int h = 0; h < ShipEntity.HOLD_COUNT; h++) {
				dc.fill[h] = fills[h];
				dc.mat[h] = ship.material(h);
			}
			renderCargo(ship, fills, hatch, dc.sink, light);
			renderCovers(hatch, dc.sink, light);
			renderBeds(ship, dc.sink, light);
		}
		// in de schaduwpass alleen dichtbij: scheelt veel werk met shaders
		if (!shadow || cam.squaredDistanceTo(shipPos) < 48 * 48)
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

	private void renderCargo(ShipEntity ship, float[] fills, float hatch, Sink vc, int light) {
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

	private static float pileHeight(int x, int z, int a, int b, float f, float cap) {
		float cx = (a + b + 1) / 2f, halfLen = (b - a + 1) / 2f;
		float dx = Math.abs(x + 0.5f - cx) / halfLen, dz = Math.abs(z + 0.5f - 9.5f) / 6.5f;
		long hsh = (x * 3129871L) ^ (z * 116129781L);
		hsh = hsh * hsh * 42317861L + hsh * 11L;
		float noise = ((hsh >> 16) & 15) / 15f - 0.5f;
		float hgt = f * (8.4f - 3.0f * dx * dx - 2.2f * dz * dz) + noise * 0.3f * f;
		float c = cap;
		// onder de luikenstapel aan de voorkant van elk ruim blijft de stort onder de luiken
		for (int g = 0; g < ShipEntity.COVERS; g++) {
			if (ShipEntity.coverGroupEnd(g) != g) continue;
			float sx = ShipEntity.stackX(g);
			if (x + 1 > sx && x < sx + ShipEntity.CV_LEN) c = Math.min(c, CLOSED_CAP);
		}
		return MathHelper.clamp(hgt, 0.05f, c);
	}

	/** Hoogste stort boven de ruimvloer onder gesloten luiken (vloer -3, luiken op 4,5). */
	private static final float CLOSED_CAP = 7.3f;

	private void renderPile(Sink vc, Sprite[] sp, int a, int b, int z0, int z1, float floor, float coaming,
							float f, float hatch, int light) {
		float cap = MathHelper.lerp(hatch, 8.4f, CLOSED_CAP);
		int w = b - a + 1, d = z1 - z0 + 1;
		float[][] hm = new float[w][d];
		for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) hm[i][k] = pileHeight(a + i, z0 + k, a, b, f, cap);
		// Langs de wanden van het ruim blijft de stort onder de rand van het luikhoofd, de top mag erboven uitsteken.
		float edge = coaming - floor - 0.06f;
		for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) {
			int ring = Math.min(Math.min(i, w - 1 - i), Math.min(k, d - 1 - k));
			if (ring == 0) hm[i][k] = Math.min(hm[i][k], edge);
			else if (ring == 1) hm[i][k] = Math.min(hm[i][k], edge + 0.5f);
		}
		for (int i = 0; i < w; i++) {
			for (int k = 0; k < d; k++) {
				float x = a + i, z = z0 + k, top = floor + hm[i][k];
				quadUp(vc, sp[0], x, top, z, x + 1, z + 1, 1f, light);
				// zijkanten waar de buur lager ligt
				float hxm = i == 0 ? 0 : hm[i - 1][k], hxp = i == w - 1 ? 0 : hm[i + 1][k];
				float hzm = k == 0 ? 0 : hm[i][k - 1], hzp = k == d - 1 ? 0 : hm[i][k + 1];
				sideX(vc, sp[1], x, z, z + 1, floor + hxm, top, false, floor, light);
				sideX(vc, sp[1], x + 1, z, z + 1, floor + hxp, top, true, floor, light);
				sideZ(vc, sp[1], z, x, x + 1, floor + hzm, top, false, floor, light);
				sideZ(vc, sp[1], z + 1, x, x + 1, floor + hzp, top, true, floor, light);
			}
		}
	}

	/** Zeecontainers (20 ft) netjes in rijen en lagen; ze zakken van boven in het ruim als ze geladen worden. */
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
			boxFast(vc, s, x0, y0 + 0.08f, zz, x0 + 6.05f, y0 + 2.51f, zz + 2.44f, light, 1f);
			// ribbels in de zijwanden
			for (float rx = x0 + 0.45f; rx < x0 + 5.7f; rx += 1.3f) {
				boxFast(vc, s, rx, y0 + 0.2f, zz - 0.04f, rx + 0.3f, y0 + 2.4f, zz, light, 0.82f);
				boxFast(vc, s, rx, y0 + 0.2f, zz + 2.44f, rx + 0.3f, y0 + 2.4f, zz + 2.48f, light, 0.82f);
			}
			// hoekstijlen, onder- en bovenregels
			boxFast(vc, dark, x0, y0, zz, x0 + 6.05f, y0 + 0.08f, zz + 2.44f, light, 0.7f);
			boxFast(vc, s, x0, y0 + 2.51f, zz, x0 + 6.05f, y0 + 2.59f, zz + 2.44f, light, 0.75f);
			for (float cx : new float[]{x0, x0 + 5.9f})
				for (float cz : new float[]{zz, zz + 2.29f}) boxFast(vc, dark, cx, y0, cz, cx + 0.15f, y0 + 2.59f, cz + 0.15f, light, 0.8f);
			// deuren aan de kopse kant met vergrendelstangen
			boxFast(vc, s, x0 + 6.05f, y0 + 0.1f, zz + 0.1f, x0 + 6.1f, y0 + 2.49f, zz + 2.34f, light, 0.55f);
			for (float bz : new float[]{zz + 0.5f, zz + 0.9f, zz + 1.5f, zz + 1.9f})
				boxFast(vc, dark, x0 + 6.1f, y0 + 0.2f, bz, x0 + 6.14f, y0 + 2.4f, bz + 0.06f, light, 0.6f);
		}
	}

	// ---------------------------------------------------------------- luiken

	/** Schuifluiken: dun, en per ruim rollen ze naar voren op een lage stapel, zodat je vrij zicht houdt. */
	private void renderCovers(float t, Sink vc, int light) {
		Sprite s = sprites(Blocks.LIGHT_GRAY_CONCRETE.getDefaultState())[0];
		Sprite rim = sprites(Blocks.GRAY_CONCRETE.getDefaultState())[0];
		Sprite wheel = sprites(Blocks.BLACK_CONCRETE.getDefaultState())[0];
		int n = ShipEntity.COVERS;
		float len = ShipEntity.CV_LEN, z0 = ShipEntity.CV_Z0, z1 = ShipEntity.CV_Z1, base = ShipEntity.CV_Y;
		for (int i = 0; i < n; i++) {
			int level = ShipEntity.coverGroupEnd(i) - i;              // the front one stays down, the others ride on top
			float stagger = 0.08f, dur = 0.55f;
			float p = MathHelper.clamp((t - stagger * Math.min(level, 5)) / dur, 0f, 1f);
			p = p * p * (3 - 2 * p);
			float q = 1f - p;                                         // 0 = closed, 1 = on the stack
			float rise = MathHelper.clamp(q / 0.15f, 0f, 1f), roll = MathHelper.clamp((q - 0.15f) / 0.85f, 0f, 1f);
			float x0 = MathHelper.lerp(roll, ShipEntity.CV_X0 + len * i, ShipEntity.stackX(i));
			float y = MathHelper.lerp(rise, base, base + ShipEntity.CV_STACK * level);
			float x1 = x0 + len - 0.04f;
			boxFast(vc, rim, x0 + 0.04f, y, z0, x1, y + 0.14f, z1, light, 0.95f);
			boxFast(vc, s, x0 + 0.04f, y + 0.14f, z0 + ShipEntity.CV_IN1, x1, y + 0.26f, z1 - ShipEntity.CV_IN1, light, 1f);
			boxFast(vc, s, x0 + 0.04f, y + 0.26f, z0 + ShipEntity.CV_IN2, x1, y + ShipEntity.CV_THICK, z1 - ShipEntity.CV_IN2, light, 1f);
			for (float wx : new float[]{x0 + 0.4f, x1 - 0.7f}) {
				boxFast(vc, wheel, wx, y - 0.08f, z0 + 0.1f, wx + 0.3f, y, z0 + 0.32f, light, 0.8f);
				boxFast(vc, wheel, wx, y - 0.08f, z1 - 0.32f, wx + 0.3f, y, z1 - 0.1f, light, 0.8f);
			}
		}
	}

	/** Kast met één vlak per zijde (de textuur wordt uitgerekt): veel goedkoper dan per blok. */
	private static void boxFast(Sink vc, Sprite s, float x0, float y0, float z0, float x1, float y1, float z1, int light, float tint) {
		quadUp(vc, s, x0, y1, z0, x1, z1, tint, light);
		quadDown(vc, s, x0, y0, z0, x1, z1, tint * 0.6f, light);
		sideXTile(vc, s, x0, z0, z1, y0, y1, false, light, tint);
		sideXTile(vc, s, x1, z0, z1, y0, y1, true, light, tint);
		sideZTile(vc, s, z0, x0, x1, y0, y1, false, light, tint);
		sideZTile(vc, s, z1, x0, x1, y0, y1, true, light, tint);
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
		float scale = 0.14f;
		float w = tr.getWidth(name) * scale;
		// stuurboord (z+)
		matrices.push();
		matrices.translate(160.0f, 1.95f, 19.01f);
		matrices.scale(scale, -scale, scale);
		draw(tr, name, matrices, consumers, light);
		matrices.pop();
		// bakboord (z-)
		matrices.push();
		matrices.translate(160.0f + w, 1.95f, -0.01f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
		matrices.scale(scale, -scale, scale);
		draw(tr, name, matrices, consumers, light);
		matrices.pop();
		// stern: smaller, just above the waterline
		float ss = 0.09f;
		float ws = tr.getWidth(name) * ss;
		matrices.push();
		matrices.translate(-0.01f, 1.97f, ShipTemplate.CZ - ws / 2f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90f));
		matrices.scale(ss, -ss, ss);
		draw(tr, name, matrices, consumers, light);
		matrices.pop();
	}

	private static void draw(TextRenderer tr, String s, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		tr.draw(s, 0, 0, 0xFF1E2A5A, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
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
		final float[] fill = filled(ShipEntity.HOLD_COUNT);

		private static float[] filled(int n) {
			float[] a = new float[n];
			java.util.Arrays.fill(a, -1f);
			return a;
		}
		final int[] mat = new int[ShipEntity.HOLD_COUNT];
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
				quadDown(vc, s, x, y0, z, xe, ze, tint * 0.6f, light);
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
