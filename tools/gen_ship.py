"""Genereert de scheepsstructuur van de HENDRIKA voor de mod.

Assen (sjabloon): x = lengte (0 = achtersteven, 95 = voorsteven), z = breedte (0 = bakboord), y = hoogte.
Laag y=0 ligt bij volle belading precies in de bovenste waterlaag.
Uitvoer: src/main/resources/binnenvaart/hendrika.json
"""
import json, os

B = {}
def S(x, y, z, s):
    if not s.startswith("minecraft:"):
        s = "minecraft:" + s
    B[(x, y, z)] = s
def R(a, b):
    return range(a, b + 1)

L = 96
def zr(x):
    if x == 0 or x == 95: return (2, 8)
    if x == 1 or x == 94: return (1, 9)
    return (0, 10)
def bot(x):
    if x <= 4: return -1
    if x == 5: return -2
    if x == 6: return -3
    if x <= 86: return -4
    if x <= 89: return -3
    if x <= 92: return -2
    if x <= 94: return -1
    return 0

UW = "red_terracotta"
def side(x, y):
    if y <= -1: return UW
    if x <= 13 or x >= 81: return "blue_concrete"
    return "black_concrete" if y == 0 else "gray_concrete"

DECK = "light_gray_concrete"
SS = "smooth_stone"
SSL = "smooth_stone_slab[type=bottom]"

# ---------------- romp ----------------
for x in range(L):
    z0, z1 = zr(x); b = bot(x)
    for z in R(z0, z1):
        S(x, b, z, UW if b <= -1 else side(x, b))
    for y in R(b + 1, 1):
        S(x, y, z0, side(x, y)); S(x, y, z1, side(x, y))
    if x in (0, 95):
        for z in R(z0, z1):
            for y in R(b, 1):
                S(x, y, z, side(x, y))
for x in list(R(0, 13)) + list(R(81, 95)):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1):
        if x not in (0, 95):
            S(x, 1, z, DECK)

# ---------------- laadruim (leeg) ----------------
HOLDS = [(15, 30), (32, 47), (49, 63), (65, 79)]
BEAMS = [31, 48, 64]
for x in R(14, 80):
    for y in R(-3, 2):
        S(x, y, 1, SS); S(x, y, 9, SS)
    S(x, 3, 1, SSL); S(x, 3, 9, SSL)
for x in (14, 80):
    for z in R(1, 9):
        for y in R(-3, 2):
            S(x, y, z, SS)
        S(x, 3, z, SSL)
for x in R(15, 79):
    for z in R(2, 8):
        S(x, -3, z, SS)
for b in BEAMS:
    for z in R(2, 8):
        S(b, 3, z, SSL)
# ruimnummers op de vloer (lichte strepen) en ladders per ruim
for i, (a, b) in enumerate(HOLDS):
    lx = a + 1
    for y in R(-2, 3):
        S(lx, y, 2, "ladder[facing=south]")
    # ladder on the outside of the coaming: from the side deck over the edge into the hold
    for y in (2, 3): S(lx, y, 0, "ladder[facing=north]")

# ---------------- achterschip: roer, schroeven ----------------
for x in R(2, 4):
    for y in R(-4, -2): S(x, y, 5, UW)
S(5, -4, 5, UW); S(5, -3, 5, UW); S(6, -4, 5, UW)
for zc in (3, 7):
    for y in R(-4, -2): S(1, y, zc, UW)
    S(3, -3, zc, "lightning_rod[facing=west]")
    S(3, -2, zc, "waxed_cut_copper_slab[type=bottom]")
    S(3, -4, zc, "waxed_cut_copper_slab[type=top]")
    S(3, -3, zc - 1, "waxed_cut_copper_slab[type=top]")
    S(3, -3, zc + 1, "waxed_cut_copper_slab[type=bottom]")
    S(4, -3, zc, "chain[axis=x]")
    S(5, -3, zc, "chain[axis=x]")

