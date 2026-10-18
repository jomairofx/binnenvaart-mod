package nl.binnenvaart.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import nl.binnenvaart.Binnenvaart;
import nl.binnenvaart.net.ShipPayloads;
import nl.binnenvaart.ship.ShipEntity;
import nl.binnenvaart.ship.ShipHolder;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

public class BinnenvaartClient implements ClientModInitializer {
	private static KeyBinding thrusterLeft, thrusterRight, horn, hatches;
	private static boolean prevForward, prevBack;
	private static int throttle;
	private static int lastShipId = -1;
	private static final Map<ShipEntity, ShipEngineSound> SOUNDS = new WeakHashMap<>();

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(Binnenvaart.SHIP, ShipRenderer::new);
		ShipEntity.clientSender = ClientPlayNetworking::send;

		thrusterLeft = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.binnenvaart.boeg_links", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.binnenvaart"));
		thrusterRight = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.binnenvaart.boeg_rechts", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "category.binnenvaart"));
		horn = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.binnenvaart.hoorn", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.binnenvaart"));
		hatches = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.binnenvaart.luiken", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.binnenvaart"));

		ClientPlayNetworking.registerGlobalReceiver(ShipPayloads.Blocks.ID, (payload, context) -> {
			var world = context.client().world;
			if (world != null && world.getEntityById(payload.entityId()) instanceof ShipEntity ship)
				ship.applyBlockChanges(payload.positions(), payload.states());
		});

		ClientTickEvents.END_CLIENT_TICK.register(BinnenvaartClient::tick);
		HudRenderCallback.EVENT.register((ctx, tickCounter) -> renderHud(ctx));
	}

	private static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) return;
		// motorgeluid voor schepen in de buurt
		for (ShipEntity ship : ShipHolder.of(mc.world)) {
			ShipEngineSound s = SOUNDS.get(ship);
			if ((s == null || s.isDone()) && ship.squaredDistanceTo(mc.player) < 100 * 100) {
				s = new ShipEngineSound(ship);
				SOUNDS.put(ship, s);
				mc.getSoundManager().play(s);
			}
		}

		if (!(mc.player.getVehicle() instanceof ShipEntity ship)) {
			lastShipId = -1;
			// toetsen niet laten opstapelen
			while (horn.wasPressed()) { }
			while (hatches.wasPressed()) { }
			return;
		}
		if (lastShipId != ship.getId()) {
			lastShipId = ship.getId();
			throttle = ship.syncedThrottle();
			prevForward = mc.options.forwardKey.isPressed();
			prevBack = mc.options.backKey.isPressed();
		}
		boolean fw = mc.options.forwardKey.isPressed(), bk = mc.options.backKey.isPressed();
		if (mc.currentScreen == null) {
			if (fw && !prevForward) throttle = Math.min(3, throttle + 1);
			if (bk && !prevBack) throttle = Math.max(-2, throttle - 1);
		}
		prevForward = fw;
		prevBack = bk;
		int rudder = 0, thruster = 0;
		if (mc.currentScreen == null) {
			if (mc.options.leftKey.isPressed()) rudder -= 1;
			if (mc.options.rightKey.isPressed()) rudder += 1;
			if (thrusterLeft.isPressed()) thruster -= 1;
			if (thrusterRight.isPressed()) thruster += 1;
		}
		boolean h = false, l = false;
		while (horn.wasPressed()) h = true;
		while (hatches.wasPressed()) l = true;
		ClientPlayNetworking.send(new ShipPayloads.Control(ship.getId(), throttle, rudder, thruster, h, l));
	}

	// ------------------------------------------------------------------ HUD

	private static void renderHud(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) return;
		if (!(mc.player.getVehicle() instanceof ShipEntity ship)) return;
		TextRenderer tr = mc.textRenderer;
		int w = 236, hgt = 104;
		int x = 6, y = ctx.getScaledWindowHeight() - hgt - 6;
		ctx.fill(x, y, x + w, y + hgt, 0xB0101820);
		ctx.fill(x, y, x + w, y + 1, 0xFF3D7BD9);
		String where = ship.isAtBowHelm(mc.player) ? "Stuurstand voor" : "Stuurhuis";
		ctx.drawTextWithShadow(tr, ship.template.name + "  ·  " + where, x + 6, y + 5, 0xFFFFFF);

		// telegraaf
		int thr = ship.syncedThrottle();
		String[] labels = {"<<", "<", "STOP", ">", ">>", ">>>"};
		int bx = x + 6, by = y + 18;
		for (int i = 0; i < 6; i++) {
			int bw = i == 2 ? 34 : 30;
			boolean on = i == thr + 2;
			int col = on ? (i < 2 ? 0xFFD9822B : i == 2 ? 0xFFB0B0B0 : 0xFF3DBE6A) : 0xFF2A333D;
			ctx.fill(bx, by, bx + bw, by + 12, col);
			int tw = tr.getWidth(labels[i]);
			ctx.drawText(tr, labels[i], bx + (bw - tw) / 2, by + 2, on ? 0x101010 : 0x9AA5B0, false);
			bx += bw + 3;
		}
		ctx.drawTextWithShadow(tr, ShipEntity.STEP_NAMES[thr + 2], x + 6, y + 33, 0xD8E0E8);

		double kmh = Math.abs(ship.syncedSpeed()) * 20 * 3.6;
		double heading = ((ship.shipYaw * 180 / Math.PI) - 90 + 180) % 360;
		if (heading < 0) heading += 360;
		ctx.drawTextWithShadow(tr, String.format("Snelheid %.1f km/u%s", kmh, ship.syncedSpeed() < -0.001 ? " achteruit" : ""), x + 6, y + 46, 0xFFFFFF);
		ctx.drawTextWithShadow(tr, String.format("Koers %03.0f°", heading), x + 150, y + 46, 0xFFFFFF);

		// roerstand
		float r = ship.syncedRudder();
		int rx = x + 6, ry = y + 59, rw = 120;
		ctx.fill(rx, ry, rx + rw, ry + 6, 0xFF2A333D);
		ctx.fill(rx + rw / 2, ry - 1, rx + rw / 2 + 1, ry + 7, 0xFF8090A0);
		int pos = rx + rw / 2 + Math.round(r * (rw / 2 - 2));
		ctx.fill(pos - 2, ry - 2, pos + 2, ry + 8, 0xFFFFD34D);
		int deg = Math.round(Math.abs(r) * 35);
		ctx.drawTextWithShadow(tr, "Roer " + deg + "°" + (deg == 0 ? "" : r < 0 ? " BB" : " SB"), rx + rw + 6, ry - 1, 0xFFFFFF);

		int th = ship.syncedThruster();
		ctx.drawTextWithShadow(tr, "Boegschroef " + (th < 0 ? "<< BB" : th > 0 ? "SB >>" : "uit"), x + 6, y + 70, th == 0 ? 0x9AA5B0 : 0x7FD6FF);
		String hatch = ship.hatchProgress >= 0.999f ? "dicht" : ship.hatchProgress <= 0.001f ? "open" : "bewegen...";
		ctx.drawTextWithShadow(tr, "Luiken " + hatch, x + 150, y + 70, 0xFFFFFF);
		ctx.drawTextWithShadow(tr, String.format("Lading %d%%   Diepgang %.1f m", Math.round(ship.load() * 100), ship.draft()), x + 6, y + 81, 0xFFFFFF);
		if (ship.isAground()) ctx.drawTextWithShadow(tr, "AAN DE GROND", x + 160, y + 81, 0xFF5555);
		ctx.drawText(tr, "W/S telegraaf  A/D roer  Z/X boeg  H hoorn  L luiken", x + 6, y + 93, 0x7F8C99, false);
	}
}
