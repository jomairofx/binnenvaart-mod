package nl.greatrivers;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.RailShape;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;

/**
 * Railways: double track main lines across the country, dead straight (family 0 runs north-south at a fixed x,
 * family 1 east-west at a fixed z), about 11 km apart. Ballast on a low embankment, overhead wire on masts.
 * They climb gently (1 in 30) to steel truss bridges over the river, as high as the motorway bridges, and go over
 * the motorways on viaducts. Where a provincial road crosses there is a level crossing with barriers and lights,
 * and a small station ("halte") with platforms, a shelter and bike racks. At open sea the line ends at buffers.
 */
public final class Rails {
	public static final int RS = 11000;
	public static final int BASE_Y = 63;                // ballast top in the polder
	public static final double HALF = 6.5;              // ballast, masts, embankment top
	public static final double RAMP = 750;              // 1 in 30 up to a river bridge
	public static final double VIA_RAMP = 270;          // 1 in 30 over a motorway
	public static final int VIA_CLEAR = 8;
	public static final int ROAD = 0, RAMPK = 1, BRIDGE = 2, VIADUCT = 3;
	public static final int CITY_Y = 73;                // on a viaduct through a metropolis: 8 clear over the streets
	public static final double CITY_RAMP = 330;         // 1 in 30 from the polder up to the city viaduct
	public static final double STATION_HALF = 90;       // half length of a central station

	private final RiverField f;
	private final Infra infra;
	private final int[] off = new int[2];

	Rails(RiverField f, Infra infra, long seed) {
		this.f = f;
		this.infra = infra;
		off[0] = (int) Math.floorMod(Harbours.mix(seed + 0x7A11L), (long) RS);
		off[1] = (int) Math.floorMod(Harbours.mix(seed + 0x7A12L), (long) RS);
	}

	/** {k, signed distance across, u} of the nearest railway of a family. */
	public double[] nearest(int fam, double x, double z) {
		double p = fam == 0 ? x : z, u = fam == 0 ? z : x;
		long k = Math.round((p - off[fam] - 0.5) / RS);
		double line = off[fam] + k * (double) RS + 0.5;
		return new double[]{k, p - line, u};
	}

	public double distance(double x, double z) {
		return Math.min(Math.abs(nearest(0, x, z)[1]), Math.abs(nearest(1, x, z)[1]));
	}

	public boolean near(int x, int z, double r) {
		return distance(x + 0.5, z + 0.5) < HALF + 12 + r;
	}

	public double[] centre(int fam, long k, double u) {
		double line = off[fam] + k * (double) RS + 0.5;
		return fam == 0 ? new double[]{line, u} : new double[]{u, line};
	}

	// ------------------------------------------------------------------ profile

	private static final int NC = 1 << 13;

	private static final class Nodes {
		final long[] key = new long[NC];
		final Rails[] owner = new Rails[NC];
		final double[] land = new double[NC], rise = new double[NC], e = new double[NC];
		final long[] seaKey = new long[NC];
		final Rails[] seaOwner = new Rails[NC];
		final boolean[] sea = new boolean[NC];
	}

	private static final ThreadLocal<Nodes> NODES = ThreadLocal.withInitial(Nodes::new);

	private boolean seaAt(int fam, long k, long g) {
		Nodes n = NODES.get();
		long key = ((long) fam << 62) ^ ((k & 0xfffff) << 40) ^ (g & 0xffffffffffL) ^ 0x5EA5EAL;
		int i = (int) (Harbours.mix(key) & (NC - 1));
		if (n.seaOwner[i] == this && n.seaKey[i] == key) return n.sea[i];
		double[] p = centre(fam, k, g * 40.0);
		boolean sea = f.coastal((int) Math.floor(p[0]), (int) Math.floor(p[1])) < Lowlands.COAST + 0.03;
		n.seaOwner[i] = this;
		n.seaKey[i] = key;
		n.sea[i] = sea;
		return sea;
	}

