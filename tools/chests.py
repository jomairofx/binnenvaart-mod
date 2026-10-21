"""Places a big double chest at a free spot against a wall, inside a given box of a ship template."""

DIRS = {"west": (-1, 0), "east": (1, 0), "north": (0, -1), "south": (0, 1)}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}


def _air(B, p):
    return B.get(p, "minecraft:air") == "minecraft:air"


def _solid(B, p):
    s = B.get(p, "minecraft:air")
    if s == "minecraft:air":
        return False
    weak = ("ladder", "carpet", "slab", "stairs", "trapdoor", "door", "rail", "pane", "bars", "fence", "wall",
            "lantern", "torch", "candle", "flower", "button", "lever", "chain", "rod", "water")
    return not any(w in s for w in weak)


def place(B, S, xs, ys, zs, avoid=(), strict=True):
    """Tries every spot and facing; returns the spot used or None."""
    for y in ys:
        for x in xs:
            for z in zs:
                for facing, (fx, fz) in DIRS.items():
                    # the chest faces 'facing'; the wall is behind it, the right half is clockwise of facing... vanilla:
                    # for a LEFT half the other half lies in direction rotateYClockwise(facing)
                    ox, oz = DIRS[CW[facing]]
                    a, b = (x, y, z), (x + ox, y, z + oz)
                    if any(p in avoid for p in (a, b)):
                        continue
                    if not (_air(B, a) and _air(B, b)):
                        continue
                    if not (_solid(B, (a[0], y - 1, a[2])) and _solid(B, (b[0], y - 1, b[2]))):
                        continue
                    if not (_air(B, (a[0], y + 1, a[2])) and _air(B, (b[0], y + 1, b[2]))):
                        continue
                    back = [(p[0] - fx, y, p[2] - fz) for p in (a, b)]
                    front = [(p[0] + fx, y, p[2] + fz) for p in (a, b)]
                    if not all(_solid(B, q) for q in back):
                        continue
                    if not all(_air(B, q) and _air(B, (q[0], y + 1, q[2])) for q in front):
                        continue
                    # room in front of it: two free steps, so it never blocks a corridor
                    front2 = [(p[0] + 2 * fx, y, p[2] + 2 * fz) for p in (a, b)]
                    if strict and not all(_air(B, q) and _air(B, (q[0], y + 1, q[2])) for q in front2):
                        continue
                    S(*a, "chest[facing=%s,type=left]" % facing)
                    S(*b, "chest[facing=%s,type=right]" % facing)
                    return a
    return None
