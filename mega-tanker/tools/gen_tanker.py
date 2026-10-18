"""Builds the TITAN STAR, a 180 x 30 crude oil tanker, for the Mega Oil Tanker mod.

Template axes: x = length (0 = stern, 179 = bow), z = beam (0 = port, 29 = starboard), y = height.
Main deck plating is layer y=1 (walking surface 2.0). Fully loaded, the water surface sits at y = -1.0.
The hull is a closed shell: the tanks inside are not walkable, so the mod does not need to push water
out of the hull and the hidden inner faces are never drawn.
Output: src/main/resources/megatanker/titan_star.json
"""
import json, os, math

B = {}
def S(x, y, z, s):
    if not s.startswith("minecraft:"):
        s = "minecraft:" + s
    B[(x, y, z)] = s
def R(a, b):
    return range(a, b + 1)

L, BEAM = 180, 30
FCSLE = 162                     # forecastle starts here (raised deck)

def zr(x):
    """Port and starboard hull line at deck height."""
    if x <= 4:
        inset = [4, 3, 2, 1, 1][x]
        return (inset, BEAM - 1 - inset)
    if x >= 150:
        t = (x - 150) / 30.0
        h = 14.6 * (1 - t ** 2.3)
        h = max(h, 0.6)
        return (int(round(14.5 - h)), int(round(14.5 + h)))
    return (0, BEAM - 1)

def bot(x):
    if x <= 3: return -4
    if x <= 7: return [-6, -8, -10, -11][x - 4]
    return -12

def top(x):
    return 4 if x >= FCSLE else 1

def zrange(x, y):
    z0, z1 = zr(x)
    if x >= 168 and y <= -6:                      # bulbous bow
        return (12, 17)
    if x >= 160 and y <= -2:                      # bow narrows below the waterline
        k = min(1.0, (-2 - y) / 8.0) * (x - 160) / 20.0
        c = 14.5
        h = (z1 - z0) / 2.0 * (1 - 0.35 * k)
        return (int(round(c - h)), int(round(c + h)))
    return (z0, z1)

# ---------------- hull as a closed shell ----------------
vol = set()
for x in range(L):
    for y in R(bot(x), top(x)):
        a, b = zrange(x, y)
        for z in R(a, b):
            vol.add((x, y, z))
# bulb sticks out a little in front of the stem
for x in R(L, L + 2):
    for y in R(-11, -7):
        for z in R(13, 16):
            vol.add((x, y, z))

RED, BLACK, DECK = "red_concrete", "black_concrete", "green_terracotta"
for (x, y, z) in vol:
    nb = [(x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)]
    if all(n in vol for n in nb):
        continue                                  # enclosed: left empty (cargo tanks)
    if y == top(x if x < L else L - 1) and (x, y + 1, z) not in vol:
        S(x, y, z, DECK if x < FCSLE else "red_terracotta")
    elif y <= -2:
        S(x, y, z, RED)
    else:
        S(x, y, z, BLACK)
# white boot line on the forecastle edge
for x in range(FCSLE, L):
    a, b = zr(x)
    S(x, 4, a, "white_concrete"); S(x, 4, b, "white_concrete")

# ---------------- deck: railings, pipes, catwalk ----------------
for x in R(8, FCSLE - 1):
    a, b = zr(x)
    if x in (95, 96): continue                    # gap for the pilot ladders
    S(x, 2, a, "iron_bars"); S(x, 2, b, "iron_bars")
for x in range(FCSLE, L):
    a, b = zr(x)
    S(x, 5, a, "iron_bars"); S(x, 5, b, "iron_bars")
for x in R(0, 7):
    a, b = zr(x)
    S(x, 2, a, "iron_bars"); S(x, 2, b, "iron_bars")
for z in R(zr(0)[0], zr(0)[1]): S(0, 2, z, "iron_bars")

TANKS = [(32, 62), (64, 94), (97, 127), (129, 159)]
for x in R(30, 160):
    for z in (11, 12, 17, 18):
        S(x, 2, z, "andesite_wall")
for x in R(94, 97):                               # crossover pipes at the manifold
    for z in R(3, 26):
        if z not in (11, 12, 17, 18): S(x, 2, z, "andesite_wall")
# catwalk on posts above the pipes
for x in R(30, 160):
    S(x, 4, 14, "smooth_stone_slab[type=bottom]"); S(x, 4, 15, "smooth_stone_slab[type=bottom]")
    S(x, 5, 13, "iron_bars"); S(x, 5, 16, "iron_bars")
    if x % 8 == 0:
        for y in (2, 3): S(x, y, 14, "iron_bars"); S(x, y, 15, "iron_bars")
