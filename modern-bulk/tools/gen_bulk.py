"""Builds the ZEELANDIA template (120 x 17) for the Modern Bulk Carrier mod.

A modern Dutch inland bulk carrier (motorvrachtschip): dark hull with light blue bow and stern,
white accommodation aft, a hydraulic lifting wheelhouse on a column, a car with its crane on the aft deck,
one long cargo space with high grey coamings (blue top rail) split by transverse beams into four holds,
rolling hatch covers, and a raised foredeck with anchor winches, A-mast and bow steering position.

Template axes: x = length (0 = stern, 119 = bow), z = beam (0 = port, 16 = starboard), y = height.
Deck plating is layer y=1 (top = 2.0). Output: src/main/resources/modernbulk/zeelandia.json
"""
import json, os

B = {}
def S(x, y, z, s):
    if not s.startswith("minecraft:"):
        s = "minecraft:" + s
    B[(x, y, z)] = s
def R(a, b):
    return range(a, b + 1)

L = 120
W_BEAM = 17
ZC = 8                      # centre line
def zr(x):
    if x == 0: return (2, 14)
    if x == 1: return (1, 15)
    bow = {113: (1, 15), 114: (1, 15), 115: (2, 14), 116: (3, 13), 117: (4, 12), 118: (5, 11), 119: (6, 10)}
    return bow.get(x, (0, 16))
def bot(x):
    if x <= 3: return -2
    if x == 4: return -3
    if x == 5: return -4
    if x <= 105: return -5
    if x <= 109: return -4
    if x <= 113: return -3
    if x <= 116: return -2
    return -1

HULL = "black_concrete"
DARK = "gray_concrete"
BLUE = "light_blue_concrete"
NAVY = "blue_concrete"
WHITE = "white_concrete"
DECK = "light_gray_concrete"
COAM = "light_gray_concrete"
CAP = "smooth_quartz_slab[type=bottom]"
INNER = "light_gray_concrete"
AFT_END, HOLD_START, HOLD_END, FORE_START = 22, 23, 102, 103

def side_colour(x, y):
    if y <= -1: return HULL
    if x <= AFT_END or x >= FORE_START: return BLUE      # light blue painted bow and stern
    return HULL if y == 0 else BLUE                      # dark hull, light blue sheer strake along the cargo section

# ---------------- hull ----------------
for x in range(L):
    z0, z1 = zr(x); b = bot(x)
    for z in R(z0, z1):
        S(x, b, z, HULL)
    for y in R(b + 1, 1):
        S(x, y, z0, side_colour(x, y)); S(x, y, z1, side_colour(x, y))
    if x in (0, L - 1):
        for z in R(z0, z1):
            for y in R(b, 1):
                S(x, y, z, side_colour(x, y))
# decks
for x in list(R(1, AFT_END)) + list(R(FORE_START, L - 2)):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1):
        S(x, 1, z, DECK)
# side decks (gangboord) along the cargo section: the hull top at z=0 / z=16 is the walkway
for x in R(HOLD_START, HOLD_END):
    S(x, 1, 0, BLUE); S(x, 1, 16, BLUE)

# ---------------- stern: rudders, propellers ----------------
for zc in (5, 11):
    for y in R(-4, -2): S(1, y, zc, HULL)
    S(3, -3, zc, "lightning_rod[facing=west]")
    S(3, -2, zc, "waxed_cut_copper_slab[type=bottom]"); S(3, -4, zc, "waxed_cut_copper_slab[type=top]")
    S(3, -3, zc - 1, "waxed_cut_copper_slab[type=top]"); S(3, -3, zc + 1, "waxed_cut_copper_slab[type=bottom]")
    for x in (4, 5): S(x, -3, zc, "chain[axis=x]")

# ---------------- aft deck (x 1..7) ----------------
# white bulwark with steel rail around the stern
for x in R(0, 7):
    z0, z1 = zr(x)
    S(x, 2, z0, WHITE); S(x, 2, z1, WHITE)
for z in R(2, 14):
    S(0, 2, z, WHITE)
for x in R(0, 7):
    z0, z1 = zr(x)
    S(x, 3, z0, "iron_bars"); S(x, 3, z1, "iron_bars")
for z in R(2, 14):
    if z != ZC: S(0, 3, z, "iron_bars")
S(1, 3, 1, "iron_bars"); S(1, 3, 15, "iron_bars")
# flagstaff on the transom
for y in R(2, 5): S(0, y, ZC, "birch_fence")
S(0, 6, ZC, "end_rod[facing=up]")
# mooring winches and bollards (kept off the walkways)
for z in (3, 13):
    S(2, 2, z, "iron_block"); S(3, 2, z, "chain[axis=x]"); S(1, 2, z, "polished_blackstone_wall")
