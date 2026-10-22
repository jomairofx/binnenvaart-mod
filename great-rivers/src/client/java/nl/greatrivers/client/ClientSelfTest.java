package nl.greatrivers.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import nl.greatrivers.GreatRivers;
import nl.greatrivers.RiverField;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Automatic client test (only with -Dgreatrivers.selftest=client): screenshots of both kinds of river. */
public final class ClientSelfTest {
	private static int t;
	private static final List<String> log = new ArrayList<>();
	private static final List<double[]> views = new ArrayList<>();
	private static final List<String> names = new ArrayList<>();
	private static final int PER = 400;

	public static void register() {
		if (!System.getProperty("greatrivers.selftest", "").equals("client")) return;
		ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
	}

	private static void cmd(IntegratedServer srv, String c) {
		srv.execute(() -> srv.getCommandManager().executeWithPrefix(srv.getCommandSource(), c));
	}

	private static void add(String name, double ex, double ey, double ez, double tx, double ty, double tz) {
		names.add(name);
		views.add(new double[]{ex, ey, ez, tx, ty, tz});
	}

	private static net.minecraft.server.world.ServerWorld srvWorld;

	private static void plan(RiverField.Kind k, RiverField.Kind other, String tag, double across, double high) {
		int[] c = nl.greatrivers.SelfTestSpots.clearSpot(k, other, srvWorld);
		if (c == null) {
			log.add("FAIL geen " + k.name());
			return;
		}
		double x = c[0], z = c[1];
		double[] nrm = k.normal(x, z);
		double gx = nrm[0], gz = nrm[1];
		double hw = k.halfWidth(x, z);
		add(tag + "_vanaf_de_oever", x + gx * (hw + across), high, z + gz * (hw + across), x - gx * hw * 0.3, 62, z - gz * hw * 0.3);
		add(tag + "_langs_de_rivier", x + gx * hw * 0.3 - gz * 40, 70, z + gz * hw * 0.3 + gx * 40, x + gz * 250, 62, z - gx * 250);
		add(tag + "_bovenaanzicht", x + gx * 30, 190, z + gz * 30, x, 62, z);
	}