S(28, 2, 14, "smooth_stone_stairs[facing=east,half=bottom]"); S(28, 2, 15, "smooth_stone_stairs[facing=east,half=bottom]")
S(29, 3, 14, "smooth_stone_stairs[facing=east,half=bottom]"); S(29, 3, 15, "smooth_stone_stairs[facing=east,half=bottom]")
S(29, 2, 14, "smooth_stone"); S(29, 2, 15, "smooth_stone")
for x in (29, 30):
    S(x, 5, 13, "air"); S(x, 5, 16, "air")
# stairs from the main deck up to the forecastle
S(158, 2, 5, "smooth_stone_stairs[facing=east,half=bottom]")
S(159, 3, 5, "smooth_stone_stairs[facing=east,half=bottom]"); S(159, 2, 5, "smooth_stone")
S(160, 4, 5, "smooth_stone_stairs[facing=east,half=bottom]"); S(160, 3, 5, "smooth_stone"); S(160, 2, 5, "smooth_stone")
S(161, 4, 5, "smooth_stone"); S(161, 3, 5, "smooth_stone"); S(161, 2, 5, "smooth_stone")
# tank valves (click to open a tank), tank hatches and vent posts
for i, (a, b) in enumerate(TANKS):
    cx = (a + b) // 2
    S(cx, 2, 3, "cauldron"); S(cx, 2, 26, "cauldron")
    S(cx - 1, 2, 3, "lever[face=floor,facing=east,powered=false]")
    for hx in (a + 4, b - 4):
        S(hx, 2, 7, "iron_trapdoor[facing=north,half=bottom,open=false]")
        S(hx, 2, 22, "iron_trapdoor[facing=north,half=bottom,open=false]")
    S(cx, 2, 9, "lightning_rod[facing=up]"); S(cx, 3, 9, "lightning_rod[facing=up]")
    S(cx, 2, 20, "lightning_rod[facing=up]"); S(cx, 3, 20, "lightning_rod[facing=up]")
# hose cranes at the manifold
for (cx, cz, dz) in ((92, 4, 1), (99, 25, -1)):
    for y in R(2, 8): S(cx, y, cz, "yellow_concrete")
    for k in range(1, 8): S(cx, 8, cz + dz * k, "yellow_concrete" if k < 7 else "chain[axis=y]")
    S(cx, 7, cz + dz * 7, "chain[axis=y]"); S(cx, 6, cz + dz * 7, "lantern[hanging=true]")
# pilot ladders on both sides, from below the light waterline up to the deck
for y in R(-10, 1):
    S(95, y, -1, "ladder[facing=north]"); S(95, y, BEAM, "ladder[facing=south]")

# ---------------- stern: rudder, propeller, mooring deck, lifeboat ----------------
for y in R(-11, -5): S(1, y, 14, BLACK if y > -2 else RED); S(1, y, 15, RED)
for y in R(-10, -6): S(5, y, 15, "waxed_cut_copper")
for z in R(13, 17): S(5, -8, z, "waxed_cut_copper")
S(6, -8, 15, "chain[axis=x]"); S(7, -8, 15, "chain[axis=x]")
for (bx, bz) in ((2, 6), (2, 23), (6, 3), (6, 26), (150, 3), (150, 26), (40, 2), (40, 27), (120, 2), (120, 27)):
    S(bx, 2, bz, "polished_blackstone_wall")
for (wx, wz) in ((4, 9), (4, 20)):
    S(wx, 2, wz, "grindstone[face=floor,facing=north]")
# free-fall lifeboat on its ramp
for x in R(1, 6):
    for z in (12, 17): S(x, 3, z, "iron_bars"); S(x, 2, z, "iron_bars")
for x in R(1, 6):
    for z in R(13, 16):
        S(x, 4 + (x >= 4), z, "orange_concrete")
for z in R(13, 16): S(6, 6, z, "orange_concrete"); S(5, 6, z, "white_concrete")

