package com.speedboat.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Game controller support (PlayStation 5 DualSense, and every other controller GLFW knows as a gamepad). It is
 * found by itself as soon as it is plugged in. On a ship:
 * left stick: steering (smooth), R2: gas, L2: astern, D-pad up / down: throttle lever, L1 / R1: neutral,
 * triangle: horn, square: lights, OPTIONS: engines,
 * circle: leave the helm, right stick: look around, R3: camera view.
 */
final class Gamepad {
	private Gamepad() {
	}

	private static int jid = -1;
	private static int scan;
	private static GLFWGamepadState state;
	private static final boolean[] prev = new boolean[GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1];
	private static final boolean[] now = new boolean[GLFW.GLFW_GAMEPAD_BUTTON_LAST + 1];
	private static boolean ok;
	private static long lastLook;

	/** Look for a controller now and then; read it. Call once per client tick. */
	static void tick() {
		if (--scan <= 0 || (jid >= 0 && !GLFW.glfwJoystickPresent(jid))) {
			scan = 20;
			jid = -1;
			for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++)
				if (GLFW.glfwJoystickPresent(j) && GLFW.glfwJoystickIsGamepad(j)) {
					jid = j;
					break;
				}
		}
		System.arraycopy(now, 0, prev, 0, now.length);
		ok = false;
		if (jid < 0) return;
		if (state == null) state = GLFWGamepadState.create();
		ok = GLFW.glfwGetGamepadState(jid, state);
		for (int b = 0; b < now.length; b++) now[b] = ok && state.buttons(b) == GLFW.GLFW_PRESS;
	}

	/** Is a controller plugged in (and read)? */
	static boolean connected() {
		return jid >= 0 && ok;
	}

	/** The name of the controller, for the panel. */
	static String name() {
		String n = jid >= 0 ? GLFW.glfwGetGamepadName(jid) : null;
		return n == null ? "Controller" : n;
	}

	static boolean held(int button) {
		return ok && now[button];
	}

	/** Pressed since the last tick. */
	static boolean pressed(int button) {
		return ok && now[button] && !prev[button];
	}

	/** A stick axis with a dead zone and a gentle curve: fine control round the middle, full at the end. */
	static float stick(int axis) {
		if (!ok) return 0;
		float v = state.axes(axis);
		float a = Math.abs(v);
		if (a < 0.12f) return 0;
		float t = Math.min(1f, (a - 0.12f) / 0.86f);
		return Math.signum(v) * (float) Math.pow(t, 1.6);
	}

	/** A trigger, 0 released .. 1 fully pressed. */
	static float trigger(int axis) {
		if (!ok) return 0;
		float t = (state.axes(axis) + 1f) / 2f;
		return t < 0.08f ? 0 : Math.min(1f, (t - 0.08f) / 0.9f);
	}

	/** Look around with the right stick, smoothly every frame; R3 changes the camera view. */
	static void frame(MinecraftClient mc) {
		long t = System.nanoTime();
		double dt = lastLook == 0 ? 0 : Math.min(0.1, (t - lastLook) / 1e9);
		lastLook = t;
		if (!connected() || mc.player == null || mc.currentScreen != null) return;
		float rx = stick(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X), ry = stick(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
		if (rx != 0 || ry != 0) {
			double speed = 220 * dt;     // degrees per second at full deflection
			mc.player.setYaw(mc.player.getYaw() + (float) (rx * speed));
			mc.player.setPitch(Math.max(-90f, Math.min(90f, mc.player.getPitch() + (float) (ry * speed * 0.8))));
		}
	}

	/** R3: the next camera view, as F5. */
	static void camera(MinecraftClient mc) {
		if (pressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB)) {
			Perspective p = mc.options.getPerspective();
			mc.options.setPerspective(p.next());
		}
	}
}
