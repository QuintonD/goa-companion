"""The web app's fonts (ADR-043 "Addendum (R3 first load)"): WOFF2 subsets of the Android app's TTFs.

The phone bundles the full TrueType files (core/designsystem/src/main/res/font). A browser on a phone
downloads them on its first visit, so the web app ships them as WOFF2 (Brotli inside), cut down to the
scripts the app can show: Latin (with Latin Extended for players' names), Greek, and the punctuation,
arrows, maths and symbol blocks the catalog, rules and screens use. Hinting is dropped (iOS ignores
it); every OpenType layout feature (kerning, tabular figures, ligatures) is kept. A character outside
the subset (a Cyrillic name, say) falls back to the system font. All three families are OFL 1.1
without Reserved Font Names, so a subset keeps its name (third_party/fonts/OFL-*.txt).

Run it again whenever a TTF changes; `:web:app:webAssets` refuses to build while `sources.sha256` no
longer matches the TTFs:

    python -m pip install fonttools==4.63.0 brotli==1.2.1
    python web/app/fonts/subset_fonts.py
"""

import hashlib
import pathlib

from fontTools import subset

HERE = pathlib.Path(__file__).resolve().parent
SOURCE = HERE.parents[2] / "core" / "designsystem" / "src" / "main" / "res" / "font"

UNICODES = (
    "U+0000-024F,U+0259,U+02B0-02FF,U+0300-036F,U+0370-03FF,U+1D00-1DBF,U+1E00-1EFF,"
    "U+2000-206F,U+2070-209F,U+20A0-20CF,U+2100-214F,U+2150-218F,U+2190-21FF,U+2200-22FF,"
    "U+2300-23FF,U+2460-24FF,U+25A0-25FF,U+2600-26FF,U+2700-27BF,U+2B00-2BFF,U+FB00-FB06,"
    "U+FEFF,U+FFFD"
)


def main() -> None:
    lines = []
    for ttf in sorted(SOURCE.glob("*.ttf")):
        out = HERE / (ttf.stem + ".woff2")
        subset.main([
            str(ttf),
            f"--unicodes={UNICODES}",
            "--layout-features=*",
            "--flavor=woff2",
            "--no-hinting",
            f"--output-file={out}",
        ])
        lines.append(f"{hashlib.sha256(ttf.read_bytes()).hexdigest()}  {ttf.name}")
        print(f"{ttf.name}: {ttf.stat().st_size} -> {out.name}: {out.stat().st_size}")
    (HERE / "sources.sha256").write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
