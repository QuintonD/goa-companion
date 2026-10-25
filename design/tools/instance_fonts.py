"""Instance the upstream OFL variable fonts into the static weights the app bundles.

Usage (run with `python -P -E` so nothing is imported from the download dir; keep
downloads in their own directory):
    python -P -E design/tools/instance_fonts.py <download-dir> <output-res-font-dir>

Inputs (from github.com/google/fonts, see third_party/ASSETS.md for the pinned commit):
    <download-dir>/Cinzel-VF.ttf   = ofl/cinzel/Cinzel[wght].ttf
    <download-dir>/Inter-VF.ttf    = ofl/inter/Inter[opsz,wght].ttf
    <download-dir>/CrimsonPro-VF.ttf        = ofl/crimsonpro/CrimsonPro[wght].ttf
    <download-dir>/CrimsonPro-Italic-VF.ttf = ofl/crimsonpro/CrimsonPro-Italic[wght].ttf
A family whose input is missing is skipped (so one family can be rebuilt on its own).

Outputs: cinzel_{regular,semibold,bold}.ttf, inter_{regular,medium,semibold,bold}.ttf and
crimson_{regular,semibold,italic}.ttf, subset to Latin + punctuation/symbols with every OpenType
layout feature kept (tnum, etc.). None of the fonts declares a Reserved Font Name, so modified
(instanced/subset) copies may keep their names under OFL-1.1.
"""

import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

CINZEL = {"regular": 400, "semibold": 600, "bold": 700}
INTER = {"regular": 400, "medium": 500, "semibold": 600, "bold": 700}
# Crimson Pro: the printed voice (card text, rulebook excerpts), an open Minion look-alike.
CRIMSON = {"regular": 400, "semibold": 600}
CRIMSON_ITALIC = {"italic": 400}

UNICODES = (
    list(range(0x0020, 0x007F))
    + list(range(0x00A0, 0x0250))  # Latin-1 supplement, Latin Extended-A/B
    + list(range(0x2000, 0x2070))  # general punctuation
    + list(range(0x20A0, 0x20C0))  # currency
    + list(range(0x2100, 0x2200))  # letterlike, number forms, arrows
    + list(range(0x2200, 0x2300))  # math operators
    + list(range(0x25A0, 0x2600))  # geometric shapes
)


def build(src, family, weights, out_dir, extra_axes):
    for name, wght in weights.items():
        font = TTFont(src)
        axes = {"wght": wght, **extra_axes}
        static = instancer.instantiateVariableFont(font, axes, updateFontNames=False)
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.name_languages = ["*"]
        options.notdef_outline = True
        options.glyph_names = False
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=UNICODES)
        subsetter.subset(static)
        path = os.path.join(out_dir, f"{family}_{name}.ttf")
        static.save(path)
        print(path, os.path.getsize(path))


def main():
    src_dir, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    jobs = [
        ("Cinzel-VF.ttf", "cinzel", CINZEL, {}),
        ("Inter-VF.ttf", "inter", INTER, {"opsz": 14}),
        ("CrimsonPro-VF.ttf", "crimson", CRIMSON, {}),
        ("CrimsonPro-Italic-VF.ttf", "crimson", CRIMSON_ITALIC, {}),
    ]
    for file, family, weights, extra in jobs:
        src = os.path.join(src_dir, file)
        if os.path.isfile(src):
            build(src, family, weights, out_dir, extra)
        else:
            print("skipped (missing)", src)


if __name__ == "__main__":
    main()
