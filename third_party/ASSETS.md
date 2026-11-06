# Third-party and original assets

Every non-code asset bundled in the app, where it came from and under what terms.
Official game assets (card images, hero art) are tracked separately in
`app/src/main/assets/official/SOURCES.md`.

## Fonts (SIL Open Font License 1.1)

| Family | Files | Source | Licence |
|--------|-------|--------|---------|
| Cinzel (Natanael Gama, The Cinzel Project Authors) | `core/designsystem/src/main/res/font/cinzel_{regular,semibold,bold}.ttf` | `github.com/google/fonts` @ `47d54ca1ebb14fd864695f1d39dc734f39017c33`, `ofl/cinzel/Cinzel[wght].ttf` (sha256 `f4d83d34…0f34`) | [`fonts/OFL-Cinzel.txt`](fonts/OFL-Cinzel.txt) |
| Inter (Rasmus Andersson, The Inter Project Authors) | `core/designsystem/src/main/res/font/inter_{regular,medium,semibold,bold}.ttf` | same commit, `ofl/inter/Inter[opsz,wght].ttf` (sha256 `29160a80…9031`) | [`fonts/OFL-Inter.txt`](fonts/OFL-Inter.txt) |
| Crimson Pro (Jacques Le Bailly, The Crimson Pro Project Authors) — the printed face (ADR-042) | `core/designsystem/src/main/res/font/crimson_{regular,semibold,italic}.ttf` | same commit, `ofl/crimsonpro/CrimsonPro[wght].ttf` (sha256 `16aa9fb7…af6a`) and `CrimsonPro-Italic[wght].ttf` (sha256 `a74dc11d…2559`) | [`fonts/OFL-CrimsonPro.txt`](fonts/OFL-CrimsonPro.txt) |

The bundled files are **modified versions**: static instances (Cinzel wght 400/600/700; Inter
wght 400/500/600/700 at opsz 14; Crimson Pro wght 400/600 and italic 400) subset to Latin, punctuation, arrows, maths and geometric
shapes with all OpenType features kept (incl. `tnum`). Made with fontTools by
`design/tools/instance_fonts.py`. None of the fonts declares a Reserved Font Name, so the family
names are kept. The licence texts are also bundled as `res/raw/license_ofl_*.txt` and shown in
Settings → About → Fonts.

## Original artwork (made for this app)

All drawn by hand as SVG in `design/` and converted with `design/tools/svg2vd.py`; no third-party
or official imagery was traced or copied. Sigils are abstract emblems inspired only by each
hero's name and title.

| Asset | Master | Generated |
|-------|--------|-----------|
| 32 hero sigils | `design/sigils/<hero-id>.svg` | `core/designsystem/src/main/res/drawable/sigil_<hero-id>.xml` |
| 24 player crests | `design/avatars/crest_NN.svg` | `core/designsystem/src/main/res/drawable/crest_NN.xml` |
| Bottom-bar icons, brand mark | `design/icons/*.svg` | `core/designsystem/src/main/res/drawable/ic_*.xml` |
| App icon (foreground, background, monochrome) | `design/brand/ic_launcher_*.svg` | `app/src/main/res/drawable/ic_launcher_*.xml` |

## Generated painted art (made for this app)

Decorative scenes for the Play tab's entry plates, generated with `gpt-image-2.5` (Azure AI Foundry deployment
`gpt-image-2.5-sunburst-Global`, `tools/ui-hillclimb/hc.py imagegen`, 1536 x 1024, quality high) on 2026-11-06.
They carry no game data: no cards, portraits, symbols, boards or rules; the figures are anonymous. Official art
was passed only as a style reference (`--ref`): Arien's portrait plus Brogan's or Wasp's portrait, or the
Forgotten Island painting, all from `app/src/main/assets/official/`. Cropped to a wide banner and saved as WebP
(quality 78) in `feature/board/src/main/res/drawable-nodpi/`.

