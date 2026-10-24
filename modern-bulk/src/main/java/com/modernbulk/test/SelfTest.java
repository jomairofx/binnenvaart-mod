package com.modernbulk.test;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.modernbulk.ModernBulk;
import com.modernbulk.ship.ShipEntity;
import com.modernbulk.ship.ShipHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatische test op een server (alleen actief met -Dmodernbulk.selftest=server).
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
		String mode = System.getProperty("modernbulk.selftest", "");
		if (!mode.equals("server")) return;
		ServerTickEvents.END_SERVER_TICK.register(SelfTest::onTick);
	}

	private static void check(String name, boolean ok, String detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + " - " + detail;
		log.add(line);
		ModernBulk.LOGGER.info("SELFTEST {}", line);
		if (!ok) failed = true;
	}

	private static void onTick(MinecraftServer server) {
		tick++;
		ServerWorld w = server.getOverworld();
		try {
			if (tick == 1) {
				for (int cx = -4; cx <= 16; cx++) for (int cz = -6; cz <= 6; cz++) w.setChunkForced(cx, cz, true);
			}
			if (tick == 60) {
				int top = -64;
				BlockPos.Mutable m = new BlockPos.Mutable(60, -40, 0);
				while (m.getY() > -64 && !w.getFluidState(m).isIn(FluidTags.WATER)) m.move(0, -1, 0);
				top = m.getY();
				check("water", w.getFluidState(m).isIn(FluidTags.WATER), "wateroppervlak op y=" + top);
				ship = new ShipEntity(ModernBulk.SHIP, w);
				ship.place(60, top, 0.5, 0);
				boolean blocked = ship.hullBlocked(ship.getX(), ship.getY(), ship.getZ(), ship.shipYaw);
				check("ruimte", !blocked, "romp vrij bij plaatsen");
				w.spawnEntity(ship);
				emptyY = ship.getY();
			}
			if (tick == 70) {
				check("geregistreerd", ShipHolder.of(w).contains(ship), "schip in lijst");
				check("waterverplaatsing", ship.displacedCount() > 500, ship.displacedCount() + " waterblokken verplaatst");
				Vec3d hold = ship.toWorld(40.5, -2.5, 9.5);
				var st = w.getBlockState(BlockPos.ofFloored(hold));
				check("droog ruim", st.isOf(ModernBulk.DISPLACED_WATER) || st.isAir(), "onder in het ruim: " + st);
				Vec3d drop = ship.toWorld(40.5, 1.5, 9.5);
				probe = new ItemEntity(w, drop.x, drop.y, drop.z, new ItemStack(Items.DIAMOND));
				probe.setVelocity(0, 0, 0);
				probe.setNeverDespawn();
				w.spawnEntity(probe);
			}
			if (tick == 130) {
				Vec3d l = ship.toLocal(probe.getPos());
				check("botsing ruimvloer", Math.abs(l.y - (-3.0)) < 0.15, String.format("voorwerp ligt op lokale y=%.3f (vloer -3)", l.y));
				Vec3d deck = ship.toWorld(60.5, 3.0, 1.5);
				ItemEntity d2 = new ItemEntity(w, deck.x, deck.y, deck.z, new ItemStack(Items.EMERALD));
				d2.setVelocity(0, 0, 0);
				w.spawnEntity(d2);
				start = ship.getPos();
				startYaw = ship.shipYaw;
				ship.testControl(3, 0, 0);
			}
			if (tick == 430) {
				double dist = ship.getPos().distanceTo(start);
				check("varen", dist > 25, String.format("schip voer %.1f blokken in 15 s (snelheid %.3f b/t)", dist, ship.currentSpeed()));
				Vec3d l = ship.toLocal(probe.getPos());
				check("meenemen", Math.abs(l.x - 40.5) < 1.5 && Math.abs(l.z - 9.5) < 1.5 && Math.abs(l.y + 3) < 0.3,
						String.format("voorwerp op lokaal (%.2f, %.2f, %.2f)", l.x, l.y, l.z));
				ship.testControl(2, 1, 0);
			}
			if (tick == 630) {
				double dyaw = Math.toDegrees(ship.shipYaw - startYaw);
				check("draaien", Math.abs(dyaw) > 10, String.format("koers %.1f graden veranderd", dyaw));
				ship.testControl(0, 0, 1);
				for (int i = 0; i < ShipEntity.HOLD_SLOTS; i++) ship.hold(0).setStack(i, new ItemStack(Items.COAL, 64));
				ship.hold(0).markDirty();
				for (int i = 0; i < 30; i++) ship.hold(1).setStack(i, new ItemStack(Items.SAND, 64));
				ship.hold(1).markDirty();
				for (int i = 0; i < 40; i++) ship.hold(2).setStack(i, i % 2 == 0 ? new ItemStack(Items.BREAD, 64) : new ItemStack(Items.IRON_PICKAXE));
				ship.hold(2).markDirty();
				// gemengde lading: zand los gestort, brood in containers
				for (int i = 0; i < 20; i++) ship.hold(3).setStack(i, new ItemStack(i % 2 == 0 ? Items.SAND : Items.BREAD, 64));
				ship.hold(3).markDirty();
				for (int i = 0; i < 10; i++) ship.hold(4).setStack(i, new ItemStack(Items.DIAMOND, 64));
				ship.hold(4).markDirty();
			}
			if (tick == 830) {
				check("vulling", ship.fill(0) > 0.99f && ship.material(0) > 0, "ruim 1: " + ship.fill(0) + " materiaal " + ship.material(0));
				check("containers", ship.material(2) == -1, "ruim 3 materiaal " + ship.material(2));
				check("gemengde lading", ship.material(3) > 0 && ship.containerShare(3) > 0.3f && ship.containerShare(3) < 0.7f,
						"ruim 4 stortgoed " + ship.material(3) + ", deel in containers " + ship.containerShare(3));
				check("edelstenen in containers", ship.material(4) == -1, "ruim 5 materiaal " + ship.material(4));
				check("diepgang", ship.getY() < emptyY - 0.4, String.format("schip zakte %.2f blok door lading", emptyY - ship.getY()));
				check("boegschroef", true, "koers nu " + Math.round(Math.toDegrees(ship.shipYaw)));
				ship.testControl(0, 0, 0);
			}
			if (tick == 1000) {
				check("stilstand", Math.abs(ship.currentSpeed()) < 0.01, String.format("snelheid %.4f", ship.currentSpeed()));
				ship.testSetLift(3.0);
			}
			if (tick == 1180) {
				check("stuurhut omhoog", ship.liftOffset() > 2.9, String.format("stuurhut staat %.2f blok omhoog", ship.liftOffset()));
				ship.setLights(false);
				var lamp = ship.structure.get(10, 5, 5);
				check("licht uit", !ship.lightsOn() && !lamp.isOf(net.minecraft.block.Blocks.SEA_LANTERN), "plafondlamp nu " + lamp);
				ship.setLights(true);
				check("licht aan", ship.lightsOn() && ship.structure.get(10, 5, 5).isOf(net.minecraft.block.Blocks.SEA_LANTERN), "plafondlamp weer " + ship.structure.get(10, 5, 5));
				ship.testSetLift(0);
			}
			if (tick == 1200) {
				int restored = 0;
				log.add("Schip ligt op " + ship.getBlockPos().toShortString() + " koers " + Math.toDegrees(ship.shipYaw));
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\n" + (failed ? "RESULT FAIL" : "RESULT PASS") + "\n");
				server.saveAll(false, true, true);
				server.stop(false);
			}
		} catch (Throwable t) {
			ModernBulk.LOGGER.error("SELFTEST fout", t);
			log.add("FAIL exception " + t);
			try {
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\nRESULT FAIL\n");
			} catch (IOException ignored) {
			}
			server.stop(false);
		}
	}
}
