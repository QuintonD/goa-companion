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
`gpt-image-2.5-sunburst-Global`, `tools/ui-hillclimb/hc.py imagegen`, 1536 x 1024, quality high) on 2026-11-07,
replacing a first, glossier set of 2026-11-06 that both judges found too photographic beside the official art.
They carry no game data: no cards, portraits, symbols, boards or rules; the figures are anonymous. Official art
was passed only as a style reference (`--ref`, two or three each): hero portraits (`official/heroes/<id>/portrait.webp`)
and the board paintings (`official/maps/<id>.webp`), all from `app/src/main/assets/official/`, converted to PNG.
Cropped to a wide banner (the row of the source given below) and saved as WebP (quality 78) in
`feature/board/src/main/res/drawable-nodpi/`.

Every prompt ends with the same style line: "Hand-painted fantasy concept art in exactly the style of the reference
paintings: loose, visible, flat painterly brush strokes, simplified shapes, soft airy hazy backgrounds with large calm
areas, detail only at the focal point, saturated teal and turquoise against warm red, orange and bronze accents, matte
finish. Not photographic, not glossy, not a 3D render, no lens flare, no bokeh, no heavy dark vignette. Wide
composition with calm, simple space in the lower left. No text, no letters, no numbers, no logos, no user interface,
no playing cards, no game symbols, no hexagon grid. Generic anonymous figures only, no recognisable characters."

| File | Size (rows of the 1024) | Refs | Prompt (before the style line) |
|------|------|------|--------------------------------|
| `play_art_computer.webp` | 1200 x 480 (260–874) | Brogan, Garrus, Forgotten Island map | Two small hosts of anonymous armoured warriors charge toward each other across a windswept sea-cliff meadow, the left host under ember-red banners, the right host under teal-blue banners, dust and spray between them, a pale ruined Atlantean temple on a far headland, seen from a raised vantage point in bright hazy daylight. |
| `play_art_puzzles.webp` | 960 x 480 (110–878) | Arien, Hanu | Close view of an ancient stone strategy table on a sunlit terrace: a few small bronze and teal miniature warriors stand on it, one hand of an anonymous player in a red sleeve reaching to move one, the rest of the table in soft haze, a shaft of teal light across it. |
| `play_art_sandbox.webp` | 960 x 480 (128–896) | Forgotten Island map, Magma and Ice map, Arien | A bird's-eye view of a small lush island in a turquoise sea: dark basalt cliffs, green jungle, pale sandy plains and two bronze and teal ancient ruins at the far ends, painted loosely like the reference map, bright daylight. |
| `play_art_online.webp` | 960 x 480 (150–918) | Wasp, Arien, Forgotten Island map | A slender ancient Atlantean sea tower on a rocky islet with a glowing teal crystal at its top, beams of light reaching across a hazy turquoise sea to distant islands where small answering lights glow, at dusk with a warm orange sky. |
| `play_art_trivia.webp` | 960 x 480 (200–968) | Dodger, Hanu | A scholar's desk in a bright ancient sea-side library: an open old tome with blank pages, a quill, a brass astrolabe and a magnifying lens, warm orange light from a tall window, teal sea visible outside. |
| `play_art_gamenight.webp` | 960 x 480 (170–938) | Brynn, Hanu | A warm evening gathering in an ancient Atlantean seaside hall: four anonymous friends seen from behind and the side lean around a sturdy wooden table covered with a cloth, small bronze and teal miniature warriors standing on it, a brass hourglass with glowing sand in the middle of the table, a hanging lantern casting warm orange light, a tall arched window behind them opening onto a calm teal sea at dusk. (Generated 2026-11-07, round 3.) |

In the first set, a "computer" scene of two champions duelling came out too close to Arien and Brogan themselves
and was not used.

