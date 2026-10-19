"""Adds the lifting wheelhouse + lights on/off system to one ship mod.

Usage: python3 tools/add_lift_lights.py <mod root> <java package> <mod namespace> <ClientClass> <MainClass>
The ship template json decides whether a wheelhouse can be lifted ("lift" entry); lights work on every ship.
"""
import re, sys

root, pkg, ns, client_cls, main_cls = sys.argv[1:6]
base = "" if root in (".", "src") else root.rstrip("/") + "/"
P = pkg.replace(".", "/")
SHIP = f"{base}src/main/java/{P}/ship/ShipEntity.java"
TPL = f"{base}src/main/java/{P}/ship/ShipTemplate.java"
PAY = f"{base}src/main/java/{P}/net/ShipPayloads.java"
MESH = f"{base}src/client/java/{P}/client/ShipMesh.java"
REND = f"{base}src/client/java/{P}/client/ShipRenderer.java"
CLI = f"{base}src/client/java/{P}/client/{client_cls}.java"


def patch(f, pairs, regex=False):
    s = open(f).read()
    for a, b in pairs:
        if regex:
            s2, n = re.subn(a, b, s, count=1, flags=re.S)
            if n == 0: raise SystemExit(f"MISSING (re) in {f}: {a[:90]}")
            s = s2
        else:
            if s.count(a) != 1: raise SystemExit(f"anchor x{s.count(a)} in {f}: {a[:90]}")
            s = s.replace(a, b)
    open(f, "w").write(s)


# ------------------------------------------------------------------ template
patch(TPL, [
    ("	private static double[] vec(JsonArray a) {", """	/** Hefbare stuurhut: {x0,y0,z0,x1,y1,z1} (sjablooncoördinaten) of null als het schip er geen heeft. */
	public int[] liftRegion;
	public double liftMax;
	/** Hydraulische kolom onder de stuurhut: {x0,z0,x1,z1}. */
	public int[] liftColumn;
	/** Posities (BlockPos.asLong) van alle lampen, voor het licht aan/uit. */
	public long[] lightPositions = new long[0];

	/** Kolommen (x, z) binnen het gebied die niet meebewegen, bijvoorbeeld een ladder door de vloer. */
	public int[][] liftExclude = new int[0][];

	public boolean inLift(int x, int y, int z) {
		int[] r = liftRegion;
		if (r == null || x < r[0] || x > r[3] || y < r[1] || y > r[4] || z < r[2] || z > r[5]) return false;
		for (int[] e : liftExclude) if (e[0] == x && e[1] == z) return false;
		return true;
	}

	public boolean inLiftXZ(double x, double z) {
		int[] r = liftRegion;
		if (r == null || x < r[0] || x >= r[3] + 1 || z < r[2] || z >= r[5] + 1) return false;
		int fx = (int) Math.floor(x), fz = (int) Math.floor(z);
		for (int[] e : liftExclude) if (e[0] == fx && e[1] == fz) return false;
		return true;
	}

	/** Hoe een lamp eruitziet als het licht uit staat. */
	public static BlockState lightOff(BlockState s) {
		if (s.contains(net.minecraft.state.property.Properties.LIT)) return s.with(net.minecraft.state.property.Properties.LIT, false);
		if (s.isOf(Blocks.SEA_LANTERN) || s.isOf(Blocks.GLOWSTONE) || s.isOf(Blocks.SHROOMLIGHT)) return Blocks.WHITE_CONCRETE.getDefaultState();
		return s;
	}

	private void readExtras(JsonObject json) {
		if (json.has("lift")) {
			JsonObject lift = json.getAsJsonObject("lift");
			liftRegion = ints(lift.getAsJsonArray("region"));
			liftMax = lift.get("max").getAsDouble();
			liftColumn = ints(lift.getAsJsonArray("column"));
			if (lift.has("exclude")) {
				JsonArray ex = lift.getAsJsonArray("exclude");
				liftExclude = new int[ex.size()][];
				for (int i = 0; i < ex.size(); i++) liftExclude[i] = ints(ex.get(i).getAsJsonArray());
			}
		}
		it.unimi.dsi.fastutil.longs.LongArrayList lp = new it.unimi.dsi.fastutil.longs.LongArrayList();
		for (int x = MIN_X; x < MIN_X + SIZE_X; x++)
			for (int y = MIN_Y; y < MIN_Y + SIZE_Y; y++)
				for (int z = MIN_Z; z < MIN_Z + SIZE_Z; z++) {
					BlockState s = getState(x, y, z);
					if (s == null || s.isAir()) continue;
					boolean lit = s.contains(net.minecraft.state.property.Properties.LIT) && s.get(net.minecraft.state.property.Properties.LIT);
					if (s.getLuminance() > 0 || lit) lp.add(net.minecraft.util.math.BlockPos.asLong(x, y, z));
				}
		lightPositions = lp.toLongArray();
	}

	private static double[] vec(JsonArray a) {"""),
    ("""		helmBow = ints(zones.getAsJsonArray("bow"));
	}""", """		helmBow = ints(zones.getAsJsonArray("bow"));
		readExtras(json);
	}"""),
])
s = open(TPL).read()
if "import net.minecraft.block.Blocks;" not in s:
    s = s.replace("import net.minecraft.block.BlockState;\n", "import net.minecraft.block.BlockState;\nimport net.minecraft.block.Blocks;\n")