# car on the aft deck (port side) with its car crane
for x in R(2, 5):
    for z in (10, 11): S(x, 2, z, "black_concrete")
for z in (10, 11):
    S(2, 3, z, "smooth_stone_slab[type=bottom]"); S(5, 3, z, "smooth_stone_slab[type=bottom]")
    S(3, 3, z, "gray_stained_glass"); S(4, 3, z, "black_concrete")
for x in (2, 5):
    S(x, 2, 9, "polished_blackstone_button[face=wall,facing=north,powered=false]")
    S(x, 2, 12, "polished_blackstone_button[face=wall,facing=south,powered=false]")
for y in R(2, 5): S(6, y, 14, "white_concrete")
S(6, 6, 14, "smooth_quartz_stairs[facing=east,half=bottom]")
for (x, z) in [(5, 13), (4, 12), (3, 11)]: S(x, 6, z, "smooth_quartz_slab[type=top]")
S(3, 5, 11, "chain[axis=y]"); S(3, 4, 11, "chain[axis=y]")
# deck hatch to the steering gear room
S(4, 1, 6, "iron_trapdoor[facing=north,half=top,open=false]")

# ---------------- accommodation (x 8..20, z 2..14) ----------------
HX0, HX1, HZ0, HZ1 = 8, 20, 2, 14
for x in R(HX0, HX1):
    for z in R(HZ0, HZ1):
        edge = x in (HX0, HX1) or z in (HZ0, HZ1)
        for y in R(2, 4):
            S(x, y, z, WHITE if edge else "air")
        S(x, 1, z, "spruce_planks")
        S(x, 5, z, BLUE if edge else "light_gray_concrete")       # roof with light blue rim
# side decks along the house (z=1 / z=15 walk, white bulwark at z=0 / z=16)
for x in R(8, AFT_END):
    S(x, 2, 0, WHITE); S(x, 2, 16, WHITE)
# window band: openings at eye height, generous on the sides and the front
for x in (9, 10, 12, 13, 15, 16, 18, 19):
    S(x, 3, HZ0, "air"); S(x, 3, HZ1, "air")
for z in (4, 5, 11, 12): S(HX1, 3, z, "air")
for z in (4, 12): S(HX0, 3, z, "air")
# doors
S(HX0, 2, ZC, "dark_oak_door[facing=west,half=lower,hinge=left,open=false]")
S(HX0, 3, ZC, "dark_oak_door[facing=west,half=upper,hinge=left,open=false]")
S(HX1, 2, ZC, "dark_oak_door[facing=east,half=lower,hinge=right,open=false]")
S(HX1, 3, ZC, "dark_oak_door[facing=east,half=upper,hinge=right,open=false]")
for x in R(9, 19): S(x, 1, ZC, "light_gray_wool")                   # corridor runner (flush, walkable)
# partitions: corridor z=8, cabins to port (z 3..6), living + galley to starboard (z 10..13)
for x in R(9, 19):
    for y in R(2, 4): S(x, y, 7, "birch_planks")
for y in R(2, 4):
    for x in (9, 10, 17, 18, 19): S(x, y, 9, "birch_planks")
for z in R(3, 6):
    for y in R(2, 4): S(13, y, z, "birch_planks"); S(17, y, z, "birch_planks")
for (dx, hinge) in [(11, "left"), (15, "right"), (18, "left")]:
    S(dx, 2, 7, f"spruce_door[facing=north,half=lower,hinge={hinge},open=false]")
    S(dx, 3, 7, f"spruce_door[facing=north,half=upper,hinge={hinge},open=false]")
# master bedroom (x 9..12): double bed, headboard, bedside table, wardrobe
for z in (3, 4):
    S(9, 2, z, "light_blue_bed[facing=west,part=head,occupied=false]")
    S(10, 2, z, "light_blue_bed[facing=west,part=foot,occupied=false]")
    S(9, 3, z, "dark_oak_trapdoor[facing=east,half=bottom,open=true]")
