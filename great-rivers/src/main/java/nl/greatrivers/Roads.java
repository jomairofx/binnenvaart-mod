package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provincial roads ("N-wegen") between neighbouring villages and towns on the same side of the river, and from the
 * locks to the nearest village: a two-lane asphalt road with a row of trees along it and a separate red cycle path,
 * the most Dutch of all roads. They follow the land (over the dikes, over culverts across the ditches).
 */
public final class Roads {
	public static final double HALF = 3.0;        // half width of the asphalt
	public static final double OUTER = 7.5;       // with verge, trees and cycle path

	/** A straight stretch of road, with the side of its cycle path (+1 / -1). */
	public record Seg(double x0, double z0, double x1, double z1, int bike) {
		/** {signed distance across, distance along} or null when beside the ends. */
		double[] at(double x, double z) {
			double dx = x1 - x0, dz = z1 - z0, l2 = dx * dx + dz * dz;
			double t = ((x - x0) * dx + (z - z0) * dz) / l2;
			if (t < 0 || t > 1) return null;
			double l = Math.sqrt(l2);
			return new double[]{((x - x0) * dz - (z - z0) * dx) / l, t * l};
		}
	}

	private final RiverField f;
	private final ConcurrentHashMap<Long, List<Seg>> segs = new ConcurrentHashMap<>();

	Roads(RiverField f) {
		this.f = f;
	}

	/** The roads that start at the town of this cell (to its east and south neighbours). */
	public List<Seg> from(int cellX, int cellZ) {
		long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
		return segs.computeIfAbsent(key, k -> make(cellX, cellZ));
	}

	private List<Seg> make(int cellX, int cellZ) {
		Villages.Town a = f.villages.town(cellX, cellZ);
		if (a == null) return List.of();
		List<Seg> out = new ArrayList<>(2);
		for (Villages.Town b : f.villages.neighbours(cellX, cellZ)) {
			Seg s = link(a, b);
			if (s != null) out.add(s);
		}
		return out;
	}

	private Seg link(Villages.Town a, Villages.Town b) {
		double d = Math.hypot(b.cx() - a.cx(), b.cz() - a.cz());
		if (d > 2300 || d < a.radius() + b.radius() + 60) return null;
		double ux = (b.cx() - a.cx()) / d, uz = (b.cz() - a.cz()) / d;
		double x0 = a.cx() + ux * (a.radius() + 3), z0 = a.cz() + uz * (a.radius() + 3);
		double x1 = b.cx() - ux * (b.radius() + 3), z1 = b.cz() - uz * (b.radius() + 3);
		double len = Math.hypot(x1 - x0, z1 - z0);
		RiverField.Kind k = f.great;
		for (double s = 0; s <= len; s += 10) {
			double qx = x0 + (x1 - x0) * s / len, qz = z0 + (z1 - z0) * s / len;
			int ix = (int) Math.floor(qx), iz = (int) Math.floor(qz);
			if (k.distance(qx, qz) - k.halfWidth(qx, qz) < 14) return null;                 // never across the river
			if (f.infra.motorwayDistance(qx, qz) < Infra.EDGE + OUTER + 4) return null;     // nor across a motorway
			if (f.harbours.siteAt(ix, iz) != null) return null;
			if (f.coastal(ix, iz) < Lowlands.COAST + 0.07) return null;
			Villages.Town o = f.villages.at(ix, iz);
			if (o != null && !same(o, a) && !same(o, b)) return null;
			if (f.infra.locks.near(ix, iz, 10)) return null;
		}
		int bike = (Harbours.mix(a.seed() ^ b.seed()) & 1) == 0 ? 1 : -1;
		return new Seg(x0, z0, x1, z1, bike);
	}

	private static boolean same(Villages.Town p, Villages.Town q) {
		return p.cx() == q.cx() && p.cz() == q.cz();
	}

