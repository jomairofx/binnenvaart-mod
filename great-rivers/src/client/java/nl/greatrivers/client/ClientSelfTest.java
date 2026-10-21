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
		double x = c[0], z = c[1], h = 4;
		double gx = k.distance(x + h, z) - k.distance(x - h, z), gz = k.distance(x, z + h) - k.distance(x, z - h);
		double l = Math.max(1e-9, Math.hypot(gx, gz));
		gx /= l;
		gz /= l;
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
					nl.greatrivers.Villages.Town t = GreatRivers.findTown(f, 0, 0, 15, city);
					if (t == null) continue;
					String tag = city ? "stad" : "dorp";
					add(tag + "_bovenaanzicht", t.cx() - 70, 120, t.cz() - 70, t.cx() + 10, 63, t.cz() + 10);
					int sx = t.cx() + nl.greatrivers.Villages.PU + 2, sz = t.cz() + 2;
					add(tag + "_straat", sx + 0.5, 64.6, sz + 0.5, sx + 40.5, 65, sz + 0.5);
					add(tag + "_kerk", t.cx() - 14, 70, t.cz() + 30, t.cx() + 15, 72, t.cz() + 14);
					int hx = t.cx() + nl.greatrivers.Villages.PU + nl.greatrivers.Villages.STREET + 3, hz = t.cz() + nl.greatrivers.Villages.STREET + 4;
					add(tag + "_huis_binnen", hx + 0.5, 64.62, hz + 0.5, hx + 0.5, 64.2, hz + 4.5);
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
