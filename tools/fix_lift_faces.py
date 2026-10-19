"""Draw faces on the border between the lifting wheelhouse and the rest of the ship (they were culled)."""
import sys
for f in sys.argv[1:]:
    s = open(f).read()
    a = "						if (!Block.shouldDrawSide(st, s, pos, dir, pos.offset(dir))) continue;\n"
    b = """						boolean border = part != 0 && s.template.inLift(x, y, z) != s.template.inLift(x + dir.getOffsetX(), y + dir.getOffsetY(), z + dir.getOffsetZ());
						if (!border && !Block.shouldDrawSide(st, s, pos, dir, pos.offset(dir))) continue;
"""
    assert s.count(a) == 1, f
    s = s.replace(a, b)
    a = "						for (BakedQuad q : quads) emit(buf[sec * 3 + lev], q, st, x, y, z, dir, sky, blk, opaque, colors);\n"
    b = """						if (border) lev = 0;
						for (BakedQuad q : quads) emit(buf[sec * 3 + lev], q, st, x, y, z, border ? null : dir, sky, blk, opaque, colors, border);
"""
    assert s.count(a) == 1, f
    s = s.replace(a, b)
    s = s.replace("emit(buf[sec * 3 + levFree], q, st, x, y, z, null, sky, blk, opaque, colors);",
                  "emit(buf[sec * 3 + levFree], q, st, x, y, z, null, sky, blk, opaque, colors, false);")
    a = """	private static void emit(FloatList buf, BakedQuad q, BlockState st, int x, int y, int z, Direction cull,
							 int[] sky, int[] blk, boolean[] opaque, net.minecraft.client.color.block.BlockColors colors) {"""
    assert s.count(a) == 1, f
    s = s.replace(a, """	private static void emit(FloatList buf, BakedQuad q, BlockState st, int x, int y, int z, Direction cull,
							 int[] sky, int[] blk, boolean[] opaque, net.minecraft.client.color.block.BlockColors colors, boolean border) {""")
    a = "		int light = LightmapTextureManager.pack(blkL, skyL);\n"
    assert s.count(a) == 1, f
    s = s.replace(a, "		if (border) skyL = 15;   // face between the raised wheelhouse and the roof: open air when lifted\n" + a)
    open(f, "w").write(s)
    print("ok", f)