	/** Nearest road stretch within r of this point: {c, along, index} with the stretch, or null. */
	public Object[] hit(double x, double z, double r) {
		int cx = Math.floorDiv((int) Math.floor(x), Villages.CELL), cz = Math.floorDiv((int) Math.floor(z), Villages.CELL);
		Object[] best = null;
		double bd = r;
		for (int i = -2; i <= 1; i++)
			for (int j = -2; j <= 1; j++)
				for (Seg s : from(cx + i, cz + j)) {
					double[] p = s.at(x, z);
					if (p == null || Math.abs(p[0]) > bd) continue;
					bd = Math.abs(p[0]);
					best = new Object[]{p, s};
				}
		return best;
	}

	public boolean near(int x, int z, double r) {
		return hit(x + 0.5, z + 0.5, OUTER + r) != null;
	}

	/** No ditches or lakes under the road and the cycle path. */
	public double shape(int x, int z, double t) {
		if (t >= Lowlands.POLDER) return t;
		return hit(x + 0.5, z + 0.5, OUTER + 1) != null ? Lowlands.POLDER : t;
	}

	// ------------------------------------------------------------------ blocks

	private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.getDefaultState();
	private static final BlockState LINE = Blocks.WHITE_CONCRETE.getDefaultState();
	private static final BlockState CYCLE = Blocks.RED_TERRACOTTA.getDefaultState();

	/** Builds one column of a provincial road (also used for the lock roads). Returns true if it did. */
	static boolean build(StructureWorldAccess w, BlockPos.Mutable m, Seg s, double c, double along, int x, int z) {
		double a = Math.abs(c);
		if (a > OUTER + 1) return false;
		int top = w.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) - 1;
		if (top < RiverField.WATER_TOP - 1) return false;
		BlockState ground = w.getBlockState(m.set(x, top, z));
		if (!ground.getFluidState().isEmpty()) return false;
		// no trees, grass or flowers on the road
		for (int y = top + 1; y <= top + 14; y++) {
			BlockState st = w.getBlockState(m.set(x, y, z));
			if (!st.isAir() && st.getFluidState().isEmpty() && (st.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)
					|| st.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || st.isReplaceable() || st.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)
					|| st.isOf(Blocks.SUGAR_CANE) || st.isIn(net.minecraft.registry.tag.BlockTags.SAPLINGS)))
				w.setBlockState(m, Blocks.AIR.getDefaultState(), 2);
		}
		double dx = s.x1() - s.x0(), dz = s.z1() - s.z0(), l = Math.hypot(dx, dz);
		double lw = (Math.abs(dx) + Math.abs(dz)) / l;      // 1 for a road along x or z, 1.41 diagonal
		boolean straight = lw < 1.08;                        // only then the lines can be drawn cleanly
		int side = c >= 0 ? 1 : -1;
		long al = (long) Math.floor(along);
		if (a < HALF) {
			BlockState st = ASPHALT;
			if (straight && a >= HALF - 1) st = LINE;                                        // edge lines
			if (straight && a < 0.5 && Math.floorMod(al, 9L) < 3) st = LINE;                // centre line
			w.setBlockState(m.set(x, top, z), st, 2);
			return true;
		}
		if (a >= HALF + 0.6 && a < HALF + 1.6 && side != s.bike() && Math.floorMod(al, 11L) == 0) {
			// a row of trees on the side without the cycle path
			Countryside.tree(w, m, x, top + 1, z, Harbours.mix(x * 31L + z));
			return true;
		}
		if (side == s.bike()) {
			if (a >= HALF + 1.5 && a < HALF + 3.5) {
				w.setBlockState(m.set(x, top, z), CYCLE, 2);
				return true;
			}
			if (a >= HALF + 0.4 && a < HALF + 1.4 && Math.floorMod(al, 11L) == 5) {
				// trees between the road and the cycle path
				Countryside.tree(w, m, x, top + 1, z, Harbours.mix(x * 17L + z * 5));
				return true;
			}
		}
		return a < OUTER;
	}
}
