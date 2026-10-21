package nl.greatrivers;

/** Test helper, shared by the server and client test. */
public final class SelfTestSpots {
	/** A spot on this river that is far from the other kind (so we measure this river alone). */
	/** A spot deep in a polder, away from rivers, canals and harbours. */
	public static int[] polderSpot(RiverField f) {
		for (int r = 0; r < 20000; r += 96)
			for (int a = 0; a < 24; a++) {
				int x = (int) (r * Math.cos(a * Math.PI / 12)), z = (int) (r * Math.sin(a * Math.PI / 12));
				if (f.polder(x, z) < 0.99) continue;
				if (f.river.distance(x, z) - f.river.halfWidth(x, z) < 250 || f.great.distance(x, z) - f.great.halfWidth(x, z) < 350) continue;
				if (f.low.canalDistance(x, z) < 80 || f.harbours.siteAt(x, z) != null) continue;
				return new int[]{x, z};
			}
		return null;
	}

	/** A canal crossing a polder: middle of the canal. */
	public static int[] canalSpot(RiverField f) {
		int[] p = polderSpot(f);
		if (p == null) return null;
		for (int s = 0; s < 15000; s++)
			for (int sg = -1; sg <= 1; sg += 2) {
				int x = p[0] + sg * s, z = p[1];
				if (f.low.canalDistance(x, z) < 0.6 && f.polder(x, z) > 0.97) return new int[]{x, z};
				x = p[0];
				z = p[1] + sg * s;
				if (f.low.canalDistance(x, z) < 0.6 && f.polder(x, z) > 0.97) return new int[]{x, z};
			}
		return null;
	}

	/** A Maas-size river with a dike in full polder land: middle of the river and the direction to the dike. */
	public static double[] dikeSpot(RiverField f) {
		for (int r = 0; r < 20000; r += 160)
			for (int a = 0; a < 24; a++) {
				int x = (int) (r * Math.cos(a * Math.PI / 12)), z = (int) (r * Math.sin(a * Math.PI / 12));
				RiverField.Kind k = f.great;
				if (k.distance(x, z) > k.halfWidth(x, z) * 0.3) continue;
				double e = 4, gx = k.distance(x + e, z) - k.distance(x - e, z), gz = k.distance(x, z + e) - k.distance(x, z - e);
				double l = Math.hypot(gx, gz);
				if (l < 1e-9) continue;
				gx /= l;
				gz /= l;
				double hw = k.halfWidth(x, z), fo = Lowlands.foreland(k);
				int cx = (int) Math.round(x + gx * (hw + fo + 17)), cz = (int) Math.round(z + gz * (hw + fo + 17));
				if (f.polder(cx, cz) < 0.99 || f.polder(x, z) < 0.99) continue;
				if (f.low.canalDistance(cx, cz) < 80 || f.harbours.siteAt(cx, cz) != null) continue;
				return new double[]{x, z, gx, gz, hw, fo};
			}
		return null;
	}

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
