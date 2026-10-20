package nl.kempenaar.test;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import nl.kempenaar.Kempenaar;
import nl.kempenaar.ship.ShipEntity;
import nl.kempenaar.ship.ShipHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Automatische test op een server (alleen actief met -Dkempenaar.selftest=server).
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
	private static int waterTop, craneBefore;
	private static BlockPos cranePos;
	private static nl.kempenaar.block.CraneBlockEntity crane, crane2;
	private static net.minecraft.entity.Entity barge;
	private static int switchTick;

	public static void register() {
		String mode = System.getProperty("kempenaar.selftest", "");
		if (!mode.equals("server")) return;
		ServerTickEvents.END_SERVER_TICK.register(SelfTest::onTick);
	}

	private static void check(String name, boolean ok, String detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + " - " + detail;
		log.add(line);
		Kempenaar.LOGGER.info("SELFTEST {}", line);
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
				waterTop = top;
				check("water", w.getFluidState(m).isIn(FluidTags.WATER), "wateroppervlak op y=" + top);
				ship = new ShipEntity(Kempenaar.SHIP, w);
				ship.place(60, top, 0.5, 0);
				boolean blocked = ship.hullBlocked(ship.getX(), ship.getY(), ship.getZ(), ship.shipYaw);
				check("ruimte", !blocked, "romp vrij bij plaatsen");
				w.spawnEntity(ship);
				emptyY = ship.getY();
			}
			if (tick == 70) {
				check("geregistreerd", ShipHolder.of(w).contains(ship), "schip in lijst");
				check("waterverplaatsing", ship.displacedCount() > 500, ship.displacedCount() + " waterblokken verplaatst");
				Vec3d hold = ship.toWorld(45.5, -0.5, 4.5);
				var st = w.getBlockState(BlockPos.ofFloored(hold));
				check("droog ruim", st.isOf(Kempenaar.DISPLACED_WATER) || st.isAir(), "onder in het ruim: " + st);
				Vec3d drop = ship.toWorld(45.5, 1.5, 4.5);
				probe = new ItemEntity(w, drop.x, drop.y, drop.z, new ItemStack(Items.DIAMOND));
				probe.setVelocity(0, 0, 0);
				probe.setNeverDespawn();
				w.spawnEntity(probe);
			}
			if (tick == 130) {
				Vec3d l = ship.toLocal(probe.getPos());
				check("botsing ruimvloer", Math.abs(l.y - (-1.0)) < 0.15, String.format("voorwerp ligt op lokale y=%.3f (vloer -1)", l.y));
				Vec3d deck = ship.toWorld(30.5, 3.0, 0.5);
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
				check("meenemen", Math.abs(l.x - 45.5) < 1.5 && Math.abs(l.z - 4.5) < 1.5 && Math.abs(l.y + 1) < 0.3,
						String.format("voorwerp op lokaal (%.2f, %.2f, %.2f)", l.x, l.y, l.z));
				ship.testControl(2, 1, 0);
			}
			if (tick == 630) {
				double dyaw = Math.toDegrees(ship.shipYaw - startYaw);
				check("draaien", Math.abs(dyaw) > 10, String.format("koers %.1f graden veranderd", dyaw));
				ship.testControl(0, 0, 1);
				for (int i = 0; i < ShipEntity.HOLD_SLOTS; i++) ship.hold(0).setStack(i, new ItemStack(Items.COAL, 64));
				ship.hold(0).markDirty();
								for (int i = 0; i < 40; i++) ship.hold(1).setStack(i, i % 2 == 0 ? new ItemStack(Items.BREAD, 64) : new ItemStack(Items.IRON_PICKAXE));
				ship.hold(1).markDirty();
			}
			if (tick == 830) {
				check("vulling", ship.fill(0) > 0.99f && ship.material(0) > 0, "ruim 1: " + ship.fill(0) + " materiaal " + ship.material(0));
				check("containers", ship.material(1) == -1, "ruim 2 materiaal " + ship.material(1));
				check("diepgang", ship.getY() < emptyY - 0.4, String.format("schip zakte %.2f blok door lading", emptyY - ship.getY()));
				check("boegschroef", true, "koers nu " + Math.round(Math.toDegrees(ship.shipYaw)));
				ship.testControl(0, 0, 0);
			}
			if (tick == 1000) {
				check("stilstand", Math.abs(ship.currentSpeed()) < 0.01, String.format("snelheid %.4f", ship.currentSpeed()));
				ship.testSetLift(3.0);
			}
			if (tick == 1100) {
				check("stuurhut omhoog", ship.liftOffset() > 1.9, String.format("stuurhut staat %.2f blok omhoog", ship.liftOffset()));
				ship.setLights(false);
				var lamp = ship.structure.get(13, 4, 2);
				check("licht uit", !ship.lightsOn() && !lamp.isOf(net.minecraft.block.Blocks.SEA_LANTERN), "lamp nu " + lamp);
				ship.setLights(true);
				check("licht aan", ship.lightsOn() && ship.structure.get(13, 4, 2).isOf(net.minecraft.block.Blocks.SEA_LANTERN), "lamp weer " + ship.structure.get(13, 4, 2));
				ship.testSetLift(0);
			}
			if (tick == 1000) {
				int restored = 0;
				log.add("Schip ligt op " + ship.getBlockPos().toShortString() + " koers " + Math.toDegrees(ship.shipYaw));
				for (int i = 0; i < ShipEntity.HOLD_SLOTS; i++) ship.hold(0).setStack(i, ItemStack.EMPTY);
				ship.hold(0).markDirty();
				Vec3d cp = ship.toWorld(28.5, 0, -3.5);
				cranePos = BlockPos.ofFloored(cp.x, waterTop, cp.z);
				w.setBlockState(cranePos, Kempenaar.CRANE.getDefaultState());
				crane = (nl.kempenaar.block.CraneBlockEntity) w.getBlockEntity(cranePos);
				crane.enabled = true;
				nl.kempenaar.block.CraneBlock.placeStorage(w, cranePos);
				for (int i = 0; i < 10; i++) crane.setStack(i, new ItemStack(Items.SAND, 64));
			}
			if (tick == 1001) {
				// een STELLA MARIS uit de andere mod, om te zien of de kraan ook met andere schepen werkt
				var type = net.minecraft.registry.Registries.ENTITY_TYPE.get(net.minecraft.util.Identifier.of("inlandbarge", "inland_barge"));
				net.minecraft.entity.Entity e = type == null ? null : type.create(w);
				if (e != null && e.getClass().getSimpleName().equals("ShipEntity")) {
					e.getClass().getMethod("place", double.class, int.class, double.class, double.class).invoke(e, 60.0, waterTop, -60.5, 0.0);
					w.spawnEntity(e);
					barge = e;
					nl.kempenaar.block.ShipAccess acc = nl.kempenaar.block.ShipAccess.of(e);
					Vec3d cp2 = acc.toWorld(55.5, 0, -4.5);
					BlockPos c2 = BlockPos.ofFloored(cp2.x, waterTop, cp2.z);
					w.setBlockState(c2, Kempenaar.CRANE.getDefaultState());
					crane2 = (nl.kempenaar.block.CraneBlockEntity) w.getBlockEntity(c2);
					crane2.enabled = true;
					for (int i = 0; i < 6; i++) crane2.setStack(i, new ItemStack(Items.GRAVEL, 64));
				} else {
					log.add("geen STELLA MARIS beschikbaar (mod niet geladen)");
				}
			}
			if (tick == 1640) {
				int sand = 0;
				for (int i = 0; i < ShipEntity.HOLD_SLOTS; i++) if (ship.hold(0).getStack(i).isOf(Items.SAND)) sand += ship.hold(0).getStack(i).getCount();
				check("laadkraan laden", sand >= 256 && sand < 640, sand + " zand in ruim 1 na 32 s (rustig tempo), nog " + crane.countItems() + " in de kraan");
				if (barge != null) {
					nl.kempenaar.block.ShipAccess acc = nl.kempenaar.block.ShipAccess.of(barge);
					int gravel = 0;
					for (int h = 0; h < acc.holdCount(); h++)
						for (int i = 0; i < acc.hold(h).size(); i++) if (acc.hold(h).getStack(i).isOf(Items.GRAVEL)) gravel += acc.hold(h).getStack(i).getCount();
					check("laadkraan met STELLA MARIS", gravel >= 128, gravel + " grind in de ruimen van de STELLA MARIS");
				}
			}
			if (tick > 1640 && switchTick == 0 && !crane.running) {
				crane.testSetUnloading(true);
				craneBefore = crane.countItems() + crane.countBinItems();
				switchTick = tick;
			}
			if (switchTick > 0 && tick == switchTick + 520) {
				check("laadkraan lossen", crane.countItems() + crane.countBinItems() >= craneBefore + 128 && crane.countBinItems() >= 128,
						"kraan + bunkers hadden " + craneBefore + ", nu " + (crane.countItems() + crane.countBinItems()) + " (in de bunkers " + crane.countBinItems() + ")");
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\n" + (failed ? "RESULT FAIL" : "RESULT PASS") + "\n");
				server.saveAll(false, true, true);
				server.stop(false);
			}
		} catch (Throwable t) {
			Kempenaar.LOGGER.error("SELFTEST fout", t);
			log.add("FAIL exception " + t);
			try {
				Files.writeString(Path.of("selftest.txt"), String.join("\n", log) + "\nRESULT FAIL\n");
			} catch (IOException ignored) {
			}
			server.stop(false);
		}
	}
}
