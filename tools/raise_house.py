"""Makes the accommodation one block higher: everything on and above its roof (roof, fascia, rails, exhaust,
lifting wheelhouse, ladder mast) moves up one block and the walls get one more layer. The windows grow with it."""


def raise_house(B, x0=7, x1=22, roof=5, white=("minecraft:white_concrete",)):
    moved = {}
    keep = {}
    for (x, y, z), s in B.items():
        if x0 <= x <= x1 and y >= roof:
            moved[(x, y + 1, z)] = s
        else:
            keep[(x, y, z)] = s
    keep.update(moved)
    # the new wall layer: a copy of the layer under the old roof (walls, partitions, ladders), not the furniture
    for (x, y, z), s in list(B.items()):
        if not (x0 <= x <= x1 and y == roof - 1): continue
        b = s.split("[")[0]
        if s in white or b.endswith("_planks") or b.endswith("ladder") or b.endswith("concrete") or b == "minecraft:smooth_quartz":
            keep[(x, roof, z)] = s
    # taller windows: where the wall has an opening one block lower, open this layer too
    for (x, y, z), s in list(B.items()):
        if not (x0 <= x <= x1 and y == roof - 2 and s == "minecraft:air"): continue
        above = B.get((x, roof - 1, z))
        if above in white: keep[(x, roof - 1, z)] = "minecraft:air"
    B.clear()
    B.update(keep)