	private int node(int fam, long k, long un) {
		Nodes n = NODES.get();
		long key = ((long) fam << 62) ^ ((k & 0xfffff) << 40) ^ (un & 0xffffffffffL);
		int i = (int) (Harbours.mix(key) & (NC - 1));
		if (n.owner[i] == this && n.key[i] == key) return i;
		double u = un * 8.0;
		double[] c = centre(fam, k, u);
		long g0 = (long) Math.floor(u / 40);
		double seaE = Double.MAX_VALUE;
		boolean open = false;
		int reach = (int) ((Infra.BR_END + RAMP + 120) / 40);
		for (int d = 0; d <= reach + 4 && !open; d++)
			for (int sg = -1; sg <= 1; sg += 2) {
				long g = g0 + sg * (long) d;
				if (!seaAt(fam, k, g)) continue;
				int run = 1;
				for (int q = 1; q <= 26 && seaAt(fam, k, g + q); q++) run++;
				for (int q = 1; q <= 26 && seaAt(fam, k, g - q); q++) run++;
				if (run > 25) open = true;
				else seaE = Math.min(seaE, Math.max(0, Math.abs(g * 40.0 - u) - 40));
			}
		n.land[i] = open ? 0 : 1;
		double r = 0;
		for (int s = -192; s <= 192; s += 48) {
			double[] q = centre(fam, k, u + s);
			int ix = (int) Math.floor(q[0]), iz = (int) Math.floor(q[1]);
			double lowl = f.low.lowland(ix, iz);
			double eRiver = f.great.distance(ix, iz) - f.great.halfWidth(ix, iz) - Lowlands.foreland(f.great);
			r += f.low.rise(ix, iz, lowl) * Lowlands.smooth((eRiver - 40) / 220);
		}
		n.rise[i] = r / 9;
		n.e[i] = Math.min(seaE, f.great.distance(c[0], c[1]) - f.great.halfWidth(c[0], c[1]));
		n.owner[i] = this;
		n.key[i] = key;
		return i;
	}

	/** A point of a railway. */
	public static final class Hit {
		public int fam, kind, y;
		public long k;
		public double c, u, cx, cz, e, base;
		public double dm;        // over a motorway: distance to its middle
		public int underY;
		/** On the viaduct through a metropolis (no embankment, piers down to the street). */
		public boolean elev;
		public Metros.Metro city;
	}

	public Hit prof(int fam, long k, double u) {
		long un = (long) Math.floor(u / 8);
		double fr = (u - un * 8.0) / 8;
		Nodes n = NODES.get();
		int a = node(fam, k, un);
		double la = n.land[a], ra = n.rise[a], ea = n.e[a];
		int b = node(fam, k, un + 1);
		if (la < 0.5 || n.land[b] < 0.5) return null;
		double rise = ra + (n.rise[b] - ra) * fr, e = ea + (n.e[b] - ea) * fr;
		Hit h = new Hit();
		h.fam = fam;
		h.k = k;
		h.u = u;
		double[] c = centre(fam, k, u);
		h.cx = c[0];
		h.cz = c[1];
		double base = BASE_Y + rise;
		// through a metropolis on a viaduct, up a long ramp outside it
		Metros.Metro mt = f.metros.big(h.cx, h.cz, Metros.RING + 30 + CITY_RAMP);
		if (mt != null && mt.mega()) {
			double dm = Math.hypot(h.cx - mt.cx(), h.cz - mt.cz()) - mt.radius() - 30 - Metros.RING;
			if (dm < 0) {
				base = CITY_Y;
				h.elev = true;
				h.city = mt;
			} else base = base + (CITY_Y - base) * (1 - dm / CITY_RAMP);
		}
		h.base = base;
		double H = base;
		int kind = ROAD;
		if (e < Infra.BR_END) {
			H = Infra.DECK;
			kind = BRIDGE;
		} else if (e < Infra.BR_END + RAMP) {
			double r = base + (Infra.DECK - base) * (1 - (e - Infra.BR_END) / RAMP);
			if (r > H + 0.5) {
				H = r;
				kind = RAMPK;
			}
		}
		if (kind != BRIDGE) {
			// over the motorways
			for (int mf = 0; mf < 2; mf++) {
				double[] nl = infra.nearestLine(mf, h.cx, h.cz);
				double dm = Math.abs(nl[1]);
				if (dm > 16 + VIA_RAMP) continue;
				Infra.Hit mh = infra.prof(mf, (int) nl[0], nl[2]);
				if (mh == null || mh.kind == Infra.BRIDGE) continue;
				double hv = Math.max(Math.floor(base + 0.5), mh.y) + VIA_CLEAR;
				if (dm < 16) {
					H = Math.max(H, hv);
					kind = VIADUCT;
					h.dm = dm;
					h.underY = mh.y;
				} else {
					double r = base + (hv - base) * (1 - (dm - 16) / VIA_RAMP);
					if (r > H + 0.5) {
						H = r;
						kind = RAMPK;
					}
				}
			}
		}
		h.e = e;
		h.kind = kind;
		h.y = (int) Math.floor(H + 0.5);
		return h;
	}

