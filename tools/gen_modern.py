"""Shared generator for the modern-style inland ships (STELLA MARIS, HENDRIKA tanker).

Same layout idea as the ZEELANDIA: aft deck with a car and its crane, white accommodation with a lifting
wheelhouse on a column (reached by a ladder mast that stays in place), engine room below, a long cargo
section (dry holds with coamings and rolling covers, or closed tanks with pipework for a tanker) and a
fore ship with anchor gear, bow helm and (optionally) an A-mast.

Template axes: x = length (0 = stern), z = beam (0 = port), y = height. Deck plating is y=1 (top 2.0).
"""
import json, os


def build(cfg):
    B = {}

    def S(x, y, z, s):
        if not s.startswith("minecraft:"):
            s = "minecraft:" + s
        B[(x, y, z)] = s

    def R(a, b):
        return range(a, b + 1)

    W = cfg["beam"]
    ZC = W // 2                                   # centre line block
    CZ = W / 2.0                                  # centre of the ship
    Z0, Z1 = 0, W - 1
    TANKER = cfg.get("tanker", False)
    HOLD_LEN, N_HOLDS = cfg["hold_len"], cfg["holds"]
    AFT_END = 23
    HOLD_START = AFT_END + 1
    HOLDS = []
    x = HOLD_START + 1
    for i in range(N_HOLDS):
        HOLDS.append((x, x + HOLD_LEN - 1))
        x += HOLD_LEN + 1
    BEAMS = [b + 1 for a, b in HOLDS[:-1]]
    HOLD_END = HOLDS[-1][1] + 1
    FORE_START = HOLD_END + 1
    L = cfg.get("length", FORE_START + cfg.get("fore_len", 16))
    assert L - FORE_START >= 12, "fore ship too short"
    FLOOR_Y = cfg.get("floor_y", -4)              # hold floor block
    HULL_BOT = cfg.get("hull_bottom", -5)
    CO0, CO1 = (1, W - 2)                         # coamings / tank side walls
    HZ = (CO0 + 1, CO1 - 1)

    HULL = cfg.get("hull", "black_concrete")
    ACC = cfg.get("accent", "blue_concrete")      # paint of bow, stern and sheer strake
    BAND = cfg.get("band", "white_concrete")      # coaming band
    WHITE = "white_concrete"
    DECK = cfg.get("deck", "gray_concrete")
    CAP = cfg.get("cap", "smooth_quartz_slab[type=bottom]")
    INNER = "light_gray_concrete"
    RAIL = "iron_bars"
    FASCIA = cfg.get("fascia", "blue_terracotta")

    BOW = L - 8

    def zr(x):
        if x == 0: return (2, W - 3)
        if x == 1: return (1, W - 2)
        d = x - BOW
        if d < 1: return (0, W - 1)
        f = min(d, 8) / 8.0
        z0 = max(1, int(round((f ** 1.25) * (W / 2.0 - 1.5))))
        return (z0, W - 1 - z0)

    def bot(x):
        if x <= 3: return -2
        if x == 4: return -3
        if x == 5: return max(HULL_BOT, -4)
        if x <= L - 14: return HULL_BOT
        if x <= L - 10: return max(HULL_BOT, -4)
        if x <= L - 6: return -3
        if x <= L - 3: return -2
        return -1

    def side_colour(x, y):
        if y <= -1: return HULL
        if x <= AFT_END - 2 or x >= FORE_START: return ACC
        return ACC if y == 1 else HULL

    # ---------------- hull ----------------
    for x in range(L):
        z0, z1 = zr(x); b = bot(x)
        for z in R(z0, z1): S(x, b, z, HULL)
        for y in R(b + 1, 1):
            S(x, y, z0, side_colour(x, y)); S(x, y, z1, side_colour(x, y))
        if x in (0, L - 1):
            for z in R(z0, z1):
                for y in R(b, 1): S(x, y, z, side_colour(x, y))
        if 4 < x < L - 4:
            S(x, -2, z0, "red_concrete"); S(x, -2, z1, "red_concrete")       # boot-topping
    for x in R(2, L - 3):
        z0, z1 = zr(x)
        if z0 > 0: continue
        S(x, 0, -1, "blackstone_slab[type=top]"); S(x, 0, W, "blackstone_slab[type=top]")   # rubbing strakes
    for x in list(R(1, AFT_END)) + list(R(FORE_START, L - 2)):
        z0, z1 = zr(x)
        for z in R(z0 + 1, z1 - 1): S(x, 1, z, DECK)
    BOARD_X = (HOLD_START + 2, HOLD_END - 2)

    # ---------------- stern gear ----------------
    for zc in (ZC - 3, ZC + 3):
        for y in R(-4, -2): S(1, y, zc, HULL)
        S(3, -3, zc, "lightning_rod[facing=west]")
        S(3, -2, zc, "waxed_cut_copper_slab[type=bottom]"); S(3, -4, zc, "waxed_cut_copper_slab[type=top]")
        S(3, -3, zc - 1, "waxed_cut_copper_slab[type=top]"); S(3, -3, zc + 1, "waxed_cut_copper_slab[type=bottom]")
        for xx in (4, 5): S(xx, -3, zc, "chain[axis=x]")

    # ---------------- aft deck (x 1..7) ----------------
    for x in R(0, 7):
        z0, z1 = zr(x)
        S(x, 2, z0, WHITE); S(x, 2, z1, WHITE)
        S(x, 3, z0, RAIL); S(x, 3, z1, RAIL)
    for z in R(2, W - 3):
        S(0, 2, z, WHITE)
        if z != ZC: S(0, 3, z, RAIL)
    S(1, 3, 1, RAIL); S(1, 3, W - 2, RAIL)
    for y in R(2, 5): S(0, y, ZC, "birch_fence")                            # flagstaff
    S(0, 6, ZC, "end_rod[facing=up]")
    for z in (2, W - 3):
        S(2, 2, z, "polished_blackstone_wall"); S(3, 2, z, "polished_blackstone_wall")   # bollards
    S(5, 2, 3, "iron_block"); S(5, 3, 3, "polished_blackstone_slab[type=bottom]"); S(6, 2, 3, "chain[axis=x]")
    # car with its crane on the starboard side of the aft deck
    cz0, cz1 = W - 5, W - 4
    for x in R(2, 5):
        for z in (cz0, cz1): S(x, 2, z, cfg.get("car", "gray_concrete"))
    for z in (cz0, cz1):
        S(2, 3, z, "black_stained_glass"); S(3, 3, z, "black_stained_glass"); S(4, 3, z, "black_stained_glass")
        S(5, 3, z, "polished_deepslate_slab[type=bottom]")
        for x in (2, 3, 4): S(x, 4, z, "gray_carpet")
    for x in (2, 5):
        S(x, 2, cz0 - 1, "polished_blackstone_button[face=wall,facing=north,powered=false]")
        S(x, 2, cz1 + 1, "polished_blackstone_button[face=wall,facing=south,powered=false]")
    S(6, 2, cz0, "end_rod[facing=east]"); S(6, 2, cz1, "end_rod[facing=east]")
    S(1, 2, cz0, "red_candle[candles=1,lit=false]"); S(1, 2, cz1, "red_candle[candles=1,lit=false]")
    S(4, 3, cz0 - 1, "lever[face=wall,facing=north,powered=false]"); S(4, 3, cz1 + 1, "lever[face=wall,facing=south,powered=false]")
    crz = W - 3
    for y in R(2, 6): S(6, y, crz, "yellow_concrete")
    for (x, z) in [(5, crz - 1), (4, cz1), (3, cz1)]: S(x, 6, z, "yellow_concrete")
    S(3, 5, cz1, "chain[axis=y]")
    # steering gear room hatch: ladder up through the deck, hatch on top
    S(4, 2, ZC - 1, "spruce_trapdoor[facing=north,half=bottom,open=false]")
    for y in R(-2, 1): S(4, y, ZC - 1, "ladder[facing=north]")

    # ---------------- accommodation (x 8..21) ----------------
    HX0, HX1, HZ0, HZ1 = 8, 21, 2, W - 3
    for x in R(HX0, HX1):
        for z in R(HZ0, HZ1):
            edge = x in (HX0, HX1) or z in (HZ0, HZ1)
            for y in R(2, 4): S(x, y, z, WHITE if edge else "air")
            S(x, 1, z, "spruce_planks")
            S(x, 5, z, "smooth_quartz" if edge else "light_gray_concrete")
    for x in R(HX0 - 1, HX1 + 1):
        S(x, 5, HZ0 - 1, FASCIA); S(x, 5, HZ1 + 1, FASCIA)
    for z in R(HZ0, HZ1):
        S(HX0 - 1, 5, z, FASCIA); S(HX1 + 1, 5, z, FASCIA)
    for x in R(8, AFT_END - (2 if W < 14 else 0)):
        S(x, 2, 0, WHITE); S(x, 2, W - 1, WHITE)
        S(x, 3, 0, RAIL); S(x, 3, W - 1, RAIL)
    # window openings (no glass)
    for x in R(HX0 + 1, HX1 - 1):
        if x not in (13, 17):
            S(x, 3, HZ0, "air"); S(x, 3, HZ1, "air")
    for z in R(HZ0 + 1, HZ1 - 1):
        if abs(z - ZC) >= 2: S(HX1, 3, z, "air")
    NARROW = W < 14
    DZ = HZ1 - 1 if NARROW else ZC                # corridor / door line
    S(HX0, 2, DZ, "dark_oak_door[facing=west,half=lower,hinge=left,open=false]")
    S(HX0, 3, DZ, "dark_oak_door[facing=west,half=upper,hinge=left,open=false]")
    S(HX1, 2, DZ, "dark_oak_door[facing=east,half=lower,hinge=right,open=false]")
    S(HX1, 3, DZ, "dark_oak_door[facing=east,half=upper,hinge=right,open=false]")
    for x in R(HX0 + 1, HX1 - 1): S(x, 1, DZ, "light_gray_wool")
    if NARROW:
        # corridor along starboard, rooms to port: living room, galley, bedroom
        PW = DZ - 1
        P0, P1 = HZ0 + 1, PW - 1
        for x in R(HX0 + 1, HX1 - 1):
            if x in (11, 15, 19): continue                 # doorways
            for y in R(2, 4): S(x, y, PW, "birch_planks")
        for z in R(P0, P1):
            for y in R(2, 4): S(13, y, z, "birch_planks"); S(17, y, z, "birch_planks")
        # living room x 9..12: sofa against the partition, TV on the aft wall, coffee table
        for z in R(P0, P1): S(HX0, 3, z, "black_concrete")
        for z in R(P0, P1): S(9, 2, z, "dark_oak_slab[type=bottom]")
        for z in (P0, P0 + 1): S(12, 2, z, "dark_oak_stairs[facing=east,half=bottom]")   # sofa looking at the TV
        S(10, 2, P0, "spruce_slab[type=bottom]")                                          # coffee table
        S(9, 2, P1, "spruce_fence"); S(9, 3, P1, "lantern[hanging=false]")               # standing lamp
        # galley x 14..16 along the hull side
        for x in R(14, 16):
            for z in R(P0, P1): S(x, 1, z, "polished_diorite" if (x + z) % 2 == 0 else "polished_andesite")
        S(14, 2, P0, "iron_block"); S(14, 3, P0, "iron_block")
        S(15, 2, P0, "water_cauldron[level=3]"); S(15, 3, P0, "tripwire_hook[facing=south,attached=false,powered=false]")
        S(16, 2, P0, "smoker[facing=south,lit=false]"); S(16, 4, P0, "hopper[facing=down,enabled=true]")
        for x in (14, 15): S(x, 4, P0, "spruce_trapdoor[facing=south,half=top,open=true]")
        S(16, 2, P1, "spruce_fence"); S(16, 3, P1, "spruce_pressure_plate[powered=false]")
        # bedroom x 18..20: double bed (head against the front wall), bedside table, wardrobe
        for z in (P0, P0 + 1):
            S(20, 2, z, "light_blue_bed[facing=east,part=head,occupied=false]")
            S(19, 2, z, "light_blue_bed[facing=east,part=foot,occupied=false]")
            S(20, 3, z, "dark_oak_trapdoor[facing=west,half=bottom,open=true]")
        if P1 >= P0 + 2:
            S(20, 2, P1, "spruce_slab[type=top]"); S(20, 3, P1, "lantern[hanging=false]")
        S(18, 2, P0, "dark_oak_planks"); S(18, 3, P0, "dark_oak_planks")
        S0, S1 = DZ, DZ
        LIGHTS = [(10, 5, P0), (15, 5, P0), (19, 5, P0), (11, 5, DZ), (17, 5, DZ)]
    else:
        P0 = HZ0 + 1
        S0, S1 = ZC + 1, HZ1 - 1
        PW = ZC - 1
        for x in R(HX0 + 1, HX1 - 1):
            for y in R(2, 4): S(x, y, PW, "birch_planks")
        P1 = PW - 1
        for z in R(P0, P1):
            for y in R(2, 4): S(13, y, z, "birch_planks"); S(17, y, z, "birch_planks")
        for (dx, hinge) in [(11, "left"), (15, "right"), (19, "left")]:
            S(dx, 2, PW, f"spruce_door[facing=north,half=lower,hinge={hinge},open=false]")
            S(dx, 3, PW, f"spruce_door[facing=north,half=upper,hinge={hinge},open=false]")
        for z in (P0, P0 + 1):
            S(9, 2, z, "light_blue_bed[facing=west,part=head,occupied=false]")
            S(10, 2, z, "light_blue_bed[facing=west,part=foot,occupied=false]")
            S(9, 3, z, "dark_oak_trapdoor[facing=east,half=bottom,open=true]")
        S(12, 2, P0, "dark_oak_planks"); S(12, 3, P0, "dark_oak_planks")
        if P1 >= P0 + 2:
            S(9, 2, P1, "spruce_slab[type=top]"); S(9, 3, P1, "lantern[hanging=false]")
            S(11, 2, P1, "white_carpet")
        S(15, 2, P0, "water_cauldron[level=3]"); S(15, 4, P0, "lightning_rod[facing=down]")
        S(16, 2, P0, "quartz_slab[type=top]"); S(16, 3, P0, "tripwire_hook[facing=south,attached=false,powered=false]")
        S(14, 2, P1, "quartz_stairs[facing=north,half=bottom]"); S(14, 3, P1, "quartz_slab[type=bottom]")
        for xx in R(14, 16):
            for z in R(P0, P1): S(xx, 1, z, "white_concrete")
        S(20, 2, P0, "white_bed[facing=east,part=head,occupied=false]"); S(19, 2, P0, "white_bed[facing=east,part=foot,occupied=false]")
        S(18, 2, P0, "barrel[facing=up]"); S(18, 3, P0, "lantern[hanging=false]")
        if P1 > P0: S(20, 2, P1, "spruce_slab[type=top]"); S(20, 3, P1, "flower_pot")
        for z in R(S0, S1): S(12, 2, z, "dark_oak_stairs[facing=east,half=bottom]")
        for z in R(S0, S1): S(HX0, 3, z, "black_concrete")
        for z in R(S0, S1):
            S(9, 2, z, "dark_oak_slab[type=bottom]")
            S(10, 2, z, "light_gray_carpet")
        S(11, 2, (S0 + S1) // 2, "spruce_slab[type=bottom]")
        S(13, 2, S1, "spruce_fence"); S(13, 3, S1, "lantern[hanging=false]")
        for xx in R(14, 20):
            for z in R(S0, S1): S(xx, 1, z, "polished_diorite" if (xx + z) % 2 == 0 else "polished_andesite")
        S(20, 2, S1, "iron_block"); S(20, 3, S1, "iron_block")
        S(14, 2, S1, "barrel[facing=north,open=false]"); S(15, 2, S1, "dropper[facing=north,triggered=false]")
        S(16, 2, S1, "water_cauldron[level=3]"); S(16, 3, S1, "tripwire_hook[facing=north,attached=false,powered=false]")
        S(17, 2, S1, "smoker[facing=north,lit=false]"); S(17, 4, S1, "hopper[facing=down,enabled=true]")
        S(18, 2, S1, "blast_furnace[facing=north,lit=false]"); S(19, 2, S1, "barrel[facing=north,open=false]")
        for xx in (14, 15, 18, 19): S(xx, 4, S1, "spruce_trapdoor[facing=north,half=top,open=true]")
        if S1 - S0 >= 3:
            for xx in (16, 17, 18): S(xx, 2, S1 - 2, "polished_deepslate_slab[type=top]")
            for xx in (16, 18): S(xx, 2, S1 - 3, "spruce_fence"); S(xx, 3, S1 - 3, "spruce_pressure_plate[powered=false]")
        LIGHTS = [(10, 5, P0), (15, 5, P0), (19, 5, P0), (11, 5, ZC), (17, 5, ZC), (10, 5, S1), (15, 5, S1), (19, 5, S1 - 1)]
    for p in LIGHTS: S(*p, "sea_lantern")
    # engine room below (x 9..20)
    for xx in R(9, 20):
        for z in R(1, W - 2): S(xx, -4, z, "smooth_stone")
    for z in R(1, W - 2):
        for y in R(-4, 0): S(8, y, z, INNER); S(21, y, z, INNER)
    for xx in R(9, 20):
        for y in R(-4, 0): S(xx, y, 1, INNER); S(xx, y, W - 2, INNER)
    for zc in sorted({max(3, ZC - 3), min(W - 4, ZC + 3)}):
        for xx in R(11, 17):
            S(xx, -3, zc, "iron_block" if xx in (11, 17) else "black_concrete"); S(xx, -2, zc, "polished_blackstone_slab[type=bottom]")
        for xx in (12, 14, 16): S(xx, -1, zc, "lightning_rod[facing=up]")
    S(10, -3, DZ, "lever[face=floor,facing=east,powered=false]")
    for p in [(15, 0, ZC), (12, 0, 3), (12, 0, W - 4), (18, 0, ZC)]: S(*p, "lantern[hanging=true]")
    S(20, 2, DZ, "spruce_trapdoor[facing=west,half=bottom,open=false]")
    for y in R(-3, 1): S(20, y, DZ, "ladder[facing=west]")

    # ---------------- roof, ladder, exhaust, tender ----------------
    RL = HZ0 + 1 if NARROW else HZ1 - 1           # roof ladder z
    for xx in R(HX0, HX1):
        S(xx, 6, HZ0, RAIL); S(xx, 6, HZ1, RAIL)
    for z in R(HZ0, HZ1):
        S(HX1, 6, z, RAIL)
        if z != RL: S(HX0, 6, z, RAIL)
    for y in R(2, 5): S(7, y, RL, "ladder[facing=west]")
    for y in R(6, 8): S(10, y, HZ1 - 1, "polished_blackstone_wall")
    S(10, 9, HZ1 - 1, "cauldron")
    EXHAUST = (10.5, 9.9, HZ1 - 0.5)
    if not NARROW:
        S(8, 6, HZ0 + 1, "smooth_quartz_stairs[facing=west,half=bottom]"); S(9, 6, HZ0 + 1, "smooth_quartz_slab[type=bottom]")
        S(10, 6, HZ0 + 1, "smooth_quartz_slab[type=bottom]"); S(11, 6, HZ0 + 1, "smooth_quartz_stairs[facing=east,half=bottom]")

    # ---------------- lifting wheelhouse ----------------
    WX0, WX1, WY = 14, 19, 6
    WZ0, WZ1 = (HZ0, HZ1) if NARROW else (max(HZ0 + 2, ZC - 4), min(HZ1 - 2, ZC + 4))
    LIFT_MAX = cfg.get("lift_max", 4.0)
    LIFT = {"region": [WX0, WY, WZ0 - 1, WX1 + 1, WY + 7, WZ1 + 1], "max": LIFT_MAX, "column": [15, ZC - 1, 17, ZC + 1]}
    for xx in R(WX0, WX1):
        for z in R(WZ0, WZ1):
            edge = xx in (WX0, WX1) or z in (WZ0, WZ1)
            S(xx, WY, z, WHITE if edge else "spruce_planks")
            S(xx, WY + 4, z, WHITE)
            if edge:
                S(xx, WY + 1, z, WHITE)
                S(xx, WY + 2, z, "air")
                S(xx, WY + 3, z, "smooth_quartz_slab[type=top]")
    for (xx, z) in [(WX0, WZ0), (WX0, WZ1), (WX1, WZ0), (WX1, WZ1)]: S(xx, WY + 2, z, WHITE)
    for xx in R(WX0 + 1, WX1 - 1):                                 # window posts along the sides
        if (xx - WX0) % 2 == 0: S(xx, WY + 2, WZ0, "smooth_quartz_wall" if False else WHITE); S(xx, WY + 2, WZ1, WHITE) if (xx - WX0) % 2 == 0 else None
    for z in R(WZ0 + 1, WZ1 - 1):
        if (z - WZ0) % 3 == 0: S(WX1, WY + 2, z, WHITE); S(WX0, WY + 2, z, WHITE) if z != ZC else None
    for xx in R(WX0, WX1):
        for z in (WZ0, WZ1): S(xx, WY + 3, z, WHITE)               # full header band under the roof
    for z in R(WZ0, WZ1):
        S(WX0, WY + 3, z, WHITE); S(WX1, WY + 3, z, WHITE)
    for z in R(WZ0 + 1, WZ1 - 1): S(WX1, WY + 1, z, "smooth_quartz_slab[type=bottom]")
    for xx in R(WX0, WX1 + 1):
        S(xx, WY + 4, WZ0 - 1, "smooth_quartz_slab[type=bottom]"); S(xx, WY + 4, WZ1 + 1, "smooth_quartz_slab[type=bottom]")
    for z in R(WZ0 - 1, WZ1 + 1): S(WX1 + 1, WY + 4, z, "smooth_quartz_slab[type=bottom]")
    S(17, WY + 5, ZC, "iron_bars"); S(17, WY + 6, ZC, "end_rod[facing=up]")
    for z in R(ZC - 2, ZC + 2): S(17, WY + 7, z, "polished_blackstone_slab[type=bottom]")    # radar
    S(16, WY + 5, WZ0 + 1, "lightning_rod[facing=up]"); S(16, WY + 5, WZ1 - 1, "lightning_rod[facing=up]")
    S(WX1, WY + 5, WZ0, "red_candle[candles=1,lit=true]"); S(WX1, WY + 5, WZ1, "lime_candle[candles=1,lit=true]")
    # rear doorway + ladder mast (outside the lifting part, long enough for the raised house)
    S(WX0, WY + 1, ZC, "air"); S(WX0, WY + 2, ZC, "air")
    for y in R(WY, WY + 1 + int(LIFT_MAX)): S(12, y, ZC, "andesite_wall")
    for y in R(WY, WY + int(LIFT_MAX)): S(13, y, ZC, "ladder[facing=east]")
    # helm console, everything standing on the desk
    DESK = "polished_deepslate_slab[type=top]"
    for z in R(WZ0 + 1, WZ1 - 1): S(WX1 - 1, WY + 1, z, DESK)
    for z in (WZ0 + 1, WZ1 - 1): S(WX1 - 2, WY + 1, z, DESK)
    D = WY + 2
    S(WX1 - 1, D, ZC, "black_carpet")
    for z in R(WZ0 + 1, WZ1 - 1):
        if z != ZC and abs(z - ZC) <= 2: S(WX1 - 1, D, z, "gray_carpet" if abs(z - ZC) == 1 else "black_carpet")
    S(WX1 - 1, D, WZ0 + 1, "lever[face=floor,facing=west,powered=false]")
    S(WX1 - 1, D, WZ1 - 1, "lever[face=floor,facing=west,powered=false]")
    S(WX1 - 2, D, WZ0 + 1, "stone_button[face=floor,facing=west,powered=false]")
    S(WX1 - 2, D, WZ1 - 1, "polished_blackstone_button[face=floor,facing=west,powered=false]")
    for z in R(max(WZ0 + 1, ZC - 2), min(WZ1 - 1, ZC + 2)): S(WX1 - 1, WY + 3, z, "polished_blackstone_slab[type=top]")
    S(16, WY + 1, ZC, "polished_blackstone_stairs[facing=west,half=bottom]")
    S(16, WY + 1, ZC - 1, "polished_blackstone_slab[type=bottom]"); S(16, WY + 1, ZC + 1, "polished_blackstone_slab[type=bottom]")
    S(15, WY + 1, WZ0 + 1, "cartography_table"); S(15, WY + 2, WZ0 + 1, "white_carpet")
    S(15, WY + 1, WZ1 - 1, "barrel[facing=up]"); S(15, WY + 2, WZ1 - 1, "flower_pot")
    if WZ1 - WZ0 >= 7:
        S(15, WY + 1, WZ1 - 2, "smoker[facing=east,lit=false]"); S(15, WY + 2, WZ1 - 2, "brewing_stand")
    S(16, WY + 4, ZC, "sea_lantern")
    LIGHTS.append((16, WY + 4, ZC))

    # ---------------- cargo section ----------------
    if not TANKER:
        for xx in R(HOLD_START, HOLD_END):
            for y in R(FLOOR_Y, 1):
                S(xx, y, CO0, INNER); S(xx, y, CO1, INNER)
            S(xx, 2, CO0, BAND); S(xx, 2, CO1, BAND)
            S(xx, 3, CO0, CAP); S(xx, 3, CO1, CAP)
        for xx in (HOLD_START, HOLD_END):
            for z in R(CO0, CO1):
                for y in R(FLOOR_Y, 1): S(xx, y, z, INNER)
                S(xx, 2, z, BAND); S(xx, 3, z, CAP)
        for xx in R(HOLD_START + 1, HOLD_END - 1):
            for z in R(HZ[0], HZ[1]): S(xx, FLOOR_Y, z, "smooth_stone")
        for b in BEAMS:
            for z in R(HZ[0], HZ[1]): S(b, 3, z, CAP)
        for a, b in HOLDS:
            for y in R(FLOOR_Y + 1, 3): S(a + 1, y, HZ[0], "ladder[facing=south]")
            for y in (2, 3): S(a + 1, y, 0, "ladder[facing=north]")
        COAMING_TOP = 3.5
    else:
        # closed tanks under a flat deck with pipework, catwalk, domes and a manifold with hose crane
        TD = cfg.get("tank_deck", "gray_concrete")
        for xx in R(HOLD_START, HOLD_END):
            for z in R(1, W - 2): S(xx, 1, z, TD)
            for y in R(FLOOR_Y, 0): S(xx, y, 1, INNER); S(xx, y, W - 2, INNER)
            S(xx, 2, 0, WHITE); S(xx, 3, 0, RAIL) if xx % 2 == 0 else S(xx, 3, 0, "air")
            S(xx, 2, W - 1, WHITE); S(xx, 3, W - 1, RAIL) if xx % 2 == 0 else S(xx, 3, W - 1, "air")
        for xx in [HOLD_START] + BEAMS + [HOLD_END]:
            for z in R(1, W - 2):
                for y in R(FLOOR_Y, 0): S(xx, y, z, INNER)              # tank bulkheads
        for xx in R(HOLD_START + 1, HOLD_END - 1):
            for z in R(2, W - 3): S(xx, FLOOR_Y, z, "smooth_stone")
        # central catwalk on supports with yellow rails
        for xx in R(HOLD_START, HOLD_END):
            S(xx, 2, ZC, "smooth_stone_slab[type=top]")
            if xx % 3 == 0:
                S(xx, 3, ZC - 1, "yellow_concrete") if False else None
        for xx in R(HOLD_START, HOLD_END, ):
            pass
        # pipelines on both sides of the catwalk
        for xx in R(HOLD_START, HOLD_END):
            for z in (ZC - 2, ZC + 2):
                S(xx, 2, z, "lightning_rod[facing=east]")
            S(xx, 2, ZC - 4, "chain[axis=x]"); S(xx, 2, ZC + 4, "chain[axis=x]")
        # per tank: expansion dome, pump, vent, inspection hatch, deck light
        for i, (a, b) in enumerate(HOLDS):
            mid = (a + b) // 2
            for z in (ZC - 3, ZC + 3):
                S(mid, 2, z, "cauldron"); S(mid - 3, 2, z, "light_blue_terracotta"); S(mid - 3, 3, z, "lever[face=floor,facing=east,powered=false]")
            S(mid + 3, 2, ZC - 5 if ZC - 5 > 1 else 2, "blue_concrete"); S(mid + 3, 3, ZC - 5 if ZC - 5 > 1 else 2, "end_rod[facing=up]")
            S(mid + 3, 2, ZC + 5 if ZC + 5 < W - 2 else W - 3, "blue_concrete"); S(mid + 3, 3, ZC + 5 if ZC + 5 < W - 2 else W - 3, "end_rod[facing=up]")
            S(a + 2, 2, ZC - 1, "iron_trapdoor[facing=north,half=bottom,open=false]")
            S(b - 1, 2, ZC + 1, "polished_blackstone_wall"); S(b - 1, 3, ZC + 1, "polished_blackstone_wall"); S(b - 1, 4, ZC + 1, "lantern[hanging=false]")
        # manifold with hose crane amidships
        MX = (HOLD_START + HOLD_END) // 2
        for z in R(2, W - 3):
            S(MX, 2, z, "polished_andesite_slab[type=bottom]")
        for z in (2, W - 3):
            S(MX, 3, z, "blue_concrete"); S(MX + 1, 3, z, "polished_blackstone_button[face=floor,facing=east,powered=false]")
        for y in R(2, 7): S(MX + 2, y, ZC + 2, "yellow_concrete")
        for k, (dx, dz) in enumerate([(3, 1), (4, 0), (5, -1)]):
            S(MX + 2 + dx - 2, 8, ZC + 2 + dz - 1, "yellow_concrete")
        S(MX + 3, 7, ZC, "chain[axis=y]"); S(MX + 3, 6, ZC, "chain[axis=y]")
        # blue painted area around the manifold like on the photos
        for xx in R(MX - 4, MX + 4):
            for z in (1, W - 2): S(xx, 1, z, "blue_concrete")
        COAMING_TOP = 2.0
    # winch / reel at the fore end of the cargo section
    for z in (2, W - 3):
        S(HOLD_END + 1, 2, z, "iron_block"); S(HOLD_END + 1, 3, z, "polished_blackstone_slab[type=bottom]")

    # ---------------- fore ship ----------------
    for xx in R(FORE_START + (2 if W < 14 else 0), L - 3):
        z0, z1 = zr(xx)
        S(xx, 2, z0, ACC if not TANKER else WHITE); S(xx, 2, z1, ACC if not TANKER else WHITE)
        S(xx, 3, z0, RAIL); S(xx, 3, z1, RAIL)
    for z in R(zr(L - 1)[0], zr(L - 1)[1]): S(L - 1, 2, z, ACC if not TANKER else WHITE)
    for z in R(zr(L - 2)[0], zr(L - 2)[1]): S(L - 2, 2, z, ACC if not TANKER else WHITE)
    FC = cfg.get("forecastle", "gray_concrete")
    for xx in R(L - 9, L - 2):
        z0, z1 = zr(xx)
        for z in R(z0 + 1, z1 - 1): S(xx, 2, z, FC)
        S(xx, 3, z0, ACC if not TANKER else WHITE); S(xx, 3, z1, ACC if not TANKER else WHITE)
        S(xx, 4, z0, RAIL); S(xx, 4, z1, RAIL)
    for z in (ZC - 1, ZC, ZC + 1): S(L - 10, 2, z, "polished_andesite_stairs[facing=east,half=bottom]")
    for z in (ZC - 3, ZC + 3):
        S(L - 6, 3, z, "iron_block"); S(L - 7, 3, z, "anvil[facing=north]"); S(L - 5, 3, z, "chain[axis=x]"); S(L - 4, 3, z, "chain[axis=x]")
    BT = FORE_START + 3
    S(BT, 2, 2, "spruce_trapdoor[facing=south,half=bottom,open=false]")
    for y in R(bot(BT) + 1, 1): S(BT, y, 2, "ladder[facing=south]")
    for z in R(1, W - 2):
        for y in R(-4, 0): S(FORE_START + 1, y, z, INNER)
    S(BT + 2, -3, ZC, "iron_block"); S(BT + 3, -3, ZC, "iron_block"); S(BT + 2, -2, ZC, "lightning_rod[facing=up]")
    S(BT + 4, 0, ZC, "lantern[hanging=true]")
    BH = FORE_START + 5
    for z in R(ZC - 1, ZC + 1): S(BH + 1, 2, z, "polished_deepslate_slab[type=top]")
    S(BH + 1, 3, ZC - 1, "black_carpet"); S(BH + 1, 3, ZC + 1, "black_carpet"); S(BH + 1, 3, ZC, "lever[face=floor,facing=west,powered=false]")
    S(BH, 2, ZC, "polished_blackstone_stairs[facing=west,half=bottom]")
    if cfg.get("a_mast", True) and cfg.get("slim_mast", False):
        AM = L - 5
        for y in R(3, 9): S(AM, y, ZC, RAIL)                       # thin steel pole
        for z in (ZC - 2, ZC - 1, ZC + 1, ZC + 2): S(AM, 8, z, RAIL)   # yard
        for z in (ZC - 2, ZC + 2): S(AM, 7, z, "lantern[hanging=true]")
        S(AM, 10, ZC, "end_rod[facing=up]")                         # masthead light
    elif cfg.get("a_mast", True):
        AM = L - 5
        m0, m1 = max(zr(AM)[0] + 1, ZC - 4), min(zr(AM)[1] - 1, ZC + 4)
        for y in R(3, 9):
            S(AM, y, m0, RAIL); S(AM, y, m1, RAIL)
        for z in R(m0, m1): S(AM, 10, z, RAIL)
        for z in (m0 + 1, m1 - 1): S(AM, 9, z, "lantern[hanging=true]")
        if cfg.get("flags", True):
            S(AM, 9, ZC - 1, "white_wool"); S(AM, 8, ZC - 1, ACC.replace("concrete", "wool"))
    else:
        # simple foremast with the masthead light (no flags)
        for y in R(3, 7): S(L - 5, y, ZC, "polished_blackstone_wall")
        S(L - 5, 8, ZC, "lantern[hanging=false]")
    for y in R(-4, 1):
        for xx in BOARD_X:
            S(xx, y, -1, "ladder[facing=north]"); S(xx, y, W, "ladder[facing=south]")
    for xx in BOARD_X:
        S(xx, 2, 0, "air"); S(xx, 2, W - 1, "air"); S(xx, 3, 0, "air"); S(xx, 3, W - 1, "air")

    B = {k: v for k, v in B.items() if v != "minecraft:air"}

    def base(s): return s.split("[")[0].replace("minecraft:", "")
    NONFULL = ("slab", "stairs", "pane", "bars", "fence", "wall", "trapdoor", "door", "ladder", "lantern", "carpet",
               "rod", "chain", "potted", "cauldron", "lever", "campfire", "grindstone", "anvil", "_bed", "button", "hook",
               "candle", "plate", "glass", "hopper", "flower_pot", "brewing")

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
        "name": cfg["name"],
        "length": L, "beam": W,
        "palette": pal,
        "blocks": flat,
        "hull": [[x, zr(x)[0], zr(x)[1], bot(x)] for x in range(L)],
        "holds": [[a, b] for a, b in HOLDS],
        "holdZ": [HZ[0], HZ[1]], "holdFloorY": FLOOR_Y + 1, "coamingTopY": COAMING_TOP,
        "tanker": TANKER,
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
    json.dump(out, open(cfg["out"], "w"), separators=(",", ":"))
    info = dict(L=L, W=W, ZC=ZC, CZ=CZ, holds=HOLDS, hold_start=HOLD_START, hold_end=HOLD_END, fore_start=FORE_START,
                BH=BH, BT=BT, DZ=DZ, P0=P0, RL=RL, board=BOARD_X, wy=WY, wz=(WZ0, WZ1), exhaust=EXHAUST, lights=LIGHTS, blocks=len(B))
    print(json.dumps(info))
    return info