# machinekamer x=8..12, y=-3..0
for z in R(1, 9):
    for y in R(-3, 0):
        S(7, y, z, SS); S(13, y, z, SS)
    for x in R(8, 12):
        S(x, -3, z, "iron_trapdoor[facing=north,half=bottom,open=false]")
for zc in (3, 7):
    S(8, -3, zc, "iron_block")
    for x in R(9, 11): S(x, -3, zc, "waxed_weathered_cut_copper")
    for x in R(9, 10): S(x, -2, zc, "waxed_weathered_cut_copper_slab[type=bottom]")
    for y in R(-2, 0): S(11, y, zc, "lightning_rod[facing=up]")
S(10, -3, 9, "iron_block"); S(11, -3, 9, "waxed_weathered_cut_copper")
S(12, -3, 9, "lectern[facing=west]")
S(9, -3, 1, "smithing_table"); S(10, -3, 1, "crafting_table"); S(11, -3, 1, "anvil[facing=east]")
S(8, -3, 1, "barrel[facing=up]"); S(8, -2, 1, "barrel[facing=up]"); S(8, -3, 9, "barrel[facing=up]")
S(8, 0, 5, "lantern[hanging=true]"); S(11, 0, 5, "lantern[hanging=true]")
for y in R(-2, 0): S(12, y, 5, "ladder[facing=west]")
S(12, 1, 5, "spruce_trapdoor[facing=west,half=top,open=false]")

# ---------------- woning x=3..11 ----------------
W = "white_concrete"
for x in R(3, 11):
    for z in R(1, 9):
        S(x, 4, z, "light_gray_concrete")
        edge = x in (3, 11) or z in (1, 9)
        for y in (2, 3):
            if edge: S(x, y, z, W)
        if not edge: S(x, 1, z, "spruce_planks")
for p in [(4, 4, 3), (5, 4, 8), (8, 4, 6), (9, 4, 3)]: S(*p, "sea_lantern")
for z in R(2, 8):
    for y in (2, 3): S(6, y, z, W)
for x in (4, 5):
    for y in (2, 3): S(x, y, 6, W)
    for z in (7, 8): S(x, 1, z, "smooth_quartz")
S(6, 2, 5, "birch_door[facing=east,half=lower,hinge=left,open=false]")
S(6, 3, 5, "birch_door[facing=east,half=upper,hinge=left,open=false]")
S(5, 2, 6, "birch_door[facing=south,half=lower,hinge=left,open=false]")
S(5, 3, 6, "birch_door[facing=south,half=upper,hinge=left,open=false]")
S(11, 2, 5, "birch_door[facing=east,half=lower,hinge=right,open=false]")
S(11, 3, 5, "birch_door[facing=east,half=upper,hinge=right,open=false]")
for x in (4, 5, 7, 8, 9): S(x, 3, 1, "glass_pane")
for x in (8, 9, 10): S(x, 3, 9, "glass_pane")
for x in (4, 5): S(x, 3, 9, "white_stained_glass_pane")
for z in (2, 3, 7, 8): S(11, 3, z, "glass_pane")
for z in (2, 3): S(3, 3, z, "glass_pane")
S(3, 3, 7, "white_stained_glass_pane")
# slaapkamer: tweepersoonsbed (matras + hoofdeinde), kast, nachtlamp
for z in (2, 3):
    S(4, 2, z, "white_bed[facing=west,part=head,occupied=false]"); S(5, 2, z, "white_bed[facing=west,part=foot,occupied=false]")