	public Hit hit(int fam, double x, double z, double maxC) {
		double[] nl = nearest(fam, x, z);
		if (Math.abs(nl[1]) > maxC) return null;
		Hit h = prof(fam, (long) nl[0], nl[2]);
		if (h == null) return null;
		h.c = nl[1];
		return h;
	}

	/** The embankments, cuttings and ramps of the railways. */
	public double shape(int x, int z, double t) {
		for (int fam = 0; fam < 2; fam++) {
			Hit h = hit(fam, x + 0.5, z + 0.5, HALF + 45);
			if (h == null || h.elev) continue;
			double a = Math.abs(h.c);
			if (h.kind == BRIDGE) {
				double dEnd = Infra.BR_END - h.e;
				if (dEnd < 40) {
					double lo = Infra.DECK - 2.7 - Math.max(dEnd, Math.max(0, a - HALF)) / 1.5;
					if (t < lo) t = lo;
				}
				continue;
			}
			if (h.kind == VIADUCT) continue;
			double top = h.y + 0.3;
			if (a <= HALF) t = top;
			else {
				double lo = top - (a - HALF) / 1.5, hi = top + (a - HALF);
				if (t < lo) t = lo;
				if (t > hi) t = hi;
			}
		}
		return t;
	}

	/** Does the line end (open sea) within d along? */
	public boolean endsNear(int fam, long k, double u, double d) {
		return prof(fam, k, u + d) == null || prof(fam, k, u - d) == null;
	}

