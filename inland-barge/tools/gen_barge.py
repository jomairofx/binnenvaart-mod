"""Builds the STELLA MARIS template (110 x 11) for the Inland Barge mod.

Template axes: x = length (0 = stern, 109 = bow), z = beam (0 = port), y = height.
Layer y=0 is the hull layer just below the deck plating. When fully loaded the water
surface sits half a block below the side deck (y=1 top = 2.0, water at 1.5).
Output: src/main/resources/inlandbarge/stella_maris.json
"""
import json, os

B = {}
def S(x, y, z, s):
    if not s.startswith("minecraft:"):
        s = "minecraft:" + s
    B[(x, y, z)] = s
def R(a, b):
    return range(a, b + 1)

L = 110
def zr(x):
    if x == 0 or x == L - 1: return (2, 8)
    if x == 1 or x == L - 2: return (1, 9)
    return (0, 10)
def bot(x):
    if x <= 4: return -1
    if x == 5: return -2
    if x == 6: return -3
    if x <= 98: return -4
    if x <= 101: return -3
    if x <= 104: return -2
    if x <= 107: return -1
    return 0

HULL = "black_concrete"
DB = "blue_concrete"          # dark blue
W = "white_concrete"
INNER = "light_gray_concrete"
SSL = "smooth_quartz_slab[type=bottom]"

# ---------------- hull ----------------
for x in range(L):
    z0, z1 = zr(x); b = bot(x)
    for z in R(z0, z1):
        S(x, b, z, HULL)
    for y in R(b + 1, 1):
        S(x, y, z0, HULL); S(x, y, z1, HULL)
    if x in (0, L - 1):
        for z in R(z0, z1):
            for y in R(b, 1):
                S(x, y, z, HULL)
AFT_DECK_END, HOLD_START, HOLD_END, FORE_DECK = 14, 15, 95, 96
for x in list(R(0, AFT_DECK_END)) + list(R(FORE_DECK, L - 1)):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1):
        if x not in (0, L - 1):
            S(x, 1, z, "light_gray_concrete" if x <= AFT_DECK_END else DB)

# ---------------- cargo holds (empty) ----------------
HOLDS = [(16, 34), (36, 54), (56, 74), (76, 94)]
BEAMS = [35, 55, 75]
for x in R(HOLD_START, HOLD_END):
    for y in R(-3, 1):
        S(x, y, 1, INNER); S(x, y, 9, INNER)
    S(x, 2, 1, DB); S(x, 2, 9, DB)          # dark blue coaming band
    S(x, 3, 1, SSL); S(x, 3, 9, SSL)        # white coaming cap
for x in (HOLD_START, HOLD_END):
    for z in R(1, 9):
        for y in R(-3, 1):
            S(x, y, z, INNER)
        S(x, 2, z, DB)
        S(x, 3, z, SSL)
for x in R(HOLD_START + 1, HOLD_END - 1):
    for z in R(2, 8):
        S(x, -3, z, "smooth_stone")
for b in BEAMS:
    for z in R(2, 8):
        S(b, 3, z, SSL)
for a, b in HOLDS:
    for y in R(-2, 3):
        S(a + 1, y, 2, "ladder[facing=south]")
    # ladder on the outside of the coaming: from the side deck over the edge into the hold
    for y in (2, 3): S(a + 1, y, 0, "ladder[facing=north]")

# ---------------- stern gear ----------------
for x in R(2, 4):
    for y in R(-4, -2): S(x, y, 5, HULL)
S(5, -4, 5, HULL); S(5, -3, 5, HULL); S(6, -4, 5, HULL)
for zc in (3, 7):
    for y in R(-4, -2): S(1, y, zc, HULL)
    S(3, -3, zc, "lightning_rod[facing=west]")
    S(3, -2, zc, "waxed_cut_copper_slab[type=bottom]")
    S(3, -4, zc, "waxed_cut_copper_slab[type=top]")
    S(3, -3, zc - 1, "waxed_cut_copper_slab[type=top]")
    S(3, -3, zc + 1, "waxed_cut_copper_slab[type=bottom]")
    S(4, -3, zc, "chain[axis=x]")
    S(5, -3, zc, "chain[axis=x]")

