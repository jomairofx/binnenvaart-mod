package nl.kempenaar.client;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import nl.kempenaar.block.CraneStorageBlockEntity;

import java.util.List;

/** Grote stalen opslagbunker op poten (1,8 x 1,8 x 2), met nummerbord en zichtbare lading bovenin. */
public class BinRenderer implements BlockEntityRenderer<CraneStorageBlockEntity> {
	public BinRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	private static Sprite particle(BlockState s) {
		return MinecraftClient.getInstance().getBlockRenderManager().getModels().getModelParticleSprite(s);
	}

	private static Sprite top(BlockState s) {
		List<BakedQuad> q = MinecraftClient.getInstance().getBlockRenderManager().getModel(s).getQuads(s, Direction.UP, Random.create(42L));
		return q.isEmpty() ? particle(s) : q.get(0).getSprite();
	}

	@Override
	public boolean rendersOutsideBoundingBox(CraneStorageBlockEntity be) {
		return true;
	}

	@Override
	public void render(CraneStorageBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light, int overlay) {
		MatrixStack own = new MatrixStack();
		own.peek().getPositionMatrix().set(m.peek().getPositionMatrix());
		own.peek().getNormalMatrix().set(m.peek().getNormalMatrix());
		try {
			renderSafe(be, tickDelta, own, v, light, overlay);
		} catch (RuntimeException ex) {
			nl.kempenaar.ship.ShipEntity.reportError("crane render", ex);
		}
	}

	private void renderSafe(CraneStorageBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light, int overlay) {
		if (be.getWorld() == null) return;
		BlockState st = be.getCachedState();
		int l = WorldRenderer.getLightmapCoordinates(be.getWorld(), be.getPos().up(2));
		Sprite side = particle(st), grate = top(st);
		Sprite dark = particle(Blocks.POLISHED_BLACKSTONE.getDefaultState());
		Sprite yellow = particle(Blocks.YELLOW_CONCRETE.getDefaultState());
		CraneRenderer.Geo g = new CraneRenderer.Geo();
		// poten en trechter onderaan
		for (float[] c : new float[][]{{-0.38f, -0.38f}, {1.22f, -0.38f}, {-0.38f, 1.22f}, {1.22f, 1.22f}})
			g.box(dark, c[0], 0f, c[1], c[0] + 0.16f, 0.6f, c[1] + 0.16f, 1f);
		g.box(dark, -0.05f, 0.3f, -0.05f, 1.05f, 0.6f, 1.05f, 0.9f);
		g.box(yellow, 0.35f, 0.15f, 0.35f, 0.65f, 0.3f, 0.65f, 1f);                 // uitloop
		// romp met nummer, en de bovenrand
		g.box(side, -0.4f, 0.6f, -0.4f, 1.4f, 2.0f, 1.4f, 1f);
		g.box(grate, -0.4f, 2.0f, -0.4f, 1.4f, 2.02f, 1.4f, 1f);
		g.box(yellow, -0.42f, 1.98f, -0.42f, 1.42f, 2.06f, -0.34f, 1f);
		g.box(yellow, -0.42f, 1.98f, 1.34f, 1.42f, 2.06f, 1.42f, 1f);
		g.box(yellow, -0.42f, 1.98f, -0.34f, -0.34f, 2.06f, 1.34f, 1f);
		g.box(yellow, 1.34f, 1.98f, -0.34f, 1.42f, 2.06f, 1.34f, 1f);
		// lading die bovenuit komt
		if (be.shownFill > 0.01f) {
			Sprite mat = be.shownMat > 0 ? particle(Block.getStateFromRawId(be.shownMat - 1)) : particle(Blocks.BARREL.getDefaultState());
			float h = 0.05f + 0.45f * be.shownFill;
			g.box(mat, -0.3f, 2.02f, -0.3f, 1.3f, 2.02f + h * 0.6f, 1.3f, 1f);
			g.box(mat, 0.0f, 2.02f, 0.0f, 1.0f, 2.02f + h, 1.0f, 1f);
		}
		VertexConsumer vc = v.getBuffer(TexturedRenderLayers.getEntityCutout());
		g.emit(vc, m.peek(), l);
	}
}
