package com.speedboat.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import com.speedboat.Speedboat;
import com.speedboat.ship.ShipEntity;
import com.speedboat.ship.ShipHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Automatische client-test (alleen met -Dspeedboat.selftest=client): maakt screenshots van het schip. */
public final class ClientSelfTest {
	private static int t;
	private static final List<String> log = new ArrayList<>();
	private static boolean failed;
	private static double ladderMax = -99;
	private static double yaw0;

	private static void check(String name, boolean ok, String detail) {
		log.add((ok ? "PASS " : "FAIL ") + name + " - " + detail);
		Speedboat.LOGGER.info("SELFTEST {} {} {}", ok ? "PASS" : "FAIL", name, detail);
		if (!ok) failed = true;
	}

	private record Shot(String name, double[] eye, double[] target, boolean hud) {}

	private static final Shot[] SHOTS = {
			new Shot("01_overview", new double[]{-6, 7, 13}, new double[]{8, 0.5, 2}, false),
			new Shot("02_side", new double[]{7.5, 2.5, 16}, new double[]{7.5, 0.8, 2}, false),
			new Shot("03_bow", new double[]{24, 3.5, 7}, new double[]{12, 1, 2}, false),
			new Shot("04_stern_outboards", new double[]{-7, 2.5, -3}, new double[]{0, 0.5, 2}, false),
			new Shot("05_cockpit", new double[]{0.5, 3.2, 2.0}, new double[]{6, 1.0, 2.0}, false),
			new Shot("06_cabin", new double[]{8.3, 0.62, 1.5}, new double[]{12, -0.5, 2.0}, false),
			new Shot("07_helm", new double[]{3.6, 2.4, 2.5}, new double[]{5.2, 1.3, 2.5}, false),
			new Shot("08_underwater", new double[]{7.5, -2.5, 9}, new double[]{7.5, -0.5, 2}, false),
	};

	public static void register() {
		if (!System.getProperty("speedboat.selftest", "").equals("client")) return;
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
			if (t == 60) cmd(srv, "tp @p 20 -40 0");
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
		int t0 = afterShots + 20;
		if (t == t0) {
			cmd(srv, "gamemode survival @p");
			mc.options.hudHidden = true;
			tp(srv, ship, new double[]{3.5, 1.62, 1.5}, new double[]{10, 1.6, 1.5});
		}
		if (t == t0 + 40) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			check("standing in the cockpit", Math.abs(l.y) < 0.1, String.format("player at local (%.2f, %.2f, %.2f)", l.x, l.y, l.z));
		}
		// the swim ladder at the transom, out of the water
		if (t == t0 + 60) tp(srv, ship, new double[]{-1.75, -0.9 + 1.62, 3.5}, new double[]{2, -0.2, 3.5});
		if (t == t0 + 80) {
			mc.options.forwardKey.setPressed(true);
			mc.options.jumpKey.setPressed(true);      // swim up, as a player does
		}
		if (t > t0 + 80 && t < t0 + 180) {
			double ly = ship.toLocal(mc.player.getPos()).y;
			if (ly > ladderMax) ladderMax = ly;
			if (ly >= 0.98) {
				mc.options.forwardKey.setPressed(false);
				mc.options.jumpKey.setPressed(false);
			}
		}
		if (t > t0 + 70 && t < t0 + 180 && t % 10 == 0) {
			Vec3d l = ship.toLocal(mc.player.getPos());
			log.add(String.format("ladder t=%d local (%.2f, %.2f, %.2f) climbing=%s water=%s", t - t0, l.x, l.y, l.z, mc.player.isClimbing(), mc.player.isTouchingWater()));
			if (t == t0 + 110) shot(mc, "09_swim_ladder");
		}
		if (t == t0 + 180) {
			mc.options.forwardKey.setPressed(false);
			mc.options.jumpKey.setPressed(false);
			check("swim ladder", ladderMax >= 0.95, String.format("climbed out of the water to local y=%.2f (transom 1.0)", ladderMax));
			tp(srv, ship, new double[]{5.5, 1.62, 1.5}, new double[]{7.5, 0.0, 1.5});
		}
		// cabin door and the chest
		if (t == t0 + 200) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.handleInteract(sp, new Vec3d(7.5, -0.5, 1.5), false);
			});
		}
		if (t == t0 + 220) {
			var st = ship.structure.get(7, -1, 1);
			boolean open = st.contains(net.minecraft.state.property.Properties.OPEN) && st.get(net.minecraft.state.property.Properties.OPEN);
			check("cabin door", open, "door: " + st);
			shot(mc, "10_cabin_door_open");
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) s.handleInteract(sp, new Vec3d(9.5, -0.5, 2.5), false);
			});
		}
		if (t == t0 + 235) {
			boolean open = mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
			check("chest", open, "screen: " + (mc.currentScreen == null ? "none" : mc.currentScreen.getTitle().getString()));
			if (open) shot(mc, "11_chest");
		}
		if (t == t0 + 239 && mc.player.currentScreenHandler != mc.player.playerScreenHandler) mc.player.closeHandledScreen();
		int helm = t0 + 250;
		if (t == helm) {
			cmd(srv, "gamemode creative @p");
			tp(srv, ship, new double[]{3.5, 1.62, 2.5}, new double[]{10, 1.6, 2.5});
		}
		if (t == helm + 30) {
			srv.execute(() -> {
				ServerPlayerEntity sp = srv.getPlayerManager().getPlayerList().get(0);
				if (srv.getOverworld().getEntityById(ship.getId()) instanceof ShipEntity s) {
					s.testMode = false;
					s.handleInteract(sp, new Vec3d(4.5, 0.5, 2.5), false);
				}
			});
		}
		if (t == helm + 50) {
			check("at the wheel", mc.player.getVehicle() == ship, "player in the driver's seat");
			mc.options.hudHidden = false;
			mc.player.setYaw((float) Math.toDegrees(ship.shipYaw) - 90f);
			mc.player.setPitch(10f);
			SpeedboatClient.testThrottle(100);
		}
		if (t == helm + 120) shot(mc, "12_accelerating");
		if (t == helm + 330) {
			check("full speed from the wheel", ship.syncedSpeed() > 1.4, String.format("%.0f km/h", ship.syncedSpeed() * 72));
			shot(mc, "13_full_speed");
			mc.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
		}
		if (t == helm + 345) shot(mc, "14_full_speed_spray");
		if (t == helm + 350) {
			yaw0 = ship.shipYaw;
			mc.options.leftKey.setPressed(true);
		}
		if (t == helm + 375) shot(mc, "15_banking_turn");
		if (t == helm + 390) {
			mc.options.leftKey.setPressed(false);
			double d = Math.toDegrees(ship.shipYaw - yaw0);
			check("steering (A key)", Math.abs(d) > 20 && ship.syncedRudder() < -0.5, String.format("heading changed %.1f degrees, wheel %.2f", d, ship.syncedRudder()));
			SpeedboatClient.testThrottle(0);
		}
		if (t == helm + 600) {
			mc.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
			check("neutral", Math.abs(ship.syncedSpeed()) < 0.05, String.format("speed %.3f", ship.syncedSpeed()));
			srv.execute(() -> srv.getPlayerManager().getPlayerList().get(0).stopRiding());
			finish(mc, failed ? "RESULT FAIL" : "RESULT PASS");
		}
	}
}