open(TPL, "w").write(s)

# ------------------------------------------------------------------ payload
patch(PAY, [
    ("public record Control(int entityId, int throttle, int rudder, int thruster, boolean horn, boolean toggleHatches) implements CustomPayload {",
     "public record Control(int entityId, int throttle, int rudder, int thruster, boolean horn, boolean toggleHatches, int lift, boolean toggleLights) implements CustomPayload {"),
    ("""			buf.writeBoolean(toggleHatches);
		}""", """			buf.writeBoolean(toggleHatches);
			buf.writeByte(lift);
			buf.writeBoolean(toggleLights);
		}"""),
    ("return new Control(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readBoolean());",
     "return new Control(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readBoolean(), buf.readByte(), buf.readBoolean());"),
])

# ------------------------------------------------------------------ ship entity
s = open(SHIP).read()
m = re.search(r"(\tprivate static final TrackedData<Boolean> AGROUND = [^\n]*\n)", s)
assert m, "AGROUND"
s = s.replace(m.group(1), m.group(1) + """	private static final TrackedData<Float> LIFT = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Boolean> LIGHTS = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
""", 1)
open(SHIP, "w").write(s)
patch(SHIP, [
    ("		builder.add(AGROUND, false);\n", "		builder.add(AGROUND, false);\n		builder.add(LIFT, 0f);\n		builder.add(LIGHTS, true);\n"),
    ("	public float syncedSpeed() {", """	// ---- hefbare stuurhut en verlichting ----
	private double liftTarget, liftCur, carriedLift;
	public float liftClient, prevLiftClient;

	/** Hoe ver de stuurhut nu omhoog staat (blokken). */
	public float liftOffset() {
		return getWorld().isClient ? liftClient : (float) liftCur;
	}

	public float liftLerp(float tickDelta) {
		return MathHelper.lerp(tickDelta, prevLiftClient, liftClient);
	}

	public boolean lightsOn() {
		return dataTracker.get(LIGHTS);
	}

	/** Punt in sjablooncoördinaten zoals je het ziet (met de stuurhut omhoog) terug naar het sjabloon. */
	public Vec3d unlift(Vec3d t) {
		double L = liftOffset();
		if (L <= 0.001 || template.liftRegion == null) return t;
		if (template.inLiftXZ(t.x, t.z) && t.y >= template.liftRegion[1] + L - 0.001) return new Vec3d(t.x, t.y - L, t.z);
		return t;
	}

	private double liftAt(double x, double y, double z) {
		return template.inLift(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z)) ? liftOffset() : 0;
	}

	/** Alleen voor de automatische test. */
	public void testSetLift(double target) {
		liftTarget = MathHelper.clamp(target, 0, template.liftMax);
	}

	public void setLights(boolean on) {
		if (getWorld().isClient || lightsOn() == on) return;
		dataTracker.set(LIGHTS, on);
		long[] lp = template.lightPositions;
		int[] st = new int[lp.length];
		for (int i = 0; i < lp.length; i++) {
			BlockPos p = BlockPos.fromLong(lp[i]);
			BlockState orig = template.getState(p.getX(), p.getY(), p.getZ());
			BlockState now = on ? orig : ShipTemplate.lightOff(orig);
			structure.set(p.getX(), p.getY(), p.getZ(), now);
			st[i] = Block.getRawIdFromState(now);
		}
		ShipPayloads.Blocks payload = new ShipPayloads.Blocks(getId(), lp, st);
		for (ServerPlayerEntity p : PlayerLookup.tracking(this)) ServerPlayNetworking.send(p, payload);
	}

	/** Een BlockView van alleen de stuurhut (onlyLift) of alles behalve de stuurhut. */
	private final class LiftView implements net.minecraft.world.BlockView {
		private final boolean onlyLift;

		LiftView(boolean onlyLift) {
			this.onlyLift = onlyLift;
		}

		@Override
		public BlockState getBlockState(BlockPos pos) {
			if (template.inLift(pos.getX(), pos.getY(), pos.getZ()) != onlyLift) return Blocks.AIR.getDefaultState();
			return structure.get(pos.getX(), pos.getY(), pos.getZ());
		}

		@Override
		public net.minecraft.fluid.FluidState getFluidState(BlockPos pos) {
			return net.minecraft.fluid.Fluids.EMPTY.getDefaultState();
		}

		@Nullable
		@Override
		public net.minecraft.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
			return null;
		}

		@Override
		public int getHeight() {
			return structure.getHeight();
		}

		@Override
		public int getBottomY() {
			return structure.getBottomY();
		}
	}

	private final LiftView baseView = new LiftView(false), liftView = new LiftView(true);

	public float syncedSpeed() {"""),
    # client: smooth lift
    ("	private void clientTick() {\n", """	private void clientTick() {
		prevLiftClient = liftClient;
		float lt = dataTracker.get(LIFT);
		liftClient += (lt - liftClient) * 0.5f;
		if (Math.abs(lt - liftClient) < 0.002f) liftClient = lt;
"""),
    # server: move lift
    ("		dataTracker.set(AGROUND, aground);\n", """		dataTracker.set(AGROUND, aground);
		if (template.liftRegion != null) {
			double before = liftCur;
			liftCur = approach(liftCur, liftTarget, 0.025);
			dataTracker.set(LIFT, (float) liftCur);
			if (before != liftCur && age % 10 == 0) {
				Vec3d hp = toWorld(template.liftColumn[0] + 1, template.liftRegion[1], template.liftColumn[1] + 1);
				getWorld().playSound(null, hp.x, hp.y, hp.z, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.NEUTRAL, 0.25f, 0.4f);
			}
		}
"""),
    # carry entities with the lift
    ("""	private void carryEntities(double ox, double oy, double oz, double oyaw) {
		if (ox == getX() && oy == getY() && oz == getZ() && oyaw == shipYaw) return;""", """	private void carryEntities(double ox, double oy, double oz, double oyaw) {
		double lNow = liftOffset(), dl = lNow - carriedLift;
		carriedLift = lNow;
		if (ox == getX() && oy == getY() && oz == getZ() && oyaw == shipYaw && dl == 0) return;"""),
    ("""			Vec3d l = toLocal(ox, oy, oz, oyaw, e.getX(), e.getY(), e.getZ());
			if (!insideCarryVolume(l)) continue;""", """			Vec3d l = toLocal(ox, oy, oz, oyaw, e.getX(), e.getY(), e.getZ());
			if (!insideCarryVolume(l)) continue;
			if (dl != 0 && template.inLiftXZ(l.x, l.z) && l.y >= template.liftRegion[1] + (lNow - dl) - 0.6) l = l.add(0, dl, 0);"""),
    ("if (l.y < template.hullBottom[xi] - 0.5 || l.y > 15) return false;", "if (l.y < template.hullBottom[xi] - 0.5 || l.y > 24) return false;"),
    ("		Vec3d w = toWorld(seat[0], seat[1], seat[2]);\n", "		Vec3d w = toWorld(seat[0], seat[1] + liftAt(seat[0], seat[1], seat[2]), seat[2]);\n"),
    ("""		Vec3d t = toLocal(w);
		Action a = actionAt(t);
		if (a == Action.NONE) return ActionResult.PASS;""", """		Vec3d t = unlift(toLocal(w));
		Action a = actionAt(t);
		if (a == Action.NONE) return ActionResult.PASS;"""),
    ("""			Vec3d l = s.toLocal(e.getPos());
			BlockState st = s.structure.get(MathHelper.floor(l.x), MathHelper.floor(l.y), MathHelper.floor(l.z));""", """			Vec3d l = s.unlift(s.toLocal(e.getPos()));
			BlockState st = s.structure.get(MathHelper.floor(l.x), MathHelper.floor(l.y), MathHelper.floor(l.z));"""),
    ("""		Vec3d a = toLocal(startW), b = toLocal(endW);
		BlockHitResult r = structure.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, e));
		Vec3d best = r.getType() == HitResult.Type.MISS ? null : r.getPos();
		double bestD = best == null ? Double.MAX_VALUE : best.squaredDistanceTo(a);""", """		Vec3d a = toLocal(startW), b = toLocal(endW);
		Vec3d best;
		double bestD;
		if (template.liftRegion == null) {
			BlockHitResult r = structure.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, e));
			best = r.getType() == HitResult.Type.MISS ? null : r.getPos();
		} else {
			double L = liftOffset();
			BlockHitResult r1 = baseView.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, e));
			BlockHitResult r2 = liftView.raycast(new RaycastContext(a.add(0, -L, 0), b.add(0, -L, 0), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, e));
			Vec3d p1 = r1.getType() == HitResult.Type.MISS ? null : r1.getPos();
			Vec3d p2 = r2.getType() == HitResult.Type.MISS ? null : r2.getPos().add(0, L, 0);
			best = p1 == null ? p2 : p2 == null ? p1 : (p1.squaredDistanceTo(a) <= p2.squaredDistanceTo(a) ? p1 : p2);
		}
		bestD = best == null ? Double.MAX_VALUE : best.squaredDistanceTo(a);"""),
    ("""		thrusterInput = MathHelper.clamp(c.thruster(), -1, 1);
""", """		thrusterInput = MathHelper.clamp(c.thruster(), -1, 1);
		if (template.liftRegion != null && c.lift() != 0)
			liftTarget = MathHelper.clamp(liftTarget + Math.signum(c.lift()) * 0.06, 0, template.liftMax);
		if (c.toggleLights()) {
			setLights(!lightsOn());
			Vec3d lp = getPos();
			getWorld().playSound(null, lp.x, lp.y, lp.z, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.6f, lightsOn() ? 0.6f : 0.5f);
			player.sendMessage(Text.literal(lightsOn() ? "Lights on" : "Lights off").formatted(Formatting.GRAY), true);
		}
"""),
    ("	protected void writeCustomDataToNbt(NbtCompound nbt) {\n", """	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putDouble("LiftTarget", liftTarget);
		nbt.putDouble("Lift", liftCur);
		nbt.putBoolean("Lights", lightsOn());
"""),
    ("	protected void readCustomDataFromNbt(NbtCompound nbt) {\n", """	protected void readCustomDataFromNbt(NbtCompound nbt) {
		liftTarget = nbt.getDouble("LiftTarget");
		liftCur = nbt.getDouble("Lift");
		dataTracker.set(LIFT, (float) liftCur);
		dataTracker.set(LIGHTS, !nbt.contains("Lights") || nbt.getBoolean("Lights"));
"""),
])
# gather shapes: skip lifted blocks in the normal loop, add them shifted afterwards
s = open(SHIP).read()
i = s.index("	private void gatherShapes(")
j = s.index("		for (Box b : coverBoxes()) if (b.intersects(localQuery)) out.add(VoxelShapes.cuboid(b));", i)
seg = s[i:j]
a = """					BlockState s = structure.get(x, y, z);
					if (s.isAir()) continue;"""