# ---------------- accommodation block with bridge ----------------
AX0, AX1, AZ0, AZ1 = 8, 26, 6, 23
FLOORS = [4, 7, 10, 13, 16]
ROOF = 20
W = "white_concrete"
for x in R(AX0, AX1):
    for z in R(AZ0, AZ1):
        edge = x in (AX0, AX1) or z in (AZ0, AZ1)
        for y in R(2, ROOF - 1):
            if edge: S(x, y, z, W)
        for f in FLOORS:
            if not edge: S(x, f, z, "smooth_stone" if f < 16 else "dark_oak_planks")
        S(x, ROOF, z, W if not edge else "blue_concrete")
# window openings on every deck (no glass) and the open bridge band
for f in [1] + FLOORS[:-1]:
    wy = f + 2
    for x in range(AX0 + 2, AX1 - 1, 3):
        S(x, wy, AZ0, "air"); S(x, wy, AZ1, "air")
    for z in range(AZ0 + 2, AZ1 - 1, 3):
        if z not in (14, 15): S(AX1, wy, z, "air")
for y in (18, 19):
    for z in R(AZ0 + 1, AZ1 - 1): S(AX1, y, z, "air")
    for x in R(AX0 + 4, AX1 - 1): S(x, y, AZ0, "air"); S(x, y, AZ1, "air")
# bridge wings
for z in list(R(0, AZ0 - 1)) + list(R(AZ1 + 1, BEAM - 1)):
    for x in R(20, AX1):
        S(x, 16, z, "smooth_stone_slab[type=top]")
    S(AX1, 17, z, "iron_bars"); S(20, 17, z, "iron_bars")
for x in R(20, AX1):
    S(x, 17, 0, "iron_bars"); S(x, 17, BEAM - 1, "iron_bars")
S(AX1, 17, 0, "red_concrete"); S(AX1, 17, BEAM - 1, "lime_concrete")
for z in (AZ0, AZ1):
    for y in (17,): S(22, y, z, "air"); S(22, y + 1, z, "air")    # doors out to the wings
# stairwell: ladder shaft from the main deck to the bridge
for y in R(2, 16):
    S(18, y, 14, "smooth_stone")
    S(17, y, 14, "ladder[facing=west]")
# doors on the main deck
S(AX1, 2, 14, "dark_oak_door[facing=east,half=lower,hinge=right,open=false]")
S(AX1, 3, 14, "dark_oak_door[facing=east,half=upper,hinge=right,open=false]")
S(20, 2, AZ0, "dark_oak_door[facing=north,half=lower,hinge=left,open=false]")
S(20, 3, AZ0, "dark_oak_door[facing=north,half=upper,hinge=left,open=false]")
S(20, 2, AZ1, "dark_oak_door[facing=south,half=lower,hinge=right,open=false]")
S(20, 3, AZ1, "dark_oak_door[facing=south,half=upper,hinge=right,open=false]")
for p in [(13, 4, 10), (13, 4, 19), (22, 4, 10), (22, 4, 19)]:
    for dy in (0, 3, 6, 9): S(p[0], p[1] + dy, p[2], "sea_lantern")
S(14, ROOF, 14, "sea_lantern"); S(22, ROOF, 15, "sea_lantern")
# main deck: engine control room (consoles against the aft wall), galley and mess
for z in R(8, 12): S(9, 2, z, "polished_deepslate_slab[type=top]")
S(9, 3, 9, "black_carpet"); S(9, 3, 11, "black_carpet")
S(9, 2, 17, "smoker[facing=east,lit=false]"); S(9, 2, 18, "water_cauldron[level=3]"); S(9, 2, 19, "iron_block"); S(9, 3, 19, "iron_block")
for z in (20, 21): S(15, 2, z, "dark_oak_slab[type=top]")
S(14, 2, 20, "dark_oak_stairs[facing=west,half=bottom]"); S(16, 2, 21, "dark_oak_stairs[facing=east,half=bottom]")
# cabins on the middle decks: bed + desk against the outer walls, centre kept free
for f in FLOORS[:-1]:
    y = f + 1
    for (bx, bz) in ((10, 7), (14, 7), (22, 7), (10, 22), (14, 22), (22, 22)):
        S(bx, y, bz, "white_bed[facing=west,part=head,occupied=false]"); S(bx + 1, y, bz, "white_bed[facing=west,part=foot,occupied=false]")
    S(24, y, 7, "barrel[facing=up]"); S(24, y, 22, "barrel[facing=up]")
    S(10, y, 12, "potted_fern")