Two foot paintings (`ScreenFoot`, 2026-11-07), so leaderboards and group sync stop ending in the rulebook's
battlefield: same model and size, refs the rulebook's battlefield silhouette (`official/ui/silhouette.webp` on a flat
navy, PNG) and Arien's portrait. The flat navy was keyed out to transparency and the result cropped to its content and
saved at 1200 px wide as WebP (quality 80) in `core/designsystem/src/main/res/drawable-nodpi/`. Both prompts end with:
"Painted in exactly the style of the first reference image: a dark painted silhouette scene rising from the bottom
edge, loose visible flat painterly brush strokes, simplified shapes, muted dark rust-brown, slate and deep teal tones
with only small touches of light, matte finish. The whole upper half and all space around the objects is one single
perfectly flat, uniform, empty dark navy blue colour (#283C5A) exactly like the first reference's background, with no
gradient, no stars, no clouds, no texture. Not photographic, not glossy, not a 3D render, no lens flare, no vignette.
No text, no letters, no numbers, no logos, no user interface, no playing cards, no game symbols, no hexagon grid, no
people, no faces."

| File | Size | Prompt (before the style line) |
|------|------|--------------------------------|
| `foot_hall.webp` (leaderboards) | 1200 x 751 | Along the bottom edge, the stone steps of an ancient Atlantean hall of honour: a low stepped dais with a bronze laurel-wreath trophy on a short pedestal at its centre, flanked by a row of tall banner poles of different heights from which long ragged hanging pennants fall, some ember red, some teal blue, some dull bronze, a broken column at one side. The objects occupy only the lower 45 percent of the picture, wider on the right. |
| `foot_beacons.webp` (group sync) | 1200 x 491 | Along the bottom edge, a dark rocky sea coast: on two separate headlands stand two slender ancient Atlantean stone beacon towers, each with a small teal flame burning in a brazier at its top; between them three small dark messenger birds fly from one tower toward the other, low over calm dark water. The rocks and towers occupy only the lower 45 percent of the picture (the birds may be a little higher), the taller tower on the right. |

Round 4 of the hill climb (2026-11-07): the backgrounds judged soft were painted again at full resolution, each
compared with the one it replaces before the swap (all three were clearly sharper and on style). Same model; sizes are
the generated canvas, then each saved as WebP (quality 80 to 82). The two foot paintings above were generated again
with their own prompts and style line at 2400 x 1600 (refs as before), keyed and cropped as before and saved 2000 px
wide (`foot_hall.webp` 2000 x 1094, `foot_beacons.webp` 2000 x 1019): the 1200 px cuts were upscaled on a 1440 px
phone. The keying now takes only the ground joined to the empty sky (and gaps of nearly exact ground), so the
painting's own blue-grey tones stay opaque.

| File | Size | Refs | Prompt |
|------|------|------|--------|
| `timer_sea.webp` (portrait), `timer_sea_wide.webp` (landscape), `core/designsystem/src/main/res/drawable-nodpi/` | 1440 x 2160, 2160 x 1440 | the old `official/ui/sea.webp` (a 411 px crop of the Forgotten Island painting) and the Forgotten Island map | The open sea seen straight from above, filling the whole picture edge to edge: deep turquoise and teal water with long gentle diagonal swells, soft lighter streaks where light catches the ripples and slightly deeper blue troughs, the same water as around the island in the second reference image. Painted in exactly the style of the references: loose, visible, flat painterly brush strokes, crisp at full resolution, matte finish, the palette of the first reference. Only water: no boats, no ships, no wreckage, no rocks, no land, no islands, no creatures, no foam patches, no shadows of objects. Even, calm detail across the whole picture, no vignette. Not photographic, not glossy, not a 3D render, no text, no letters, no logos, no user interface. |
| `official/ui/silhouette.webp` (master `design/look/parts/silhouette.png`; it replaces the Real-ESRGAN upscale of the rulebook cover's object 8940, so `official/SOURCES.md` names it too) | 2400 x 1456, keyed, saved 2000 x 1213 | the upscaled silhouette on its flat navy | Along the bottom edge, the aftermath of a battle as a dark painted silhouette scene, recreating the first reference image faithfully: a low rubble mound of rust-brown earth and slate rocks running across the lower part of the picture, broken swords and spears driven upright into it at different angles, a hanging rusted chain on one blade, torn ragged pennants on two tall spears at the right, a fallen dented blue-steel helmet and armour half buried on the right, a broken shield rim at the lower left. Same composition, same shapes and the same muted dark rust-brown, slate and deep blue palette as the first reference, painted at full resolution with crisp, deliberate, loose painterly brush strokes and clean silhouette edges, matte finish. The whole upper part and all space around the objects is one single perfectly flat, uniform, empty dark navy blue colour (#283C5A) exactly like the reference's background, with no gradient, no stars, no clouds, no texture. Not photographic, not glossy, not a 3D render, no lens flare, no vignette. No text, no letters, no numbers, no logos, no user interface, no people, no faces. |

The timer no longer draws `official/ui/sea.webp` (the import still makes it, and the web's timer still uses it).

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
