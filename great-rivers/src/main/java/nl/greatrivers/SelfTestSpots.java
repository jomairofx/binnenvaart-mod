package nl.greatrivers;

/** Test helper, shared by the server and client test. */
public final class SelfTestSpots {
	/** A spot on this river that is far from the other kind (so we measure this river alone). */
	public static int[] clearSpot(RiverField.Kind kind, RiverField.Kind other, net.minecraft.server.world.ServerWorld w) {
		var gen = w.getChunkManager().getChunkGenerator();
		var noise = w.getChunkManager().getNoiseConfig().getMultiNoiseSampler();
		for (int i = 0; i < 40; i++) {
			int sx = (int) (Math.cos(i * 2.4) * 1500 * (1 + i / 4)), sz = (int) (Math.sin(i * 2.4) * 1500 * (1 + i / 4));
			int[] c = GreatRivers.nearest(kind, sx, sz, 20000);
			if (c == null || other.distance(c[0], c[1]) <= other.halfWidth(c[0], c[1]) + 400) continue;
			// on land, not where the river runs through a sea
			boolean sea = false;
			for (int k = -2; k <= 2 && !sea; k++)
				sea = gen.getBiomeSource().getBiome((c[0] >> 2) + k * 40, 15, (c[1] >> 2) + k * 40, noise).isIn(net.minecraft.registry.tag.BiomeTags.IS_OCEAN);
			if (!sea) return c;
		}
		return GreatRivers.nearest(kind, 0, 0, 30000);
	}

}
