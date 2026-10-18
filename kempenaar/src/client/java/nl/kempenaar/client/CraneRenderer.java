package nl.kempenaar.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import nl.kempenaar.block.CraneBlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Grote havenkraan: vakwerktoren van 22 m, draaiende giek van 44 m met tegengiek en contragewichten,
 * trekstangen naar de kop, cabine, loopkat met kabels en een grijper die echt opengaat.
 * De vaste delen worden één keer opgebouwd en daarna alleen nog gedraaid (snel).
 */
public class CraneRenderer implements BlockEntityRenderer<CraneBlockEntity> {
	private static final float H = (float) CraneBlockEntity.TOWER;
	private static final float JIB = 44f, CJIB = 15f;

	private Geo tower, slew;
	private Sprite yellowRef;
	private Sprite yellow, white, gray, dark, black, iron, concrete, lamp, crate;

	public CraneRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	@Override
	public boolean rendersOutsideBoundingBox(CraneBlockEntity be) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 256;
	}

	private static Sprite sprite(BlockState s) {
		return MinecraftClient.getInstance().getBlockRenderManager().getModels().getModelParticleSprite(s);
	}

	private void ensureBuilt() {
		Sprite y = sprite(Blocks.YELLOW_CONCRETE.getDefaultState());
		if (tower != null && y == yellowRef) return;
		yellowRef = y;
		yellow = y;
		white = sprite(Blocks.WHITE_CONCRETE.getDefaultState());
		gray = sprite(Blocks.GRAY_CONCRETE.getDefaultState());
		dark = sprite(Blocks.POLISHED_BLACKSTONE.getDefaultState());
		black = sprite(Blocks.BLACK_CONCRETE.getDefaultState());
		iron = sprite(Blocks.IRON_BLOCK.getDefaultState());
		concrete = sprite(Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
		lamp = sprite(Blocks.REDSTONE_LAMP.getDefaultState().with(net.minecraft.state.property.Properties.LIT, true));
		crate = sprite(Blocks.BARREL.getDefaultState());
		buildTower();
		buildSlew();
	}

	// ------------------------------------------------------------------ vaste delen

	private void buildTower() {
		Geo g = new Geo();
		// voet: kruisbalken en ballastblokken
		g.box(gray, -1.6f, 0, 0.2f, 2.6f, 0.35f, 0.8f, 0.9f);
		g.box(gray, 0.2f, 0, -1.6f, 0.8f, 0.35f, 2.6f, 0.9f);
		for (float[] c : new float[][]{{-1.5f, -1.5f}, {1.5f, -1.5f}, {-1.5f, 1.5f}, {1.5f, 1.5f}})
			g.box(concrete, c[0], 0, c[1], c[0] + 1f, 0.9f, c[1] + 1f, 1f);
		// vakwerktoren 1,6 x 1,6
		float a = -0.3f, b = 1.3f, p = 0.16f;
		float[][] corners = {{a, a}, {b - p, a}, {a, b - p}, {b - p, b - p}};
		for (float[] c : corners) g.box(yellow, c[0], 1, c[1], c[0] + p, H, c[1] + p, 1f);
		float[][] faces = {{a, a, b, a}, {b, a, b, b}, {b, b, a, b}, {a, b, a, a}};
		for (float y = 1.2f; y < H - 1f; y += 2f) {
			for (float[] f : faces) {
				g.bar(yellow, f[0], y, f[1], f[2], y, f[3], 0.08f);
				g.bar(yellow, f[0], y, f[1], f[2], y + 2f, f[3], 0.07f);
			}
		}
		// ladder met rustbordessen
		g.box(dark, 0.42f, 1, 0.42f, 0.58f, H, 0.58f, 1f);
		for (float y : new float[]{8f, 15f}) g.box(gray, -0.25f, y, -0.25f, 1.25f, y + 0.08f, 0.2f, 0.9f);
		tower = g;
	}

	private void buildSlew() {
		Geo g = new Geo();
		// draaikrans, plateau en cabine
		g.box(gray, -1.0f, H, -1.0f, 1.0f, H + 0.5f, 1.0f, 0.9f);
		g.box(gray, -2.2f, H + 0.5f, -1.1f, 2.2f, H + 0.62f, 1.1f, 0.85f);
		g.box(white, 0.3f, H + 0.62f, 1.05f, 1.9f, H + 2.0f, 2.15f, 1f);
		g.box(yellow, 0.25f, H + 2.0f, 1.0f, 1.95f, H + 2.1f, 2.2f, 1f);
		g.box(black, 1.9f, H + 1.1f, 1.12f, 1.93f, H + 1.85f, 2.08f, 1f);   // voorruit (dicht, i.v.m. shaders)
		g.box(black, 0.4f, H + 1.1f, 2.15f, 1.8f, H + 1.85f, 2.18f, 1f);     // zijruit
		// kop (cat-head) met vier stijlen
		float top = H + 8f;
		for (float[] c : new float[][]{{-0.6f, -0.6f}, {0.6f, -0.6f}, {-0.6f, 0.6f}, {0.6f, 0.6f}})
			g.bar(yellow, c[0], H + 0.62f, c[1], 0, top, 0, 0.18f);
		g.box(yellow, -0.25f, top - 0.2f, -0.25f, 0.25f, top + 0.25f, 0.25f, 1f);
		g.box(lamp, -0.12f, top + 0.25f, -0.12f, 0.12f, top + 0.45f, 0.12f, 1f);
		// giek: driehoekig vakwerk
		float yb = H + 0.72f, yt = H + 2.39f;
		g.box(yellow, 0.8f, H + 0.62f, -0.65f, JIB, H + 0.82f, -0.45f, 1f);
		g.box(yellow, 0.8f, H + 0.62f, 0.45f, JIB, H + 0.82f, 0.65f, 1f);
		g.box(yellow, 0.8f, yt - 0.09f, -0.09f, JIB - 1.2f, yt + 0.09f, 0.09f, 1f);
		for (float x = 0.8f; x < JIB - 2.1f; x += 2f) {
			for (float zs : new float[]{-0.55f, 0.55f}) {
				g.bar(yellow, x, yb, zs, x + 1f, yt, 0, 0.07f);
				g.bar(yellow, x + 1f, yt, 0, x + 2f, yb, zs, 0.07f);
			}
			g.bar(yellow, x, yb, -0.55f, x, yb, 0.55f, 0.07f);
		}
		g.bar(yellow, JIB - 1.2f, yt, 0, JIB, yb, -0.55f, 0.08f);
		g.bar(yellow, JIB - 1.2f, yt, 0, JIB, yb, 0.55f, 0.08f);
		g.box(lamp, JIB - 0.15f, yb + 0.1f, -0.12f, JIB + 0.1f, yb + 0.35f, 0.12f, 1f);
		// tegengiek met contragewichten en lier
		g.box(yellow, -CJIB, H + 0.62f, -0.8f, -0.8f, H + 0.9f, -0.6f, 1f);
		g.box(yellow, -CJIB, H + 0.62f, 0.6f, -0.8f, H + 0.9f, 0.8f, 1f);
		for (float x = -CJIB; x < -1f; x += 2f) g.bar(yellow, x, H + 0.76f, -0.7f, x, H + 0.76f, 0.7f, 0.08f);
		g.box(gray, -CJIB + 0.5f, H + 0.86f, -0.7f, -1.5f, H + 0.92f, 0.7f, 0.8f);         // looppad
		for (int i = 0; i < 3; i++) {
			float x0 = -CJIB + 0.4f + i * 1.3f;
			g.box(concrete, x0, H - 1.8f, -0.95f, x0 + 1.2f, H + 0.62f, 0.95f, 1f);
		}
		g.box(dark, -9.6f, H + 0.92f, -0.6f, -7.4f, H + 1.9f, 0.6f, 1f);
		g.box(iron, -9.2f, H + 1.0f, -0.65f, -7.8f, H + 1.7f, 0.65f, 1f);
		g.box(lamp, -CJIB - 0.1f, H + 0.9f, -0.12f, -CJIB + 0.15f, H + 1.15f, 0.12f, 1f);
		// trekstangen van de kop naar giek en tegengiek
		g.bar(iron, 0, top, 0, 16f, yt, 0, 0.06f);
		g.bar(iron, 0, top, 0, 34f, yt, 0, 0.06f);
		g.bar(iron, 0, top, -0.05f, -CJIB + 0.5f, H + 0.9f, -0.6f, 0.06f);
		g.bar(iron, 0, top, 0.05f, -CJIB + 0.5f, H + 0.9f, 0.6f, 0.06f);
		slew = g;
	}

	// ------------------------------------------------------------------ tekenen

	@Override
	public void render(CraneBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light, int overlay) {
		World w = be.getWorld();
		if (w == null) return;
		ensureBuilt();
		int lLow = WorldRenderer.getLightmapCoordinates(w, be.getPos().up());
		int lHigh = WorldRenderer.getLightmapCoordinates(w, be.getPos().up((int) H + 2));
		double time = w.getTime() + tickDelta;
		CraneBlockEntity.Pose p = be.pose(time);
		VertexConsumer vc = v.getBuffer(TexturedRenderLayers.getEntityCutout());
		tower.emit(vc, m.peek(), lLow);

		m.push();
		m.translate(0.5, 0, 0.5);
		m.multiply(RotationAxis.POSITIVE_Y.rotation(-p.yaw()));
		slew.emit(vc, m.peek(), lHigh);

		Geo d = new Geo();
		float r = p.radius(), hook = p.hookY();
		// loopkat met schijven
		d.box(iron, r - 0.65f, H + 0.25f, -0.6f, r + 0.65f, H + 0.62f, 0.6f, 1f);
		d.box(dark, r - 0.3f, H + 0.05f, -0.35f, r + 0.3f, H + 0.25f, 0.35f, 1f);
		// kabels
		float grabTop = hook + 1.65f;
		for (float zc : new float[]{-0.18f, 0.18f})
			d.box(black, r - 0.03f, grabTop, zc - 0.03f, r + 0.03f, H + 0.05f, zc + 0.03f, 1f);
		// grijper: kop + twee schalen die om een scharnier openklappen
		d.box(dark, r - 0.45f, hook + 1.2f, -0.45f, r + 0.45f, hook + 1.65f, 0.45f, 1f);
		float open = be.grabOpen(time) * 0.8f; // tot ~45 graden
		Matrix4f left = new Matrix4f().translate(r, hook + 1.2f, 0).rotateZ(-open);
		Matrix4f right = new Matrix4f().translate(r, hook + 1.2f, 0).rotateZ(open);
		d.box(left, yellow, -0.85f, -1.2f, -0.75f, 0f, 0f, 0.75f, 1f);
		d.box(right, yellow, 0f, -1.2f, -0.75f, 0.85f, 0f, 0.75f, 0.95f);
		if (be.carryMat != 0 && open < 0.25f) {
			Sprite ms = be.carryMat > 0 ? sprite(Block.getStateFromRawId(be.carryMat - 1)) : crate;
			d.box(ms, r - 0.72f, hook + 0.9f, -0.62f, r + 0.72f, hook + 1.22f, 0.62f, 1f);
		}
		int lHook = WorldRenderer.getLightmapCoordinates(w, be.getPos().up(Math.max(1, (int) hook + 1)));
		d.emit(vc, m.peek(), lHook);
		m.pop();
	}

	// ------------------------------------------------------------------ geometrie

	/** Een lijst vlakken (x y z u v kleur nx ny nz), opgebouwd in een eigen assenstelsel. */
	static final class Geo {
		private static final Matrix4f IDENTITY = new Matrix4f();
		float[] a = new float[4096];
		int n;

		private void put(Matrix4f xf, float x, float y, float z, float u, float v, int color, Vector3f nrm) {
			if (n + 9 > a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
			Vector3f pt = xf.transformPosition(x, y, z, new Vector3f());
			a[n++] = pt.x;
			a[n++] = pt.y;
			a[n++] = pt.z;
			a[n++] = u;
			a[n++] = v;
			a[n++] = Float.intBitsToFloat(color);
			a[n++] = nrm.x;
			a[n++] = nrm.y;
			a[n++] = nrm.z;
		}

		private static int grey(float c) {
			int ci = Math.min(255, Math.max(0, (int) (c * 255)));
			return 0xFF000000 | (ci << 16) | (ci << 8) | ci;
		}

		void box(Sprite s, float x0, float y0, float z0, float x1, float y1, float z1, float tint) {
			box(IDENTITY, s, x0, y0, z0, x1, y1, z1, tint);
		}

		void box(Matrix4f xf, Sprite s, float x0, float y0, float z0, float x1, float y1, float z1, float tint) {
			float u0 = s.getMinU(), u1 = s.getMaxU(), v0 = s.getMinV(), v1 = s.getMaxV();
			Matrix3f nm = new Matrix3f(xf);
			// boven, onder, noord (-z), zuid (+z), west (-x), oost (+x)
			face(xf, nm, 0, 1, 0, grey(tint), u0, v0, u1, v1, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
			face(xf, nm, 0, -1, 0, grey(tint * 0.5f), u0, v0, u1, v1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
			face(xf, nm, 0, 0, -1, grey(tint * 0.8f), u0, v0, u1, v1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
			face(xf, nm, 0, 0, 1, grey(tint * 0.8f), u0, v0, u1, v1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
			face(xf, nm, -1, 0, 0, grey(tint * 0.6f), u0, v0, u1, v1, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
			face(xf, nm, 1, 0, 0, grey(tint * 0.6f), u0, v0, u1, v1, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
		}

		private void face(Matrix4f xf, Matrix3f nm, float nx, float ny, float nz, int c, float u0, float v0, float u1, float v1,
						  float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
			Vector3f nrm = nm.transform(new Vector3f(nx, ny, nz)).normalize();
			put(xf, ax, ay, az, u0, v0, c, nrm);
			put(xf, bx, by, bz, u0, v1, c, nrm);
			put(xf, cx, cy, cz, u1, v1, c, nrm);
			put(xf, dx, dy, dz, u1, v0, c, nrm);
		}

		/** Dunne staaf van punt A naar punt B. */
		void bar(Sprite s, float xa, float ya, float za, float xb, float yb, float zb, float t) {
			Vector3f d = new Vector3f(xb - xa, yb - ya, zb - za);
			float len = d.length();
			if (len < 1e-4f) return;
			Matrix4f xf = new Matrix4f().translate(xa, ya, za).rotate(new Quaternionf().rotationTo(new Vector3f(1, 0, 0), d.normalize()));
			box(xf, s, 0, -t / 2, -t / 2, len, t / 2, t / 2, 1f);
		}

		void emit(VertexConsumer vc, MatrixStack.Entry e, int light) {
			Matrix4f m = e.getPositionMatrix();
			for (int i = 0; i < n; i += 9) {
				vc.vertex(m, a[i], a[i + 1], a[i + 2])
						.color(Float.floatToRawIntBits(a[i + 5]))
						.texture(a[i + 3], a[i + 4])
						.overlay(OverlayTexture.DEFAULT_UV)
						.light(light)
						.normal(e, a[i + 6], a[i + 7], a[i + 8]);
			}
		}
	}
}