S(9, 2, 5, "barrel[facing=up]"); S(9, 3, 5, "lantern[hanging=false]")
S(12, 2, 3, "barrel[facing=west]"); S(12, 3, 3, "barrel[facing=west]")
# bathroom (x 14..16)
S(14, 2, 3, "light_blue_carpet"); S(14, 4, 3, "lightning_rod[facing=south]")
S(15, 2, 3, "water_cauldron[level=3]"); S(15, 3, 3, "tripwire_hook[facing=south,attached=false,powered=false]")
S(16, 2, 3, "quartz_stairs[facing=north,half=bottom]"); S(16, 3, 3, "quartz_slab[type=bottom]")
# second cabin (x 18..19)
S(19, 2, 3, "white_bed[facing=east,part=head,occupied=false]"); S(18, 2, 3, "white_bed[facing=east,part=foot,occupied=false]")
S(19, 2, 5, "spruce_slab[type=top]"); S(18, 2, 5, "spruce_stairs[facing=west,half=bottom]")
# living room (x 9..13, starboard): sofa facing the TV on the aft wall, coffee table, lamp
for z in (11, 12, 13): S(12, 2, z, "dark_oak_stairs[facing=east,half=bottom]")
for z in (11, 12, 13): S(HX0, 3, z, "black_concrete")                # TV
for (x, z) in [(9, 11), (9, 13), (10, 11), (10, 13), (11, 11), (11, 12), (11, 13), (9, 12)]: S(x, 2, z, "gray_carpet")
S(10, 2, 12, "spruce_slab[type=bottom]")
S(13, 2, 13, "spruce_fence"); S(13, 3, 13, "lantern[hanging=false]")
S(13, 2, 10, "potted_fern")
# galley (x 14..19, starboard)
for x in R(14, 19):
    for z in R(10, 13): S(x, 1, z, "polished_diorite" if (x + z) % 2 == 0 else "polished_andesite")
S(19, 2, 13, "iron_block"); S(19, 3, 13, "iron_block"); S(19, 3, 12, "stone_button[face=wall,facing=north,powered=false]")
S(14, 2, 13, "barrel[facing=north,open=false]"); S(15, 2, 13, "dropper[facing=north,triggered=false]")
S(16, 2, 13, "water_cauldron[level=3]"); S(16, 3, 13, "tripwire_hook[facing=north,attached=false,powered=false]")
S(17, 2, 13, "smoker[facing=north,lit=false]"); S(17, 4, 13, "hopper[facing=down,enabled=true]")
S(18, 2, 13, "barrel[facing=north,open=false]")
for x in (14, 15, 18): S(x, 4, 13, "barrel[facing=north,open=false]")
for x in (15, 16):
    S(x, 2, 11, "spruce_fence"); S(x, 3, 11, "spruce_pressure_plate[powered=false]")
S(14, 2, 11, "dark_oak_stairs[facing=west,half=bottom]"); S(17, 2, 11, "dark_oak_stairs[facing=east,half=bottom]")
# ceiling lights
for p in [(10, 5, 4), (15, 5, 4), (18, 5, 4), (11, 5, 8), (16, 5, 8), (10, 5, 12), (16, 5, 12)]: S(*p, "sea_lantern")
# engine room below (x 9..19, y -4..0) with hatch + ladder from the corridor
for x in R(9, 19):
    for z in R(1, 15): S(x, -4, z, "smooth_stone")
for z in R(1, 15):
    for y in R(-4, 0): S(8, y, z, INNER); S(20, y, z, INNER)
for zc in (5, 11):
    for x in R(11, 16):
        S(x, -3, zc, "iron_block" if x in (11, 16) else "black_concrete"); S(x, -2, zc, "polished_blackstone_slab[type=bottom]")
    for x in (12, 14): S(x, -1, zc, "lightning_rod[facing=up]")
S(10, -3, 8, "lever[face=floor,facing=east,powered=false]"); S(10, -3, 9, "black_carpet")
S(15, 0, 8, "lantern[hanging=true]"); S(11, 0, 4, "lantern[hanging=true]"); S(11, 0, 12, "lantern[hanging=true]")
S(19, 1, ZC, "spruce_trapdoor[facing=west,half=top,open=false]")
for y in R(-3, 0): S(19, y, ZC, "ladder[facing=west]")

# ---------------- roof deck, mast for the ladder, exhaust, tender ----------------
for x in R(HX0, HX1):
    S(x, 6, HZ0, "iron_bars"); S(x, 6, HZ1, "iron_bars")
for z in R(HZ0, HZ1):
    S(HX1, 6, z, "iron_bars")
    if z != 11: S(HX0, 6, z, "iron_bars")
