"""Hatch covers on every cargo ship: thin, and per hold they slide onto a low stack at the front of that hold."""
import sys, re
root, pkg, X0, LEN, Y, Z0, Z1, IN1, IN2 = sys.argv[1:10]
P = pkg.replace(".", "/")

def method_span(s, head):
    i = s.index(head)
    j = s.index("\n\t}\n", i) + 4
    return i, j

# ---------------- ShipEntity ----------------
f = f"{root}/main/java/{P}/ship/ShipEntity.java"
s = open(f).read()
# old bulk-only helpers
if "public static float stackX(int g)" in s:
    i, j = method_span(s, "	/** Waar de luiken van stapel g liggen")
    s = s[:i] + s[j:]
i, j = method_span(s, "	public List<Box> coverBoxes() {")
s = s[:i] + f"""	// ---- luiken: maten in sjablooncoördinaten ----
	public static final float CV_X0 = {X0}f, CV_LEN = {LEN}f, CV_Y = {Y}f, CV_Z0 = {Z0}f, CV_Z1 = {Z1}f, CV_IN1 = {IN1}f, CV_IN2 = {IN2}f;
	/** Hoogte per luik op de stapel en dikte van een luik. */
	public static final float CV_STACK = 0.36f, CV_THICK = 0.34f;
	private static int[] coverGroupEnd;

	/** Het voorste luik van de groep (per ruim) waar luik i bij hoort: daar komt de stapel. */
	public static int coverGroupEnd(int i) {{
		if (coverGroupEnd == null) {{
			int[][] h = ShipTemplate.get().holds;
			int[] grp = new int[COVERS];
			for (int k = 0; k < COVERS; k++) {{
				float c = CV_X0 + CV_LEN * (k + 0.5f);
				int g = h.length - 1;
				for (int j = 0; j < h.length; j++) if (c < h[j][1] + 1.5f) {{ g = j; break; }}
				grp[k] = g;
			}}
			int[] end = new int[COVERS];
			for (int k = COVERS - 1; k >= 0; k--) end[k] = (k == COVERS - 1 || grp[k + 1] != grp[k]) ? k : end[k + 1];
			coverGroupEnd = end;
		}}
		return coverGroupEnd[i];
	}}

	/** Waar luik i ligt als het ruim open is. */
	public static float stackX(int i) {{
		return CV_X0 + CV_LEN * coverGroupEnd(i);
	}}

	/** Luikvakken in sjablooncoördinaten (alleen als ze stilstaan). */
	public List<Box> coverBoxes() {{
		List<Box> list = new ArrayList<>();
		if (hatchProgress >= 0.999f) {{
			for (int i = 0; i < COVERS; i++) {{
				double x0 = CV_X0 + CV_LEN * i;
				list.add(new Box(x0, CV_Y, CV_Z0, x0 + CV_LEN, CV_Y + 0.14, CV_Z1));
				list.add(new Box(x0, CV_Y + 0.14, CV_Z0 + CV_IN1, x0 + CV_LEN, CV_Y + CV_THICK, CV_Z1 - CV_IN1));
			}}
		}} else if (hatchProgress <= 0.001f) {{
			int start = 0;
			for (int i = 0; i < COVERS; i++) {{
				if (coverGroupEnd(i) != i) continue;
				double sx = stackX(i);
				list.add(new Box(sx, CV_Y, CV_Z0, sx + CV_LEN, CV_Y + CV_STACK * (i - start) + CV_THICK, CV_Z1));
				start = i + 1;
			}}
		}}
		return list;
	}}
""" + s[j:]
open(f, "w").write(s)