	private static void tick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) return;
		IntegratedServer srv = mc.getServer();
		if (srv == null) return;
		t++;
		if (t == 20) {
			cmd(srv, "gamerule doDaylightCycle false");
			cmd(srv, "time set 5000");
			cmd(srv, "weather clear");
			cmd(srv, "gamemode spectator @p");
			srvWorld = srv.getOverworld();
			RiverField f = RiverField.current;
			if (f == null) {
				log.add("FAIL geen riviernet");
			} else {
				plan(f.great, f.river, "rivier", 60, 95);
				for (boolean city : new boolean[]{false, true}) {
					nl.greatrivers.Villages.Town t = GreatRivers.findTown(f, 0, 0, 15, city, Boolean.FALSE);
					if (t == null) continue;
					String tag = city ? "stad" : "dorp";
					add(tag + "_bovenaanzicht", t.cx() - 70, 122, t.cz() - 70, t.cx() + 10, 65, t.cz() + 10);
					int sx = t.cx() + nl.greatrivers.Villages.PU + 2, sz = t.cz() + 2;
					add(tag + "_straat", sx + 0.5, 66.6, sz + 0.5, sx + 40.5, 67, sz + 0.5);
					add(tag + "_kerk", t.cx() - 14, 72, t.cz() + 30, t.cx() + 15, 74, t.cz() + 14);
					int hx = t.cx() + nl.greatrivers.Villages.PU + nl.greatrivers.Villages.STREET + 3, hz = t.cz() + nl.greatrivers.Villages.STREET + 4;
					add(tag + "_huis_binnen", hx + 0.5, 66.62, hz + 0.5, hx + 0.5, 66.2, hz + 4.5);
					add(tag + "_rand", t.cx() + t.radius() + 60, 76, t.cz() + 20, t.cx(), 66, t.cz());
				}
				for (int size = 0; size <= 2; size++) {
					nl.greatrivers.Harbours.Site h = GreatRivers.findHarbour(f, 0, 0, 25, size);
					if (h == null) continue;
					String tag = "haven_" + h.sizeName() + "_" + h.goods();
					int[] q = GreatRivers.quaySpot(h);
					double big = h.size() == 2 ? 2.2 : h.size() == 1 ? 1.5 : 1.0;
					double mx = h.wx(0, h.bank() + (h.size() == 2 ? h.B() * 0.5 : h.B())), mz = h.wz(0, h.bank() + (h.size() == 2 ? h.B() * 0.5 : h.B()));
					add(tag + "_bovenaanzicht", mx - h.gx() * 90 * big, 120 + 40 * big, mz - h.gz() * 90 * big, mx + h.gx() * 20, 64, mz + h.gz() * 20);
					add(tag + "_op_de_kade", q[0] - h.tx() * 40, 67, q[1] - h.tz() * 40, q[0] + h.tx() * 30 + h.gx() * 10, 66, q[1] + h.tz() * 30 + h.gz() * 10);
				}
				int[] pp = nl.greatrivers.SelfTestSpots.polderSpot(f);
				if (pp != null) add("polder_met_sloten", pp[0] - 60, 95, pp[1] - 60, pp[0] + 20, 64, pp[1] + 20);
				int[] cs = nl.greatrivers.SelfTestSpots.canalSpot(f);
				if (cs != null) add("kanaal", cs[0] + 25, 72, cs[1] + 25, cs[0] - 60, 63, cs[1] - 60);
				double[] ds = nl.greatrivers.SelfTestSpots.dikeSpot(f);
				if (ds != null) {
					double ex = ds[0] + ds[2] * (ds[4] + ds[5] + 60), ez = ds[1] + ds[3] * (ds[4] + ds[5] + 60);
					add("dijk_en_uiterwaard", ex - ds[3] * 50, 80, ez + ds[2] * 50, ds[0], 63, ds[1]);
					double cx = ds[0] + ds[2] * (ds[4] + ds[5] + 17), cz = ds[1] + ds[3] * (ds[4] + ds[5] + 17);
					add("op_de_dijk", cx, 71, cz, cx - ds[3] * 80, 69, cz + ds[2] * 80);
				}
				nl.greatrivers.Villages.Town rc = GreatRivers.findTown(f, 0, 0, 25, true, Boolean.TRUE);
				if (rc != null) {
					double[] n = f.great.normal(rc.cx(), rc.cz());
					double tx = -n[1], tz = n[0];
					add("rivierstad_vanaf_het_water", rc.cx() + tx * 120, 70, rc.cz() + tz * 120, rc.cx() - tx * 40, 80, rc.cz() - tz * 40);
					add("rivierstad_kade", rc.cx() + n[0] * (f.great.halfWidth(rc.cx(), rc.cz()) + 6) + tx * 60, 66.6,
							rc.cz() + n[1] * (f.great.halfWidth(rc.cx(), rc.cz()) + 6) + tz * 60, rc.cx() + n[0] * (f.great.halfWidth(rc.cx(), rc.cz()) + 6), 66, rc.cz() + n[1] * (f.great.halfWidth(rc.cx(), rc.cz()) + 6));
					add("rivierstad_boven", rc.cx() - 150, 170, rc.cz() - 150, rc.cx(), 70, rc.cz());
				}
				// the big cities
				nl.greatrivers.Metros.Metro mt = f.metros.nearest(0, 0, 4);
				if (mt != null) {
					add("grote_stad_boven", mt.cx() - 130, 210, mt.cz() - 130, mt.cx(), 64, mt.cz());
					add("grote_stad_centrum", mt.cx() + 120, 110, mt.cz() + 140, mt.cx(), 70, mt.cz());
					int ax = mt.cx() + 3 * nl.greatrivers.Villages.PU + nl.greatrivers.Villages.STREET + 14;
					add("grote_stad_boulevard", ax + 0.5, 67, mt.cz() + 300, ax + 0.5, 66, mt.cz() + 200);
					for (int bx = -14; bx <= 14; bx++) {
						int bz = (int) Math.round(mt.radius() * 0.85 / nl.greatrivers.Villages.PW) * (bx < 0 ? -1 : 1);
						if (mt.district(bx, bz) == nl.greatrivers.Metros.INDUSTRY) {
							double qx = mt.cx() + bx * nl.greatrivers.Villages.PU, qz = mt.cz() + bz * nl.greatrivers.Villages.PW;
							add("grote_stad_bedrijventerrein", qx - 50, 95, qz - 50, qx + 15, 66, qz + 15);
							break;
						}
					}
					add("grote_stad_gracht", mt.cx() + 2 * nl.greatrivers.Villages.PU + 3, 68, mt.cz() + nl.greatrivers.Villages.PW + 5 + 10.5,
							mt.cx() + 2 * nl.greatrivers.Villages.PU + 60, 66, mt.cz() + nl.greatrivers.Villages.PW + 5 + 10.5);
				}
				nl.greatrivers.Metros.Metro rm = null;
				for (int i = -4; i <= 4 && rm == null; i++)
					for (int j = -4; j <= 4 && rm == null; j++) {
						nl.greatrivers.Metros.Metro c = f.metros.metro(i, j);
						if (c != null && c.river()) rm = c;
					}
				if (rm != null) {
					double[] n = f.great.normal(rm.cx(), rm.cz());
					double tx = -n[1], tz = n[0];
					add("grote_stad_rivier", rm.cx() + tx * 140, 72, rm.cz() + tz * 140, rm.cx(), 80, rm.cz());
					add("grote_stad_rivier_boven", rm.cx() + tx * 100 + n[0] * 100, 170, rm.cz() + tz * 100 + n[1] * 100, rm.cx(), 70, rm.cz());
				}
				// bridges, viaducts, motorways, locks
				double[] br = f.infra.findBridge(0, 0, 40000);
				if (br != null) {
					double[] n = f.great.normal(br[0], br[1]);
					double tx = -n[1], tz = n[0];
					add("brug_vanaf_het_water", br[0] + tx * 95 + n[0] * 20, 68, br[1] + tz * 95 + n[1] * 20, br[0], 86, br[1]);
					add("brug_zijaanzicht", br[0] + tx * 130 - n[0] * 70, 98, br[1] + tz * 130 - n[1] * 70, br[0] - n[0] * 20, 84, br[1] - n[1] * 20);
					int fam = (int) br[2], k = (int) br[3];
					double u = fam == 0 ? br[1] : br[0];
					double[] a0 = f.infra.centre(fam, k, u - 140), a1 = f.infra.centre(fam, k, u);
					nl.greatrivers.Infra.Hit hp = f.infra.prof(fam, k, u - 140);
					add("brug_oprit", a0[0] + 5, (hp == null ? 70 : hp.y) + 2.5, a0[1] + 5, a1[0], 95, a1[1]);
				}
				double[] vd = f.infra.findViaduct(0, 0, 3);
				if (vd != null) {
					int ek = (int) f.infra.nearestLine(1, vd[0], vd[1])[0];
					double[] e0 = f.infra.centre(1, ek, vd[0] - 70);
					nl.greatrivers.Infra.Hit eh = f.infra.prof(1, ek, vd[0] - 70);
					double ey = (eh == null ? 63 : eh.y) + 2.6;
					add("viaduct", e0[0], ey, e0[1] + 5, vd[0], ey + 3, vd[1]);
					add("viaduct_boven", vd[0] - 90, 120, vd[1] - 90, vd[0], 64, vd[1]);
				}
				nl.greatrivers.Locks.Site lk = f.infra.locks.nearest(0, 0, 8);
				if (lk != null) {
					double ow = lk.hw() + 30;
					add("sluis_overzicht", lk.wx(-260, ow + 60), 125, lk.wz(-260, ow + 60), lk.wx(0, lk.ch0()), 63, lk.wz(0, lk.ch0()));
					add("sluis_kolk", lk.wx(-150, lk.hw() - 3), 67.6, lk.wz(-150, lk.hw() - 3), lk.wx(60, lk.ch0() + 6), 63, lk.wz(60, lk.ch0() + 6));
					add("stuw", lk.wx(-90, lk.mid0() - 40), 75, lk.wz(-90, lk.mid0() - 40), lk.wx(0, lk.mid0() - 40), 75, lk.wz(0, lk.mid0() - 40));
					if (lk.hasRoad()) add("sluis_weg", lk.rx0() + (lk.rx1() - lk.rx0()) * 0.3, 70, lk.rz0() + (lk.rz1() - lk.rz0()) * 0.3, lk.rx1(), 64, lk.rz1());
				}
			}
			mc.options.hudHidden = true;
		}
		if (t < 40) return;
		int i = (t - 40) / PER, at = (t - 40) % PER;
		if (i >= views.size()) {
			log.add(log.stream().anyMatch(s -> s.startsWith("FAIL")) ? "RESULT FAIL" : "RESULT PASS");
			try {
				Files.writeString(Path.of("client_selftest.txt"), String.join("\n", log) + "\n");
			} catch (Exception ignored) {
			}
			mc.scheduleStop();
			t = -100000;
			return;
		}
		double[] v = views.get(i);
		if (at == 0) {
			double dx = v[3] - v[0], dy = v[4] - v[1], dz = v[5] - v[2];
			double yaw = Math.toDegrees(Math.atan2(-dx, dz));
			double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			cmd(srv, String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.2f %.2f", v[0], v[1], v[2], yaw, pitch));
		}
		if (at == PER - 5) {
			ScreenshotRecorder.saveScreenshot(mc.runDirectory, names.get(i) + ".png", mc.getFramebuffer(), msg -> {});
			log.add("PASS shot " + names.get(i) + String.format(java.util.Locale.ROOT, " vanaf %.0f %.0f %.0f", v[0], v[1], v[2]));
		}
	}
}
