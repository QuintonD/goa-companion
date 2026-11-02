# Design sources

Master SVGs for all original artwork. The SVG is the source of truth; the VectorDrawable XML
is generated — never edit it by hand.

- `sigils/` — one hero sigil per hero id (24-unit grid, 2-unit round stroke).
- `avatars/` — 24 player crests `crest_00..23` (same grid and stroke).
- `icons/` — UI icons not covered by Material Symbols (`nav_*` bottom bar, `brand_mark`).
- `brand/` — app icon layers (108-unit adaptive-icon grid, safe zone r33 around 54,54).
- `tools/` — `svg2vd.py` (SVG → VectorDrawable) and `instance_fonts.py` (font instancing).
- `look/` — `render_parts.py` and its masters in `look/parts/`: the metal frames the rulebook prints too small for a phone, redrawn at high resolution (ADR-058). `python -P design/look/render_parts.py` rewrites the masters (numpy and Pillow), then `:tools:catalog-import:run` bundles them. `dashboard-frame.png` and `silhouette.png` are the rulebook's own objects upscaled, not drawn: `realesrgan-ncnn-vulkan -i <extracted>.png -o <out>.png -n realesrgan-x4plus -s 4` (release v0.2.5.0 of xinntao/Real-ESRGAN), then Lanczos down to 1600 wide.

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
