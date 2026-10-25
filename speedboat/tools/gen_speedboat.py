"""Builds the ZEPHYR: a 15 m offshore performance boat with twin outboards, a cockpit for four and a cuddy cabin.

Template coordinates: x = length (0 = transom, 14 = bow), z = beam (0 = port, 3 = starboard), y = height
(0 = the floor of the cockpit and the cabin, just above the waterline).
"""
import json, os

here = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(here, "..", "src", "main", "resources", "speedboat", "zephyr.json")

L, W = 15, 4
B = {}


def S(x, y, z, s):
    if not s.startswith("minecraft:"):
        s = "minecraft:" + s
    B[(x, y, z)] = s


def zr(x):
    return (1, 2) if x >= 13 else (0, 3)


HULL = "white_concrete"
BOOT = "black_concrete"           # the black band at the waterline, out of the water when she planes
STRIPE = "red_concrete"           # racing graphics along the cabin
CAP = "smooth_quartz_slab[type=bottom]"
DECK = "smooth_quartz_slab[type=bottom]"
TEAK = "spruce_planks"
CABIN_FLOOR = "birch_planks"
DARK = "polished_blackstone"

# Profile (top surfaces): cockpit gunwale 1.5, a low wrap-round windscreen at x=6 (1..2), the long foredeck at 1.5
# sloping to 1.0 and a raked stem. The driver sits at 1.0 and looks over the screen and the whole foredeck.
# The cuddy cabin lies below the waterline (floor at -1, kept dry by the hull), down a step from the cockpit.
CABIN = range(6, 13)          # x with the deep hull (bottom at y=-2)


def bottom(x):
    return -2 if x in CABIN else -1


for x in range(L):
    z0, z1 = zr(x)
    for z in range(z0, z1 + 1):
        side = z in (z0, z1)
        b = bottom(x)
        for y in range(b, 0):
            if side or x == 0 or x >= 13:
                S(x, y, z, BOOT)
            elif y == b:
                S(x, y, z, TEAK if x <= 5 else CABIN_FLOOR)
        if side and x <= 12:
            S(x, 0, z, HULL)

# transom with the underwater lights
for z in range(4):
    S(0, 0, z, HULL)
S(0, -1, 1, "sea_lantern")
S(0, -1, 2, "sea_lantern")

# cockpit gunwale (x 1..5): white cap at 1.5; the transom is a flat step at 1.0
for x in range(1, 6):
    for z in (0, 3):
        S(x, 1, z, CAP)
# along the cabin: the racing stripe with long dark windows just above the water
for x in range(7, 12):
    for z in (0, 3):
        S(x, 0, z, "black_stained_glass" if x in (8, 9, 10) else STRIPE)
# the foredeck at 1.5 over the cabin, down to 1.0 forward
for x in range(7, 12):
    for z in range(4):
        S(x, 1, z, DECK)
for z in range(4):
    S(12, 0, z, HULL if z in (1, 2) else ("quartz_stairs[facing=south,half=bottom]" if z == 0 else "quartz_stairs[facing=north,half=bottom]"))
# the bow: narrowing to a raked stem
for z in (1, 2):
    S(13, -1, z, BOOT)
    S(13, 0, z, HULL)
    S(14, -1, z, "quartz_stairs[facing=west,half=top]")
    S(14, 0, z, "quartz_stairs[facing=west,half=bottom]")
S(13, -1, 0, "quartz_stairs[facing=south,half=top]")
S(13, -1, 3, "quartz_stairs[facing=north,half=top]")
S(13, 0, 0, "smooth_quartz_slab[type=bottom]")
S(13, 0, 3, "smooth_quartz_slab[type=bottom]")
# a flush deck hatch and the bow cleat
S(10, 1, 1, "spruce_trapdoor[facing=east,half=bottom,open=false]")
S(13, 1, 1, "tripwire_hook[facing=west]")
S(13, 1, 2, "tripwire_hook[facing=west]")
# navigation lights at the bow, port red and starboard green
S(12, 1, 0, "red_candle[candles=1,lit=true]")
S(12, 1, 3, "green_candle[candles=1,lit=true]")

# ---------------- windscreen and companionway (x=6) ----------------
for z in (0, 3):
    S(6, 0, z, HULL)
    S(6, 1, z, "black_stained_glass_pane")
S(6, 0, 2, HULL)                                              # behind the console
S(6, 1, 2, "glass_pane")
S(6, -1, 2, HULL)
S(6, -1, 1, "spruce_stairs[facing=west,half=bottom]")         # the step down into the cabin
# (6, 0..1, 1) stays open: the walk-through in the windscreen
# the cabin door at the foot of the step
S(7, -1, 1, "dark_oak_door[facing=east,half=lower,hinge=left,open=false]")
S(7, 0, 1, "dark_oak_door[facing=east,half=upper,hinge=left,open=false]")
B.pop((7, 1, 1), None)                                        # the companionway hatch stands open over the steps
S(7, -1, 2, HULL)
S(7, 0, 2, HULL)

