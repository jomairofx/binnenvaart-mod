package com.modernbulk.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import com.modernbulk.ModernBulk;
import com.modernbulk.ship.ShipEntity;
import com.modernbulk.ship.ShipHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Automatische client-test (alleen met -Dmodernbulk.selftest=client): maakt screenshots van het schip. */
public final class ClientSelfTest {
	private static int t;
	private static final List<String> log = new ArrayList<>();
	private static boolean failed;
	private static double ladderMax = -99;
	private static double yaw0;

	private static void check(String name, boolean ok, String detail) {
		log.add((ok ? "PASS " : "FAIL ") + name + " - " + detail);
		ModernBulk.LOGGER.info("SELFTEST {} {} {}", ok ? "PASS" : "FAIL", name, detail);
		if (!ok) failed = true;
	}

	private record Shot(String name, double[] eye, double[] target, boolean hud) {}

	private static final Shot[] SHOTS = {
			new Shot("01_overzicht", new double[]{20, 34, 62}, new double[]{60, 0, 8.5}, false),
			new Shot("02_zijkant", new double[]{60, 4, 72}, new double[]{60, 2, 8.5}, false),
			new Shot("03_boeg", new double[]{140, 9, 34}, new double[]{112, 2, 8.5}, false),
			new Shot("04_achterkant", new double[]{-26, 13, -18}, new double[]{9, 6, 8.5}, false),
			new Shot("05_ruim", new double[]{70, 8.5, 8.5}, new double[]{20, 6, 8.5}, false),
			new Shot("06_woonkamer", new double[]{13.6, 3.62, 12.0}, new double[]{8.5, 3.0, 12.0}, false),
			new Shot("06b_keuken", new double[]{14.2, 3.62, 10.3}, new double[]{18.5, 2.6, 13.5}, false),
			new Shot("07_gang", new double[]{19.4, 3.62, 8.5}, new double[]{9.0, 3.4, 8.5}, false),
			new Shot("07b_slaapkamer", new double[]{12.4, 3.62, 5.6}, new double[]{9.0, 2.5, 3.5}, false),
			new Shot("08_onderwater", new double[]{60, -8, 32}, new double[]{60, -3, 8.5}, false),
			new Shot("09_stuurhuis_binnen", new double[]{14.6, 10.62, 8.5}, new double[]{60, 6, 8.5}, false),
			new Shot("09b_achterdek_auto", new double[]{11.5, 6.5, 4.0}, new double[]{3.5, 2.5, 10.5}, false),
	};


	public static void register() {
		if (!System.getProperty("modernbulk.selftest", "").equals("client")) return;
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
			tp(srv, ship, new double[]{60.5, 3.62, 0.5}, new double[]{80, 3.6, 0.5});
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
			check("meevaren aan dek", Math.abs(l.x - 60.5) < 2 && Math.abs(l.y - 2.0) < 0.15,
					String.format("speler op lokaal (%.2f, %.2f, %.2f), schip snelheid %.3f", l.x, l.y, l.z, ship.syncedSpeed()));
			shot(mc, "14_aan_dek_varen");
			srv.execute(() -> {
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.testControl(0, 0, 0);
			});
		}
		if (t == t0 + 320) {
			tp(srv, ship, new double[]{24.5, -1.0 + 1.62, -0.62}, new double[]{24.5, 0.6, 6});
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
			tp(srv, ship, new double[]{21.6, 2.0 + 1.62, 8.5}, new double[]{20.5, 3.0, 8.5});
		}
		if (t == t0 + 460) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.handleInteract(sp, new Vec3d(20.5, 2.5, 8.5), false);
			});
		}
		if (t == t0 + 480) {
			var st = ship.structure.get(20, 2, 8);
			boolean open = st.contains(net.minecraft.state.property.Properties.OPEN) && st.get(net.minecraft.state.property.Properties.OPEN);
			check("deur", open, "voordeur woning: " + st);
			shot(mc, "15_deur_open");
		}
		if (t == t0 + 485) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.handleInteract(sp, new Vec3d(24.8, 2.5, 8.5), false);
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
			tp(srv, ship, new double[]{15.5, 10.62, 6.5}, new double[]{60, 8, 8.5});
		}
		if (t == helm + 30) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) {
					s.testMode = false;
					s.handleInteract(sp, new Vec3d(17.5, 9.5, 8.5), false);
				}
			});
		}
		if (t == helm + 50) {
			check("aan het roer", mc.player.getVehicle() == ship, "speler zit aan het roer");
			mc.options.hudHidden = false;
			mc.player.setYaw((float) Math.toDegrees(ship.shipYaw) - 90f);
			mc.player.setPitch(8f);
			ModernBulkClient.testThrottle(3);
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
			ModernBulkClient.testThrottle(0);
			ModernBulkClient.thrusterRight.setPressed(true);
		}
		if (t == helm + 525) yaw0 = ship.shipYaw;
		if (t == helm + 575) {
			ModernBulkClient.thrusterRight.setPressed(false);
			double d = Math.toDegrees(ship.shipYaw - yaw0);
			check("boegschroef (pijltje rechts)", d > 0.5 && ship.syncedThruster() == 1, String.format("koers %.1f graden naar stuurboord, boegschroef %d", d, ship.syncedThruster()));
			ModernBulkClient.testLift = 1;
		}
		if (t == helm + 700) {
			ModernBulkClient.testLift = 0;
			Vec3d l = ship.toLocal(mc.player.getPos());
			check("stuurhut omhoog (PgUp)", ship.liftOffset() > 2.5 && mc.player.getVehicle() == ship && l.y > 11.5,
					String.format("stuurhut %.2f omhoog, speler op lokale y=%.2f, aan het roer %s", ship.liftOffset(), l.y, mc.player.getVehicle() == ship));
			shot(mc, "18_stuurhut_omhoog");
			mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
			cmd(srv, "time set 18000");
		}
		if (t == helm + 725) {
			shot(mc, "18b_stuurhut_omhoog_buiten");
		}
		if (t == helm + 730) ModernBulkClient.testLights = true;
		if (t == helm + 755) {
			check("licht uit (K)", !ship.lightsOn(), "licht staat " + (ship.lightsOn() ? "aan" : "uit"));
			shot(mc, "19_licht_uit_nacht");
			ModernBulkClient.testLights = true;
		}
		if (t == helm + 780) {
			check("licht weer aan", ship.lightsOn(), "licht staat " + (ship.lightsOn() ? "aan" : "uit"));
			shot(mc, "19b_licht_aan_nacht");
			mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
			cmd(srv, "time set 5000");
			srv.execute(() -> srv.getPlayerManager().getPlayerList().get(0).stopRiding());
		}
		if (t == helm + 810) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			check("staan in opgeheven stuurhut", Math.abs(l.y - (9.0 + ship.liftOffset())) < 0.25,
					String.format("speler op lokale y=%.2f, vloer op %.2f", l.y, 9.0 + ship.liftOffset()));
			srv.execute(() -> {
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.testSetLift(0);
			});
		}
		if (t == helm + 960) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			check("meezakken met de stuurhut", ship.liftOffset() < 0.05 && Math.abs(l.y - 9.0) < 0.25,
					String.format("stuurhut %.2f, speler op lokale y=%.2f", ship.liftOffset(), l.y));
			shot(mc, "20_stuurhut_omlaag");
			finish(mc, failed ? "RESULT FAIL" : "RESULT PASS");
		}
	}
}
