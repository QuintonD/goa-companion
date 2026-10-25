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