# ---------------- ShipRenderer ----------------
f = f"{root}/client/java/{P}/client/ShipRenderer.java"
s = open(f).read()
i, j = method_span(s, "	private void renderCovers(float t, Sink vc, int light) {")
old = s[i:j]
m1 = re.search(r"\t\tSprite s = [^\n]+\n", old); m2 = re.search(r"\t\tSprite rim = [^\n]+\n", old)
start = s.rfind("\t/**", 0, i)
if start != -1 and s[start:i].count("\n") <= 1: i = start
s = s[:i] + """	/** Schuifluiken: dun, en per ruim rollen ze naar voren op een lage stapel, zodat je vrij zicht houdt. */
	private void renderCovers(float t, Sink vc, int light) {
""" + m1.group(0) + m2.group(0) + """		Sprite wheel = sprites(Blocks.BLACK_CONCRETE.getDefaultState())[0];
		int n = ShipEntity.COVERS;
		float len = ShipEntity.CV_LEN, z0 = ShipEntity.CV_Z0, z1 = ShipEntity.CV_Z1, base = ShipEntity.CV_Y;
		for (int i = 0; i < n; i++) {
			int level = ShipEntity.coverGroupEnd(i) - i;              // the front one stays down, the others ride on top
			float stagger = 0.08f, dur = 0.55f;
			float p = MathHelper.clamp((t - stagger * Math.min(level, 5)) / dur, 0f, 1f);
			p = p * p * (3 - 2 * p);
			float q = 1f - p;                                         // 0 = closed, 1 = on the stack
			float rise = MathHelper.clamp(q / 0.15f, 0f, 1f), roll = MathHelper.clamp((q - 0.15f) / 0.85f, 0f, 1f);
			float x0 = MathHelper.lerp(roll, ShipEntity.CV_X0 + len * i, ShipEntity.stackX(i));
			float y = MathHelper.lerp(rise, base, base + ShipEntity.CV_STACK * level);
			float x1 = x0 + len - 0.04f;
			boxFast(vc, rim, x0 + 0.04f, y, z0, x1, y + 0.14f, z1, light, 0.95f);
			boxFast(vc, s, x0 + 0.04f, y + 0.14f, z0 + ShipEntity.CV_IN1, x1, y + 0.26f, z1 - ShipEntity.CV_IN1, light, 1f);
			boxFast(vc, s, x0 + 0.04f, y + 0.26f, z0 + ShipEntity.CV_IN2, x1, y + ShipEntity.CV_THICK, z1 - ShipEntity.CV_IN2, light, 1f);
			for (float wx : new float[]{x0 + 0.4f, x1 - 0.7f}) {
				boxFast(vc, wheel, wx, y - 0.08f, z0 + 0.1f, wx + 0.3f, y, z0 + 0.32f, light, 0.8f);
				boxFast(vc, wheel, wx, y - 0.08f, z1 - 0.32f, wx + 0.3f, y, z1 - 0.1f, light, 0.8f);
			}
		}
	}

	/** Kast met één vlak per zijde (de textuur wordt uitgerekt): veel goedkoper dan per blok. */
	private static void boxFast(Sink vc, Sprite s, float x0, float y0, float z0, float x1, float y1, float z1, int light, float tint) {
		quadUp(vc, s, x0, y1, z0, x1, z1, tint, light);
		quadDown(vc, s, x0, y0, z0, x1, z1, tint * 0.6f, light);
		sideXTile(vc, s, x0, z0, z1, y0, y1, false, light, tint);
		sideXTile(vc, s, x1, z0, z1, y0, y1, true, light, tint);
		sideZTile(vc, s, z0, x0, x1, y0, y1, false, light, tint);
		sideZTile(vc, s, z1, x0, x1, y0, y1, true, light, tint);
	}

""" + s[j:]
# containers: cheaper boxes and fewer ribs
i, j = method_span(s, "	private void renderContainers(")
c = s[i:j].replace("box(vc,", "boxFast(vc,").replace("rx += 0.7f", "rx += 1.3f")
s = s[:i] + c + s[j:]
# bulk pile helper used the old group API
s = s.replace("""		for (int g = 0; g * ShipEntity.COVER_GROUP < ShipEntity.COVERS; g++) {
			float sx = ShipEntity.stackX(g);
			if (x + 1 > sx && x < sx + ShipEntity.COVER_LEN) c = Math.min(c, CLOSED_CAP);
		}""", """		for (int g = 0; g < ShipEntity.COVERS; g++) {
			if (ShipEntity.coverGroupEnd(g) != g) continue;
			float sx = ShipEntity.stackX(g);
			if (x + 1 > sx && x < sx + ShipEntity.CV_LEN) c = Math.min(c, CLOSED_CAP);
		}""")
open(f, "w").write(s)
print("ok", root)
