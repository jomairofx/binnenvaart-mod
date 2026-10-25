package com.speedboat.test;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.speedboat.Speedboat;
import com.speedboat.ship.ShipEntity;
import com.speedboat.ship.ShipHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatische test op een server (alleen actief met -Dspeedboat.selftest=server).
 * Zet een schip neer, laat het varen, draaien en laden, en schrijft de uitkomst naar selftest.txt.
 */
public final class SelfTest {
	private static int tick;
	private static ShipEntity ship;
	private static ItemEntity probe;
	private static Vec3d start;
	private static double startYaw, emptyY;
	private static final List<String> log = new ArrayList<>();
	private static boolean failed;

	public static void register() {
		String mode = System.getProperty("speedboat.selftest", "");
		if (!mode.equals("server")) return;
		ServerTickEvents.END_SERVER_TICK.register(SelfTest::onTick);
	}

	private static void check(String name, boolean ok, String detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + " - " + detail;
		log.add(line);
		Speedboat.LOGGER.info("SELFTEST {}", line);
		if (!ok) failed = true;
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		ServerWorld w = server.getOverworld();
		try {
			if (tick == 1) {
				for (int cx = -4; cx <= 40; cx++) for (int cz = -12; cz <= 12; cz++) w.setChunkForced(cx, cz, true);
			}
			if (tick == 60) {
				int top = -64;
				BlockPos.Mutable m = new BlockPos.Mutable(20, -40, 0);
				while (m.getY() > -64 && !w.getFluidState(m).isIn(FluidTags.WATER)) m.move(0, -1, 0);
				top = m.getY();
				check("water", w.getFluidState(m).isIn(FluidTags.WATER), "water surface at y=" + top);
				ship = new ShipEntity(Speedboat.SHIP, w);
				ship.place(20, top, 0.5, 0);
				boolean blocked = ship.hullBlocked(ship.getX(), ship.getY(), ship.getZ(), ship.shipYaw);
				check("room", !blocked, "hull clear when placed");
				w.spawnEntity(ship);
				emptyY = ship.getY();
			}
			if (tick == 70) {
				check("registered", ShipHolder.of(w).contains(ship), "boat in the list");
				check("displacement", ship.displacedCount() > 20, ship.displacedCount() + " water blocks displaced");
				var chest = ship.structure.get(9, 0, 2);
				check("chest", chest.getBlock() instanceof net.minecraft.block.ChestBlock, "cabin: " + chest);
				int beds = ship.bedHeads().size();
				check("berth", beds == 2, beds + " beds in the V-berth");
				Vec3d drop = ship.toWorld(3.5, 1.2, 1.5);
				probe = new ItemEntity(w, drop.x, drop.y, drop.z, new ItemStack(Items.DIAMOND));
				probe.setVelocity(0, 0, 0);
				probe.setNeverDespawn();
				w.spawnEntity(probe);
			}
			if (tick == 130) {
				Vec3d l = ship.toLocal(probe.getPos());
				check("cockpit floor", Math.abs(l.y) < 0.15, String.format("item lies at local y=%.3f (floor 0)", l.y));
				check("freeboard", ship.getY() - ship.waterSurfaceY() > 0.05, String.format("floor %.2f above the water", ship.getY() - ship.waterSurfaceY()));
				start = ship.getPos();
				startYaw = ship.shipYaw;
				ship.testControl(100, 0, 0);
			}
			if (tick == 410) {
				double dist = ship.getPos().distanceTo(start);
				double kmh = ship.currentSpeed() * 72;
				check("top speed", kmh > 110, String.format("%.0f km/h after 14 s, %.0f blocks run", kmh, dist));
				check("planing", ship.getY() - emptyY > 0.2, String.format("she rose %.2f blocks out of the water", ship.getY() - emptyY));
				Vec3d l = ship.toLocal(probe.getPos());
				check("carried", Math.abs(l.x - 3.5) < 1.5 && Math.abs(l.z - 1.5) < 1.5 && Math.abs(l.y) < 0.4,
						String.format("item at local (%.2f, %.2f, %.2f)", l.x, l.y, l.z));
				startYaw = ship.shipYaw;
				ship.testControl(100, 1, 0);
			}
			if (tick == 450) {
				double dyaw = Math.toDegrees(ship.shipYaw - startYaw);
				check("turn at speed", Math.abs(dyaw) > 30, String.format("heading changed %.1f degrees in 2 s, now %.0f km/h", dyaw, ship.currentSpeed() * 72));
				ship.testControl(0, 0, 0);
			}
			if (tick == 660) {
				check("stops", Math.abs(ship.currentSpeed()) < 0.01, String.format("speed %.4f after 10 s in neutral", ship.currentSpeed()));
				check("off the plane", ship.getY() - emptyY < 0.05, String.format("%.2f above rest", ship.getY() - emptyY));
				ship.testControl(-40, 0, 0);
			}
			if (tick == 760) {
				check("astern", ship.currentSpeed() < -0.15, String.format("speed %.3f b/t", ship.currentSpeed()));
				ship.testControl(0, 0, 0);
				ship.setLights(false);
				var lamp = ship.structure.get(0, -1, 1);
				check("lights off", !ship.lightsOn() && !lamp.isOf(net.minecraft.block.Blocks.SEA_LANTERN), "lamp now " + lamp);
				ship.setLights(true);
				check("lights on", ship.lightsOn() && ship.structure.get(0, -1, 1).isOf(net.minecraft.block.Blocks.SEA_LANTERN), "lamp " + ship.structure.get(0, -1, 1));
			}
			if (tick == 860) {
				// back to the start, for the client test
				ship.refreshPositionAndAngles(20, ship.getY(), 0.5, ship.getYaw(), 0);
				log.add("Boat at " + ship.getBlockPos().toShortString() + " heading " + Math.toDegrees(ship.shipYaw));
			}
			if (tick == 920) {
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\n" + (failed ? "RESULT FAIL" : "RESULT PASS") + "\n");
				server.saveAll(false, true, true);
				server.stop(false);
			}
		} catch (Throwable t) {
			Speedboat.LOGGER.error("SELFTEST fout", t);
			log.add("FAIL exception " + t);
			try {
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\nRESULT FAIL\n");
			} catch (IOException ignored) {
			}
			server.stop(false);
		}
	}
}
