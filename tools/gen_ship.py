"""Builds the HENDRIKA template: a modern inland tanker (like the ETERNITY on the photos), 13 wide and 133 long with six tanks,
using the shared modern layout (lifting wheelhouse, car with crane on the aft deck)."""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, here)
from gen_modern import build

build({
    "name": "HENDRIKA", "beam": 13, "holds": 6, "hold_len": 15, "fore_len": 12,
    "tanker": True, "floor_y": -4, "hull_bottom": -5, "lift_max": 4.0,
    "accent": "blue_concrete", "band": "white_concrete", "fascia": "blue_concrete",
    "forecastle": "blue_concrete", "tank_deck": "gray_concrete",
    "a_mast": False, "flags": False,
    "out": os.path.join(here, "..", "src", "main", "resources", "binnenvaart", "hendrika.json"),
})
