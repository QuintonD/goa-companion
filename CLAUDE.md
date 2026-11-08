@AGENTS.md

## Claude Code specifics

- Project skills live in `.claude/skills/`:
  - `import-catalog` — build/refresh all hero data and images from the pinned sources.
  - `fix-hero` — targeted correction of one hero/card via the overrides file.
  - `ship-check` — full verification gate before calling a milestone done.
  - `sol-review` — independent review by gpt-6.1-sol (code, plans or UI screenshots); `council` mode adds an Opus
    subagent so two providers score the work (adapted from Theo's tstack `council`/`sol-loop`).
  - `imagegen` — painted raster assets with gpt-image, when no official asset serves (never game data).
- Use the `Plan` agent before starting a new milestone; use `Explore` for wide code sweeps.
- When generating original art, write SVG source into `design/` first, then convert to
  VectorDrawable (see `docs/DESIGN.md`). Raster art goes through the `imagegen` skill.
- boardgamegeek.com pages return 403 to WebFetch and its XML API needs auth; use
  `curl -sL -A "Mozilla/5.0" "https://api.geekdo.com/api/articles?threadid=<id>"` instead.
