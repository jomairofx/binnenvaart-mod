package nl.kempenaar.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import nl.kempenaar.block.CraneBlockEntity;

/** Torenkraan: geel geschilderde toren, draaiende giek met loopkat, kabel en grijper. */
public class CraneRenderer implements BlockEntityRenderer<CraneBlockEntity> {
	private static final BlockState YELLOW = Blocks.YELLOW_CONCRETE.getDefaultState();
	private static final BlockState WHITE = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState DARK = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState BLACK = Blocks.BLACK_CONCRETE.getDefaultState();
	private static final BlockState IRON = Blocks.IRON_BLOCK.getDefaultState();
	private static final BlockState WEIGHT = Blocks.POLISHED_ANDESITE.getDefaultState();
	private static final BlockState CHAIN = Blocks.CHAIN.getDefaultState();
	private static final BlockState GRAB = Blocks.HOPPER.getDefaultState();
	private static final BlockState LAMP = Blocks.REDSTONE_LAMP.getDefaultState().with(net.minecraft.state.property.Properties.LIT, true);
	private static final BlockState CRATE = Blocks.BARREL.getDefaultState();

	private final BlockRenderManager brm;

	public CraneRenderer(BlockEntityRendererFactory.Context ctx) {
		this.brm = ctx.getRenderManager();
	}

	@Override
	public boolean rendersOutsideBoundingBox(CraneBlockEntity be) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 160;
	}

	private void box(MatrixStack m, VertexConsumerProvider v, BlockState s, double x0, double y0, double z0, double sx, double sy, double sz, int light) {
		m.push();
		m.translate(x0, y0, z0);
		m.scale((float) sx, (float) sy, (float) sz);
		brm.renderBlockAsEntity(s, m, v, light, OverlayTexture.DEFAULT_UV);
		m.pop();
	}

	/** Schuine trekstang tussen twee punten in het vlak van de giek (x = langs de giek, y = hoogte). */
	private void bar(MatrixStack m, VertexConsumerProvider v, double xa, double ya, double xb, double yb, double t, int light) {
		double dx = xb - xa, dy = yb - ya, len = Math.sqrt(dx * dx + dy * dy);
		m.push();
		m.translate(xa, ya, 0);
		m.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
		box(m, v, IRON, 0, -t / 2, -t / 2, len, t, t, light);
		m.pop();
	}

	@Override
	public void render(CraneBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light, int overlay) {
		World w = be.getWorld();
		if (w == null) return;
		int lLow = WorldRenderer.getLightmapCoordinates(w, be.getPos().up());
		int lHigh = WorldRenderer.getLightmapCoordinates(w, be.getPos().up(9));
		CraneBlockEntity.Pose p = be.pose(w.getTime() + tickDelta);

		// toren: vier hoekstijlen met regelwerk, geel
		double half = 0.34, post = 0.14;
		for (int i = 0; i < 4; i++) {
			double px = (i % 2 == 0 ? 0.5 - half : 0.5 + half - post), pz = (i < 2 ? 0.5 - half : 0.5 + half - post);
			box(m, v, YELLOW, px, 1, pz, post, 7, post, lLow);
		}
		for (int k = 0; k < 7; k++) {
			double y = 1.4 + k;
			box(m, v, YELLOW, 0.5 - half, y, 0.5 - half, half * 2, 0.08, 0.08, lLow);
			box(m, v, YELLOW, 0.5 - half, y + 0.5, 0.5 + half - 0.08, half * 2, 0.08, 0.08, lLow);
			box(m, v, YELLOW, 0.5 - half, y + 0.25, 0.5 - half, 0.08, 0.08, half * 2, lLow);
			box(m, v, YELLOW, 0.5 + half - 0.08, y + 0.75, 0.5 - half, 0.08, 0.08, half * 2, lLow);
		}
		// ladder in de toren
		box(m, v, DARK, 0.45, 1, 0.45, 0.1, 7, 0.1, lLow);

		m.push();
		m.translate(0.5, 0, 0.5);
		m.multiply(RotationAxis.POSITIVE_Y.rotation(-p.yaw()));
		// draaikrans en cabine
		box(m, v, DARK, -0.55, 8.0, -0.55, 1.1, 0.3, 1.1, lHigh);
		box(m, v, WHITE, -0.35, 8.3, 0.25, 1.0, 0.95, 0.85, lHigh);
		box(m, v, BLACK, 0.6, 8.65, 0.32, 0.06, 0.45, 0.71, lHigh);        // voorruit (dicht, i.v.m. shaders)
		box(m, v, BLACK, -0.3, 8.65, 1.1, 0.85, 0.45, 0.02, lHigh);       // zijruit
		// giek met onder- en bovenrand
		double jib = 11.5;
		box(m, v, YELLOW, -0.2, 9.1, -0.25, jib + 0.2, 0.14, 0.12, lHigh);
		box(m, v, YELLOW, -0.2, 9.1, 0.13, jib + 0.2, 0.14, 0.12, lHigh);
		box(m, v, YELLOW, -0.2, 9.55, -0.06, jib + 0.2, 0.12, 0.12, lHigh);
		for (int k = 0; k < 12; k++) {
			double x = k;
			bar(m, v, x, 9.18, x + 0.5, 9.6, 0.06, lHigh);
			bar(m, v, x + 0.5, 9.6, x + 1, 9.18, 0.06, lHigh);
		}
		box(m, v, LAMP, jib - 0.12, 9.67, -0.1, 0.2, 0.2, 0.2, lHigh);
		// tegengiek en contragewicht
		box(m, v, YELLOW, -3.8, 9.1, -0.3, 3.6, 0.3, 0.6, lHigh);
		box(m, v, WEIGHT, -3.7, 8.35, -0.5, 1.3, 0.75, 1.0, lHigh);
		// top met trekstangen
		box(m, v, YELLOW, -0.15, 9.4, -0.15, 0.3, 2.2, 0.3, lHigh);
		bar(m, v, 0, 11.5, 7.5, 9.66, 0.07, lHigh);
		bar(m, v, 0, 11.5, -3.6, 9.4, 0.07, lHigh);
		// loopkat
		double r = p.radius();
		box(m, v, IRON, r - 0.4, 8.9, -0.32, 0.8, 0.22, 0.64, lHigh);
		// kabels (in stukjes van een blok, zodat de ketting niet uitrekt)
		double top = 8.9, hook = p.hookY(), grabTop = hook + 0.85;
		for (double zc : new double[]{-0.18, 0.18}) {
			for (double y = grabTop; y < top - 1e-3; y += 1) {
				double seg = Math.min(1, top - y);
				box(m, v, CHAIN, r - 0.5 + 0.0, y, zc - 0.5, 1, seg, 1, lHigh);
			}
		}
		// grijper met lading
		int lHook = WorldRenderer.getLightmapCoordinates(w, be.getPos().up(Math.max(1, (int) hook + 1)));
		box(m, v, GRAB, r - 0.45, hook, -0.45, 0.9, 0.9, 0.9, lHook);
		if (be.carryMat > 0) {
			BlockState mat = Block.getStateFromRawId(be.carryMat - 1);
			box(m, v, mat, r - 0.33, hook + 0.6, -0.33, 0.66, 0.38, 0.66, lHook);
		} else if (be.carryMat < 0) {
			box(m, v, CRATE, r - 0.3, hook + 0.58, -0.3, 0.6, 0.55, 0.6, lHook);
		}
		m.pop();
	}
}
