"""Bouwt de JOHANNA, een klassieke Kempenaar (70 x 9) met twee laadruimen.

Sjabloonassen: x = lengte (0 = achtersteven, 69 = boeg), z = breedte (0 = bakboord, 8 = stuurboord), y = hoogte.
Het gangboord (laag y=1) heeft zijn loopvlak op 2.0. Volgeladen staat het water op 1.29: ruim een halve meter vrijboord.

Indeling (van achter naar voor):
  x 0-5    achterdek met trap naar het woningdak, vlaggenstok, luik naar de machinekamer (x 1-5, onder het dek)
  x 6-17   woning, een blok verzonken: gang in het midden, slaapkamer en badkamer achter, woonkeuken voor
  x 12-16  stuurhuis op het woningdak, bereikbaar via de trap op het achterdek
  x 18     looppad naar de voordeur
  x 19-57  twee laadruimen (20-37 en 39-56) met schuifluiken
  x 58-69  voordek met verhoogd voorschip, ankerlier, mast en stuurstand voor
Uitvoer: src/main/resources/kempenaar/johanna.json
"""
import json, os

B = {}
def S(x, y, z, s):
    if not s.startswith("minecraft:"):
        s = "minecraft:" + s
    B[(x, y, z)] = s
def R(a, b):
    return range(a, b + 1)

L = 70
def zr(x):
    return {0: (2, 6), 1: (1, 7), 66: (1, 7), 67: (1, 7), 68: (2, 6), 69: (3, 5)}.get(x, (0, 8))
def bot(x):
    return {0: -1, 1: -1, 2: -2, 65: -2, 66: -2, 67: -1, 68: 0, 69: 1}.get(x, -3)
def top(x):
    return 2 if x >= 64 else 1

BLACK, RED, DECK, WHITE, GREEN = "black_concrete", "red_terracotta", "green_terracotta", "white_concrete", "green_concrete"
INNER = "gray_concrete"
CAP = "smooth_quartz_slab[type=bottom]"
def hullmat(y):
    return RED if y <= 0 else BLACK

# ---------------- romp ----------------
for x in range(L):
    z0, z1 = zr(x); b = bot(x); t = top(x)
    for z in R(z0, z1):
        S(x, b, z, RED)
    for y in R(b + 1, t):
        S(x, y, z0, hullmat(y)); S(x, y, z1, hullmat(y))
    if x in (0, L - 1):
        for z in R(z0, z1):
            for y in R(b, t):
                S(x, y, z, hullmat(y))
    # kim: afgeronde onderhoek met trappen
    if 3 <= x <= 64:
        S(x, b, z0, "red_nether_brick_stairs[facing=south,half=top]")
        S(x, b, z1, "red_nether_brick_stairs[facing=north,half=top]")
# aflopende boeg en opgetrokken achterschip
for x in (65, 66, 67, 68):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1): S(x, bot(x), z, "red_nether_brick_stairs[facing=west,half=top]")
for x in (1, 2):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1): S(x, bot(x), z, "red_nether_brick_stairs[facing=east,half=top]")
# dekken
for x in list(R(1, 5)) + [18] + list(R(58, 68)):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1):
        S(x, top(x), z, DECK)
# opstapje naar het verhoogde voorschip
for z in R(1, 7): S(63, 2, z, "spruce_stairs[facing=east,half=bottom]")
# berghout (houten wrijflijst) langs beide zijden
for x in R(19, 57):
    S(x, 2, 0, "white_carpet"); S(x, 2, 8, "white_carpet")

# ---------------- machinekamer (x 1-5, onder het achterdek) ----------------
for x in R(2, 5):
    z0, z1 = zr(x)
    for z in R(z0 + 1, z1 - 1): S(x, -2, z, "iron_trapdoor[facing=north,half=top,open=false]" if x > 2 else "smooth_stone")
for z in R(1, 7):
    for y in R(-2, 1): S(6, y, z, INNER)
for z in (3, 5):
    S(3, -1, z, BLACK); S(4, -1, z, BLACK)
    S(4, 0, z, "polished_blackstone_slab[type=bottom]")
S(2, -1, 4, "iron_block"); S(2, 0, 4, "lightning_rod[facing=up]")
S(5, -1, 1, "crafting_table"); S(4, -1, 1, "barrel[facing=up]")
S(3, 0, 4, "lantern[hanging=true]")
for y in R(-1, 0): S(5, y, 7, "ladder[facing=west]")
S(5, 1, 7, "spruce_trapdoor[facing=west,half=top,open=false]")