# bridge: helm console, chair, chart table, radar screens
for z in R(12, 17): S(25, 17, z, "polished_deepslate_slab[type=top]")
for z in (12, 17): S(25, 18, z, "black_carpet")
S(25, 18, 14, "lever[face=floor,facing=west,powered=false]"); S(25, 18, 15, "lever[face=floor,facing=west,powered=false]")
S(23, 17, 14, "dark_oak_stairs[facing=west,half=bottom]")
S(12, 17, 9, "cartography_table"); S(12, 17, 20, "barrel[facing=up]")
# mast, radar and horn on the bridge roof
for y in R(21, 26): S(20, y, 15, "iron_bars")
for z in R(12, 18): S(20, 26, z, "iron_bars")
S(20, 27, 15, "lantern[hanging=false]")
for z in R(13, 17): S(22, 22, z, "polished_blackstone_slab[type=bottom]")
S(22, 21, 15, "end_rod[facing=up]")
for z in R(10, 12): S(17, 22, z, "polished_blackstone_slab[type=bottom]")
S(17, 21, 11, "end_rod[facing=up]")
S(24, 21, 9, "white_wool"); S(24, 21, 20, "white_wool")
# funnel
for x in R(9, 13):
    for z in R(11, 18):
        if x in (9, 13) or z in (11, 18):
            for y in R(21, 27):
                S(x, y, z, "black_concrete" if y >= 26 else ("blue_concrete" if y in (23, 24) else W))

# ---------------- forecastle ----------------
for zc, zz in ((9, R(3, 8)), (20, R(21, 26))):
    S(171, 5, zc, "grindstone[face=floor,facing=east]")
    for z in zz:
        if (171, 5, z) not in B: S(171, 5, z, "chain[axis=z]")
for z in (4, 25):
    pass
S(174, 5, 14, "iron_bars"); S(174, 5, 15, "iron_bars")
for y in R(5, 12): S(176, y, 14, "iron_bars")
S(176, 13, 14, "lantern[hanging=false]")
S(176, 9, 13, "iron_bars"); S(176, 9, 15, "iron_bars")
# bow steering position
for z in R(13, 16): S(169, 5, z, "polished_deepslate_slab[type=top]")
S(169, 6, 13, "black_carpet"); S(169, 6, 16, "black_carpet")
S(169, 6, 14, "lever[face=floor,facing=west,powered=false]")
for p in [(165, 5, 4), (165, 5, 25), (178, 5, 12), (178, 5, 17)]: S(*p, "polished_blackstone_wall")

B = {k: v for k, v in B.items() if v != "minecraft:air"}

# ---------------- connections ----------------
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
    elif b.endswith("_wall"): fam = "wall"
    if not fam: continue
    props = {}
    for d, (dx, dy, dz) in dirs.items():
        n = B.get((p[0] + dx, p[1], p[2] + dz))
        nb = base(n) if n else ""
        con = is_full(n) or (fam == "pane" and (nb == "iron_bars" or nb.endswith("_wall"))) \
            or (fam == "wall" and (nb.endswith("_wall") or nb == "iron_bars"))
        props[d] = ("low" if con else "none") if fam == "wall" else ("true" if con else "false")
    if fam == "wall": props["up"] = "true" if b == "polished_blackstone_wall" else "false"
    B[p] = "minecraft:" + b + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"

pal = sorted(set(B.values()))
pidx = {s: i for i, s in enumerate(pal)}
flat = []
for (x, y, z), s in sorted(B.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
    flat += [x, y, z, pidx[s]]
out = {
    "name": "TITAN STAR",
    "length": L, "beam": BEAM,
    "palette": pal,
    "blocks": flat,
    "hull": [[x, zr(x)[0], zr(x)[1], bot(x)] for x in range(L)],
    "holds": [[a, b] for a, b in TANKS],
    "holdZ": [2, 27], "holdFloorY": 2, "coamingTopY": 2,
    "seats": {
        "aft": {"pos": [23.5, 17.45, 14.5], "yaw": 0},
        "bow": {"pos": [167.5, 5.0, 14.5], "yaw": 0}
    },
    "helmZones": {
        "aft": [23, 17, 12, 25, 19, 17],
        "bow": [168, 5, 13, 169, 6, 16]
    }
}
here = os.path.dirname(os.path.abspath(__file__))
dst = os.path.join(here, "..", "src", "main", "resources", "megatanker", "titan_star.json")
os.makedirs(os.path.dirname(dst), exist_ok=True)
json.dump(out, open(dst, "w"), separators=(",", ":"))
print("blocks", len(B), "palette", len(pal), "->", os.path.relpath(dst))
