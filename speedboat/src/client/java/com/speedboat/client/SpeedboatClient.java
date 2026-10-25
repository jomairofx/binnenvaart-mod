package com.speedboat.client;

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
import net.minecraft.util.math.MathHelper;
import com.speedboat.Speedboat;
import com.speedboat.net.ShipPayloads;
import com.speedboat.ship.ShipEntity;
import com.speedboat.ship.ShipHolder;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.WeakHashMap;

public class SpeedboatClient implements ClientModInitializer {
	static KeyBinding horn, lights, engine;
	private static boolean prevHornKey, prevLightKey, prevEngineKey;
	/** The throttle lever, -40 .. 100 %: stays where you put it (keys and D-pad). */
	private static int throttle;
	/** Neutral detent: going through neutral needs the key let go first. */
	private static boolean detent;
	public static boolean testEngine;
	private static int lastShipId = -1;
	private static final Map<ShipEntity, ShipEngineSound> SOUNDS = new WeakHashMap<>();

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(Speedboat.SHIP, ShipRenderer::new);
		ShipEntity.clientSender = ClientPlayNetworking::send;

		horn = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.speedboat.horn", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.speedboat"));
		lights = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.speedboat.lights", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.speedboat"));
		engine = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.speedboat.engine", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.speedboat"));

		ClientPlayNetworking.registerGlobalReceiver(ShipPayloads.Blocks.ID, (payload, context) -> {
			var world = context.client().world;
			if (world != null && world.getEntityById(payload.entityId()) instanceof ShipEntity ship)
				ship.applyBlockChanges(payload.positions(), payload.states());
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> ShipEntity.safely("client tick", () -> tick(mc)));
		ClientSelfTest.register();
		HudRenderCallback.EVENT.register((ctx, tickCounter) -> ShipEntity.safely("hud", () -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (mc.player != null && mc.player.getVehicle() instanceof ShipEntity) Gamepad.frame(mc);
			renderHud(ctx);
		}));
	}

	static int testLift;
	static boolean testLights;

	/** Alleen voor de automatische test. */
	static void testThrottle(int t) {
		throttle = t;
	}

	private static boolean padSneak;
	private static float padRudder;

	private static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) return;
		Gamepad.tick();
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
			if (padSneak) {
				mc.options.sneakKey.setPressed(false);
				padSneak = false;
			}
			// toetsen niet laten opstapelen
			while (horn.wasPressed()) { }
			while (lights.wasPressed()) { }
			prevHornKey = held(mc, horn);
			return;
		}
		if (lastShipId != ship.getId()) {
			lastShipId = ship.getId();
			throttle = ship.syncedThrottle();
		}
		boolean fw = mc.currentScreen == null && mc.options.forwardKey.isPressed(), bk = mc.currentScreen == null && mc.options.backKey.isPressed();
		// W / S: push the throttle lever forward or pull it back while the key is held (about 2.5 s from neutral to wide open)
		if (fw || bk) {
			int next = throttle + (fw ? 2 : 0) - (bk ? 2 : 0);
			if (!detent && ((throttle > 0 && next <= 0) || (throttle < 0 && next >= 0))) {
				next = 0;
				detent = true;      // stops in neutral: let go and press again to go the other way
			}
			if (!detent || next == 0) throttle = MathHelper.clamp(next, ShipEntity.THROTTLE_MIN, ShipEntity.THROTTLE_MAX);
		} else detent = false;
		if (mc.currentScreen == null) {
			// controller: D-pad up / down moves the lever in steps of 10 %
			if (Gamepad.pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP)) throttle = Math.min(ShipEntity.THROTTLE_MAX, throttle + 10);
			if (Gamepad.pressed(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN)) throttle = Math.max(ShipEntity.THROTTLE_MIN, throttle - 10);
			if (Gamepad.pressed(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER) || Gamepad.pressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER)) throttle = 0;
		}
		int rudder = 0;
		if (mc.currentScreen == null) {
			if (mc.options.leftKey.isPressed()) rudder -= 1;
			if (mc.options.rightKey.isPressed()) rudder += 1;
		}
		int rudderOut = rudder * 100, throttleOut = throttle;
		if (Gamepad.connected() && mc.currentScreen == null) {
			float sx = Gamepad.stick(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
			// a light, quick boat: the wheel follows the stick closely, still a little softer round the middle
			float target = rudder != 0 ? 0 : (float) (Math.signum(sx) * Math.pow(Math.abs(sx), 1.25) * 100);
			padRudder += Math.max(-7f, Math.min(7f, target - padRudder));
			if (rudder == 0 && Math.abs(padRudder) > 0.5f) rudderOut = Math.round(padRudder);
			// R2 gas, L2 astern, like a car: while a trigger is pressed it overrides the lever
			float gas = Gamepad.trigger(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER), rev = Gamepad.trigger(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER);
			if (gas > 0 || rev > 0) throttleOut = MathHelper.clamp(Math.round(gas * 100 - rev * 40), ShipEntity.THROTTLE_MIN, ShipEntity.THROTTLE_MAX);
			Gamepad.camera(mc);
			// circle: leave the helm (as the sneak key)
			boolean leave = Gamepad.held(GLFW.GLFW_GAMEPAD_BUTTON_B);
			if (leave != padSneak) {
				mc.options.sneakKey.setPressed(leave);
				padSneak = leave;
			}
		}
		boolean h = Gamepad.pressed(GLFW.GLFW_GAMEPAD_BUTTON_Y);
		while (horn.wasPressed()) h = true;
		// Meerdere scheepsmods gebruiken dezelfde toetsen; Minecraft geeft een toets dan maar aan één van hen door.
		boolean hk = mc.currentScreen == null && held(mc, horn);
		if (hk && !prevHornKey) h = true;
		prevHornKey = hk;
		boolean lt = Gamepad.pressed(GLFW.GLFW_GAMEPAD_BUTTON_X);
		while (lights.wasPressed()) lt = true;
		boolean lightKey = mc.currentScreen == null && held(mc, lights);
		if (lightKey && !prevLightKey) lt = true;
		prevLightKey = lightKey;
		boolean et = Gamepad.pressed(GLFW.GLFW_GAMEPAD_BUTTON_START);
		while (engine.wasPressed()) et = true;
		boolean engineKey = mc.currentScreen == null && held(mc, engine);
		if (engineKey && !prevEngineKey) et = true;
		prevEngineKey = engineKey;
		if (testEngine) {
			et = true;
			testEngine = false;
		}
		if (!ship.engineOn() && !et) {
			throttle = 0;
			throttleOut = 0;
		}
		if (testLights) {
			lt = true;
			testLights = false;
		}
		ClientPlayNetworking.send(new ShipPayloads.Control(ship.getId(), throttleOut, rudderOut, 0, h, false, 0, lt, et));
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

	/** Compact panel at the left: throttle, speed, rev counter, wheel and compass. */
	private static void renderHud(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) return;
		if (!(mc.player.getVehicle() instanceof ShipEntity ship)) return;
		TextRenderer tr = mc.textRenderer;
		float sc = 0.75f;
		var m = ctx.getMatrices();
		m.push();
		m.scale(sc, sc, 1f);
		int w = 286, hgt = 112;
		int x = Math.round(6 / sc), y = Math.round(ctx.getScaledWindowHeight() / 2f / sc - hgt / 2f);
		ctx.fill(x, y, x + w, y + hgt, 0xB0101820);
		ctx.fill(x, y, x + 2, y + hgt, 0xFFD93D3D);
		int lx = x + 7;
		String where = ship.isAtBowHelm(mc.player) ? "Co-driver" : "Driver";
		ctx.drawTextWithShadow(tr, ship.template.name + " · " + where, lx, y + 5, 0xFFFFFF);
		// throttle: a bar from full astern through neutral to wide open
		int thr = ship.syncedThrottle();
		int bx = lx, by = y + 18, bw = 180;
		int zero = bx + Math.round(bw * 40f / 140f);
		ctx.fill(bx, by, bx + bw, by + 8, 0xFF2A333D);
		if (thr > 0) ctx.fill(zero, by, zero + Math.round((bw - (zero - bx)) * thr / 100f), by + 8, thr > 85 ? 0xFFE0563B : 0xFF3DBE6A);
		if (thr < 0) ctx.fill(zero - Math.round((zero - bx) * -thr / 40f), by, zero, by + 8, 0xFFD9822B);
		ctx.fill(zero, by - 2, zero + 1, by + 10, 0xFFB0B0B0);
		String tl = thr == 0 ? "Neutral" : thr > 0 ? "Ahead " + thr + "%" : "Astern " + (-thr * 100 / 40) + "%";
		ctx.drawTextWithShadow(tr, tl, bx + bw + 6, by, 0xD8E0E8);
		double kmh = Math.abs(ship.syncedSpeed()) * 20 * 3.6;
		double heading = ((ship.shipYaw * 180 / Math.PI) - 90 + 180) % 360;
		if (heading < 0) heading += 360;
		ctx.drawTextWithShadow(tr, String.format("%.0f km/h%s", kmh, ship.syncedSpeed() < -0.001 ? " astern" : ""), lx, y + 33, 0xFFFFFF);
		ctx.drawTextWithShadow(tr, String.format("%.0f kn", kmh / 1.852), lx + 62, y + 33, 0xAEB8C2);
		ctx.drawTextWithShadow(tr, String.format("Heading %03.0f°", heading), lx + 110, y + 33, 0xFFFFFF);
		// rev counter of the twin outboards
		int rpm = ship.engineOn() ? Math.round(650 + ship.engineLoad * 5850) / 50 * 50 : 0;
		ctx.drawTextWithShadow(tr, String.format("Twin outboards %d rpm", rpm), lx, y + 45, rpm > 6000 ? 0xFFE0563B : 0xFFFFFF);
		boolean plane = ship.syncedSpeed() > 0.6;
		ctx.drawTextWithShadow(tr, plane ? "On the plane" : ship.syncedSpeed() > 0.25 ? "Climbing onto the plane" : "", lx + 150, y + 45, 0x7FD8FF);
		// the wheel
		float r = ship.syncedRudder();
		int ry = y + 59, rw = 90;
		ctx.fill(lx, ry, lx + rw, ry + 5, 0xFF2A333D);
		ctx.fill(lx + rw / 2, ry - 1, lx + rw / 2 + 1, ry + 6, 0xFF8090A0);
		int pos = lx + rw / 2 + Math.round(r * (rw / 2 - 2));
		ctx.fill(pos - 2, ry - 2, pos + 2, ry + 7, 0xFFFFD34D);
		int deg = Math.round(Math.abs(r) * 30);
		ctx.drawTextWithShadow(tr, "Drives " + deg + "°" + (deg == 0 ? "" : r < 0 ? " P" : " S"), lx + rw + 6, ry - 1, 0xFFFFFF);
		if (ship.isAground()) ctx.drawTextWithShadow(tr, "AGROUND", lx + 190, ry - 1, 0xFF5555);
		if (Gamepad.connected()) {
			// PlayStation controls when a controller is plugged in
			ctx.drawText(tr, "R2 gas  L2 astern  L-stick steer  D-pad \u2191\u2193 throttle lever  L1/R1 neutral", lx, y + 73, 0x7F8C99, false);
			ctx.drawText(tr, "\u25B3 horn  R-stick look  R3 camera  \u25CB leave the wheel  (" + Gamepad.name() + ")", lx, y + 85, 0x7F8C99, false);
			ctx.drawText(tr, "Lights \u25A1: " + (ship.lightsOn() ? "on" : "off") + "  Engines OPTIONS: " + (ship.engineOn() ? "running" : "OFF"), lx, y + 97, 0x7F8C99, false);
		} else {
			ctx.drawText(tr, "W/S throttle (hold)  A/D steer  H horn", lx, y + 73, 0x7F8C99, false);
			ctx.drawText(tr, "F5 camera  (scroll wheel: zoom)  Shift: leave the wheel", lx, y + 85, 0x7F8C99, false);
			ctx.drawText(tr, "Lights " + keyName(lights) + ": " + (ship.lightsOn() ? "on" : "off") + "  Engines " + keyName(engine) + ": " + (ship.engineOn() ? "running" : "OFF"), lx, y + 97, 0x7F8C99, false);
		}
		m.pop();
	}
}