for y in R(2, 6): S(7, y, 11, "ladder[facing=west]")                  # aft deck -> roof
for y in R(6, 9): S(10, y, 13, "polished_blackstone_wall")
S(10, 10, 13, "cauldron")                                            # exhaust pipe
S(11, 6, 3, "oak_stairs[facing=west,half=bottom]"); S(12, 6, 3, "oak_slab[type=bottom]")
S(13, 6, 3, "oak_slab[type=bottom]"); S(14, 6, 3, "oak_stairs[facing=east,half=bottom]")
# hydraulic column (lower, fixed part) + steel mast with a ladder up to the wheelhouse door
for x in (15, 16):
    for z in (8, 9):
        for y in (6, 7): S(x, y, z, "gray_concrete")

# ---------------- lifting wheelhouse (x 13..19, z 4..12, floor y=8) ----------------
WX0, WX1, WZ0, WZ1, WY = 13, 19, 4, 12, 8
LIFT = {"region": [WX0, WY, WZ0 - 1, WX1 + 1, WY + 7, WZ1 + 1], "max": 3.0, "column": [15, 8, 16, 9]}
for x in R(WX0, WX1):
    for z in R(WZ0, WZ1):
        edge = x in (WX0, WX1) or z in (WZ0, WZ1)
        S(x, WY, z, "spruce_planks" if not edge else WHITE)               # floor / white skirt
        S(x, WY + 4, z, WHITE)                                            # roof
        if edge:
            S(x, WY + 1, z, WHITE)                                        # lower panel
            S(x, WY + 3, z, "smooth_quartz_slab[type=top]")               # header above the windows
for (x, z) in [(WX0, WZ0), (WX0, WZ1), (WX1, WZ0), (WX1, WZ1)]:
    S(x, WY + 2, z, "dark_oak_fence")                                     # slim corner posts
# visor + roof gear
for x in R(WX0, WX1 + 1):
    S(x, WY + 4, WZ0 - 1, "smooth_quartz_slab[type=bottom]"); S(x, WY + 4, WZ1 + 1, "smooth_quartz_slab[type=bottom]")
for z in R(WZ0, WZ1): S(WX1 + 1, WY + 4, z, "smooth_quartz_slab[type=bottom]")
S(16, WY + 5, ZC, "iron_bars"); S(16, WY + 6, ZC, "end_rod[facing=up]")
for z in R(6, 10): S(16, WY + 7, z, "polished_blackstone_slab[type=bottom]")   # radar
S(WX1, WY + 5, WZ0, "red_candle[candles=1,lit=true]"); S(WX1, WY + 5, WZ1, "lime_candle[candles=1,lit=true]")
# rear doorway in the middle of the back wall (opening + header), ladder mast just behind it
S(WX0, WY + 1, ZC, "air"); S(WX0, WY + 2, ZC, "air")
for y in R(6, WY + 3): S(11, y, ZC, "polished_andesite_wall")
for y in R(6, WY + 3): S(12, y, ZC, "ladder[facing=east]")
# helm console (dark grey, wrap-around like the photo), black chair, screens, joysticks
# low desk so the view over the bow stays free
for z in R(5, 11): S(18, WY + 1, z, "polished_deepslate_slab[type=bottom]")
for z in (5, 6, 10, 11): S(17, WY + 1, z, "polished_deepslate_slab[type=bottom]")
S(18, WY + 2, 7, "black_carpet"); S(18, WY + 2, 9, "black_carpet")   # flat screens
S(17, WY + 2, 6, "lever[face=floor,facing=west,powered=false]")        # joysticks
S(17, WY + 2, 10, "lever[face=floor,facing=west,powered=false]")
S(16, WY + 1, ZC, "polished_blackstone_stairs[facing=west,half=bottom]")   # black helm chair
S(14, WY + 1, 5, "cartography_table"); S(14, WY + 1, 11, "barrel[facing=up]")
S(15, WY + 4, ZC, "sea_lantern")

# ---------------- cargo space with four holds ----------------
HOLDS = [(24, 42), (44, 61), (63, 80), (82, 101)]
BEAMS = [43, 62, 81]
for x in R(HOLD_START, HOLD_END):
    for y in R(-4, 1):
        S(x, y, 1, INNER); S(x, y, 15, INNER)
    for y in (2, 3):
        S(x, y, 1, COAM); S(x, y, 15, COAM)                               # high grey coaming
    S(x, 4, 1, NAVY); S(x, 4, 15, NAVY)                                   # blue top band
    S(x, 5, 1, CAP); S(x, 5, 15, CAP)
for x in (HOLD_START, HOLD_END):
    for z in R(1, 15):
        for y in R(-4, 3): S(x, y, z, INNER)
        S(x, 4, z, NAVY); S(x, 5, z, CAP)
for x in R(HOLD_START + 1, HOLD_END - 1):
    for z in R(2, 14): S(x, -4, z, "smooth_stone")
