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

	private static void check(String name, boolean ok, String detail) {
		log.add((ok ? "PASS " : "FAIL ") + name + " - " + detail);
		Binnenvaart.LOGGER.info("SELFTEST {} {} {}", ok ? "PASS" : "FAIL", name, detail);
		if (!ok) failed = true;
	}

	private record Shot(String name, double[] eye, double[] target, boolean hud) {}

	private static final Shot[] SHOTS = {
			new Shot("01_overzicht", new double[]{18, 26, 46}, new double[]{55, 0, 5.5}, false),
			new Shot("02_zijkant", new double[]{48, 3, 62}, new double[]{48, 1, 5.5}, false),
			new Shot("03_boeg", new double[]{112, 6, 24}, new double[]{86, 1, 5.5}, false),
			new Shot("04_achterkant", new double[]{-24, 8, -12}, new double[]{4, 4, 5.5}, false),
			new Shot("05_ruim", new double[]{62, 6.5, 5.5}, new double[]{10, 6, 5.5}, false),
			new Shot("06_woning", new double[]{9.6, 2.7, 5.0}, new double[]{4.5, 2.4, 3.0}, false),
			new Shot("07_machinekamer", new double[]{11.5, -1.3, 5.0}, new double[]{8.5, -2.5, 3.0}, false),
			new Shot("08_onderwater", new double[]{48, -6, 22}, new double[]{48, -2, 5.5}, false),
			new Shot("09_stuurhuis_binnen", new double[]{6.0, 9.3, 5.5}, new double[]{30, 6, 5.5}, false),
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
		cmd(srv, String.format(java.util.Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", e.x, e.y - 1.62, e.z, yaw, pitch));
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
			tp(srv, ship, new double[]{50.5, 3.62, 0.5}, new double[]{70, 3.6, 0.5});
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
			tp(srv, ship, new double[]{12.5, -1.0 + 1.62, -0.62}, new double[]{12.5, 0.6, 6});
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
			tp(srv, ship, new double[]{12.7, 2.0 + 1.62, 5.5}, new double[]{11.5, 3.0, 5.5});
		}
		if (t == t0 + 460) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.handleInteract(sp, new Vec3d(11.5, 2.5, 5.5), false);
			});
		}
		if (t == t0 + 480) {
			var st = ship.structure.get(11, 2, 5);
			boolean open = st.contains(net.minecraft.state.property.Properties.OPEN) && st.get(net.minecraft.state.property.Properties.OPEN);
			check("deur", open, "voordeur woning: " + st);
			shot(mc, "15_deur_open");
		}
		if (t == t0 + 485) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.handleInteract(sp, new Vec3d(15.5, 2.5, 5.5), false);
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
			tp(srv, ship, new double[]{8.5, 9.62, 4.5}, new double[]{60, 8, 5.5});
		}
		if (t == helm + 30) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) {
					s.testMode = false;
					s.handleInteract(sp, new Vec3d(9.5, 9.5, 5.2), false);
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
		if (t == helm + 410) {
			BinnenvaartClient.testThrottle(0);
			finish(mc, failed ? "RESULT FAIL" : "RESULT PASS");
		}
	}
}