# ---------------- achterdek ----------------
for x in R(0, 5):
    z0, z1 = zr(x)
    S(x, 2, z0, "iron_bars"); S(x, 2, z1, "iron_bars")
for z in R(zr(0)[0], zr(0)[1]): S(0, 2, z, "iron_bars")
for y in R(2, 5): S(1, y, 4, "spruce_fence")
S(1, 6, 4, "end_rod[facing=up]")
S(1, 5, 5, "red_wool"); S(1, 4, 5, "white_wool"); S(1, 3, 5, "blue_wool")
S(4, 2, 7, "grindstone[face=floor,facing=north]")
# houten trap naar het woningdak, met leuning
for z in (5, 6):
    S(3, 2, z, "spruce_stairs[facing=east,half=bottom]")
    S(4, 3, z, "spruce_stairs[facing=east,half=bottom]")
    S(5, 4, z, "spruce_stairs[facing=east,half=bottom]")
S(4, 2, 7, "iron_bars"); S(5, 2, 7, "iron_bars"); S(5, 3, 7, "iron_bars")      # stalen trapboom
S(4, 3, 7, "iron_bars"); S(5, 4, 7, "iron_bars"); S(3, 2, 7, "iron_bars")   # leuning langs de trap

# ---------------- woning (x 6-17), vloer een blok verzonken ----------------
WX0, WX1 = 6, 17
for x in R(WX0, WX1):
    for z in R(0, 8):
        edge = x in (WX0, WX1) or z in (0, 8)
        if edge:
            for y in R(1, 3):
                if not (z in (0, 8) and y == 1): S(x, y, z, WHITE)
        else:
            S(x, 0, z, "spruce_planks")
        S(x, 4, z, GREEN if edge else "light_gray_concrete")   # dak, groene rand
# hull-zijden onder het dak blijven zwart tot het gangboord
for x in R(WX0, WX1):
    S(x, 1, 0, BLACK); S(x, 1, 8, BLACK)
# ramen (open, zonder glas)
for x in (8, 10, 14, 15): S(x, 2, 0, "air")
for x in (8, 14): S(x, 2, 8, "air")
S(WX1, 2, 6, "air")
# deuren met opstapje
S(WX1, 2, 4, "dark_oak_door[facing=east,half=lower,hinge=right,open=false]")
S(WX1, 3, 4, "dark_oak_door[facing=east,half=upper,hinge=right,open=false]")
S(16, 1, 4, "spruce_stairs[facing=east,half=bottom]")
S(WX0, 2, 4, "dark_oak_door[facing=west,half=lower,hinge=left,open=false]")
S(WX0, 3, 4, "dark_oak_door[facing=west,half=upper,hinge=left,open=false]")
S(7, 1, 4, "spruce_stairs[facing=west,half=bottom]")
# ---- achterin (x 7-9): slaapkamer (bakboord) en badkamer (stuurboord) aan een gang ----
for x in R(7, 9):
    for y in R(1, 3):
        S(x, y, 3, "birch_planks"); S(x, y, 5, "birch_planks")
for z in (1, 2, 3, 5, 6, 7):
    for y in R(1, 3): S(10, y, z, "birch_planks")
for x in (8, 9, 10): S(x, 1, 4, "light_gray_carpet")
S(9, 1, 3, "spruce_door[facing=north,half=lower,hinge=left,open=false]")
S(9, 2, 3, "spruce_door[facing=north,half=upper,hinge=left,open=false]")
S(9, 1, 5, "spruce_door[facing=south,half=lower,hinge=right,open=false]")
S(9, 2, 5, "spruce_door[facing=south,half=upper,hinge=right,open=false]")
# slaapkamer: tweepersoonsbed (twee bedden naast elkaar), hoofdbord, nachtkastje met lampje
for z in (1, 2):
    S(7, 1, z, "light_blue_bed[facing=west,part=head,occupied=false]")
    S(8, 1, z, "light_blue_bed[facing=west,part=foot,occupied=false]")
    S(7, 2, z, "dark_oak_trapdoor[facing=east,half=bottom,open=true]")