# ---------------- engine room x=7..12, y=-3..0 (under the accommodation) ----------------
for z in R(1, 9):
    for y in R(-3, 0):
        S(6, y, z, INNER); S(13, y, z, INNER)
    for x in R(7, 12):
        S(x, -3, z, "iron_trapdoor[facing=north,half=bottom,open=false]")
for zc in (3, 7):
    S(7, -3, zc, "iron_block")
    for x in R(8, 10): S(x, -3, zc, "black_concrete")
    for x in R(8, 9): S(x, -2, zc, "polished_blackstone_slab[type=bottom]")
    for y in R(-2, 0): S(10, y, zc, "lightning_rod[facing=up]")
S(9, -3, 9, "iron_block"); S(10, -3, 9, "black_concrete")
S(7, -3, 1, "smithing_table"); S(8, -3, 1, "crafting_table")
S(9, 0, 5, "lantern[hanging=true]")
for y in R(-2, 0): S(12, y, 5, "ladder[facing=west]")

# ---------------- accommodation x=3..13, z=1..9 ----------------
# Clear layout: one aisle along z=5..6 from the aft door to the front door, rooms on the port side.
X0, X1 = 3, 13
for x in R(X0, X1):
    for z in R(1, 9):
        S(x, 4, z, "light_gray_concrete")          # ceiling / roof
        edge = x in (X0, X1) or z in (1, 9)
        if edge:
            S(x, 2, z, W); S(x, 3, z, W)
        else:
            S(x, 1, z, "spruce_planks")
# roof rim in dark blue
for x in R(X0, X1):
    S(x, 4, 1, DB); S(x, 4, 9, DB)
for z in R(1, 9):
    S(X0, 4, z, DB); S(X1, 4, z, DB)
for p in [(5, 4, 3), (10, 4, 3), (6, 4, 6), (10, 4, 7)]: S(*p, "sea_lantern")
# window openings (no glass) at eye height
for x in (5, 6, 10, 11): S(x, 3, 1, "air")
for x in (7, 8, 10, 11): S(x, 3, 9, "air")
for z in (3, 7): S(X1, 3, z, "air")
S(X0, 3, 8, "air")
# doors: aft door (z=7) and front door (z=5)
S(X0, 2, 6, "dark_oak_door[facing=west,half=lower,hinge=left,open=false]")
S(X0, 3, 6, "dark_oak_door[facing=west,half=upper,hinge=left,open=false]")
S(X1, 2, 5, "dark_oak_door[facing=east,half=lower,hinge=right,open=false]")
S(X1, 3, 5, "dark_oak_door[facing=east,half=upper,hinge=right,open=false]")
# port rooms (z=2..3) behind a wall at z=4: bedroom x4..7, bathroom x9..12
for x in R(4, 12):
    S(x, 2, 4, W); S(x, 3, 4, W)
for z in (2, 3):
    S(8, 2, z, W); S(8, 3, z, W)
S(6, 2, 4, "dark_oak_door[facing=south,half=lower,hinge=left,open=false]")
S(6, 3, 4, "dark_oak_door[facing=south,half=upper,hinge=left,open=false]")
S(10, 2, 4, "dark_oak_door[facing=south,half=lower,hinge=right,open=false]")
S(10, 3, 4, "dark_oak_door[facing=south,half=upper,hinge=right,open=false]")
# bedroom: double bed against the aft wall, wardrobe in the corner (door at x=6 stays free)
for z in (2, 3):
    S(4, 2, z, "blue_bed[facing=west,part=head,occupied=false]"); S(5, 2, z, "blue_bed[facing=west,part=foot,occupied=false]")
S(7, 2, 2, "barrel[facing=up]"); S(7, 3, 2, "barrel[facing=up]")
S(4, 2, 4, W)
# bathroom: shower, sink, toilet (door at x=10 stays free)
S(9, 2, 2, "water_cauldron[level=3]")
S(12, 2, 2, "light_blue_carpet"); S(12, 2, 3, "quartz_stairs[facing=east,half=bottom]")
# open plan living / kitchen on the starboard side (z=7..8); aisle z=5..6 kept clear
S(4, 2, 7, "dark_oak_stairs[facing=west,half=bottom]"); S(4, 2, 8, "dark_oak_stairs[facing=west,half=bottom]")
S(5, 2, 7, "gray_carpet"); S(5, 2, 8, "gray_carpet")
S(6, 3, 9, "black_concrete")                 # TV on the wall
S(8, 2, 8, "dark_oak_slab[type=top]")         # dining table
S(8, 2, 7, "dark_oak_stairs[facing=south,half=bottom]")
S(10, 2, 8, "water_cauldron[level=3]"); S(11, 2, 8, "smoker[facing=north,lit=false]")
S(12, 2, 8, "iron_block"); S(12, 3, 8, "iron_block")
S(12, 2, 7, "potted_fern")
# hatch to the engine room in the aisle floor
S(12, 1, 5, "dark_oak_trapdoor[facing=west,half=top,open=false]")

