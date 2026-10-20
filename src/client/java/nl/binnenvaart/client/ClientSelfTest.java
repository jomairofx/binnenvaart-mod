package nl.binnenvaart.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import nl.binnenvaart.Binnenvaart;
import nl.binnenvaart.ship.ShipEntity;
import nl.binnenvaart.ship.ShipHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Automatische client-test (alleen met -Dbinnenvaart.selftest=client): maakt screenshots van het schip. */
public final class ClientSelfTest {
	private static int t;
	private static final List<String> log = new ArrayList<>();
	private static boolean failed;
	private static double ladderMax = -99;
	private static double yaw0;

	private static void check(String name, boolean ok, String detail) {
		log.add((ok ? "PASS " : "FAIL ") + name + " - " + detail);
		Binnenvaart.LOGGER.info("SELFTEST {} {} {}", ok ? "PASS" : "FAIL", name, detail);
		if (!ok) failed = true;
	}

	private record Shot(String name, double[] eye, double[] target, boolean hud) {}

	private static final Shot[] SHOTS = {
			new Shot("01_overzicht", new double[]{25, 32, 62}, new double[]{71, 0, 8}, false),
			new Shot("02_zijkant", new double[]{71, 3, 82}, new double[]{71, 1, 8}, false),
			new Shot("03_boeg", new double[]{168, 7, 32}, new double[]{136, 1, 8}, false),
			new Shot("04_achterkant", new double[]{-24, 9, -12}, new double[]{6, 4, 8}, false),
			new Shot("05_tankdek", new double[]{62, 6, 8}, new double[]{20, 3, 8}, false),
			new Shot("05b_tankdek_boven", new double[]{75, 30, 8.1}, new double[]{75, 0, 8}, false),
			new Shot("06_woning", new double[]{13.5, 3.62, 8.5}, new double[]{8.5, 2.6, 11.0}, false),
			new Shot("06b_keuken", new double[]{13.5, 3.62, 8.5}, new double[]{19.5, 2.5, 12.0}, false),
			new Shot("07_machinekamer", new double[]{11.0, -2.4, 8.5}, new double[]{18.5, -3.0, 4.0}, false),
			new Shot("08_onderwater", new double[]{71, -6, 32}, new double[]{71, -2, 8}, false),
			new Shot("09_stuurhuis_binnen", new double[]{15.0, 8.62, 8.5}, new double[]{80, 4, 8}, false),
			new Shot("09b_achterdek_auto", new double[]{-6.0, 8.0, 4.0}, new double[]{4.0, 2.5, 11.5}, false),
	};

	public static void register() {
		if (!System.getProperty("binnenvaart.selftest", "").equals("client")) return;
		ClientTickEvents.END_CLIENT_TICK.register(ClientSelfTest::tick);
	}

	private static void cmd(IntegratedServer srv, String c) {
		srv.execute(() -> srv.getCommandManager().executeWithPrefix(srv.getCommandSource(), c));
	}