S(9, 1, 1, "barrel[facing=up]"); S(9, 2, 1, "lantern[hanging=false]")
S(7, 3, 1, "barrel[facing=east]"); S(7, 3, 2, "barrel[facing=east]")          # kastjes boven het bed
# badkamer: douche, toilet, wastafel met kraan
S(7, 1, 6, "light_blue_carpet"); S(7, 1, 7, "light_blue_carpet")
S(7, 3, 7, "lightning_rod[facing=east]")                                     # douchekop
S(8, 1, 7, "quartz_stairs[facing=south,half=bottom]")                        # toilet
S(8, 2, 7, "quartz_slab[type=bottom]")                                       # stortbak
S(9, 1, 7, "water_cauldron[level=3]"); S(9, 2, 7, "tripwire_hook[facing=north,attached=false,powered=false]")

# ---- voorin (x 11-16): open woonkamer met keuken over de volle breedte ----
for x in R(11, 16):
    for z in R(5, 7): S(x, 0, z, "polished_diorite" if (x + z) % 2 == 0 else "polished_andesite")   # tegelvloer keuken
# zithoek (bakboord): bank met de rug naar achteren, salontafel op een kleed, tv in de voorwand
for z in (1, 2, 3): S(12, 1, z, "dark_oak_stairs[facing=west,half=bottom]")
for x in (13, 15):
    for z in (1, 2, 3): S(x, 1, z, "light_gray_carpet")
S(14, 1, 1, "light_gray_carpet"); S(14, 1, 3, "light_gray_carpet")
S(14, 1, 2, "spruce_slab[type=bottom]")                                      # salontafel
for z in (1, 2, 3):
    S(16, 1, z, "dark_oak_slab[type=bottom]")                                # tv-meubel
    S(WX1, 2, z, "black_concrete")                                           # tv aan de wand
S(11, 1, 1, "potted_fern")
S(11, 1, 3, "spruce_fence"); S(11, 2, 3, "lantern[hanging=false]")           # staande lamp
# keuken langs stuurboord: koelkast, vaatwasser, lades, spoelbak onder het raam, fornuis met afzuigkap
S(11, 1, 7, "iron_block"); S(11, 2, 7, "iron_block")                         # koelkast
S(11, 2, 6, "stone_button[face=wall,facing=north,powered=false]")            # handgreep
S(12, 1, 7, "dropper[facing=north,triggered=false]")                         # vaatwasser
S(13, 1, 7, "barrel[facing=north,open=false]")                               # lades
S(14, 1, 7, "water_cauldron[level=3]")                                       # spoelbak
S(14, 2, 7, "tripwire_hook[facing=north,attached=false,powered=false]")      # kraan
S(15, 1, 7, "smoker[facing=north,lit=false]")                                # oven met kookplaat
S(15, 3, 7, "hopper[facing=down,enabled=true]")                              # afzuigkap
S(16, 1, 7, "barrel[facing=north,open=false]")
for x in (12, 13, 16): S(x, 3, 7, "barrel[facing=north,open=false]")       # bovenkastjes
S(16, 1, 6, "barrel[facing=west,open=false]"); S(16, 1, 5, "barrel[facing=west,open=false]")   # hoek van de L
S(16, 3, 6, "barrel[facing=west,open=false]")
# eettafel met twee stoelen
for x in (13, 14):
    S(x, 1, 5, "spruce_fence"); S(x, 2, 5, "spruce_pressure_plate[powered=false]")
S(12, 1, 5, "dark_oak_stairs[facing=west,half=bottom]"); S(15, 1, 5, "dark_oak_stairs[facing=east,half=bottom]")
# verlichting in het plafond
for p in [(8, 4, 1), (8, 4, 7), (8, 4, 4), (13, 4, 2), (13, 4, 6), (15, 4, 4), (12, 4, 4)]: S(*p, "sea_lantern")
# dakreling (opening bij de trap)
for x in R(WX0, WX1):
    S(x, 5, 0, "iron_bars"); S(x, 5, 8, "iron_bars")
for z in R(0, 8):
    S(WX1, 5, z, "iron_bars")
    if z not in (5, 6): S(WX0, 5, z, "iron_bars")