# ladder from the aft deck to the roof
for y in R(2, 4): S(2, y, 3, "ladder[facing=west]")
# roof railing
for x in R(X0, X1):
    S(x, 5, 1, "iron_bars"); S(x, 5, 9, "iron_bars")
for z in R(1, 9):
    S(X1, 5, z, "iron_bars")
    if z != 3: S(X0, 5, z, "iron_bars")
# chimney
for y in R(5, 8): S(4, y, 8, "polished_blackstone_wall")
S(4, 9, 8, "campfire[lit=true,facing=north]")
# lifting column + wheelhouse
for x in (7, 8):
    for z in R(4, 6):
        for y in (5, 6): S(x, y, z, "black_concrete")
for y in R(5, 7): S(6, y, 5, "ladder[facing=west]")
for x in R(5, 10):
    for z in R(2, 8):
        if (x, z) != (6, 5): S(x, 7, z, "dark_oak_planks")
for x in R(4, 11):
    for z in R(1, 9):
        inner = 5 <= x <= 10 and 2 <= z <= 8
        S(x, 11, z, W if inner else DB)
S(7, 11, 5, "sea_lantern")
for (x, z) in [(5, 2), (5, 8), (10, 2), (10, 8)]:
    for y in (8, 9, 10): S(x, y, z, W)
for z in R(3, 7): S(10, 8, z, W); S(5, 8, z, W)
for x in R(6, 9): S(x, 8, 2, W); S(x, 8, 8, W)
# open window band y=9..10 all round (no glass)
S(7, 8, 5, "dark_oak_stairs[facing=west,half=bottom]")
for z in R(3, 7): S(9, 8, z, "polished_deepslate_slab[type=top]")
S(9, 9, 3, "black_carpet"); S(9, 9, 7, "black_carpet")
S(9, 9, 4, "lever[face=floor,facing=west,powered=false]")
S(9, 9, 6, "lever[face=floor,facing=west,powered=false]")
S(10, 10, 1, "red_concrete"); S(10, 10, 9, "lime_concrete")
S(8, 12, 5, "end_rod[facing=up]")
for z in R(3, 7): S(8, 13, z, "polished_blackstone_slab[type=bottom]")
for p in [(6, 12, 3), (6, 12, 7)]: S(*p, "lightning_rod[facing=up]")
S(10, 12, 6, "white_wool")                    # satellite dome

# aft deck
for z in R(2, 8):
    if z != 5: S(0, 2, z, "iron_bars")
S(1, 2, 1, "iron_bars"); S(1, 2, 9, "iron_bars")
for y in R(2, 5): S(0, y, 5, "birch_fence")
S(0, 6, 5, "end_rod[facing=up]")
for z in (3, 4):
    S(0, 5, z, "red_wool"); S(0, 4, z, "white_wool"); S(0, 3, z, "blue_wool")
# boarding ladders (outside the hull) reaching below the waterline
for y in R(-4, 1):
    S(14, y, -1, "ladder[facing=north]"); S(14, y, 11, "ladder[facing=south]")
    S(97, y, -1, "ladder[facing=north]"); S(97, y, 11, "ladder[facing=south]")

# ---------------- fore ship ----------------
for x in R(98, L - 3): S(x, 2, 0, DB); S(x, 2, 10, DB)
S(L - 2, 2, 1, DB); S(L - 2, 2, 9, DB)
for z in R(2, 8): S(L - 1, 2, z, DB)
S(L - 1, 3, 5, "lantern[hanging=false]")
# bow steering position
for z in R(4, 6): S(100, 2, z, "polished_deepslate_slab[type=top]")
S(100, 3, 4, "black_carpet"); S(100, 3, 6, "black_carpet")
S(100, 3, 5, "lever[face=floor,facing=west,powered=false]")
# radar mast with arm (like on the photos)
S(102, 2, 5, "black_concrete")
for y in R(3, 8): S(102, y, 5, "iron_bars")
for x in R(99, 104): S(x, 8, 5, "iron_bars")
S(103, 9, 5, "lantern[hanging=false]")
S(99, 7, 5, "white_wool")
for y in (5, 6):
    S(102, y, 3, "white_wool" if y == 6 else "blue_wool"); S(102, y, 7, "blue_wool" if y == 6 else "white_wool")
