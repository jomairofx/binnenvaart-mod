"""Builds the ZEELANDIA template (178 x 19) for the Modern Bulk Carrier mod.

A modern Dutch inland bulk carrier (motorvrachtschip, like the Monika Deymann): low and long, a black hull with
turquoise bow and stern, white accommodation aft with a hydraulic lifting wheelhouse on a column, a car with its
crane on the aft deck, one long cargo space with grey coamings (blue top band) split by transverse beams into seven
holds, rolling hatch covers that stack per hold, and a raised foredeck with anchor winches, A-mast and bow helm.

Template axes: x = length (0 = stern, 177 = bow), z = beam (0 = port, 18 = starboard), y = height.
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

# ---------------- main dimensions ----------------
HOLD_LEN, N_HOLDS = 18, 7
AFT_END = 23                                    # last block of the aft ship (accommodation + walkway)
HOLD_START = AFT_END + 1                        # aft end wall of the cargo space
HOLDS = []
x = HOLD_START + 1
for i in range(N_HOLDS):
    HOLDS.append((x, x + HOLD_LEN - 1))
    x += HOLD_LEN + 1
BEAMS = [b + 1 for a, b in HOLDS[:-1]]
HOLD_END = HOLDS[-1][1] + 1                     # fore end wall of the cargo space
FORE_START = HOLD_END + 1
L = FORE_START + 20
W_BEAM = 19
ZC = 9                                          # centre line (block), centre of the ship is 9.5
Z0, Z1 = 0, W_BEAM - 1
CO0, CO1 = 2, 16                                # coamings
HZ = (CO0 + 1, CO1 - 1)                         # hold interior
FLOOR_Y = -4                                    # hold floor block (top = -3)

BOW = L - 8
def zr(x):
    if x == 0: return (2, 16)
    if x == 1: return (1, 17)
    d = x - BOW
    if d < 1: return (0, 18)
    taper = [(1, 17), (1, 17), (2, 16), (3, 15), (4, 14), (5, 13), (6, 12), (7, 11)]
    return taper[min(d - 1, 7)]
def bot(x):
    if x <= 3: return -2
    if x == 4: return -3
    if x == 5: return -4
    if x <= L - 14: return -5
    if x <= L - 10: return -4
    if x <= L - 6: return -3
    if x <= L - 3: return -2
    return -1

HULL = "black_concrete"
BLUE = "cyan_concrete"                   # turquoise paint of bow, stern and sheer strake
NAVY = "blue_concrete"
WHITE = "white_concrete"
DECK = "gray_concrete"
COAM = "gray_concrete"
CAP = "polished_andesite_slab[type=bottom]"
INNER = "light_gray_concrete"
RAIL = "iron_bars"

def side_colour(x, y):
    if y <= -1: return HULL
    if x <= AFT_END - 2 or x >= FORE_START: return BLUE
    return BLUE if y == 1 else HULL          # black hull with a turquoise sheer strake

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
    # boot-topping: a thin red line just under the waterline
    if 4 < x < L - 4:
        S(x, -2, z0, "red_concrete"); S(x, -2, z1, "red_concrete")
# rubbing strakes (slabs) along the sheer
for x in R(2, L - 3):
    z0, z1 = zr(x)
    if z0 > 0: continue
    S(x, 0, -1, "blackstone_slab[type=top]"); S(x, 0, W_BEAM, "blackstone_slab[type=top]")
# decks
for x in list(R(1, AFT_END)) + list(R(FORE_START, L - 2)):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1):
        S(x, 1, z, DECK)
# side decks (gangboord) along the cargo section: 2 wide walkway with a low rail at the edge
for x in R(HOLD_START, HOLD_END):
    for z in (Z0, Z0 + 1, Z1 - 1, Z1):
        S(x, 1, z, BLUE if z in (Z0, Z1) else DECK)
for x in R(HOLD_START - 1, HOLD_END + 1):
    if x % 4 == 0:
        S(x, 2, Z0, "polished_blackstone_wall"); S(x, 2, Z1, "polished_blackstone_wall")   # stanchions
    else:
        S(x, 2, Z0, "chain[axis=x]"); S(x, 2, Z1, "chain[axis=x]")                         # wire between them
# openings in the rail at the boarding ladders
BOARD_X = (HOLD_START + 2, HOLD_END - 2)
for x in BOARD_X:
    S(x, 2, Z0, "air"); S(x, 2, Z1, "air")

# ---------------- stern: rudders, propellers ----------------
for zc in (6, 13):
    for y in R(-4, -2): S(1, y, zc, HULL)
    S(3, -3, zc, "lightning_rod[facing=west]")
    S(3, -2, zc, "waxed_cut_copper_slab[type=bottom]"); S(3, -4, zc, "waxed_cut_copper_slab[type=top]")
    S(3, -3, zc - 1, "waxed_cut_copper_slab[type=top]"); S(3, -3, zc + 1, "waxed_cut_copper_slab[type=bottom]")
    for x in (4, 5): S(x, -3, zc, "chain[axis=x]")

# ---------------- aft deck (x 1..7) ----------------
for x in R(0, 7):
    z0, z1 = zr(x)
    S(x, 2, z0, WHITE); S(x, 2, z1, WHITE)
    S(x, 3, z0, RAIL); S(x, 3, z1, RAIL)
for z in R(2, 16):
    S(0, 2, z, WHITE)
    if z != ZC: S(0, 3, z, RAIL)
S(1, 3, 1, RAIL); S(1, 3, 17, RAIL)
# name board on the transom and flagstaff with the Dutch ensign
for y in R(2, 5): S(0, y, ZC, "birch_fence")
S(0, 6, ZC, "end_rod[facing=up]")
# mooring bollards (double) and winches against the bulwark
for z in (2, 16):
    S(2, 2, z, "polished_blackstone_wall"); S(3, 2, z, "polished_blackstone_wall")
for z in (3, 15):
    S(5, 2, z, "iron_block"); S(5, 3, z, "polished_blackstone_slab[type=bottom]"); S(6, 2, z, "chain[axis=x]")
# lifebuoys on the aft rail
S(0, 3, 4, "orange_banner[rotation=4]") if False else None
# car on the aft deck (starboard) on its turntable, with the car crane next to it
# dark grey estate car: low body, tinted windows all round, thin roof, wheels, lights and mirrors
for x in R(2, 5):
    for z in (11, 12): S(x, 2, z, "gray_concrete")                        # body
for z in (11, 12):
    S(2, 3, z, "black_stained_glass")                                     # rear window
    S(3, 3, z, "black_stained_glass"); S(4, 3, z, "black_stained_glass")  # side windows / windscreen
    S(5, 3, z, "polished_deepslate_slab[type=bottom]")                    # bonnet
    for x in (2, 3, 4): S(x, 4, z, "gray_carpet")                         # roof
for x in (2, 5):
    S(x, 2, 10, "polished_blackstone_button[face=wall,facing=north,powered=false]")   # wheels
    S(x, 2, 13, "polished_blackstone_button[face=wall,facing=south,powered=false]")
S(6, 2, 11, "end_rod[facing=east]"); S(6, 2, 12, "end_rod[facing=east]")          # headlights
S(1, 2, 11, "red_candle[candles=1,lit=false]"); S(1, 2, 12, "red_candle[candles=1,lit=false]")   # tail lights
S(4, 3, 10, "lever[face=wall,facing=north,powered=false]"); S(4, 3, 13, "lever[face=wall,facing=south,powered=false]")   # mirrors
for y in R(2, 5): S(6, y, 15, "yellow_concrete")
S(6, 6, 15, "yellow_concrete")
for (x, z) in [(5, 14), (4, 13), (3, 12)]: S(x, 6, z, "yellow_concrete")
S(3, 5, 12, "chain[axis=y]")
S(6, 2, 16, "yellow_terracotta")
# deck hatch to the steering gear room
S(4, 2, 7, "spruce_trapdoor[facing=north,half=bottom,open=false]")
for y in R(-2, 1): S(4, y, 7, "ladder[facing=north]")
for x in R(2, 6):
    for z in R(2, 16): S(x, -4, z, "smooth_stone") if bot(x) < -4 else None

# ---------------- accommodation (x 8..21, z 2..16) ----------------
HX0, HX1, HZ0, HZ1 = 8, 21, 2, 16
for x in R(HX0, HX1):
    for z in R(HZ0, HZ1):
        edge = x in (HX0, HX1) or z in (HZ0, HZ1)
        for y in R(2, 4):
            S(x, y, z, WHITE if edge else "air")
        S(x, 1, z, "spruce_planks")
        S(x, 5, z, "smooth_quartz" if edge else "gray_concrete")
# roof overhang with a turquoise fascia
for x in R(HX0 - 1, HX1 + 1):
    S(x, 5, HZ0 - 1, "cyan_terracotta"); S(x, 5, HZ1 + 1, "cyan_terracotta")
for z in R(HZ0, HZ1):
    S(HX0 - 1, 5, z, "cyan_terracotta"); S(HX1 + 1, 5, z, "cyan_terracotta")
# side decks along the house with white bulwark + rail
for x in R(8, AFT_END):
    S(x, 2, 0, WHITE); S(x, 2, 18, WHITE)
    S(x, 3, 0, RAIL); S(x, 3, 18, RAIL)
# windows: tinted glass band on all sides
G = "air"   # open window openings, no glass
for x in R(HX0 + 1, HX1 - 1):
    if x not in (13, 17):
        S(x, 3, HZ0, G); S(x, 3, HZ1, G)
for z in (4, 5, 6, 12, 13, 14): S(HX1, 3, z, G)
for z in (4, 5, 13, 14): S(HX0, 3, z, G)
# doors (aft and fore end of the corridor)
S(HX0, 2, ZC, "dark_oak_door[facing=west,half=lower,hinge=left,open=false]")
S(HX0, 3, ZC, "dark_oak_door[facing=west,half=upper,hinge=left,open=false]")
S(HX1, 2, ZC, "dark_oak_door[facing=east,half=lower,hinge=right,open=false]")
S(HX1, 3, ZC, "dark_oak_door[facing=east,half=upper,hinge=right,open=false]")
for x in R(HX0 + 1, HX1 - 1): S(x, 1, ZC, "light_gray_wool")        # corridor runner
# partitions: corridor z=9, cabins to port (z 3..8), living + galley to starboard (z 10..15)
for x in R(HX0 + 1, HX1 - 1):
    for y in R(2, 4): S(x, y, 8, "birch_planks")
for x in R(HX0 + 1, HX1 - 1):
    if x in (11, 12, 13, 14, 15, 16): continue     # open living room / galley towards the corridor
    for y in R(2, 4): S(x, y, 10, "birch_planks")
for z in R(3, 7):
    for y in R(2, 4): S(13, y, z, "birch_planks"); S(17, y, z, "birch_planks")
for (dx, hinge) in [(11, "left"), (15, "right"), (19, "left")]:
    S(dx, 2, 8, f"spruce_door[facing=north,half=lower,hinge={hinge},open=false]")
    S(dx, 3, 8, f"spruce_door[facing=north,half=upper,hinge={hinge},open=false]")
# master bedroom (x 9..12): double bed with headboard, bedside tables, wardrobe, rug
for z in (4, 5):
    S(9, 2, z, "light_blue_bed[facing=west,part=head,occupied=false]")
    S(10, 2, z, "light_blue_bed[facing=west,part=foot,occupied=false]")
    S(9, 3, z, "dark_oak_trapdoor[facing=east,half=bottom,open=true]")
for z in (3, 6):
    S(9, 2, z, "spruce_slab[type=top]"); S(9, 3, z, "lantern[hanging=false]")
S(12, 2, 3, "dark_oak_planks"); S(12, 3, 3, "dark_oak_planks"); S(12, 2, 4, "dark_oak_planks"); S(12, 3, 4, "dark_oak_planks")
S(12, 2, 5, "spruce_trapdoor[facing=west,half=bottom,open=true]")
for z in (5, 6): S(11, 2, z, "white_carpet")
S(10, 4, 3, "item_frame") if False else None
# bathroom (x 14..16): shower, washbasin with mirror, toilet

S(15, 2, 3, "water_cauldron[level=3]"); S(15, 4, 3, "lightning_rod[facing=down]")
S(16, 2, 3, "quartz_slab[type=top]"); S(16, 3, 3, "tripwire_hook[facing=south,attached=false,powered=false]")
S(16, 2, 7, "quartz_stairs[facing=north,half=bottom]"); S(16, 3, 7, "quartz_slab[type=bottom]")
for x in R(14, 16):
    for z in R(3, 7): S(x, 1, z, "white_concrete") if (x, z) != (14, 3) else None
# second cabin (x 18..20): single bed, desk, chair
S(20, 2, 3, "white_bed[facing=east,part=head,occupied=false]"); S(19, 2, 3, "white_bed[facing=east,part=foot,occupied=false]")
S(20, 2, 6, "spruce_slab[type=top]"); S(20, 3, 6, "flower_pot"); S(19, 2, 6, "spruce_stairs[facing=east,half=bottom]")
S(18, 2, 3, "barrel[facing=up]"); S(18, 3, 3, "lantern[hanging=false]")
# living room (x 9..13, starboard): L-shaped sofa facing the TV on the aft wall, coffee table, lamp, plants
for z in (12, 13, 14): S(12, 2, z, "dark_oak_stairs[facing=east,half=bottom]")
S(12, 2, 11, "dark_oak_stairs[facing=east,half=bottom]")
for x in (10, 11): S(x, 2, 15, "dark_oak_stairs[facing=south,half=bottom]")
for z in (12, 13, 14): S(HX0, 3, z, "black_concrete")                  # wall-mounted TV
S(9, 2, 12, "dark_oak_slab[type=bottom]"); S(9, 2, 13, "dark_oak_slab[type=bottom]"); S(9, 2, 14, "dark_oak_slab[type=bottom]")
for (x, z) in [(10, 12), (10, 13), (10, 14), (11, 12), (11, 14)]: S(x, 2, z, "light_gray_carpet")
S(11, 2, 13, "spruce_slab[type=bottom]")                                # coffee table
S(13, 2, 15, "spruce_fence"); S(13, 3, 15, "lantern[hanging=false]")
S(9, 2, 11, "potted_fern"); S(13, 2, 11, "potted_bamboo")
# galley + dining (x 14..20, starboard): fridge, oven, hob with hood, sink, cupboards, island with stools
for x in R(14, 20):
    for z in R(11, 15): S(x, 1, z, "polished_diorite" if (x + z) % 2 == 0 else "polished_andesite")
S(20, 2, 15, "iron_block"); S(20, 3, 15, "iron_block"); S(20, 3, 14, "stone_button[face=wall,facing=north,powered=false]")
S(14, 2, 15, "barrel[facing=north,open=false]"); S(15, 2, 15, "dropper[facing=north,triggered=false]")
S(16, 2, 15, "water_cauldron[level=3]"); S(16, 3, 15, "tripwire_hook[facing=north,attached=false,powered=false]")
S(17, 2, 15, "smoker[facing=north,lit=false]"); S(17, 4, 15, "hopper[facing=down,enabled=true]")
S(18, 2, 15, "blast_furnace[facing=north,lit=false]"); S(19, 2, 15, "barrel[facing=north,open=false]")
for x in (14, 15, 18, 19): S(x, 4, 15, "spruce_trapdoor[facing=north,half=top,open=true]")
for x in (16, 17, 18): S(x, 2, 12, "polished_deepslate_slab[type=top]")   # kitchen island
for x in (16, 18): S(x, 2, 11, "spruce_fence"); S(x, 3, 11, "spruce_pressure_plate[powered=false]")   # bar stools
S(14, 2, 12, "dark_oak_stairs[facing=east,half=bottom]"); S(15, 2, 12, "spruce_slab[type=top]")
S(15, 3, 12, "candle[candles=3,lit=false]")
# ceiling lights
LIGHTS = [(10, 5, 5), (15, 5, 5), (19, 5, 5), (11, 5, ZC), (17, 5, ZC), (10, 5, 13), (14, 5, 13), (18, 5, 13)]
for p in LIGHTS: S(*p, "sea_lantern")
# engine room below (x 9..20, y -4..0) with hatch + ladder from the corridor
for x in R(9, 20):
    for z in R(1, 17): S(x, -4, z, "smooth_stone")
for z in R(1, 17):
    for y in R(-4, 0): S(8, y, z, INNER); S(21, y, z, INNER)
for x in R(9, 20):
    for y in R(-4, 0): S(x, y, 1, INNER); S(x, y, 17, INNER)       # inner lining of the engine room
for zc in (6, 12):
    for x in R(11, 17):
        S(x, -3, zc, "iron_block" if x in (11, 17) else "black_concrete"); S(x, -2, zc, "polished_blackstone_slab[type=bottom]")
        S(x, -3, zc - 1, "gray_concrete" if x in (11, 17) else "air")
    for x in (12, 14, 16): S(x, -1, zc, "lightning_rod[facing=up]")
S(10, -3, 9, "lever[face=floor,facing=east,powered=false]"); S(10, -3, 10, "black_carpet")
S(13, -3, 15, "barrel[facing=up]"); S(14, -3, 15, "barrel[facing=up]"); S(19, -3, 3, "anvil[facing=north]")
for p in [(15, 0, 9), (12, 0, 4), (12, 0, 14), (18, 0, 9)]: S(*p, "lantern[hanging=true]")
S(20, 2, ZC, "spruce_trapdoor[facing=west,half=bottom,open=false]")      # hatch on top of the floor
for y in R(-3, 1): S(20, y, ZC, "ladder[facing=west]")                    # ladder all the way up

# ---------------- roof deck, ladder, exhaust, tender ----------------
for x in R(HX0, HX1):
    S(x, 6, HZ0, RAIL); S(x, 6, HZ1, RAIL)
for z in R(HZ0, HZ1):
    S(HX1, 6, z, RAIL)
    if z != 14: S(HX0, 6, z, RAIL)
for y in R(2, 5): S(7, y, 14, "ladder[facing=west]")                  # aft deck -> roof
# exhaust stack (black, slim) with the smoke outlet
for y in R(6, 8): S(10, y, 15, "polished_blackstone_wall")
S(10, 9, 15, "cauldron")
# tender (white with turquoise rim) on chocks
S(8, 6, 4, "smooth_quartz_stairs[facing=west,half=bottom]"); S(9, 6, 4, "smooth_quartz_slab[type=bottom]")
S(10, 6, 4, "smooth_quartz_slab[type=bottom]"); S(11, 6, 4, "smooth_quartz_stairs[facing=east,half=bottom]")
S(9, 7, 4, "cyan_carpet"); S(10, 7, 4, "cyan_carpet")
# satellite dome + antenna on the roof
S(11, 6, 12, "white_concrete"); S(11, 7, 12, "smooth_quartz_slab[type=bottom]")

# ---------------- lifting wheelhouse (x 14..20, z 5..13, floor y=6) ----------------
WX0, WX1, WZ0, WZ1, WY = 14, 19, 5, 13, 6
LIFT_MAX = 4.0
LIFT = {"region": [WX0, WY, WZ0 - 1, WX1 + 1, WY + 7, WZ1 + 1], "max": LIFT_MAX, "column": [15, 8, 17, 10]}
for x in R(WX0, WX1):
    for z in R(WZ0, WZ1):
        edge = x in (WX0, WX1) or z in (WZ0, WZ1)
        S(x, WY, z, WHITE if edge else "spruce_planks")                   # floor / white skirt
        S(x, WY + 4, z, WHITE)                                            # roof
        if edge:
            S(x, WY + 1, z, WHITE)                                        # lower panel
            S(x, WY + 2, z, "air")                                        # open window band all round (no glass)
            S(x, WY + 3, z, "smooth_quartz_slab[type=top]")               # header above the windows
for (x, z) in [(WX0, WZ0), (WX0, WZ1), (WX1, WZ0), (WX1, WZ1)]:
    S(x, WY + 2, z, "white_concrete_powder") if False else S(x, WY + 2, z, WHITE)
# the front: lower panel only half high for a free view over the bow
for z in R(WZ0 + 1, WZ1 - 1):
    S(WX1, WY + 1, z, "smooth_quartz_slab[type=bottom]")
# visor overhang, rain gutter, roof gear
for x in R(WX0, WX1 + 1):
    S(x, WY + 4, WZ0 - 1, "smooth_quartz_slab[type=bottom]"); S(x, WY + 4, WZ1 + 1, "smooth_quartz_slab[type=bottom]")
for z in R(WZ0 - 1, WZ1 + 1): S(WX1 + 1, WY + 4, z, "smooth_quartz_slab[type=bottom]")
for z in R(WZ0, WZ1): S(WX1 + 1, WY + 3, z, "cyan_terracotta") if z in (WZ0, WZ1) else None
S(17, WY + 5, ZC, "iron_bars"); S(17, WY + 6, ZC, "end_rod[facing=up]")
for z in R(7, 11): S(17, WY + 7, z, "polished_blackstone_slab[type=bottom]")   # radar scanner
S(16, WY + 5, 7, "lightning_rod[facing=up]"); S(16, WY + 5, 11, "lightning_rod[facing=up]")
S(WX1, WY + 5, WZ0, "red_candle[candles=1,lit=true]"); S(WX1, WY + 5, WZ1, "lime_candle[candles=1,lit=true]")
S(19, WY + 5, ZC, "lantern[hanging=false]")                               # mast head light
# rear doorway in the middle of the back wall; steel ladder mast behind it, long enough for the raised house
S(WX0, WY + 1, ZC, "air"); S(WX0, WY + 2, ZC, "air")
for y in R(WY, WY + 1 + int(LIFT_MAX)): S(12, y, ZC, "andesite_wall")
for y in R(WY, WY + int(LIFT_MAX)): S(13, y, ZC, "ladder[facing=east]")
# helm: wrap-around low console, black chair, screens, joysticks
for z in R(WZ0 + 1, WZ1 - 1): S(WX1 - 1, WY + 1, z, "polished_deepslate_slab[type=bottom]")
for z in (WZ0 + 1, WZ0 + 2, WZ1 - 2, WZ1 - 1): S(WX1 - 2, WY + 1, z, "polished_deepslate_slab[type=bottom]")
S(WX1 - 1, WY + 2, ZC - 1, "black_carpet"); S(WX1 - 1, WY + 2, ZC + 1, "black_carpet")   # flat screens
S(WX1 - 2, WY + 2, ZC - 2, "lever[face=floor,facing=west,powered=false]")             # joysticks
S(WX1 - 2, WY + 2, ZC + 2, "lever[face=floor,facing=west,powered=false]")
S(16, WY + 1, ZC, "polished_blackstone_stairs[facing=west,half=bottom]")             # black helm chair
S(15, WY + 1, WZ0 + 1, "cartography_table"); S(15, WY + 1, WZ1 - 1, "barrel[facing=up]")
S(16, WY + 4, ZC, "sea_lantern")
LIGHTS.append((16, WY + 4, ZC))

# ---------------- cargo space ----------------
for x in R(HOLD_START, HOLD_END):
    for y in R(FLOOR_Y, 1):
        S(x, y, CO0, INNER); S(x, y, CO1, INNER)
    S(x, 2, CO0, COAM); S(x, 2, CO1, COAM)                               # coaming
    S(x, 3, CO0, NAVY); S(x, 3, CO1, NAVY)                               # blue top band
    S(x, 4, CO0, CAP); S(x, 4, CO1, CAP)                                 # rail for the hatch covers
for x in (HOLD_START, HOLD_END):
    for z in R(CO0, CO1):
        for y in R(FLOOR_Y, 2): S(x, y, z, INNER)
        S(x, 3, z, NAVY); S(x, 4, z, CAP)
for x in R(HOLD_START + 1, HOLD_END - 1):
    for z in R(HZ[0], HZ[1]): S(x, FLOOR_Y, z, "smooth_stone")
for b in BEAMS:
    for z in R(HZ[0], HZ[1]): S(b, 3, z, NAVY); S(b, 4, z, CAP)          # transverse beams
for i, (a, b) in enumerate(HOLDS):
    for y in R(FLOOR_Y + 1, 4): S(a + 1, y, HZ[0], "ladder[facing=south]")   # ladder down into the hold
    for y in R(2, 4): S(a + 1, y, CO0 - 1, "ladder[facing=north]")           # ...and over the coaming
    # hold number painted on the coaming
    S(a + 4, 2, CO0 - 1, "white_wall_banner[facing=north]") if False else None
# winch for the hatch covers at the fore end
for z in (CO0 + 1, CO1 - 1):
    S(HOLD_END + 1, 2, z, "iron_block"); S(HOLD_END + 1, 3, z, "polished_blackstone_slab[type=bottom]")

# ---------------- fore ship ----------------
for x in R(FORE_START, L - 3):
    z0, z1 = zr(x)
    S(x, 2, z0, BLUE); S(x, 2, z1, BLUE)
    S(x, 3, z0, RAIL); S(x, 3, z1, RAIL)
for z in R(zr(L - 1)[0], zr(L - 1)[1]): S(L - 1, 2, z, BLUE)
for z in R(zr(L - 2)[0], zr(L - 2)[1]): S(L - 2, 2, z, BLUE)
# raised forecastle deck (one slab higher, as on the photo)
for x in R(L - 9, L - 2):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1): S(x, 2, z, "gray_concrete")
    S(x, 3, z0, BLUE); S(x, 3, z1, BLUE); S(x, 4, z0, RAIL); S(x, 4, z1, RAIL)
for z in R(zr(L - 9)[0] + 1, zr(L - 9)[1] - 1):
    if z not in (ZC - 1, ZC, ZC + 1): S(L - 10, 2, z, "gray_concrete_powder") if False else None
for z in (ZC - 1, ZC, ZC + 1): S(L - 10, 2, z, "polished_andesite_stairs[facing=east,half=bottom]")   # steps up
# anchor winches with chain pipes and anchors in the hawse
for z in (6, 12):
    S(L - 6, 3, z, "iron_block"); S(L - 7, 3, z, "anvil[facing=north]"); S(L - 5, 3, z, "chain[axis=x]")
    S(L - 4, 3, z, "chain[axis=x]")
for z in (2, 16):
    S(L - 5, 0, z - 1 if z < ZC else z + 1, "chain[axis=y]") if False else None
# bow thruster room hatch with ladder
BT = FORE_START + 3
S(BT, 2, 4, "spruce_trapdoor[facing=south,half=bottom,open=false]")
for y in R(-4, 1): S(BT, y, 4, "ladder[facing=south]")
for z in R(1, 17):
    for y in R(-4, 0): S(FORE_START + 1, y, z, INNER)
S(BT + 2, -3, ZC, "iron_block"); S(BT + 3, -3, ZC, "iron_block"); S(BT + 2, -2, ZC, "lightning_rod[facing=up]")
S(BT + 4, 0, ZC, "lantern[hanging=true]")
# bow steering position behind the A-mast
BH = FORE_START + 5
for z in R(ZC - 1, ZC + 1): S(BH + 1, 2, z, "polished_deepslate_slab[type=top]")
S(BH + 1, 3, ZC - 1, "black_carpet"); S(BH + 1, 3, ZC + 1, "black_carpet"); S(BH + 1, 3, ZC, "lever[face=floor,facing=west,powered=false]")
S(BH, 2, ZC, "polished_blackstone_stairs[facing=west,half=bottom]")
# deck crane / gangway boom on the foredeck
S(FORE_START + 2, 2, 15, "yellow_concrete"); S(FORE_START + 2, 3, 15, "yellow_concrete")
for (x, y) in [(FORE_START + 3, 4), (FORE_START + 4, 4), (FORE_START + 5, 4)]: S(x, y, 15, "yellow_concrete")
# A-mast with crossbar, navigation lights and flags
AM = L - 5
for y in R(3, 10):
    S(AM, y, 5, RAIL); S(AM, y, 13, RAIL)
for z in R(5, 13): S(AM, 11, z, RAIL)
S(AM, 12, ZC, "lantern[hanging=false]")
for z in (6, 12): S(AM, 10, z, "lantern[hanging=true]")
S(AM, 10, 7, "cyan_wool"); S(AM, 9, 7, "white_wool")
S(AM, 10, 11, "yellow_wool"); S(AM, 9, 11, "blue_wool")
# boarding ladders on the hull, reaching below the waterline
for y in R(-4, 1):
    for x in BOARD_X:
        S(x, y, -1, "ladder[facing=north]"); S(x, y, W_BEAM, "ladder[facing=south]")

B = {k: v for k, v in B.items() if v != "minecraft:air"}

# ---------------- connections for panes/fences/walls ----------------
def base(s): return s.split("[")[0].replace("minecraft:", "")
NONFULL = ("slab", "stairs", "pane", "bars", "fence", "wall", "trapdoor", "door", "ladder", "lantern", "carpet",
           "rod", "chain", "potted", "cauldron", "lever", "campfire", "grindstone", "anvil", "_bed", "button", "hook",
           "candle", "plate", "glass", "hopper", "flower_pot")
def is_full(s):
    if s is None: return False
    b = base(s)
    if b == "sea_lantern": return True
    return not any(k in b for k in NONFULL)
dirs = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
for p, s in list(B.items()):
    b = base(s)
    fam = None
    if b == "iron_bars" or b.endswith("glass_pane"): fam = "pane"
    elif b.endswith("fence"): fam = "fence"
    elif b.endswith("_wall"): fam = "wall"
    if not fam: continue
    props = {}
    for d, (dx, dy, dz) in dirs.items():
        n = B.get((p[0] + dx, p[1], p[2] + dz))
        nb = base(n) if n else ""
        con = is_full(n) or (fam == "pane" and (nb == "iron_bars" or nb.endswith("glass_pane") or nb.endswith("_wall"))) \
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
    "holdZ": [HZ[0], HZ[1]], "holdFloorY": FLOOR_Y + 1, "coamingTopY": 4.5,
    "seats": {
        "aft": {"pos": [16.5, WY + 1.45, ZC + 0.5], "yaw": 0},
        "bow": {"pos": [BH + 0.5, 2.45, ZC + 0.5], "yaw": 0}
    },
    "helmZones": {
        "aft": [16, WY + 1, WZ0 + 1, WX1 - 1, WY + 2, WZ1 - 1],
        "bow": [BH, 2, ZC - 1, BH + 1, 3, ZC + 1]
    },
    "lift": LIFT,
}
here = os.path.dirname(os.path.abspath(__file__))
dst = os.path.join(here, "..", "src", "main", "resources", "modernbulk", "zeelandia.json")
os.makedirs(os.path.dirname(dst), exist_ok=True)
json.dump(out, open(dst, "w"), separators=(",", ":"))
print("blocks", len(B), "palette", len(pal), "L", L, "holds", HOLDS, "beams", BEAMS, "hold_end", HOLD_END, "->", os.path.relpath(dst))
print("BOARD_X", BOARD_X, "BH", BH, "BT", BT, "AM", AM)
