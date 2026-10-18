package nl.kempenaar;

import net.fabricmc.api.ModInitializer;
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
import nl.kempenaar.block.DisplacedWaterBlock;
import nl.kempenaar.net.ShipPayloads;
import nl.kempenaar.ship.ShipEntity;
import nl.kempenaar.ship.ShipTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kempenaar implements ModInitializer {
	public static final String MOD_ID = "kempenaar";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Block DISPLACED_WATER = Registry.register(Registries.BLOCK, id("verplaatst_water"),
			new DisplacedWaterBlock(AbstractBlock.Settings.create().noCollision().nonOpaque().dropsNothing().replaceable()
					.strength(-1.0f, 3600000.0f).pistonBehavior(PistonBehavior.DESTROY).noBlockBreakParticles()));

	public static final EntityType<ShipEntity> SHIP = Registry.register(Registries.ENTITY_TYPE, id("kempenaar"),
			EntityType.Builder.<ShipEntity>create(ShipEntity::new, SpawnGroup.MISC)
					.dimensions(4.0f, 4.0f)
					.maxTrackingRange(16)
					.trackingTickInterval(1)
					.makeFireImmune()
					.build("kempenaar"));

	public static final Item SHIP_ITEM = Registry.register(Registries.ITEM, id("kempenaar"),
			new ShipItem(new Item.Settings().maxCount(1)));

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(SHIP_ITEM));

		PayloadTypeRegistry.playC2S().register(ShipPayloads.Control.ID, ShipPayloads.Control.CODEC);
		PayloadTypeRegistry.playC2S().register(ShipPayloads.Interact.ID, ShipPayloads.Interact.CODEC);
		PayloadTypeRegistry.playS2C().register(ShipPayloads.Blocks.ID, ShipPayloads.Blocks.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ShipPayloads.Control.ID, (payload, context) -> {
			if (context.player().getWorld().getEntityById(payload.entityId()) instanceof ShipEntity ship)
				ship.handleControl(context.player(), payload);
		});
		ServerPlayNetworking.registerGlobalReceiver(ShipPayloads.Interact.ID, (payload, context) -> {
			if (context.player().getWorld().getEntityById(payload.entityId()) instanceof ShipEntity ship)
				ship.handleInteract(context.player(), new Vec3d(payload.x(), payload.y(), payload.z()), payload.sneaking());
		});

		nl.kempenaar.test.SelfTest.register();

		ShipTemplate t = ShipTemplate.get();
		LOGGER.info("Kempenaar geladen: schip {} ({} m lang)", t.name, t.length);
	}
}
