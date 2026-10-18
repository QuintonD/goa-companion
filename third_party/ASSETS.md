# Third-party and original assets

Every non-code asset bundled in the app, where it came from and under what terms.
Official game assets (card images, hero art) are tracked separately in
`app/src/main/assets/official/SOURCES.md`.

## Fonts (SIL Open Font License 1.1)

| Family | Files | Source | Licence |
|--------|-------|--------|---------|
| Cinzel (Natanael Gama, The Cinzel Project Authors) | `core/designsystem/src/main/res/font/cinzel_{regular,semibold,bold}.ttf` | `github.com/google/fonts` @ `47d54ca1ebb14fd864695f1d39dc734f39017c33`, `ofl/cinzel/Cinzel[wght].ttf` (sha256 `f4d83d34…0f34`) | [`fonts/OFL-Cinzel.txt`](fonts/OFL-Cinzel.txt) |
| Inter (Rasmus Andersson, The Inter Project Authors) | `core/designsystem/src/main/res/font/inter_{regular,medium,semibold,bold}.ttf` | same commit, `ofl/inter/Inter[opsz,wght].ttf` (sha256 `29160a80…9031`) | [`fonts/OFL-Inter.txt`](fonts/OFL-Inter.txt) |

The bundled files are **modified versions**: static instances (Cinzel wght 400/600/700; Inter
wght 400/500/600/700 at opsz 14) subset to Latin, punctuation, arrows, maths and geometric
shapes with all OpenType features kept (incl. `tnum`). Made with fontTools by
`design/tools/instance_fonts.py`. Neither font declares a Reserved Font Name, so the family
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

## The hub's web app (`server/hub`)

| Item | Files | Source | Licence |
|------|-------|--------|---------|
| htmx 2.0.11 (Big Sky Software) | `server/hub/src/main/resources/static/htmx.min.js` | npm `htmx.org@2.0.11`, `dist/htmx.min.js` (tarball sha512 `Thx/WtpeOQqS…tamA==` as published; file sha256 `d6fdc75f…f717`), unmodified | 0BSD, [`htmx/LICENSE`](htmx/LICENSE) |
| Material Symbols (Outlined, 24 px): arrow_back, settings, menu_book, quiz, close, add, chevron_right, download, error, info, check, delete, edit, share, notifications, logout, devices, group, search | `server/hub/src/main/resources/static/material/*.svg` | `github.com/google/material-design-icons` @ `49d4db35df873165d6bd6ba09b063c7dafbac2f4`, `symbols/web/<name>/materialsymbolsoutlined/<name>_24px.svg`, unmodified | Apache 2.0, [`material-symbols/LICENSE`](material-symbols/LICENSE) |
| Material Symbols for the Group and Settings pages: qr_code_2, content_copy, upload, person | `server/hub/src/main/resources/static/material/*.svg` | same commit and path as above, unmodified | Apache 2.0, [`material-symbols/LICENSE`](material-symbols/LICENSE) |

The hub also serves, from the build (no copy in git): the fonts and licences above, our crests,
sigils, icons and launcher SVGs from `design/`, and the official catalog files (signed-in
devices only). Its manifest PNG icons are rendered at run time from `design/brand/` by Java2D.

## Sounds (original, made for this app)

| Asset | Master | Generated |
|-------|--------|-----------|
| "Phase over" chime (three rising bell-like notes, ~1.3 s, mono 22.05 kHz WAV) | `design/sounds/make_chime.py` (additive synthesis, no samples) | `feature/timer/src/main/res/raw/phase_chime.wav` |

In-app threshold cues are generated at runtime by `ToneGenerator`; nothing else is bundled.