	private static void tp(IntegratedServer srv, ShipEntity ship, double[] eye, double[] target) {
		Vec3d e = ship.toWorld(eye[0], eye[1], eye[2]);
		Vec3d g = ship.toWorld(target[0], target[1], target[2]);
		double dx = g.x - e.x, dy = g.y - e.y, dz = g.z - e.z;
		double yaw = Math.toDegrees(Math.atan2(-dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		cmd(srv, String.format(java.util.Locale.ROOT, "tp @p %.5f %.5f %.5f %.2f %.2f", e.x, e.y - 1.62 + 0.001, e.z, yaw, pitch));
	}

	private static void shot(MinecraftClient mc, String name) {
		ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(), msg -> {});
		log.add("shot " + name);
	}

	private static void finish(MinecraftClient mc, String result) {
		log.add(result);
		try {
			Files.writeString(Path.of("client_selftest.txt"), String.join("\n", log) + "\n");
		} catch (Exception ignored) {
		}
		mc.scheduleStop();
	}

	private static void tick(MinecraftClient mc) {
		if (mc.world == null || mc.player == null) return;
		t++;
		IntegratedServer srv = mc.getServer();
		if (srv == null) return;
		List<ShipEntity> ships = ShipHolder.of(mc.world);
		if (t == 20) {
			cmd(srv, "gamerule doDaylightCycle false");
			cmd(srv, "time set 5000");
			cmd(srv, "weather clear");
			cmd(srv, "gamemode spectator @p");
		}
		if (t < 60) return;
		if (ships.isEmpty()) {
			if (t > 600) finish(mc, "RESULT FAIL geen schip gevonden");
			if (t == 60) cmd(srv, "tp @p 60 -40 0");
			return;
		}
		ShipEntity ship = ships.get(0);
		int base = 80;
		int per = 50;
		for (int i = 0; i < SHOTS.length; i++) {
			int at = base + i * per;
			if (t == at) {
				mc.options.hudHidden = !SHOTS[i].hud();
				tp(srv, ship, SHOTS[i].eye(), SHOTS[i].target());
			}
			if (t == at + per - 5) shot(mc, SHOTS[i].name());
		}
		int afterShots = base + SHOTS.length * per;
		if (t == afterShots) {
			// luiken dicht laten schuiven
			srv.execute(() -> {
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.testSetHatches(true);
			});
			tp(srv, ship, SHOTS[0].eye(), SHOTS[0].target());
		}
		if (t == afterShots + 200) shot(mc, "10_luiken_bewegen");
		if (t == afterShots + 600) shot(mc, "11_luiken_dicht");
		int t0 = afterShots + 620;
		if (t == t0) {
			cmd(srv, "gamemode survival @p");
			mc.options.hudHidden = true;
			tp(srv, ship, new double[]{50.5, 3.62, 1.5}, new double[]{70, 3.6, 1.5});
		}
		if (t == t0 + 40) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			check("staan aan dek", Math.abs(l.y - 2.0) < 0.1, String.format("speler op lokaal (%.2f, %.2f, %.2f)", l.x, l.y, l.z));
			srv.execute(() -> {
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.testControl(3, 0, 0);
			});
		}
		if (t == t0 + 150) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			check("meevaren aan dek", Math.abs(l.x - 50.5) < 2 && Math.abs(l.y - 2.0) < 0.15,
					String.format("speler op lokaal (%.2f, %.2f, %.2f), schip snelheid %.3f", l.x, l.y, l.z, ship.syncedSpeed()));
			shot(mc, "14_aan_dek_varen");
			srv.execute(() -> {
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.testControl(0, 0, 0);
			});
		}
		if (t == t0 + 320) {
			tp(srv, ship, new double[]{26.5, -1.0 + 1.62, -0.62}, new double[]{26.5, 0.6, 6});
		}
		if (t == t0 + 340) mc.options.forwardKey.setPressed(true);
		if (t > t0 + 340 && t < t0 + 440) {
			double ly = ship.toLocal(mc.player.getPos()).y;
			if (ly > ladderMax) ladderMax = ly;
			if (ly >= 1.95) mc.options.forwardKey.setPressed(false);
		}
		if (t > t0 + 330 && t < t0 + 440 && t % 10 == 0) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			log.add(String.format("ladder t=%d lokaal (%.2f, %.2f, %.2f) climbing=%s hcoll=%s water=%s fwd=%.2f vel=%s",
					t - t0, l.x, l.y, l.z, mc.player.isClimbing(), mc.player.horizontalCollision, mc.player.isTouchingWater(),
					mc.player.input.movementForward, mc.player.getVelocity()));
			if (t == t0 + 380) shot(mc, "16_ladder");
		}
		if (t == t0 + 440) {
			mc.options.forwardKey.setPressed(false);
			Vec3d l = ship.toLocal(mc.player.getPos());
			check("ladder", ladderMax >= 1.95, String.format("vanuit het water geklommen tot lokale y=%.2f (dek = 2.0)", ladderMax));
			tp(srv, ship, new double[]{22.6, 2.0 + 1.62, 8.5}, new double[]{21.5, 3.0, 8.5});
		}
		if (t == t0 + 460) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.handleInteract(sp, new Vec3d(21.5, 2.5, 8.5), false);
			});
		}
		if (t == t0 + 480) {
			var st = ship.structure.get(21, 2, 8);
			boolean open = st.contains(net.minecraft.state.property.Properties.OPEN) && st.get(net.minecraft.state.property.Properties.OPEN);
			check("deur", open, "voordeur woning: " + st);
			shot(mc, "15_deur_open");
		}
		if (t == t0 + 485) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.handleInteract(sp, new Vec3d(25.8, 2.5, 5.5), false);
			});
		}
		if (t == t0 + 495) {
			boolean open = mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
			check("laadruim openen", open, "scherm: " + (mc.currentScreen == null ? "geen" : mc.currentScreen.getTitle().getString()));
			if (open) shot(mc, "17_laadruim");
		}
		if (t == t0 + 499 && mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
		int helm = t0 + 500;
		if (t == helm) {
			cmd(srv, "gamemode creative @p");
			tp(srv, ship, new double[]{15.5, 8.62, 8.5}, new double[]{60, 6, 8});
		}
		if (t == helm + 30) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) {
					s.testMode = false;
					s.handleInteract(sp, new Vec3d(16.5, 7.5, 8.5), false);
				}
			});
		}
		if (t == helm + 50) {
			check("aan het roer", mc.player.getVehicle() == ship, "speler zit aan het roer");
			mc.options.hudHidden = false;
			mc.player.setYaw((float) Math.toDegrees(ship.shipYaw) - 90f);
			mc.player.setPitch(8f);
			BinnenvaartClient.testThrottle(3);
		}
		if (t == helm + 100) shot(mc, "12_aan_het_roer");
		if (t == helm + 400) {
			check("varen vanaf het roer", ship.syncedSpeed() > 0.15, String.format("snelheid %.3f b/t", ship.syncedSpeed()));
			shot(mc, "13_varen");
		}
		if (t == helm + 405) {
			yaw0 = ship.shipYaw;
			mc.options.leftKey.setPressed(true);
		}
		if (t == helm + 485) {
			mc.options.leftKey.setPressed(false);
			double d = Math.toDegrees(ship.shipYaw - yaw0);
			check("roer (A-toets)", Math.abs(d) > 3 && ship.syncedRudder() < -0.5, String.format("koers %.1f graden veranderd, roer %.2f", d, ship.syncedRudder()));
			BinnenvaartClient.testThrottle(0);
			BinnenvaartClient.thrusterRight.setPressed(true);
		}
		if (t == helm + 525) yaw0 = ship.shipYaw;
		if (t == helm + 575) {
			BinnenvaartClient.thrusterRight.setPressed(false);
			double d = Math.toDegrees(ship.shipYaw - yaw0);
			// het schip draait door zijn massa nog na van het roer; kijk naar de draaikracht van de boegschroef zelf
			net.minecraft.entity.Entity se = srv.getOverworld().getEntity(ship.getUuid());
			double tr = se instanceof ShipEntity s2 ? Math.toDegrees(s2.thrusterTurnRate()) * 20 : 0;
			check("boegschroef (pijltje rechts)", tr > 0.5 && ship.syncedThruster() == 1, String.format("boegschroef draait %.1f graden/s naar stuurboord (koers %.1f), boegschroef %d", tr, d, ship.syncedThruster()));
			BinnenvaartClient.testThrottle(0);
			srv.execute(() -> srv.getPlayerManager().getPlayerList().get(0).stopRiding());
		}
		if (t == helm + 590) {
			cmd(srv, "gamemode survival @p");
			mc.options.hudHidden = false;
			tp(srv, ship, new double[]{26.5, 2.0 + 1.62, 1.5}, new double[]{26.5, 2.0, 1.5 - 6});
		}
		if (t == helm + 610) {
			// over de rand het water in springen
			Vec3d a = ship.toWorld(26.5, 2.0, 1.5), b = ship.toWorld(26.5, 2.0, 1.5 - 1);
			Vec3d dir = b.subtract(a).normalize();
			mc.player.setVelocity(dir.x * 0.45, 0.42, dir.z * 0.45);
		}
		if (t > helm + 610 && t < helm + 700 && t % 5 == 0) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			log.add(String.format("sprong t=%d lokaal (%.2f, %.2f, %.2f) water=%s", t - helm, l.x, l.y, l.z, mc.player.isTouchingWater()));
		}
		if (t == helm + 640) shot(mc, "30_sprong_water");
		if (t == helm + 720) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			check("in het water springen", mc.player.isTouchingWater() || l.z < 0, String.format("speler op lokaal (%.2f, %.2f, %.2f), water %s", l.x, l.y, l.z, mc.player.isTouchingWater()));
			finish(mc, failed ? "RESULT FAIL" : "RESULT PASS");
		}
	}
}
