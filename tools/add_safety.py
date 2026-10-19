"""Never let an error in ship code crash the game: log it (with stack trace) and carry on."""
import sys, re
root, pkg, client_cls, main_cls = sys.argv[1:5]
P = pkg.replace(".", "/")

def edit(f, fn):
    s = open(f).read()
    s2 = fn(s)
    open(f, "w").write(s2)

# ---------- ShipEntity ----------
def ship(s):
    a = "	@Override\n	public void tick() {\n"
    assert s.count(a) == 1, "tick"
    s = s.replace(a, """	@Override
	public void tick() {
		try {
			tickSafe();
		} catch (RuntimeException ex) {
			reportError("tick", ex);
		}
	}

	/** Fout in de scheepscode: in het logboek zetten (één keer per 10 s) in plaats van het spel te laten crashen. */
	public static void reportError(String where, Throwable ex) {
		long now = System.currentTimeMillis();
		if (now - lastError < 10_000) return;
		lastError = now;
		org.slf4j.LoggerFactory.getLogger("%s").error("Ship error in " + where + " (game keeps running) - please send this log", ex);
	}

	private static long lastError;

	/** Voert iets uit en vangt fouten af. */
	public static void safely(String where, Runnable r) {
		try {
			r.run();
		} catch (RuntimeException ex) {
			reportError(where, ex);
		}
	}

	private void tickSafe() {
""" % pkg)
    a = "	public static Vec3d collideAll(Entity e, Vec3d mv) {\n"
    assert s.count(a) == 1, "collide"
    s = s.replace(a, """	public static Vec3d collideAll(Entity e, Vec3d mv) {
		try {
			return collideAllSafe(e, mv);
		} catch (RuntimeException ex) {
			reportError("collision", ex);
			return mv;
		}
	}

	private static Vec3d collideAllSafe(Entity e, Vec3d mv) {
""")
    a = "	public static boolean isClimbing(Entity e) {\n"
    assert s.count(a) == 1, "climb"
    s = s.replace(a, """	public static boolean isClimbing(Entity e) {
		try {
			return isClimbingSafe(e);
		} catch (RuntimeException ex) {
			reportError("ladder", ex);
			return false;
		}
	}

	private static boolean isClimbingSafe(Entity e) {
""")
    return s
edit(f"{root}/main/java/{P}/ship/ShipEntity.java", ship)

# ---------- main class: network handlers ----------
def main(s):
    s, n1 = re.subn(r"(\t+)ship\.handleControl\(context\.player\(\), payload\);",
                    r'\1ShipEntity.safely("control", () -> ship.handleControl(context.player(), payload));', s)
    s, n2 = re.subn(r"(\t+)ship\.handleInteract\(context\.player\(\), new Vec3d\(payload\.x\(\), payload\.y\(\), payload\.z\(\)\), payload\.sneaking\(\)\);",
                    r'\1ShipEntity.safely("interact", () -> ship.handleInteract(context.player(), new Vec3d(payload.x(), payload.y(), payload.z()), payload.sneaking()));', s)
    assert n1 == 1 and n2 == 1, (n1, n2)
    return s
edit(f"{root}/main/java/{P}/{main_cls}.java", main)

# ---------- client: tick + HUD ----------
def cli(s):
    s = s.replace(f"ClientTickEvents.END_CLIENT_TICK.register({client_cls}::tick);",
                  f'ClientTickEvents.END_CLIENT_TICK.register(mc -> ShipEntity.safely("client tick", () -> tick(mc)));')
    s = s.replace("HudRenderCallback.EVENT.register((ctx, tickCounter) -> renderHud(ctx));",
                  'HudRenderCallback.EVENT.register((ctx, tickCounter) -> ShipEntity.safely("hud", () -> renderHud(ctx)));')
    assert 'safely("client tick"' in s and 'safely("hud"' in s
    return s
edit(f"{root}/client/java/{P}/client/{client_cls}.java", cli)

# ---------- renderer ----------
def rend(s):
    a = "	public void render(ShipEntity ship, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {\n"
    assert s.count(a) == 1, "render"
    s = s.replace(a, """	public void render(ShipEntity ship, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		// eigen kopie van de matrixstapel: als er iets misgaat blijft die van Minecraft heel
		MatrixStack own = new MatrixStack();
		own.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
		own.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
		try {
			renderSafe(ship, yaw, tickDelta, own, consumers, light);
		} catch (RuntimeException ex) {
			ShipEntity.reportError("render", ex);
		}
	}

	private void renderSafe(ShipEntity ship, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
""")
    return s
edit(f"{root}/client/java/{P}/client/ShipRenderer.java", rend)

# ---------- crosshair mixin ----------
f = f"{root}/client/java/{P}/client/mixin/GameRendererMixin.java"
s = open(f).read()
a = "			Vec3d hit = ship.raycast(eye, end, camera);\n"
if a in s:
    s = s.replace(a, """			Vec3d hit;
			try {
				hit = ship.raycast(eye, end, camera);
			} catch (RuntimeException ex) {
				ShipEntity.reportError("raycast", ex);
				continue;
			}
""")
    open(f, "w").write(s)
print("ok", root)