Every prompt ends with the same style line: "Painterly digital fantasy illustration in the style of the reference
art: loose confident brushwork, dramatic rim light, deep navy and teal shadows with warm bronze highlights. Wide
cinematic composition with [the subject in the right half and] calm dark space in the lower left. No text, no
letters, no numbers, no logos, no user interface, no playing cards, no game symbols, no hexagon grid."
(`play_art_computer` adds "Generic anonymous figures only, no recognisable characters.")

| File | Size | Refs | Prompt (before the style line) |
|------|------|------|--------------------------------|
| `play_art_computer.webp` | 1200 x 480 | Forgotten Island map, `play_art_online`'s source | Two armies of small anonymous armoured warriors advance toward each other across a misty sea-cliff battlefield at dusk, the left host under ember-red banners and torchlight, the right host under cold blue banners and moonlight, a ruined Atlantean temple in the distance, seen from a high vantage point. |
| `play_art_puzzles.webp` | 960 x 480 | Arien, Brogan | A hooded strategist leans over an ancient stone war table at night, a few small bronze miniature soldiers on it, one lit by a single shaft of teal light, contemplative and mysterious. |
| `play_art_sandbox.webp` | 960 x 480 | Arien, Forgotten Island map | An aerial view of a lush tropical island ringed by turquoise sea: dark basalt cliffs, jungle, sandy plains, and ancient bronze and teal ruins at the two far ends, seen from high above, morning light. |
| `play_art_online.webp` | 960 x 480 | Arien, Forgotten Island map | A glowing teal crystal beacon atop an ancient Atlantean sea tower at night, beams of light reaching across the dark ocean to distant islands where answering lights glow. |
| `play_art_trivia.webp` | 960 x 480 | Arien, Wasp | A lamplit scholar's desk in an ancient sea-side library: an open old tome with blank pages, a quill, bronze instruments, a brass magnifying lens, warm candlelight against cool blue night. |

A first "computer" scene (two champions duelling) came out too close to Arien and Brogan themselves and was
not used.

## Web app libraries (code, ADR-043)

The web app bundles three npm libraries; their licences ship in the site as
`licenses/` files, copied from `third_party/web/`:
qrcode-generator 2.0.4 (Kazuhiko Arase, MIT, `MIT-qrcode-generator.txt`) draws the codes and jsQR
1.4.0 (Apache-2.0, `Apache-2.0-jsQR.txt`) reads them from the camera. kotlinx-datetime brings
@js-joda/core 3.2.0 (BSD-3-Clause, `BSD-3-Clause-js-joda.txt`) into both bundles. All three are pinned
(`gradle/libs.versions.toml`, `kotlin-js-store/yarn.lock`) and had no known advisories on 2026-10-26
(npm's advisory database; the two findings in the lock file, `serialize-javascript` and `diff`, come with
mocha and only run the unit tests on a developer's machine).

## Material Symbols

UI icons from `androidx.compose.material:material-icons-extended` (Apache 2.0, Google).

The web app (ADR-043) draws the same game-icon glyphs in card text from SVG copies:
`web/app/src/jsMain/resources/icons/game_<token>.svg`, taken unchanged from the npm package
`@material-design-icons/svg` 0.14.15 (`filled/` and `outlined/`, the same Material Icons set; each
file's `<title>` names its source glyph), except `game_attack.svg`, the app's own sword
(`GameIcons.Sword`). Licence text: [`icons/LICENSE-Apache-2.0.txt`](icons/LICENSE-Apache-2.0.txt),
shown in the web app's About → Icons. The web app's other UI icons are original
(`web/app/src/jsMain/resources/icons/*.svg`, 24-unit grid, 2-unit stroke); it also bundles the
hero sigils from `design/sigils/` as the fallback for hero art.

## Sounds (original, made for this app)

| Asset | Master | Generated |
|-------|--------|-----------|
| "Phase over" chime (three rising bell-like notes, ~1.3 s, mono 22.05 kHz WAV) | `design/sounds/make_chime.py` (additive synthesis, no samples) | `feature/timer/src/main/res/raw/phase_chime.wav` |

In-app threshold cues are generated at runtime by `ToneGenerator`; nothing else is bundled.
