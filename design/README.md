# Design sources

Master SVGs for all original artwork. The SVG is the source of truth; the VectorDrawable XML
is generated — never edit it by hand.

- `sigils/` — one hero sigil per hero id (24-unit grid, 2-unit round stroke).
- `avatars/` — 24 player crests `crest_00..23` (same grid and stroke).
- `icons/` — UI icons not covered by Material Symbols (`nav_*` bottom bar, `brand_mark`).
- `brand/` — app icon layers (108-unit adaptive-icon grid, safe zone r33 around 54,54).
- `tools/` — `svg2vd.py` (SVG → VectorDrawable) and `instance_fonts.py` (font instancing).

## Authoring rules
- `viewBox="0 0 24 24"` (108 for brand), no transforms — bake coordinates.
- Only `path`, `line`, `polyline`, `polygon`, `circle`, `ellipse`, `rect` (no rx), plain `g`.
- Tinted assets (sigils, crests, nav icons) are white (`#FFFFFF`) strokes/fills; the app tints them.
- Gradients: `linearGradient`/`radialGradient` with `gradientUnits="userSpaceOnUse"` only.
- Put a `<title>` naming the motif; it doubles as the design note.

## Regenerate
From the repo root:

```
python -P -E design/tools/svg2vd.py          # write all drawables
python -P -E design/tools/svg2vd.py --check  # CI-style: fail if any drawable is stale
```

New hero: add `sigils/<id>.svg`, regenerate, then add the id to `HeroSigils` in
`core/designsystem/.../icon/GoaAssets.kt` (unknown ids fall back to a monogram).

See `../docs/DESIGN.md` for the asset policy.