assert a in seg, "gather loop"
seg = seg.replace(a, """					BlockState s = structure.get(x, y, z);
					if (s.isAir()) continue;
					if (template.liftRegion != null && template.inLift(x, y, z)) continue;""")
seg += """		if (template.liftRegion != null) {
			double L = liftOffset();
			int ly0 = MathHelper.floor(localQuery.minY - L) - 1, ly1 = MathHelper.floor(localQuery.maxY - L) + 1;
			int[] r = template.liftRegion;
			for (int x = Math.max(x0, r[0]); x <= Math.min(x1, r[3]); x++)
				for (int y = Math.max(ly0, r[1]); y <= Math.min(ly1, r[4]); y++)
					for (int z = Math.max(z0, r[2]); z <= Math.min(z1, r[5]); z++) {
						BlockState s = structure.get(x, y, z);
						if (s.isAir()) continue;
						m.set(x, y, z);
						VoxelShape shape = s.getCollisionShape(structure, m, ctx);
						if (!shape.isEmpty()) out.add(shape.offset(x, y + L, z));
					}
			if (L > 0.01) {
				int[] c = template.liftColumn;
				Box col = new Box(c[0], r[1], c[1], c[2] + 1, r[1] + L, c[3] + 1);
				if (col.intersects(localQuery)) out.add(VoxelShapes.cuboid(col));
			}
		}
"""
s = s[:i] + seg + s[j:]
open(SHIP, "w").write(s)