	/** Nearest point where a railway crosses the river (bridge): {x, z}. */
	public double[] findBridge(int x, int z, int range) {
		double[] best = null;
		double bd = Double.MAX_VALUE;
		for (int fam = 0; fam < 2; fam++) {
			double p = fam == 0 ? x : z, u0 = fam == 0 ? z : x;
			long kc = Math.round((p - off[fam]) / RS);
			for (long k = kc - 2; k <= kc + 2; k++) {
				double prev = Double.NaN;
				for (double u = u0 - range; u <= u0 + range; u += 24) {
					double[] c = centre(fam, k, u);
					double d = f.great.distance(c[0], c[1]) - f.great.halfWidth(c[0], c[1]);
					if (d < 0 && !(prev < 0) && prof(fam, k, u) != null) {
						double dd = Math.hypot(c[0] - x, c[1] - z);
						if (dd < bd) {
							bd = dd;
							best = c;
						}
					}
					prev = d;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------ blocks

	private static final BlockState BALLAST = Blocks.GRAVEL.getDefaultState();
	private static final BlockState CONCRETE = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
	private static final BlockState STEEL = Blocks.WAXED_OXIDIZED_CUT_COPPER.getDefaultState();

	private static void set(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, BlockState st) {
		w.setBlockState(m.set(x, y, z), st, 2);
	}

	private static void clear(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z, int h) {
		for (int yy = y + 1; yy <= y + h; yy++) {
			BlockState st = w.getBlockState(m.set(x, yy, z));
			if (!st.isAir() && st.getFluidState().isEmpty() && (st.isReplaceable() || st.isIn(BlockTags.LEAVES) || st.isIn(BlockTags.LOGS)
					|| st.isIn(BlockTags.FLOWERS) || st.isOf(Blocks.SUGAR_CANE) || st.isIn(BlockTags.SAPLINGS))) set(w, m, x, yy, z, Blocks.AIR.getDefaultState());
		}
	}

	private static int ground(StructureWorldAccess w, BlockPos.Mutable m, int x, int y, int z) {
		for (int yy = y; yy > w.getBottomY() + 1; yy--) {
			BlockState st = w.getBlockState(m.set(x, yy, z));
			if (st.isAir() || !st.getFluidState().isEmpty() || st.isReplaceable() || st.isIn(BlockTags.LEAVES)) continue;
			return yy;
		}
		return w.getBottomY() + 1;
	}

	/** Builds the railway in this column. Returns 0 nothing, 1 something (land may still be dressed), 2 the railway. */
	static int build(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, int x, int z) {
		int res = 0;
		for (int fam = 0; fam < 2; fam++) {
			Hit h = f.rails.hit(fam, x + 0.5, z + 0.5, HALF + 22);
			if (h == null) continue;
			int r = column(w, m, f, h, x, z);
			if (r > res) res = r;
		}
		return res;
	}

	private static int column(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Hit h, int x, int z) {
		double a = Math.abs(h.c);
		int ic = (int) Math.round(h.c);           // -6..6 across the ballast
		int y = h.y;
		boolean ns = h.fam == 0;
		long al = (long) Math.floor(h.u);
		boolean deck = h.kind == BRIDGE || h.kind == VIADUCT || h.elev;
		if (h.elev && a <= HALF + 7 && centralStation(w, m, f, h, x, z, a)) return 1;
		// a small station where a road crosses: platforms, shelter, bike racks
		Object[] road = h.elev ? null : f.roads.hit(x + 0.5, z + 0.5, 60);
		if (a > HALF) {
			if (!deck && h.kind == ROAD) return station(w, m, f, h, x, z, a);
			return 0;
		}
		if (deck) {
			set(w, m, x, y - 1, z, CONCRETE);
			set(w, m, x, y - 2, z, Blocks.SMOOTH_STONE.getDefaultState());
		} else {
			clear(w, m, x, y, z, 14);
			BlockState below = w.getBlockState(m.set(x, y - 1, z));
			if (below.isAir() || !below.getFluidState().isEmpty()) set(w, m, x, y - 1, z, Blocks.STONE.getDefaultState());
		}
		set(w, m, x, y, z, a >= HALF - 1 ? (deck ? CONCRETE : Blocks.COARSE_DIRT.getDefaultState()) : BALLAST);
		// the two tracks
		boolean track = ic == -2 || ic == 2;
		if (track) set(w, m, x, y + 1, z, Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, ns ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST));
		// buffers where the line ends before the open sea
		if (track && f.rails.endsNear(h.fam, h.k, h.u, 3)) {
			set(w, m, x, y + 1, z, Blocks.RED_CONCRETE.getDefaultState());
			set(w, m, x, y + 2, z, Blocks.WHITE_CONCRETE.getDefaultState());
		}
		// overhead wire: masts every 40, the contact wire over each track
		boolean mast = Math.floorMod(al, 40L) == 0;
		if (ic == -6 || ic == 6) {
			if (mast) {
				for (int yy = y + 1; yy <= y + 7; yy++) set(w, m, x, yy, z, Blocks.IRON_BARS.getDefaultState());
				set(w, m, x, y + 8, z, Blocks.IRON_BARS.getDefaultState().with(ns ? Properties.EAST : Properties.NORTH, true).with(ns ? Properties.WEST : Properties.SOUTH, true));
			} else if (deck && h.kind == BRIDGE) set(w, m, x, y + 1, z, Blocks.IRON_BARS.getDefaultState()
					.with(ns ? Properties.NORTH : Properties.EAST, true).with(ns ? Properties.SOUTH : Properties.WEST, true));
		} else if (mast) {
			set(w, m, x, y + 8, z, Blocks.IRON_BARS.getDefaultState().with(ns ? Properties.EAST : Properties.NORTH, true).with(ns ? Properties.WEST : Properties.SOUTH, true));
		}
		if (track) set(w, m, x, y + 7, z, Blocks.IRON_BARS.getDefaultState().with(ns ? Properties.NORTH : Properties.EAST, true).with(ns ? Properties.SOUTH : Properties.WEST, true));
		// level crossing: the road over the tracks, barriers and red lights
		if (road != null && !deck) {
			double[] p = (double[]) road[0];
			if (Math.abs(p[0]) <= Roads.HALF) {
				if (!track) set(w, m, x, y, z, Blocks.GRAY_CONCRETE.getDefaultState());
				else set(w, m, x, y, z, Blocks.GRAY_CONCRETE.getDefaultState());
				if (a >= HALF - 1 && Math.abs(Math.abs(p[0]) - (Roads.HALF - 0.5)) < 0.5) {
					set(w, m, x, y + 1, z, Blocks.WHITE_CONCRETE.getDefaultState());
					set(w, m, x, y + 2, z, Blocks.RED_CONCRETE.getDefaultState());
					set(w, m, x, y + 3, z, Blocks.WHITE_CONCRETE.getDefaultState());
					set(w, m, x, y + 4, z, Blocks.RED_STAINED_GLASS.getDefaultState());
				}
			}
		}
		// river bridge: steel trusses on both sides, piers in the river outside the fairway
		if (h.kind == BRIDGE) {
			double hw = f.great.halfWidth(h.cx, h.cz), dc = f.great.distance(h.cx, h.cz);
			double clear = Math.max(40, Math.min(75, hw * 0.6));
			if (ic == -6 || ic == 6) {
				int ph = (int) Math.floorMod(al, 12L);
				for (int yy = y + 1; yy <= y + 11; yy++) {
					int rel = yy - y;
					boolean chord = rel == 11 || rel == 1;
					boolean post = ph == 0;
					boolean diag = rel == 1 + ph * 10 / 12 || rel == 11 - ph * 10 / 12;
					if (chord || post || diag) set(w, m, x, yy, z, STEEL);
				}
			} else if (Math.floorMod(al, 12L) == 0) set(w, m, x, y + 11, z, STEEL);   // cross girders on top
			if (dc >= clear && Math.floorMod(al, 60L) < 3 && a < HALF - 1) {
				int g = ground(w, m, x, y - 3, z);
				for (int yy = g - 1; yy <= y - 3; yy++) set(w, m, x, yy, z, yy < RiverField.WATER_TOP - 1 ? Blocks.GRAY_CONCRETE.getDefaultState() : CONCRETE);
			}
			if (h.e > Infra.BR_END - 2.5) for (int yy = ground(w, m, x, y - 3, z); yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
		}
		// the city viaduct: concrete piers every 24 down to the street, never on a street or boulevard
		if (h.elev && h.kind != BRIDGE && (ic == -4 || ic == 4) && Math.floorMod(al, 24L) < 2 && !MetroBuilder.openGround(h.city, x, z)) {
			for (int yy = Villages.GROUND + 1; yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
		}
		if (h.kind == VIADUCT) {
			if (h.dm > 13.5) for (int yy = h.underY - 1; yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
			else if (h.dm < 1 && a < 4) for (int yy = h.underY + 1; yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
		}
		return deck ? 1 : 2;
	}

	/**
	 * The central station of a metropolis, on the viaduct near the city centre: wide platforms on both sides of
	 * the tracks under a long arched roof of glass on steel ribs, the yellow and blue signs, lamps.
	 */
	private static boolean centralStation(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Hit h, int x, int z, double a) {
		Metros.Metro mt = h.city;
		double uc = h.fam == 0 ? mt.cz() : mt.cx();
		double du = h.u - uc;
		if (Math.abs(du) > STATION_HALF) return false;
		// not on the bridge or its ramp
		Hit h0 = f.rails.prof(h.fam, h.k, uc), h1 = f.rails.prof(h.fam, h.k, uc - STATION_HALF), h2 = f.rails.prof(h.fam, h.k, uc + STATION_HALF);
		if (h0 == null || h1 == null || h2 == null || h0.kind != ROAD || h1.kind != ROAD || h2.kind != ROAD) return false;
		int y = h.y;
		boolean ns = h.fam == 0;
		long al = (long) Math.floor(h.u);
		double W = HALF + 7;
		// the deck under the platforms
		set(w, m, x, y - 1, z, CONCRETE);
		set(w, m, x, y - 2, z, Blocks.SMOOTH_STONE.getDefaultState());
		if (a > HALF) {
			// platform: one up from the ballast, white edge along the track
			set(w, m, x, y, z, CONCRETE);
			set(w, m, x, y + 1, z, a < HALF + 1 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.SMOOTH_STONE.getDefaultState());
			for (int yy = y + 2; yy <= y + 8; yy++) set(w, m, x, yy, z, Blocks.AIR.getDefaultState());
			if (a > W - 1) {
				// glass side wall
				for (int yy = y + 2; yy <= y + 6; yy++) set(w, m, x, yy, z, ns ? VillageBuilder.paneNS(Blocks.GLASS_PANE) : VillageBuilder.paneEW(Blocks.GLASS_PANE));
			} else if (Math.floorMod(al, 30L) == 15 && a > HALF + 3 && a < HALF + 4) {
				// station sign, benches
				for (int yy = y + 2; yy <= y + 3; yy++) set(w, m, x, yy, z, Blocks.IRON_BARS.getDefaultState());
				set(w, m, x, y + 4, z, Blocks.YELLOW_CONCRETE.getDefaultState());
				set(w, m, x, y + 5, z, Blocks.BLUE_CONCRETE.getDefaultState());
			} else if (Math.floorMod(al, 30L) == 5 && a > HALF + 4 && a < HALF + 5) {
				set(w, m, x, y + 2, z, VillageBuilder.stairs(Blocks.SPRUCE_STAIRS, ns ? (h.c > 0 ? Direction.EAST : Direction.WEST) : (h.c > 0 ? Direction.SOUTH : Direction.NORTH)));
			}
			// piers under the wide platforms
			if (Math.floorMod(al, 24L) < 2 && a > W - 2 && !MetroBuilder.openGround(mt, x, z))
				for (int yy = Villages.GROUND + 1; yy <= y - 3; yy++) set(w, m, x, yy, z, CONCRETE);
		}
		// the arched roof: glass between steel ribs every 8, open at the ends
		if (Math.abs(du) < STATION_HALF - 4) {
			double t = a / W;
			int ry = y + 10 + (int) Math.round(4 * (1 - t * t));
			boolean rib = Math.floorMod(al, 8L) == 0;
			set(w, m, x, ry, z, rib ? STEEL : Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
			if (rib && a > W - 1) for (int yy = y + 7; yy < ry; yy++) set(w, m, x, yy, z, STEEL);
			if (!rib && Math.floorMod(al, 8L) == 4 && Math.abs(a - HALF - 3) < 0.6) set(w, m, x, ry - 1, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
		}
		return a > HALF;
	}

	/** The central station of the metropolis on this line nearest to (x, z): {x, z, y} of its middle, or null. For the tests. */
	public double[] findCentralStation(int x, int z) {
		Metros.Metro mt = f.metros.nearestBig(x, z, Metros.MEGA, 2);
		if (mt == null) return null;
		for (int fam = 0; fam < 2; fam++) {
			double[] nl = nearest(fam, mt.cx(), mt.cz());
			if (Math.abs(nl[1]) > mt.radius() * 0.8) continue;
			double uc = fam == 0 ? mt.cz() : mt.cx();
			Hit h = prof(fam, (long) nl[0], uc);
			if (h == null || !h.elev || h.kind != ROAD) continue;
			double[] c = centre(fam, (long) nl[0], uc);
			return new double[]{c[0], c[1], h.y};
		}
		return null;
	}

	/**
	 * A small station where a provincial road crosses the line: platforms along the tracks (on the side the road
	 * leaves from), a shelter, a ticket machine, bike racks and the blue-and-yellow sign.
	 */
	private static int station(StructureWorldAccess w, BlockPos.Mutable m, RiverField f, Hit h, int x, int z, double a) {
		if (a > HALF + 4 || f.roads.hit(x + 0.5, z + 0.5, 110) == null) return 0;
		// the road crossing nearest along the line
		double[] best = null;
		for (int du = -120; du <= 120; du += 2) {
			double[] c = f.rails.centre(h.fam, h.k, h.u + du);
			Object[] r = f.roads.hit(c[0], c[1], Roads.HALF);
			if (r != null) {
				best = new double[]{du};
				break;
			}
		}
		if (best == null) return 0;
		double du = -best[0];        // our position along, relative to the crossing
		if (du < 8 || du > 98) return 0;
		if (a > HALF + 4) return 0;
		int y = h.y;
		int ic = (int) Math.floor(Math.abs(h.c) - HALF);    // 0..3 across the platform
		clear(w, m, x, y, z, 10);
		set(w, m, x, y, z, Blocks.SMOOTH_STONE.getDefaultState());
		set(w, m, x, y + 1, z, ic == 0 ? Blocks.WHITE_CONCRETE.getDefaultState() : Blocks.SMOOTH_STONE.getDefaultState());   // platform edge
		for (int yy = y - 1; yy >= y - 3; yy--) {
			BlockState b = w.getBlockState(m.set(x, yy, z));
			if (b.isAir() || !b.getFluidState().isEmpty()) set(w, m, x, yy, z, Blocks.STONE.getDefaultState());
		}
		double mid = 50;
		if (Math.abs(du - mid) < 6 && ic >= 2) {
			// the shelter
			for (int yy = y + 2; yy <= y + 4; yy++) set(w, m, x, yy, z, ic == 3 ? VillageBuilder.paneNS(Blocks.GLASS_PANE) : Blocks.AIR.getDefaultState());
			set(w, m, x, y + 5, z, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
			if (ic == 2 && Math.abs(du - mid) < 3) set(w, m, x, y + 2, z, VillageBuilder.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
		}
		if (Math.abs(du - 30) < 0.6 && ic == 2) {
			// station sign: yellow and blue
			for (int yy = y + 2; yy <= y + 4; yy++) set(w, m, x, yy, z, Blocks.IRON_BARS.getDefaultState());
			set(w, m, x, y + 5, z, Blocks.YELLOW_CONCRETE.getDefaultState());
			set(w, m, x, y + 6, z, Blocks.BLUE_CONCRETE.getDefaultState());
		}
		if (Math.abs(du - 70) < 0.6 && ic == 2) {
			set(w, m, x, y + 2, z, Blocks.YELLOW_CONCRETE.getDefaultState());          // ticket machine
			set(w, m, x, y + 3, z, Blocks.BLUE_CONCRETE.getDefaultState());
		}
		if (du < 18 && ic == 3 && ((long) Math.floor(du) & 1) == 0) set(w, m, x, y + 2, z, Blocks.IRON_BARS.getDefaultState());   // bike racks
		if (Math.floorMod((long) Math.floor(du), 20L) == 10 && ic == 3) InfraBuilder.lamp(w, m, x, y + 1, z, h.fam == 0
				? (h.c > 0 ? Direction.WEST : Direction.EAST) : (h.c > 0 ? Direction.NORTH : Direction.SOUTH), 5);
		return 2;
	}
}
