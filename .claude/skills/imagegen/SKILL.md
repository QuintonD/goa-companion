---
name: imagegen
description: Generate raster art (painted scenes, backgrounds, banners, empty-state illustrations, recreations of soft official frames) with gpt-image through the project's Azure AI Foundry key. Use when the project needs an image asset that no official or existing asset covers well enough. Never for anything that carries game data.
---

# Image generation

`tools/ai/azure_ai.py image` calls `gpt-image-2.5` (deployment `gpt-image-2.5-sunburst-Global`).

## Rules (AGENTS.md, docs/DESIGN.md "Asset policy", ADR-058, ADR-066)

1. **Official assets first:** `app/src/main/assets/official/` (listed in `official/SOURCES.md`). Use a crop or resize
   when it serves.
2. **Never generate game data:** no cards, card text, hero portraits standing in for official art, deck symbols,
   icons with rule meaning, boards or rules diagrams. Generated art is decoration only: Play hub scenes, screen
   footers, backgrounds, empty states, or a faithful recreation of a frame or painting that's too soft at its size.
   Don't depict recognisable roster heroes unless the asset is meant to.
3. **Match the game's style:** pass official art as references (`--ref`, several allowed, PNG): hero portraits, card
   art, the painted map (`app/src/main/assets/official/`). Describe the look in the prompt: flat painterly brushwork,
   saturated teal and warm tones, the game's own palette. Ask for no text in the image.
4. **Look at every result** next to the official art and the current asset (Read tool) before using it. Keep the old
   one if the new one isn't clearly better and on-style.
5. **Ship it small:** crop to the use, convert to WebP at the largest size actually drawn (e.g. 960×480 for a plate),
   and put it in the module's `res/drawable-nodpi/` or the design system. Large backgrounds load through Coil.
6. **Record it:** `third_party/ASSETS.md` gets file name, size, model and deployment, date, references, and the full
   prompt. A recreation of an official part is also listed in `official/SOURCES.md`.

## Command

```
python3 -I tools/ai/azure_ai.py image <out.png> "<prompt>" --size 1536x1024 [--quality high] [--ref <official.png>]...
```

- Sizes: `1024x1024`, `1536x1024` (landscape) and `1024x1536` (portrait). Generate at the closest size, then crop.
- Keep raw outputs out of the repo: use your scratch or `~/goa-hillclimb/wp/` dir, and commit only the final WebP.

## Rate limit

gpt-image allows **4 images per minute**, shared by every process on the machine. The client spaces requests to stay
under it and, on HTTP 429, **waits a minute and retries**. Don't add your own retry loop or fan out parallel image
calls. To produce variants, run them one after another and expect about 15 s or more per image when batching.

Credentials: `~/.config/goa-companion/secrets/azure-ai.env` (see `tools/ai/azure-ai.env.example`). If it's missing,
stop and tell the user; never put the key in the repo.