for zc, zz in ((3, (1, 2)), (7, (8, 9))):
    S(105, 2, zc, "grindstone[face=floor,facing=east]")
    for z in zz: S(105, 2, z, "chain[axis=z]")
S(105, 1, 0, "anvil[facing=east]"); S(105, 1, 10, "anvil[facing=east]")
# bow thruster
for z in R(1, 9):
    S(99, -3, z, "smooth_stone"); S(101, -3, z, "smooth_stone"); S(100, -2, z, "smooth_stone")
S(100, -3, 0, "iron_bars"); S(100, -3, 10, "iron_bars")
for y in R(-1, 0): S(100, y, 8, "ladder[facing=north]")
S(100, -1, 5, "iron_block")
S(100, 1, 8, "dark_oak_trapdoor[facing=north,half=top,open=false]")
for x in (97,):
    S(x, 2, 0, "air"); S(x, 2, 10, "air")

# free passage from the aft deck to the side gangways
for z in (1, 9): S(1, 2, z, "air")
B = {k: v for k, v in B.items() if v != "minecraft:air"}

# ---------------- connections (bars, fences, walls) ----------------
def base(s): return s.split("[")[0].replace("minecraft:", "")
NONFULL = ("slab", "stairs", "pane", "bars", "fence", "wall", "trapdoor", "door", "ladder", "lantern", "carpet",
           "rod", "chain", "potted", "cauldron", "lever", "campfire", "grindstone", "anvil", "_bed")
def is_full(s):
    if s is None: return False
    b = base(s)
    if b == "sea_lantern": return True
    return not any(k in b for k in NONFULL)
dirs = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
for p, s in list(B.items()):
    b = base(s)
    fam = None
    if b == "iron_bars": fam = "pane"
    elif b.endswith("fence"): fam = "fence"
    elif b.endswith("_wall"): fam = "wall"
    if not fam: continue
    props = {}
    for d, (dx, dy, dz) in dirs.items():
        n = B.get((p[0] + dx, p[1], p[2] + dz))
        nb = base(n) if n else ""
        con = is_full(n) or (fam == "pane" and (nb == "iron_bars" or nb.endswith("_wall"))) \
            or (fam == "fence" and nb.endswith("fence")) or (fam == "wall" and (nb.endswith("_wall") or nb == "iron_bars"))
        props[d] = ("low" if con else "none") if fam == "wall" else ("true" if con else "false")
    if fam == "wall": props["up"] = "true"
    B[p] = "minecraft:" + b + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"

pal = sorted(set(B.values()))
pidx = {s: i for i, s in enumerate(pal)}
flat = []
for (x, y, z), s in sorted(B.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
    flat += [x, y, z, pidx[s]]
out = {
    "name": "STELLA MARIS",
    "length": L, "beam": 11,
    "palette": pal,
    "blocks": flat,
    "hull": [[x, zr(x)[0], zr(x)[1], bot(x)] for x in range(L)],
    "holds": [[a, b] for a, b in HOLDS],
    "holdZ": [2, 8], "holdFloorY": -2, "coamingTopY": 3.5,
    "seats": {
        "aft": {"pos": [7.5, 8.45, 5.5], "yaw": 0},
        "bow": {"pos": [99.5, 2.0, 5.5], "yaw": 0}
    },
    "helmZones": {
        "aft": [7, 8, 3, 9, 10, 7],
        "bow": [99, 2, 4, 100, 3, 6]
    }
}
here = os.path.dirname(os.path.abspath(__file__))
dst = os.path.join(here, "..", "src", "main", "resources", "inlandbarge", "stella_maris.json")
os.makedirs(os.path.dirname(dst), exist_ok=True)
json.dump(out, open(dst, "w"), separators=(",", ":"))
print("blocks", len(B), "palette", len(pal), "->", os.path.relpath(dst))
