"""Builds the STELLA MARIS template (110 x 11) with the shared modern layout (like the ZEELANDIA)."""
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(here, "..", "..", "tools"))
from gen_modern import build

build({
    "name": "STELLA MARIS", "beam": 11, "holds": 4, "hold_len": 17, "length": 110,
    "floor_y": -3, "hull_bottom": -4, "lift_max": 3.0,
    "accent": "blue_concrete", "band": "blue_concrete", "cap": "smooth_quartz_slab[type=bottom]",
    "fascia": "blue_concrete", "forecastle": "blue_concrete",
    "out": os.path.join(here, "..", "src", "main", "resources", "inlandbarge", "stella_maris.json"),
})