S(4, 2, 4, "barrel[facing=up]"); S(4, 3, 4, "lantern[hanging=false]")
# (ton weggehaald: blokkeerde de slaapkamerdeur)
# badkamer
S(4, 2, 7, "water_cauldron[level=3]"); S(4, 2, 8, "quartz_stairs[facing=west,half=bottom]")
S(5, 2, 8, "light_blue_carpet")
# keuken
S(7, 2, 2, "barrel[facing=up]"); S(8, 2, 2, "smoker[facing=south,lit=false]")
S(9, 2, 2, "water_cauldron[level=3]"); S(10, 2, 2, "iron_block"); S(10, 3, 2, "iron_block")
S(8, 2, 4, "spruce_slab[type=top]")
S(7, 2, 4, "spruce_stairs[facing=west,half=bottom]"); S(9, 2, 4, "spruce_stairs[facing=east,half=bottom]")
# woonkamer
S(10, 2, 7, "dark_oak_stairs[facing=east,half=bottom]"); S(10, 2, 8, "dark_oak_stairs[facing=east,half=bottom]")
S(7, 2, 7, "barrel[facing=up]"); S(7, 2, 8, "barrel[facing=up]")
S(7, 3, 7, "black_concrete"); S(7, 3, 8, "black_concrete")
S(9, 2, 7, "spruce_trapdoor[facing=north,half=bottom,open=false]")
for p in [(8, 2, 6), (8, 2, 7), (8, 2, 8), (9, 2, 8), (9, 2, 6)]: S(*p, "gray_carpet")
S(10, 2, 4, "potted_fern")
# ladder naar dak
for y in R(2, 4): S(2, y, 5, "ladder[facing=west]")
# dakreling
for x in R(7, 11): S(x, 5, 1, "iron_bars")
for x in R(3, 11): S(x, 5, 9, "iron_bars")
for z in R(1, 9):
    S(11, 5, z, "iron_bars")
    if z not in (1, 2, 5): S(3, 5, z, "iron_bars")
# auto + autokraan
for z in (1, 2):
    S(3, 5, z, "polished_blackstone"); S(4, 5, z, "smooth_quartz"); S(5, 5, z, "smooth_quartz"); S(6, 5, z, "polished_blackstone")
    S(3, 6, z, "smooth_quartz_slab[type=bottom]"); S(4, 6, z, "light_gray_stained_glass"); S(5, 6, z, "light_gray_stained_glass"); S(6, 6, z, "smooth_quartz_slab[type=bottom]")
S(3, 5, 3, "yellow_concrete"); S(3, 6, 3, "yellow_concrete"); S(3, 7, 3, "yellow_concrete"); S(3, 7, 2, "yellow_concrete")
# schoorsteen
for y in R(5, 8): S(4, y, 8, "polished_deepslate_wall")
S(4, 9, 8, "campfire[lit=true,facing=north]")
# hefkolom stuurhuis
for x in (7, 8):
    for z in R(4, 6):
        for y in (5, 6): S(x, y, z, "polished_andesite")
for y in R(5, 7): S(6, y, 5, "ladder[facing=west]")
# stuurhuis x=5..10, z=2..8, vloer 7, wanden 8-10, dak 11
for x in R(5, 10):
    for z in R(2, 8):
        if (x, z) != (6, 5): S(x, 7, z, "dark_oak_planks")
for x in R(4, 11):
    for z in R(1, 9):
        inner = 5 <= x <= 10 and 2 <= z <= 8
        S(x, 11, z, W if inner else "smooth_quartz_slab[type=top]")
S(7, 11, 5, "sea_lantern")
G = "gray_stained_glass_pane"
for y in (8, 9, 10):
    for (x, z) in [(5, 2), (5, 8), (10, 2), (10, 8)]: S(x, y, z, W)
for y in (9, 10):
    for z in R(3, 7): S(10, y, z, G)
for z in R(3, 7): S(10, 8, z, W)
for x in R(6, 9):
    S(x, 8, 2, W); S(x, 8, 8, W)
    for y in (9, 10): S(x, y, 2, G); S(x, y, 8, G)
for z in R(3, 7):
    S(5, 8, z, W)
    for y in (9, 10): S(5, y, z, G)