# ------------------------------------------------------------------ mesh
patch(MESH, [
    ("	public final int version;\n", "	public final int version;\n	public final boolean lights;\n"),
    ("""	private ShipMesh(int version) {
		this.version = version;""", """	private ShipMesh(int version, boolean lights) {
		this.version = version;
		this.lights = lights;"""),
    ("""	public static ShipMesh build(ShipStructure s) {
		ShipMesh mesh = new ShipMesh(s.version);""", """	public static ShipMesh build(ShipStructure s) {
		return build(s, 0, true);
	}

	/** part: 0 = alles, 1 = alles behalve de hefbare stuurhut, 2 = alleen de stuurhut. */
	public static ShipMesh build(ShipStructure s, int part, boolean lightsOn) {
		ShipMesh mesh = new ShipMesh(s.version, lightsOn);"""),
    ("		computeLight(s, sky, blk, opaque);\n", "		computeLight(s, sky, blk, opaque, lightsOn);\n"),
    ("""					BlockState st = s.get(x, y, z);
					if (st.isAir() || st.getRenderType() != BlockRenderType.MODEL) continue;""", """					BlockState st = s.get(x, y, z);
					if (st.isAir() || st.getRenderType() != BlockRenderType.MODEL) continue;
					if (part == 1 && s.template.inLift(x, y, z)) continue;
					if (part == 2 && !s.template.inLift(x, y, z)) continue;"""),
    ("private static void computeLight(ShipStructure s, int[] sky, int[] blk, boolean[] opaque) {", "private static void computeLight(ShipStructure s, int[] sky, int[] blk, boolean[] opaque, boolean lightsOn) {"),
    ("					int lum = st.getLuminance();\n", "					int lum = lightsOn ? st.getLuminance() : 0;\n"),
])

