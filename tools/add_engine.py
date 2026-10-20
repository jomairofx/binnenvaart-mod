"""Engine on/off for a ship mod (key M): with the engine off the telegraph does nothing and the diesel is silent."""
import sys, re
root, pkg, ns, client_cls = sys.argv[1:5]
P = pkg.replace(".", "/")

def edit(f, pairs, regex=False):
    s = open(f).read()
    for a, b in pairs:
        if s.count(a) != 1: raise SystemExit(f"anchor x{s.count(a)} in {f}: {a[:80]}")
        s = s.replace(a, b)
    open(f, "w").write(s)

edit(f"{root}/main/java/{P}/net/ShipPayloads.java", [
    ("int lift, boolean toggleLights) implements CustomPayload {", "int lift, boolean toggleLights, boolean toggleEngine) implements CustomPayload {"),
    ("			buf.writeBoolean(toggleLights);\n", "			buf.writeBoolean(toggleLights);\n			buf.writeBoolean(toggleEngine);\n"),
    ("buf.readByte(), buf.readBoolean());", "buf.readByte(), buf.readBoolean(), buf.readBoolean());"),
])

SHIP = f"{root}/main/java/{P}/ship/ShipEntity.java"
s = open(SHIP).read()
m = re.search(r"\tpublic void handleControl\(ServerPlayerEntity player, ShipPayloads\.Control c\) \{\n.*?\n(\t\tthrottle = MathHelper\.clamp\(c\.throttle\(\), -2, 3\);\n)", s, re.S)
assert m
s = s.replace(m.group(1), m.group(1) + """		if (c.toggleEngine()) {
			boolean on = !engineOn();
			dataTracker.set(ENGINE, on);
			Vec3d ep = getPos();
			getWorld().playSound(null, ep.x, ep.y, ep.z, on ? SoundEvents.BLOCK_PISTON_EXTEND : SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.NEUTRAL, 1.2f, 0.4f);
			player.sendMessage(Text.literal(on ? "Engine started" : "Engine stopped").formatted(Formatting.GRAY), true);
		}
		if (!engineOn()) throttle = 0;
""", 1)
edit_pairs = [
    ("	private static final TrackedData<Boolean> LIGHTS = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);\n",
     "	private static final TrackedData<Boolean> LIGHTS = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);\n	private static final TrackedData<Boolean> ENGINE = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);\n"),
    ("		builder.add(LIGHTS, true);\n", "		builder.add(LIGHTS, true);\n		builder.add(ENGINE, true);\n"),
    ("		nbt.putBoolean(\"Lights\", lightsOn());\n", "		nbt.putBoolean(\"Lights\", lightsOn());\n		nbt.putBoolean(\"EngineOn\", engineOn());\n"),
    ("		dataTracker.set(LIGHTS, !nbt.contains(\"Lights\") || nbt.getBoolean(\"Lights\"));\n",
     "		dataTracker.set(LIGHTS, !nbt.contains(\"Lights\") || nbt.getBoolean(\"Lights\"));\n		dataTracker.set(ENGINE, !nbt.contains(\"EngineOn\") || nbt.getBoolean(\"EngineOn\"));\n"),
    ("	public void testControl(int throttle, int rudder, int thruster) {\n", "	public void testControl(int throttle, int rudder, int thruster) {\n		dataTracker.set(ENGINE, true);\n"),
    ("	public boolean lightsOn() {", "	public boolean engineOn() {\n		return dataTracker.get(ENGINE);\n	}\n\n	public void setEngine(boolean on) {\n		dataTracker.set(ENGINE, on);\n	}\n\n	public boolean lightsOn() {"),
]
for a, b in edit_pairs:
    if s.count(a) != 1: raise SystemExit(f"ship anchor x{s.count(a)}: {a[:80]}")
    s = s.replace(a, b)
open(SHIP, "w").write(s)

CLI = f"{root}/client/java/{P}/client/{client_cls}.java"
s = open(CLI).read()
pairs = [
    ("liftUp, liftDown, lights;", "liftUp, liftDown, lights, engine;"),
    ("prevLightKey;", "prevLightKey, prevEngineKey;"),
    (f"		lights = KeyBindingHelper.registerKeyBinding(new KeyBinding(\"key.{ns}.lights\", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, \"category.{ns}\"));\n",
     f"		lights = KeyBindingHelper.registerKeyBinding(new KeyBinding(\"key.{ns}.lights\", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, \"category.{ns}\"));\n		engine = KeyBindingHelper.registerKeyBinding(new KeyBinding(\"key.{ns}.engine\", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, \"category.{ns}\"));\n"),
    ("		prevLightKey = lightKey;\n", """		prevLightKey = lightKey;
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
"""),
    ("ClientPlayNetworking.send(new ShipPayloads.Control(ship.getId(), throttle, rudder, thruster, h, l, liftDir, lt));",
     "ClientPlayNetworking.send(new ShipPayloads.Control(ship.getId(), throttle, rudder, thruster, h, l, liftDir, lt, et));"),
]
for a, b in pairs:
    if s.count(a) != 1: raise SystemExit(f"client anchor x{s.count(a)}: {a[:80]}")
    s = s.replace(a, b)
# HUD: engine state in the last line
s2, n = re.subn(r'(ctx\.drawText\(tr, liftTxt \+ "Lights " \+ keyName\(lights\) \+ ": " \+ \(ship\.lightsOn\(\) \? "on" : "off"\))',
                r'\1 + "  Engine " + keyName(engine) + ": " + (ship.engineOn() ? "on" : "OFF")', s)
assert n == 1, "hud"
s = s2
s = s.replace("	private static int throttle;", "	private static int throttle;\n	public static boolean testEngine;", 1)
assert "testEngine;" in s
open(CLI, "w").write(s)

SND = f"{root}/client/java/{P}/client/ShipEngineSound.java"
s = open(SND).read()
a = "		boolean running = ship.syncedThrottle() != 0;"
assert a in s
s = s.replace(a, "		boolean running = ship.engineOn();   // motor aan = stationair gebrom, ook op Stop")
open(SND, "w").write(s)

import json, glob
for lf in glob.glob(f"{root}/main/resources/assets/{ns}/lang/*.json"):
    d = json.load(open(lf))
    d[f"key.{ns}.engine"] = "Engine on/off"
    json.dump(d, open(lf, "w"), indent=2, ensure_ascii=False)
print("ok", root)
