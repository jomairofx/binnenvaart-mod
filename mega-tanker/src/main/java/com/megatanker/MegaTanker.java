package com.megatanker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import com.megatanker.block.DisplacedWaterBlock;
import com.megatanker.net.ShipPayloads;
import com.megatanker.ship.ShipEntity;
import com.megatanker.ship.ShipTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MegaTanker implements ModInitializer {
	public static final String MOD_ID = "megatanker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Block DISPLACED_WATER = Registry.register(Registries.BLOCK, id("displaced_water"),
			new DisplacedWaterBlock(AbstractBlock.Settings.create().noCollision().nonOpaque().dropsNothing().replaceable()
					.strength(-1.0f, 3600000.0f).pistonBehavior(PistonBehavior.DESTROY).noBlockBreakParticles()));

	public static final EntityType<ShipEntity> SHIP = Registry.register(Registries.ENTITY_TYPE, id("mega_oil_tanker"),
			EntityType.Builder.<ShipEntity>create(ShipEntity::new, SpawnGroup.MISC)
					.dimensions(4.0f, 4.0f)
					.maxTrackingRange(16)
					.trackingTickInterval(1)
					.makeFireImmune()
					.build("mega_oil_tanker"));

	public static final Item SHIP_ITEM = Registry.register(Registries.ITEM, id("mega_oil_tanker"),
			new ShipItem(new Item.Settings().maxCount(1)));

	/** Slaapplaats aan boord: schip, bed en laatst bekende plek. Blijft bewaard na doodgaan. */
	public static final AttachmentType<NbtCompound> SHIP_BED = AttachmentRegistry.<NbtCompound>builder()
			.persistent(NbtCompound.CODEC).copyOnDeath().buildAndRegister(id("scheepsbed"));

	private static final Map<UUID, Integer> PENDING_RESPAWN = new HashMap<>();

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	/** Na doodgaan: terug naar het bed aan boord, ook als het schip intussen verder is gevaren. */
	private static void respawnTick(MinecraftServer server) {
		if (PENDING_RESPAWN.isEmpty()) return;
		Iterator<Map.Entry<UUID, Integer>> it = PENDING_RESPAWN.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> e = it.next();
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
			NbtCompound tag = p == null ? null : p.getAttached(SHIP_BED);
			if (tag == null) {
				it.remove();
				continue;
			}
			int n = e.getValue();
			e.setValue(n + 1);
			UUID shipId = tag.getUuid("Ship");
			ShipEntity ship = null;
			ServerWorld shipWorld = null;
			for (ServerWorld w : server.getWorlds()) {
				Entity en = w.getEntity(shipId);
				if (en instanceof ShipEntity s) {
					ship = s;
					shipWorld = w;
				}
			}
			if (ship != null) {
				int[] h = tag.getIntArray("Bed");
				Vec3d spot = h.length == 3 ? ship.bedWakeSpot(h) : ship.toWorld(9.5, 1.01, 2.5);
				p.teleport(shipWorld, spot.x, spot.y, spot.z, p.getYaw(), p.getPitch());
				p.sendMessage(Text.literal("You wake up on board the " + ship.template.name).formatted(Formatting.AQUA), true);
				it.remove();
			} else if (n == 0) {
				// schip ligt in een niet-geladen gebied: daarheen, dan laadt het vanzelf
				for (ServerWorld w : server.getWorlds()) {
					if (w.getRegistryKey().getValue().toString().equals(tag.getString("Dim")))
						p.teleport(w, tag.getDouble("X"), tag.getDouble("Y") + 1, tag.getDouble("Z"), p.getYaw(), p.getPitch());
				}
			} else if (n > 200) {
				p.sendMessage(Text.literal("Your ship could not be found; your bed on board has been reset").formatted(Formatting.RED), false);
				p.removeAttached(SHIP_BED);
				it.remove();
			}
		}
	}

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(SHIP_ITEM));

		// slapen in de bedden aan boord (die staan niet echt in de wereld)
		EntitySleepEvents.ALLOW_BED.register((entity, pos, state, vanilla) ->
				!vanilla && ShipEntity.findBed(entity.getWorld(), pos) != null ? ActionResult.SUCCESS : ActionResult.PASS);
		EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register((entity, pos, dir) -> {
			if (dir != null) return dir;
			ShipEntity.BedRef ref = ShipEntity.findBed(entity.getWorld(), pos);
			return ref != null ? ref.ship().bedDirection(ref.head()) : null;
		});
		EntitySleepEvents.MODIFY_WAKE_UP_POSITION.register((entity, pos, bedState, wake) -> {
			ShipEntity.BedRef ref = ShipEntity.findBed(entity.getWorld(), pos);
			return ref != null ? ref.ship().bedWakeSpot(ref.head()) : wake;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && newPlayer.getAttached(SHIP_BED) != null) PENDING_RESPAWN.put(newPlayer.getUuid(), 0);
		});
		ServerTickEvents.END_SERVER_TICK.register(MegaTanker::respawnTick);
		ServerTickEvents.END_SERVER_TICK.register(com_ship_beds -> com.megatanker.ship.ShipEntity.keepSleepers(com_ship_beds));

		PayloadTypeRegistry.playC2S().register(ShipPayloads.Control.ID, ShipPayloads.Control.CODEC);
		PayloadTypeRegistry.playC2S().register(ShipPayloads.Interact.ID, ShipPayloads.Interact.CODEC);
		PayloadTypeRegistry.playS2C().register(ShipPayloads.Blocks.ID, ShipPayloads.Blocks.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ShipPayloads.Control.ID, (payload, context) -> {
			if (context.player().getWorld().getEntityById(payload.entityId()) instanceof ShipEntity ship)
				ShipEntity.safely("control", () -> ship.handleControl(context.player(), payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(ShipPayloads.Interact.ID, (payload, context) -> {
			if (context.player().getWorld().getEntityById(payload.entityId()) instanceof ShipEntity ship)
				ShipEntity.safely("interact", () -> ship.handleInteract(context.player(), new Vec3d(payload.x(), payload.y(), payload.z()), payload.sneaking()));
		});

		com.megatanker.test.SelfTest.register();

		ShipTemplate t = ShipTemplate.get();
		LOGGER.info("MegaTanker geladen: schip {} ({} m lang)", t.name, t.length);
	}
}