S(7, 8, 5, "dark_oak_stairs[facing=west,half=bottom]")
# stuurconsole met schermen en stuurhendel
for z in R(3, 7): S(9, 8, z, "polished_deepslate_slab[type=top]")
S(9, 9, 3, "black_stained_glass"); S(9, 9, 7, "black_stained_glass")
S(9, 9, 4, "lever[face=floor,facing=west,powered=false]")
S(9, 9, 6, "lever[face=floor,facing=west,powered=false]")
S(6, 8, 7, "cartography_table"); S(6, 8, 3, "barrel[facing=up]")
S(10, 10, 1, "red_stained_glass"); S(10, 10, 9, "green_stained_glass")
S(8, 12, 5, "end_rod[facing=up]")
for z in R(3, 7): S(8, 13, z, "polished_blackstone_slab[type=bottom]")
for p in [(6, 12, 3), (6, 12, 7), (10, 12, 4)]: S(*p, "lightning_rod[facing=up]")
S(10, 12, 6, "end_rod[facing=up]")

# achterdek
for z in R(2, 8):
    if z != 5: S(0, 2, z, "iron_bars")
S(1, 2, 1, "iron_bars"); S(1, 2, 9, "iron_bars")
for y in R(2, 5): S(0, y, 5, "birch_fence")
S(0, 6, 5, "end_rod[facing=up]")
for z in (3, 4):
    S(0, 5, z, "red_wool"); S(0, 4, z, "white_wool"); S(0, 3, z, "blue_wool")
S(1, 2, 3, "potted_red_tulip"); S(1, 2, 7, "potted_poppy")
# buitenladders (aan boord klimmen vanuit het water), tot onder de waterlijn
for y in R(-3, 1):
    S(12, y, -1, "ladder[facing=north]")
    S(12, y, 11, "ladder[facing=south]")
for y in R(-3, 1):
    S(84, y, -1, "ladder[facing=north]")
    S(84, y, 11, "ladder[facing=south]")

# ---------------- voorschip ----------------
BW = "blue_concrete"
for x in R(84, 93): S(x, 2, 0, BW); S(x, 2, 10, BW)
S(94, 2, 1, BW); S(94, 2, 9, BW)
for z in R(2, 8): S(95, 2, z, BW)
S(84, 2, 0, "air"); S(84, 2, 10, "air")   # opening in verschansing bij buitenladder
S(95, 3, 5, "lantern[hanging=false]")
# stuurstand voor: lessenaar met schermen en hendels
for z in R(4, 6): S(86, 2, z, "polished_deepslate_slab[type=top]")
S(86, 3, 4, "black_stained_glass"); S(86, 3, 6, "black_stained_glass")
S(86, 3, 5, "lever[face=floor,facing=west,powered=false]")
S(88, 2, 5, BW); S(88, 3, 5, "polished_blackstone_wall")
for y in R(4, 7): S(88, y, 5, "iron_bars")
for z in (3, 4, 6, 7): S(88, 7, z, "iron_bars")
S(88, 8, 5, "lantern[hanging=false]")
S(88, 6, 3, "red_wool"); S(88, 5, 3, "white_wool"); S(88, 4, 3, "blue_wool")
S(88, 6, 7, "light_blue_wool"); S(88, 5, 7, "white_wool"); S(88, 4, 7, "light_blue_wool")
for zc, zz in ((3, (1, 2)), (7, (8, 9))):
    S(90, 2, zc, "grindstone[face=floor,facing=east]")
    for z in zz: S(90, 2, z, "chain[axis=z]")
S(90, 1, 0, "anvil[facing=east]"); S(90, 1, 10, "anvil[facing=east]")
# boegschroefkast
for z in R(1, 9):
    S(85, -3, z, SS); S(87, -3, z, SS); S(86, -2, z, SS)