for b in BEAMS:
    for z in R(2, 14): S(b, 4, z, NAVY); S(b, 5, z, CAP)                  # transverse beams
for a, b in HOLDS:
    for y in R(-3, 5): S(a + 1, y, 2, "ladder[facing=south]")
    for y in R(2, 5): S(a + 1, y, 0, "ladder[facing=north]")

# ---------------- fore ship ----------------
for x in R(FORE_START, L - 3):
    z0, z1 = zr(x)
    S(x, 2, z0, BLUE); S(x, 2, z1, BLUE)
    S(x, 3, z0, "iron_bars"); S(x, 3, z1, "iron_bars")
for z in R(zr(L - 1)[0], zr(L - 1)[1]): S(L - 1, 2, z, BLUE)
for z in R(zr(L - 2)[0], zr(L - 2)[1]): S(L - 2, 2, z, BLUE)
# anchor winches and chain pipes
for z in (5, 11):
    S(112, 2, z, "iron_block"); S(111, 2, z, "anvil[facing=north]"); S(113, 2, z, "chain[axis=x]")
    S(114, 2, z, "chain[axis=x]")
# bow thruster room hatch with ladder
S(106, 1, 3, "spruce_trapdoor[facing=south,half=top,open=false]")
for y in R(-3, 0): S(106, y, 3, "ladder[facing=south]")
for z in R(1, 15):
    for y in R(-4, 0): S(104, y, z, INNER)
S(108, -3, ZC, "iron_block"); S(109, -3, ZC, "iron_block"); S(108, -2, ZC, "lightning_rod[facing=up]")
S(110, 0, ZC, "lantern[hanging=true]")
# bow steering position (behind the A-mast)
for z in R(7, 9): S(108, 2, z, "polished_deepslate_slab[type=top]")
S(108, 3, 7, "black_carpet"); S(108, 3, 9, "black_carpet"); S(108, 3, 8, "lever[face=floor,facing=west,powered=false]")
S(107, 2, ZC, "polished_blackstone_stairs[facing=west,half=bottom]")
# A-mast with crossbar, lights and flags (as on the photo)
for y in R(2, 8):
    S(115, y, 4, "iron_bars"); S(115, y, 12, "iron_bars")
for z in R(4, 12): S(115, 9, z, "iron_bars")
S(115, 10, ZC, "lantern[hanging=false]")
for z in (5, 11): S(115, 8, z, "lantern[hanging=true]")
S(115, 8, 6, "light_blue_wool"); S(115, 7, 6, "white_wool")
S(115, 8, 10, "yellow_wool"); S(115, 7, 10, "blue_wool")
# boarding ladders on the hull, reaching below the waterline
for y in R(-4, 1):
    for x in (24, 101):
        S(x, y, -1, "ladder[facing=north]"); S(x, y, 17, "ladder[facing=south]")

B = {k: v for k, v in B.items() if v != "minecraft:air"}

# ---------------- connections for panes/fences/walls ----------------
def base(s): return s.split("[")[0].replace("minecraft:", "")
NONFULL = ("slab", "stairs", "pane", "bars", "fence", "wall", "trapdoor", "door", "ladder", "lantern", "carpet",
           "rod", "chain", "potted", "cauldron", "lever", "campfire", "grindstone", "anvil", "_bed", "button", "hook",
           "candle", "plate", "glass", "hopper")
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
    "name": "ZEELANDIA",
    "length": L, "beam": W_BEAM,
    "palette": pal,
    "blocks": flat,
    "hull": [[x, zr(x)[0], zr(x)[1], bot(x)] for x in range(L)],
    "holds": [[a, b] for a, b in HOLDS],
    "holdZ": [2, 14], "holdFloorY": -3, "coamingTopY": 5.5,
    "seats": {
        "aft": {"pos": [16.5, WY + 1.45, 8.5], "yaw": 0},
        "bow": {"pos": [107.5, 2.45, 8.5], "yaw": 0}
    },
    "helmZones": {
        "aft": [16, WY + 1, 5, 18, WY + 2, 11],
        "bow": [107, 2, 7, 108, 3, 9]
    },
    "lift": LIFT,
}
here = os.path.dirname(os.path.abspath(__file__))
dst = os.path.join(here, "..", "src", "main", "resources", "modernbulk", "zeelandia.json")
os.makedirs(os.path.dirname(dst), exist_ok=True)
json.dump(out, open(dst, "w"), separators=(",", ":"))
print("blocks", len(B), "palette", len(pal), "->", os.path.relpath(dst))