# ------------------------------------------------------------------ renderer
patch(REND, [
    (r"""		ShipMesh mesh = ship\.renderCache instanceof ShipMesh m \? m : null;
		if \(mesh == null \|\| mesh\.version != ship\.structure\.version\) \{
			mesh = ShipMesh\.build\(ship\.structure\);
			ship\.renderCache = mesh;
		\}""", """		ShipMesh[] meshes = ship.renderCache instanceof ShipMesh[] mm ? mm : null;
		boolean lightsOn = ship.lightsOn();
		if (meshes == null || meshes[0].version != ship.structure.version || meshes[0].lights != lightsOn) {
			meshes = ship.template.liftRegion != null
					? new ShipMesh[]{ShipMesh.build(ship.structure, 1, lightsOn), ShipMesh.build(ship.structure, 2, lightsOn)}
					: new ShipMesh[]{ShipMesh.build(ship.structure, 0, lightsOn)};
			ship.renderCache = meshes;
		}
		ShipMesh mesh = meshes[0];"""),
], regex=True)
patch(REND, [
    ("		mesh.render(matrices, consumers, lastFrustum, shipPos, y, cam, shadow ? 1 : 0);\n", """		mesh.render(matrices, consumers, lastFrustum, shipPos, y, cam, shadow ? 1 : 0);
		if (meshes.length > 1) {
			float lift = ship.liftLerp(tickDelta);
			matrices.push();
			matrices.translate(0, lift, 0);
			meshes[1].render(matrices, consumers, lastFrustum, shipPos.add(0, lift, 0), y, cam, shadow ? 1 : 0);
			matrices.pop();
			if (lift > 0.01f) {
				int[] r = ship.template.liftRegion, c = ship.template.liftColumn;
				Sink ps = new Sink();
				box(ps, sprites(Blocks.GRAY_CONCRETE.getDefaultState())[0], c[0] + 0.1f, r[1], c[1] + 0.1f, c[2] + 0.9f, r[1] + lift, c[3] + 0.9f, light, 0.9f);
				box(ps, sprites(Blocks.IRON_BLOCK.getDefaultState())[0], c[0] + 0.35f, r[1], c[1] + 0.35f, c[2] + 0.65f, r[1] + lift + 0.01f, c[3] + 0.65f, light, 1f);
				ps.emit(consumers.getBuffer(TexturedRenderLayers.getEntityCutout()), matrices.peek());
			}
		}
"""),
])

