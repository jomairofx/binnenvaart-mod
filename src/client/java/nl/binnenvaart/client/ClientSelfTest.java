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
		int helm = afterShots + 620;
		if (t == helm) {
			cmd(srv, "gamemode creative @p");
			tp(srv, ship, new double[]{8.5, 9.62, 4.5}, new double[]{60, 8, 5.5});
		}
		if (t == helm + 30) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) {
					s.handleInteract(sp, new Vec3d(9.5, 9.5, 5.2), false);
				}
			});
		}
		if (t == helm + 50) {
			log.add("rijdt mee: " + (mc.player.getVehicle() == ship));
			mc.options.hudHidden = false;
			mc.player.setYaw((float) Math.toDegrees(ship.shipYaw) - 90f);
			mc.player.setPitch(8f);
			srv.execute(() -> {
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.testControl(3, 0, 0);
			});
		}
		if (t == helm + 100) shot(mc, "12_aan_het_roer");
		if (t == helm + 400) shot(mc, "13_varen");
		if (t == helm + 410) {
			log.add(String.format("snelheid op de client %.3f, lading %.2f", ship.syncedSpeed(), ship.load()));
			finish(mc, "RESULT PASS");
		}
	}
}
