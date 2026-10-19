import json, sys
f, ns = sys.argv[1], sys.argv[2]
d = json.load(open(f))
d[f"key.{ns}.lift_up"] = "Wheelhouse up"
d[f"key.{ns}.lift_down"] = "Wheelhouse down"
d[f"key.{ns}.lights"] = "Ship lights on/off"
json.dump(d, open(f, "w"), indent=2, ensure_ascii=False)