# ------------------------------------------------------------------ client: keys, control, HUD
s = open(CLI).read()
s = s.replace("static KeyBinding thrusterLeft, thrusterRight, horn, hatches;", "static KeyBinding thrusterLeft, thrusterRight, horn, hatches, liftUp, liftDown, lights;")
s = s.replace("private static boolean prevForward, prevBack, prevHornKey, prevHatchKey;", "private static boolean prevForward, prevBack, prevHornKey, prevHatchKey, prevLightKey;")
m = re.search(r'(\t\thatches = KeyBindingHelper\.registerKeyBinding\(new KeyBinding\("(key\.[a-z]+\.[a-z_0-9]+)"[^\n]*\n)', s)
assert m, "hatches keybinding"
cat = re.search(r'"(category\.[a-z_]+)"', m.group(1)).group(1)
s = s.replace(m.group(1), m.group(1) + f"""		liftUp = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.{ns}.lift_up", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, "{cat}"));
		liftDown = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.{ns}.lift_down", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, "{cat}"));
		lights = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.{ns}.lights", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "{cat}"));
""", 1)
a = "		ClientPlayNetworking.send(new ShipPayloads.Control(ship.getId(), throttle, rudder, thruster, h, l));"
assert a in s, "control send"
s = s.replace(a, """		int liftDir = 0;
		boolean lt = false;
		if (mc.currentScreen == null) {
			if (held(mc, liftUp)) liftDir += 1;
			if (held(mc, liftDown)) liftDir -= 1;
		}
		if (testLift != 0) liftDir = testLift;
		while (lights.wasPressed()) lt = true;
		boolean lk = mc.currentScreen == null && held(mc, lights);
		if (lk && !prevLightKey) lt = true;
		prevLightKey = lk;
		if (testLights) {
			lt = true;
			testLights = false;
		}
		ClientPlayNetworking.send(new ShipPayloads.Control(ship.getId(), throttle, rudder, thruster, h, l, liftDir, lt));""")
s = s.replace("	/** Alleen voor de automatische test. */\n	static void testThrottle(int t) {", "	static int testLift;\n	static boolean testLights;\n\n	/** Alleen voor de automatische test. */\n	static void testThrottle(int t) {", 1)
s = s.replace("			while (hatches.wasPressed()) { }\n", "			while (hatches.wasPressed()) { }\n			while (lights.wasPressed()) { }\n", 1)
s = re.sub(r"int w = (\d+), hgt = 114;", r"int w = \1, hgt = 126;", s)
a = re.search(r'(\t\tctx\.drawText\(tr, "F5 camera[^\n]*\n)', s)
assert a, "F5 line"
s = s.replace(a.group(1), a.group(1) + """		String liftTxt = ship.template.liftRegion != null ? String.format("Wheelhouse %s/%s (%.1f m)  ", keyName(liftUp), keyName(liftDown), ship.liftOffset()) : "";
		ctx.drawText(tr, liftTxt + "Lights " + keyName(lights) + ": " + (ship.lightsOn() ? "on" : "off"), lx, y + 115, 0x7F8C99, false);
""")
open(CLI, "w").write(s)
print("lift + lights added to", root)