S(86, -3, 0, "iron_bars"); S(86, -3, 10, "iron_bars")
S(86, -1, 5, "iron_block"); S(86, 0, 5, "grindstone[face=floor,facing=north]")
for y in (-1, 0): S(86, y, 8, "ladder[facing=north]")
S(86, 1, 8, "spruce_trapdoor[facing=north,half=top,open=false]")

# geen glas: ruiten worden open raamopeningen, overig glas wordt beton
REPL = {
    "minecraft:black_stained_glass": "minecraft:black_carpet",
    "minecraft:red_stained_glass": "minecraft:red_concrete",
    "minecraft:green_stained_glass": "minecraft:lime_concrete",
    "minecraft:light_gray_stained_glass": "minecraft:light_gray_concrete",
}
for k, v in list(B.items()):
    if v.endswith("glass_pane"): B[k] = "minecraft:air"
    elif v in REPL: B[k] = REPL[v]

# free passage from the aft deck to the side gangways
for z in (1, 9): S(1, 2, z, "air")
B = {k: v for k, v in B.items() if v != "minecraft:air"}

# ---------------- verbindingen (ruiten, tralies, hekken, muren) ----------------
def base(s): return s.split("[")[0].replace("minecraft:", "")
NONFULL = ("slab", "stairs", "pane", "bars", "fence", "wall", "trapdoor", "door", "ladder", "lantern", "carpet",
           "rod", "chain", "potted", "cauldron", "lever", "campfire", "grindstone", "anvil", "lectern", "_bed")
def is_full(s):
    if s is None: return False
    b = base(s)
    if b == "sea_lantern": return True
    return not any(k in b for k in NONFULL)
dirs = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
for p, s in list(B.items()):
    b = base(s)
    fam = None
    if b.endswith("pane") or b == "iron_bars": fam = "pane"
    elif b.endswith("fence"): fam = "fence"
    elif b.endswith("_wall"): fam = "wall"
    if not fam: continue
    props = {}
    for d, (dx, dy, dz) in dirs.items():
        n = B.get((p[0] + dx, p[1], p[2] + dz))
        nb = base(n) if n else ""
        con = is_full(n) or (fam == "pane" and (nb.endswith("pane") or nb == "iron_bars" or nb.endswith("_wall"))) \
            or (fam == "fence" and nb.endswith("fence")) or (fam == "wall" and (nb.endswith("_wall") or nb.endswith("pane") or nb == "iron_bars"))
        props[d] = ("low" if con else "none") if fam == "wall" else ("true" if con else "false")
    if fam == "wall": props["up"] = "true"
    B[p] = "minecraft:" + b + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"

# ---------------- export ----------------
pal = sorted(set(B.values()))
pidx = {s: i for i, s in enumerate(pal)}
flat = []
for (x, y, z), s in sorted(B.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
    flat += [x, y, z, pidx[s]]
out = {
    "name": "HENDRIKA",
    "length": L, "beam": 11,
    "palette": pal,
    "blocks": flat,
    "hull": [[x, zr(x)[0], zr(x)[1], bot(x)] for x in range(L)],
    "holds": [[a, b] for a, b in HOLDS],
    "holdZ": [2, 8], "holdFloorY": -2, "coamingTopY": 3.5,
    "seats": {
        "aft": {"pos": [7.5, 8.45, 5.5], "yaw": 0},
        "bow": {"pos": [85.5, 2.0, 5.5], "yaw": 0}
    },
    "helmZones": {
        "aft": [7, 8, 3, 9, 10, 7],
        "bow": [85, 2, 4, 86, 3, 6]
    }
}
here = os.path.dirname(os.path.abspath(__file__))
dst = os.path.join(here, "..", "src", "main", "resources", "binnenvaart", "hendrika.json")
os.makedirs(os.path.dirname(dst), exist_ok=True)
json.dump(out, open(dst, "w"), separators=(",", ":"))
print("blokken", len(B), "palet", len(pal), "->", os.path.relpath(dst))