# bijboot op het dak
S(7, 5, 2, "oak_stairs[facing=west,half=bottom]"); S(8, 5, 2, "oak_slab[type=bottom]"); S(9, 5, 2, "oak_slab[type=bottom]"); S(10, 5, 2, "oak_stairs[facing=east,half=bottom]")
# uitlaat
for y in R(5, 7): S(10, y, 7, "polished_blackstone_wall")
S(10, 8, 7, "campfire[lit=true,facing=north]")

# ---------------- stuurhuis (x 12-16, z 2-6) op het woningdak ----------------
HX0, HX1, HZ0, HZ1 = 12, 16, 2, 6
for x in R(HX0, HX1):
    for z in R(HZ0, HZ1):
        edge = x in (HX0, HX1) or z in (HZ0, HZ1)
        if edge and x != HX1: S(x, 5, z, "spruce_planks")       # gelakt houten onderpaneel
        S(x, 8, z, WHITE)
for z in R(HZ0 + 1, HZ1 - 1): S(HX1, 5, z, "polished_deepslate_slab[type=top]")   # stuurlessenaar
S(HX1, 5, HZ0, "spruce_planks"); S(HX1, 5, HZ1, "spruce_planks")
for (x, z) in [(HX0, HZ0), (HX0, HZ1), (HX1, HZ0), (HX1, HZ1)]:
    S(x, 6, z, "dark_oak_fence")
for z in (3, 5):
    for y in (6, 7): S(HX0, y, z, WHITE)
S(HX0, 7, 4, WHITE)
S(HX0, 5, 4, "dark_oak_door[facing=west,half=lower,hinge=left,open=false]")
S(HX0, 6, 4, "dark_oak_door[facing=west,half=upper,hinge=left,open=false]")
# zonneklep rondom
for x in R(HX0 - 1, HX1 + 1):
    S(x, 8, HZ0 - 1, "smooth_quartz_slab[type=top]"); S(x, 8, HZ1 + 1, "smooth_quartz_slab[type=top]")
for z in R(HZ0, HZ1):
    S(HX0 - 1, 8, z, "smooth_quartz_slab[type=top]"); S(HX1 + 1, 8, z, "smooth_quartz_slab[type=top]")
for x in R(HX0, HX1):
    for z in (HZ0, HZ1):
        if (x, z) not in [(HX0, HZ0), (HX0, HZ1), (HX1, HZ0), (HX1, HZ1)]: S(x, 7, z, "smooth_quartz_slab[type=top]")
for z in R(HZ0 + 1, HZ1 - 1): S(HX1, 7, z, "smooth_quartz_slab[type=top]")
for (x, z) in [(HX0, HZ0), (HX0, HZ1), (HX1, HZ0), (HX1, HZ1)]: S(x, 7, z, WHITE)
S(HX1, 9, HZ0 - 1, "red_candle[candles=1,lit=true]"); S(HX1, 9, HZ1 + 1, "lime_candle[candles=1,lit=true]")   # boordlichten
# inrichting: stuurstoel, scherm, joystick, kaartentafel
S(14, 5, 4, "dark_oak_stairs[facing=west,half=bottom]")
S(HX1, 6, 3, "black_carpet"); S(HX1, 6, 5, "black_carpet")     # radar en kaartscherm, plat op de lessenaar
S(15, 5, 5, "lever[face=floor,facing=east,powered=false]")      # gashendel naast de stoel, uit het zicht
S(13, 5, 5, "cartography_table")
# mast met radar en hoorn
for y in R(9, 10): S(14, y, 4, "iron_bars")
S(14, 11, 4, "end_rod[facing=up]")
for z in R(3, 5): S(14, 12, z, "polished_blackstone_slab[type=bottom]")
S(13, 9, 3, "lightning_rod[facing=up]"); S(13, 9, 5, "lightning_rod[facing=up]")
S(15, 9, 4, "lantern[hanging=false]")

# ---------------- looppad x=18 en ladders langs de romp ----------------
for y in R(-2, 1):
    S(18, y, -1, "ladder[facing=north]"); S(18, y, 9, "ladder[facing=south]")
    S(58, y, -1, "ladder[facing=north]"); S(58, y, 9, "ladder[facing=south]")

# ---------------- laadruimen ----------------
HOLDS = [(20, 37), (39, 56)]
HB0, HB1, BEAM_X = 19, 57, 38
for x in R(HB0, HB1):
    for y in R(-2, 1):
        S(x, y, 1, INNER); S(x, y, 7, INNER)
    S(x, 2, 1, BLACK); S(x, 2, 7, BLACK)
    S(x, 3, 1, CAP); S(x, 3, 7, CAP)
