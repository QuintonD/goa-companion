@AGENTS.md

## Claude Code specifics

- Project skills live in `.claude/skills/`:
  - `import-catalog` — build/refresh all hero data and images from the pinned sources.
  - `fix-hero` — targeted correction of one hero/card via the overrides file.
  - `ship-check` — full verification gate before calling a milestone done.
- Use the `Plan` agent before starting a new milestone; use `Explore` for wide code sweeps.
- When generating original art, write SVG source into `design/` first, then convert to
  VectorDrawable (see `docs/DESIGN.md`). You have no raster image generation.
- boardgamegeek.com pages return 403 to WebFetch and its XML API needs auth; use
  `curl -sL -A "Mozilla/5.0" "https://api.geekdo.com/api/articles?threadid=<id>"` instead.