# ---------------- cockpit ----------------
for z in (1, 2):
    S(1, 0, z, "white_wool")                                  # sun pad over the engine well
    S(2, 0, z, "quartz_stairs[facing=west,half=bottom]")      # rear bench
    S(4, 0, z, "quartz_stairs[facing=east,half=top]")         # helm bolsters, seat at 1.0
# helm console at starboard (the wheel, levers and screen are drawn by the renderer)
S(5, 0, 2, DARK)
S(5, 1, 2, "polished_blackstone_slab[type=bottom]")
# radar arch over the rear bench
for z in (0, 3):
    S(2, 1, z, HULL)
    S(2, 2, z, "polished_blackstone_wall")
for z in range(4):
    S(2, 3, z, "polished_blackstone_slab[type=top]")
S(2, 4, 2, "end_rod[facing=up]")                              # white all-round light
S(2, 4, 1, "lightning_rod[facing=up]")                        # VHF aerial

# ---------------- twin outboards on the transom ----------------
for z in (1, 2):
    S(-1, -2, z, "polished_blackstone_wall")                  # lower unit
    S(-1, -1, z, "polished_blackstone_wall")                  # leg
    S(-1, 0, z, "black_concrete")                             # cowling
    S(-1, 1, z, "polished_blackstone_slab[type=bottom]")
    S(-2, -2, z, "lightning_rod[facing=west]")                # propeller hub
# swim ladder at the starboard corner of the transom
S(-1, -2, 3, "ladder[facing=west]")
S(-1, -1, 3, "ladder[facing=west]")
S(-1, 0, 3, "ladder[facing=west]")

# ---------------- cuddy cabin (x 8..12, z 1..2, floor at -1) ----------------
for x in range(8, 11):
    S(x, -1, 1, "gray_carpet")
S(8, -1, 2, "cauldron")                                       # galley sink
S(8, 0, 2, "lantern[hanging=true]")
# the chest: a big double chest against the starboard side
S(9, -1, 2, "chest[facing=north,type=left]")
S(10, -1, 2, "chest[facing=north,type=right]")
# V-berth in the bow, under a lower ceiling
for z in (1, 2):
    S(12, -1, z, "white_bed[facing=east,part=head]")
    S(11, -1, z, "white_bed[facing=east,part=foot]")

# ---------------- connect panes and walls ----------------
B = {k: v for k, v in B.items() if v != "minecraft:air"}


def base(s): return s.split("[")[0].replace("minecraft:", "")


NONFULL = ("ladder", "slab", "stairs", "pane", "wall", "door", "lantern", "carpet", "rod", "candle", "hook", "cauldron", "bed", "chest", "glass")


def is_full(s):
    if s is None: return False
    return not any(k in base(s) for k in NONFULL) or base(s) == "black_stained_glass"


dirs = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
for p, s in list(B.items()):
    b = base(s)
    fam = "pane" if b.endswith("pane") else "wall" if b.endswith("_wall") else None
    if not fam: continue
    props = {}
    for d, (dx, dz) in dirs.items():
        n = B.get((p[0] + dx, p[1], p[2] + dz))
        nb = base(n) if n else ""
        con = is_full(n) or (fam == "pane" and nb.endswith("pane")) or (fam == "wall" and nb.endswith("_wall"))
        props[d] = ("low" if con else "none") if fam == "wall" else ("true" if con else "false")
    if fam == "wall": props["up"] = "true"
    B[p] = "minecraft:" + b + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"

pal = sorted(set(B.values()))
pidx = {s: i for i, s in enumerate(pal)}
flat = []
for (x, y, z), s in sorted(B.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
    flat += [x, y, z, pidx[s]]
out = {
    "name": "ZEPHYR",
    "length": L, "beam": W,
    "palette": pal,
    "blocks": flat,
    "hull": [[x, zr(x)[0], zr(x)[1], bottom(x)] for x in range(L)],
    "holds": [],
    "holdZ": [1, 2], "holdFloorY": 0, "coamingTopY": 0,
    "tanker": False,
    "seats": {
        "aft": {"pos": [4.5, 0.95, 2.5], "yaw": 0},     # the driver, at starboard
        "bow": {"pos": [4.5, 0.95, 1.5], "yaw": 0}      # the co-driver, who can take the throttle too
    },
    "helmZones": {
        "aft": [4, 0, 2, 5, 1, 2],
        "bow": [4, 0, 1, 4, 1, 1]
    },
}
json.dump(out, open(OUT, "w"), separators=(",", ":"))
print("blocks", len(B), "palette", len(pal))