for x in (HB0, HB1):
    for z in R(1, 7):
        for y in R(-2, 2): S(x, y, z, INNER if y < 2 else BLACK)
        S(x, 3, z, CAP)
for x in R(HB0 + 1, HB1 - 1):
    for z in R(2, 6): S(x, -2, z, "smooth_stone")
for z in R(2, 6): S(BEAM_X, 3, z, CAP)
for a, b in HOLDS:
    for y in R(-1, 3): S(a + 1, y, 2, "ladder[facing=south]")
    # ladder aan de buitenkant van het luikhoofd: vanaf het gangboord over de rand het ruim in
    for y in (2, 3): S(a + 1, y, 0, "ladder[facing=north]")

# ---------------- voorschip ----------------
for x in R(58, 69):
    z0, z1 = zr(x); yb = top(x) + 1
    for z in (z0, z1):
        if x == 58: continue
        S(x, yb, z, BLACK); S(x, yb + 1, z, CAP)
for z in R(zr(69)[0], zr(69)[1]):
    S(69, 3, z, BLACK); S(69, 4, z, CAP)
S(69, 5, 4, "lantern[hanging=false]")
# stuurstand voor
for z in R(3, 5): S(65, 3, z, "polished_deepslate_slab[type=top]")
S(65, 4, 3, "black_carpet"); S(64, 3, 5, "lever[face=floor,facing=east,powered=false]")
# ankerlier met kettingen naar de ankers in hun kluis
S(66, 3, 3, "grindstone[face=floor,facing=east]"); S(66, 3, 5, "grindstone[face=floor,facing=east]")
S(66, 3, 2, "chain[axis=z]"); S(66, 3, 6, "chain[axis=z]")
S(66, 1, 1, "anvil[facing=east]"); S(66, 1, 7, "anvil[facing=east]")
# mast met toplicht en vlag
for y in R(3, 8): S(67, y, 4, "iron_bars")
S(67, 7, 3, "iron_bars"); S(67, 7, 5, "iron_bars")
S(67, 9, 4, "lantern[hanging=false]")
# bolders en luik boegschroefruimte
# houten luik naar de boegschroefruimte, met ladder tegen de huid
S(61, 1, 1, "spruce_trapdoor[facing=south,half=top,open=false]")
for y in R(-2, 0): S(61, y, 1, "ladder[facing=south]")
# boegschroefmotor en verlichting in de voorpiek
S(60, -2, 4, "iron_block"); S(61, -2, 4, "iron_block"); S(60, -1, 4, "lightning_rod[facing=up]")
S(61, -1, 4, "grindstone[face=floor,facing=east]")
for z in (3, 5): S(60, -2, z, "chain[axis=z]")
S(63, -2, 6, "lantern[hanging=false]"); S(58, -2, 2, "barrel[facing=up]")

B = {k: v for k, v in B.items() if v != "minecraft:air"}

# ---------------- verbindingen ----------------
def base(s): return s.split("[")[0].replace("minecraft:", "")
NONFULL = ("candle", "slab", "stairs", "pane", "bars", "fence", "wall", "trapdoor", "door", "ladder", "lantern", "carpet",
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
    "name": "JOHANNA",
    "length": L, "beam": 9,
    "palette": pal,
    "blocks": flat,
    "hull": [[x, zr(x)[0], zr(x)[1], bot(x)] for x in range(L)],
    "holds": [[a, b] for a, b in HOLDS],
    "holdZ": [2, 6], "holdFloorY": -1, "coamingTopY": 3.5,
    "seats": {
        "aft": {"pos": [14.5, 5.45, 4.5], "yaw": 0},
        "bow": {"pos": [64.5, 3.0, 4.5], "yaw": 0}
    },
    "helmZones": {
        "aft": [14, 5, 3, 16, 6, 5],
        "bow": [64, 3, 3, 65, 4, 5]
    }
}
here = os.path.dirname(os.path.abspath(__file__))
dst = os.path.join(here, "..", "src", "main", "resources", "kempenaar", "johanna.json")
os.makedirs(os.path.dirname(dst), exist_ok=True)
json.dump(out, open(dst, "w"), separators=(",", ":"))
print("blokken", len(B), "palet", len(pal), "->", os.path.relpath(dst))
