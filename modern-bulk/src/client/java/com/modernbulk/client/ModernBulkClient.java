package com.modernbulk.client;

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
import com.modernbulk.ModernBulk;
import com.modernbulk.net.ShipPayloads;
import com.modernbulk.ship.ShipEntity;
import com.modernbulk.ship.ShipHolder;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

public class ModernBulkClient implements ClientModInitializer {
	static KeyBinding thrusterLeft, thrusterRight, horn, hatches, liftUp, liftDown, lights, engine;
	private static boolean prevForward, prevBack, prevHornKey, prevHatchKey, prevLightKey, prevEngineKey;
	private static int throttle;
	public static boolean testEngine;
	private static int lastShipId = -1;
	private static final Map<ShipEntity, ShipEngineSound> SOUNDS = new WeakHashMap<>();

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModernBulk.SHIP, ShipRenderer::new);
		ShipEntity.clientSender = ClientPlayNetworking::send;

		thrusterLeft = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernbulk.thruster_port", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, "category.modernbulk"));
		thrusterRight = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernbulk.thruster_starboard", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "category.modernbulk"));
		horn = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernbulk.horn", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.modernbulk"));
		hatches = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernbulk.hatches", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.modernbulk"));
		liftUp = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernbulk.lift_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, "category.modernbulk"));
		liftDown = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernbulk.lift_down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, "category.modernbulk"));
		lights = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernbulk.lights", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.modernbulk"));
		engine = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernbulk.engine", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.modernbulk"));

		ClientPlayNetworking.registerGlobalReceiver(ShipPayloads.Blocks.ID, (payload, context) -> {
			var world = context.client().world;
			if (world != null && world.getEntityById(payload.entityId()) instanceof ShipEntity ship)
				ship.applyBlockChanges(payload.positions(), payload.states());
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> ShipEntity.safely("client tick", () -> tick(mc)));
		ClientSelfTest.register();
		HudRenderCallback.EVENT.register((ctx, tickCounter) -> ShipEntity.safely("hud", () -> renderHud(ctx)));
	}

	static int testLift;
	static boolean testLights;

	/** Alleen voor de automatische test. */
	static void testThrottle(int t) {
		throttle = t;
	}

	private static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) return;
		// motorgeluid voor schepen in de buurt
		for (ShipEntity ship : ShipHolder.of(mc.world)) {
			ShipEngineSound s = SOUNDS.get(ship);
			if ((s == null || s.isDone()) && ship.squaredDistanceTo(mc.player) < 100 * 100) {
				s = new ShipEngineSound(ship, false);
				SOUNDS.put(ship, s);
				mc.getSoundManager().play(s);
				mc.getSoundManager().play(new ShipEngineSound(ship, true));
			}
		}

		if (!(mc.player.getVehicle() instanceof ShipEntity ship)) {
			lastShipId = -1;
			// toetsen niet laten opstapelen
			while (horn.wasPressed()) { }
			while (hatches.wasPressed()) { }
			while (lights.wasPressed()) { }
			prevHornKey = held(mc, horn);
			prevHatchKey = held(mc, hatches);
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
			if (held(mc, thrusterLeft)) thruster -= 1;
			if (held(mc, thrusterRight)) thruster += 1;
		}
		boolean h = false, l = false;
		while (horn.wasPressed()) h = true;
		while (hatches.wasPressed()) l = true;
		// Meerdere scheepsmods gebruiken dezelfde toetsen; Minecraft geeft een toets dan maar aan één van hen door.
		// Daarom kijken we ook rechtstreeks of de toets is ingedrukt.
		boolean hk = mc.currentScreen == null && held(mc, horn), lk = mc.currentScreen == null && held(mc, hatches);
		if (hk && !prevHornKey) h = true;
		if (lk && !prevHatchKey) l = true;
		prevHornKey = hk;
		prevHatchKey = lk;
		int liftDir = 0;
		boolean lt = false;
		if (mc.currentScreen == null) {
			if (held(mc, liftUp)) liftDir += 1;
			if (held(mc, liftDown)) liftDir -= 1;
		}
		if (testLift != 0) liftDir = testLift;
		while (lights.wasPressed()) lt = true;
		boolean lightKey = mc.currentScreen == null && held(mc, lights);
		if (lightKey && !prevLightKey) lt = true;
		prevLightKey = lightKey;
		boolean et = false;
		while (engine.wasPressed()) et = true;
		boolean engineKey = mc.currentScreen == null && held(mc, engine);
		if (engineKey && !prevEngineKey) et = true;
		prevEngineKey = engineKey;
		if (testEngine) {
			et = true;
			testEngine = false;
		}
		if (!ship.engineOn() && !et) throttle = 0;
		if (testLights) {
			lt = true;
			testLights = false;
		}
		ClientPlayNetworking.send(new ShipPayloads.Control(ship.getId(), throttle, rudder, thruster, h, l, liftDir, lt, et));
	}

	/** Is de toets ingedrukt, ook als een andere mod dezelfde toets heeft. */
	static boolean held(MinecraftClient mc, KeyBinding kb) {
		if (kb.isPressed()) return true;
		InputUtil.Key k = KeyBindingHelper.getBoundKeyOf(kb);
		if (k.getCategory() != InputUtil.Type.KEYSYM || k.getCode() < 0) return false;
		return InputUtil.isKeyPressed(mc.getWindow().getHandle(), k.getCode());
	}

	private static String keyName(KeyBinding kb) {
		int code = KeyBindingHelper.getBoundKeyOf(kb).getCode();
		if (code == GLFW.GLFW_KEY_LEFT) return "←";
		if (code == GLFW.GLFW_KEY_RIGHT) return "→";
		return kb.getBoundKeyLocalizedText().getString();
	}

	/** Compact paneel links in het midden van het scherm. */
	private static void renderHud(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) return;
		if (!(mc.player.getVehicle() instanceof ShipEntity ship)) return;
		TextRenderer tr = mc.textRenderer;
		float sc = 0.75f;
		var m = ctx.getMatrices();
		m.push();
		m.scale(sc, sc, 1f);
		int w = 286, hgt = 126;
		int x = Math.round(6 / sc), y = Math.round(ctx.getScaledWindowHeight() / 2f / sc - hgt / 2f);
		ctx.fill(x, y, x + w, y + hgt, 0xB0101820);
		ctx.fill(x, y, x + 2, y + hgt, 0xFF3D7BD9);
		int lx = x + 7;
		String where = ship.isAtBowHelm(mc.player) ? "Bow helm" : "Wheelhouse";
		ctx.drawTextWithShadow(tr, ship.template.name + " · " + where, lx, y + 5, 0xFFFFFF);
		// telegraaf
		int thr = ship.syncedThrottle();
		String[] labels = {"<<", "<", "■", ">", ">>", ">>>"};
		int bx = lx, by = y + 17;
		for (int i = 0; i < 6; i++) {
			int bw = 25;
			boolean on = i == thr + 2;
			int col = on ? (i < 2 ? 0xFFD9822B : i == 2 ? 0xFFB0B0B0 : 0xFF3DBE6A) : 0xFF2A333D;
			ctx.fill(bx, by, bx + bw, by + 11, col);
			int tw = tr.getWidth(labels[i]);
			ctx.drawText(tr, labels[i], bx + (bw - tw) / 2, by + 2, on ? 0x101010 : 0x9AA5B0, false);
			bx += bw + 2;
		}
		ctx.drawTextWithShadow(tr, ShipEntity.STEP_NAMES[thr + 2], lx, y + 31, 0xD8E0E8);
		double kmh = Math.abs(ship.syncedSpeed()) * 20 * 3.6;
		double heading = ((ship.shipYaw * 180 / Math.PI) - 90 + 180) % 360;
		if (heading < 0) heading += 360;
		ctx.drawTextWithShadow(tr, String.format("%.1f km/h%s", kmh, ship.syncedSpeed() < -0.001 ? " astern" : ""), lx, y + 43, 0xFFFFFF);
		ctx.drawTextWithShadow(tr, String.format("Heading %03.0f°", heading), lx + 100, y + 43, 0xFFFFFF);
		// roer
		float r = ship.syncedRudder();
		int ry = y + 57, rw = 90;
		ctx.fill(lx, ry, lx + rw, ry + 5, 0xFF2A333D);
		ctx.fill(lx + rw / 2, ry - 1, lx + rw / 2 + 1, ry + 6, 0xFF8090A0);
		int pos = lx + rw / 2 + Math.round(r * (rw / 2 - 2));
		ctx.fill(pos - 2, ry - 2, pos + 2, ry + 7, 0xFFFFD34D);
		int deg = Math.round(Math.abs(r) * 35);
		ctx.drawTextWithShadow(tr, "Rudder " + deg + "°" + (deg == 0 ? "" : r < 0 ? " P" : " S"), lx + rw + 6, ry - 1, 0xFFFFFF);
		// boegschroef: pijlen lichten op
		int th = ship.syncedThruster();
		ctx.drawTextWithShadow(tr, "Thruster", lx, y + 69, 0xFFFFFF);
		int ax = lx + tr.getWidth("Thruster") + 6;
		ctx.fill(ax, y + 68, ax + 14, y + 78, th < 0 ? 0xFF3DBE6A : 0xFF2A333D);
		ctx.drawText(tr, "◀", ax + 3, y + 69, th < 0 ? 0x101010 : 0x9AA5B0, false);
		ctx.fill(ax + 16, y + 68, ax + 30, y + 78, th > 0 ? 0xFF3DBE6A : 0xFF2A333D);
		ctx.drawText(tr, "▶", ax + 19, y + 69, th > 0 ? 0x101010 : 0x9AA5B0, false);
		ctx.drawText(tr, th == 0 ? "off" : "", ax + 34, y + 69, 0x9AA5B0, false);
		String extra;
		if (ShipEntity.COVERS > 0) {
			extra = "Hatches " + (ship.hatchProgress >= 0.999f ? "closed" : ship.hatchProgress <= 0.001f ? "open" : "moving");
		} else {
			StringBuilder sb = new StringBuilder("Tanks");
			for (int i = 0; i < ship.template.holds.length; i++) sb.append(' ').append(Math.round(ship.fill(i) * 100)).append('%');
			extra = sb.toString();
		}
		ctx.drawTextWithShadow(tr, extra, lx + 112, y + 69, 0xFFFFFF);
		ctx.drawTextWithShadow(tr, String.format("Cargo %d%%  Draught %.1f m", Math.round(ship.load() * 100), ship.draft()), lx, y + 81, 0xFFFFFF);
		if (ship.isAground()) ctx.drawTextWithShadow(tr, "AGROUND", lx + 120, y + 81, 0xFF5555);
		String keys = String.format("W/S telegraph  A/D rudder  %s/%s thruster  H horn", keyName(thrusterLeft), keyName(thrusterRight)) + (ShipEntity.COVERS > 0 ? "  L hatches" : "");
		ctx.drawText(tr, keys, lx, y + 93, 0x7F8C99, false);
		ctx.drawText(tr, "F5 camera  (scroll wheel: zoom)", lx, y + 103, 0x7F8C99, false);
		String liftTxt = ship.template.liftRegion != null ? String.format("Wheelhouse %s/%s (%.1f m)  ", keyName(liftUp), keyName(liftDown), ship.liftOffset()) : "";
		ctx.drawText(tr, liftTxt + "Lights " + keyName(lights) + ": " + (ship.lightsOn() ? "on" : "off") + "  Engine " + keyName(engine) + ": " + (ship.engineOn() ? "on" : "OFF"), lx, y + 115, 0x7F8C99, false);
		m.pop();
	}
}
