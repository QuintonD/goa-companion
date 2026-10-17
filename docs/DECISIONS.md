# Decisions (ADR log)

Append-only. Format: `## ADR-NNN — Title (YYYY-MM-DD)` · Context · Decision · Consequences.
Supersede rather than edit.

## ADR-001 — Native Kotlin + Compose (2026-10-17)
**Context:** Android-only, polished, offline app with timers that must survive backgrounding.
**Decision:** Native Kotlin, Jetpack Compose, Material 3 with a custom theme.
**Consequences:** Best platform integration (foreground service, haptics, widgets later);
no iOS without a rewrite or KMP migration (core:model/domain kept pure Kotlin to keep that open).

## ADR-002 — Offline-first, no backend in v1 (2026-10-17)
**Context:** Play groups share one phone; accounts add cost, privacy and moderation burden.
**Decision:** All data local (Room/DataStore). Sharing via JSON export/import.
**Consequences:** No cross-device live sync in v1; data model uses UUIDs so a sync layer can
be added later without migration pain.

## ADR-003 — Ratings derived from match log (2026-10-17)
**Context:** Users will edit and delete past matches.
**Decision:** Elo and meta stats are recomputed by replaying the ordered match log; any cache
is disposable.
**Consequences:** Edits are always consistent; recompute must stay fast (target < 1 s / 1,000 matches).

## ADR-004 — Original assets, paraphrased card data (2026-10-17) — SUPERSEDED by ADR-006
**Context:** Card text and art are Wolff Designa's IP; the name is their trademark.
**Decision:** Ship only original visuals and structured stats + paraphrased summaries; show an
unofficial-fan-app disclaimer. No Play Store publication without an explicit owner decision.
**Consequences:** More design work (sigil set); lower legal risk.

## ADR-005 — Multi-module Now-in-Android-style layout (2026-10-17)
**Context:** Four independent features, agent-driven development, parallel work.
**Decision:** `core/*` + `feature/*` modules with convention plugins.
**Consequences:** Clear boundaries for parallel agents and faster incremental builds; slightly
more setup in M0.

## ADR-006 — Private build; official sources and assets allowed (2026-10-17)
**Context:** The owner confirmed the app is for local, personal use only and will never be
sold or published.
**Decision:** Use official sources freely: verbatim card text, official hero art, card images
and game iconography may be bundled. Our own UI/brand assets stay original. Official files
live in `assets/official/` with a `SOURCES.md` record. No public remote, no store release.
**Consequences:** Much richer catalog with far less design work. If the app is ever
distributed, ADR-004's restrictions must be reinstated and `assets/official/` removed or licensed.

## ADR-007 — Timer uses exact alarms + countdown notification, no foreground service (2026-10-17)
**Context:** The timer must stay correct while locked/backgrounded. `shortService` caps at ~3 min;
`specialUse` works but keeps a process alive for nothing.
**Decision:** Persist timer state; derive remaining time from `elapsedRealtime`; schedule phase ends
with `setExactAndAllowWhileIdle` (`USE_EXACT_ALARM`); show a chronometer countdown notification
(promoted Live Update on Android 16+); alerts use `USAGE_ALARM`. Details in ARCHITECTURE.md.
**Consequences:** No long-running service; survives process death. Continuous background audio
(ticking, TTS) would need a `specialUse` service — new ADR if ever wanted.

## ADR-008 — Toolchain baseline: AGP 9, Navigation 3, Room 3 (2026-10-17)
**Context:** New project; verified current stable releases on 2026-10-17.
**Decision:** AGP 9.4 with built-in Kotlin 2.4, KSP only, compile/targetSdk 37, Navigation 3 1.2,
Room 3 (`androidx.room3`), Vico 3, Coil 3, Roborazzi (Google's preview screenshot testing is still
alpha). detekt 2.0 alpha is the only line built for Kotlin 2.4/AGP 9; if it causes friction, drop
detekt and rely on Android Lint + ktlint rather than downgrading Kotlin.
**Consequences:** Old Now-in-Android snippets need adapting to the AGP 9 DSL. Revisit screenshot
tooling when AGP 9.5 test suites are stable.

## ADR-009 — Catalog built by an import pipeline from online sources, all products (2026-10-17)
**Context:** Owner wants every hero from every product; complete card data and images exist
online (fan/online-play implementations, BGG). Hand entry of ~900 cards is slow and error-prone.
**Decision:** `tools/catalog-import` fetches and normalises sources at dev time into the bundled
JSON + images, with a hand-maintained overrides file for corrections. Generated files are never
hand-edited. The catalog is read-only and held in memory (no Room tables). Data targets the
current (2nd) printing; `printing` is recorded per hero.
**Consequences:** Re-runnable when new heroes or a 3rd printing appear; correctness depends on
source quality, so validation tests + a per-run report are mandatory.

## ADR-010 — Build environment and module additions (2026-10-17)
**Context:** Scaffolding M0 on the owner's PC. C: has < 5 GB free; only the Android Studio JBR
21 is installed; Gradle 9.8.1 is current; cross-feature navigation needed a shared home.
**Decision:** Gradle **9.8.1** wrapper (supersedes "9.6" in ADR-008), AGP 9.4.1, Kotlin 2.4.21.
Bytecode targets Java 17 but the build runs on JBR 21 (no separate JDK 17 toolchain). Builds use
`GRADLE_USER_HOME=D:\.gradle-home` and `TMP/TEMP=D:\.tmp-build` to keep caches off C:. Added
`core:navigation` (all Navigation 3 keys + `Navigator`, so features can open each other's screens
without depending on each other) and `feature:settings` (settings, ratings config, About).
Robolectric unit tests need `--add-opens java.base/jdk.internal.access` on JDK 21 (convention plugin).
Roborazzi baselines live in each module's `src/test/screenshots/`.
**Consequences:** Anyone building must set the same `GRADLE_USER_HOME` (or free space on C:).

## ADR-011 — Seat-based Elo with double piloting (2026-10-17)
**Context:** The group often plays odd counts with one player double-piloting two heroes; the
owner wants this "and more" reflected in ratings.
**Decision:** Ratings are computed over hero *seats*. Team strength is the mean seat strength,
where a seat blends pilot and hero ratings (`heroInfluence`, `pilotInfluence`), plus an optional
`seatAdvantage` for uneven seat counts. Double pilots are updated once, weighted by
`doublePilotWeight`. All constants in `EloConfig`, user-editable, default values in DOMAIN.md.
**Consequences:** Still a pure replay of the match log (ADR-003); changing constants recomputes
history. Plain Elo is recovered by setting both influences and the weights to 0.

## ADR-012 — House draft as default, methods as data (2026-10-17)
**Context:** The group's usual draft: pool of 15, A ban+pick, B ban+2 picks, alternate pairs,
single last pick. The official methods must also be available.
**Decision:** Draft methods are data (`draft-methods.json` + user-saved methods in DataStore
JSON, with the in-progress session) run by one `DraftEngine`. The House draft is the default; official methods are presets.
**Consequences:** New formats need no code. The engine is property-tested for every bundled
method × 2–10 players × seat layouts (incl. double pilots).

## ADR-013 — In-house SVG → VectorDrawable converter (2026-10-17)
**Context:** Original art (32 hero sigils, 24 crests, nav icons, app icon) is hand-authored SVG
(DESIGN.md). Android Studio's importer is manual; `vd-tool` isn't shipped with the SDK here.
**Decision:** `design/tools/svg2vd.py` converts the restricted SVG subset we author (paths,
lines, circles, linear gradients) to VectorDrawable XML; `--check` fails if generated files are
stale. Generated XML carries a "do not edit" header; the SVG is the master.
**Consequences:** Reproducible, scriptable asset pipeline; SVG features outside the subset must
be avoided or the converter extended.

## ADR-014 — Timer re-sync via a feature-owned startup provider (2026-10-17)
**Context:** Alarms and the countdown notification must be re-synced on every app start
(ADR-007) without `feature:timer` editing `app`.
**Decision:** `feature/timer` declares a small `TimerStartupProvider` (ContentProvider) in its
own manifest that re-syncs persisted timer state at process start; boot/time/package-replaced
receivers cover the rest. Timer clocks are injected with a `@TimerClock` qualifier.
**Consequences:** Features stay independent of `app`; one extra provider runs at startup (cheap).

## ADR-015 — Printed "+" values and bold keywords in the catalog schema (2026-10-17)
**Context:** Cards print some values with a `+` (Attack `4+`: the value grows per the card
text) and set timing/choice keywords in bold (`**This turn:**`). Both were lost on import (38
and 186 cards), which hurts rules accuracy.
**Decision:** `CardValues.plus: List<Stat>` (additive, omitted from JSON when empty) holds the
stats printed with `+`; the number stays the printed base. `CardText` gains `**…**` →
`Segment.Strong` (never spans a paragraph; unmatched markers are literal; `plain()` drops
markers). Both come from source 1; `+` is cross-checked against colefin8. goa2-faq bold is
ignored: where its paragraphing is used, source-1 bold is copied word by word, and if a bold
span would not fit, the source-1 text is kept (noted in REPORT.md).
**Consequences:** Older catalog JSON still loads; every value display shows `4+` and speaks
"4 plus"; card text renders bold keywords. The importer bumps `dataVersion`.

## ADR-016 — Tie Breaker coin carried from draft to match; house-rule counters labelled (2026-10-17)
**Context:** The draft flips the Tie Breaker coin (Team A), but Match setup ignored it and asked
the table to flip again, contradicting the rulebook (p.5 step 3: flipped once at setup, before
heroes are chosen; p.7: re-flipped only after a cross-team initiative tie). Separately, the
owner's double-pilot house rule counts counters by heroes in play, which at 5 and 7 players
shows numbers (and a Double lane "requirement") the rulebook's per-player rules don't give,
without saying so.
**Decision:** `Match` gains a nullable `tieBreaker` (the coin as placed at setup).
`DraftEngine.toPendingMatch` fills it from Team A; Match setup shows it as already placed with
"Correct the coin" (flipping is offered only without a draft), starts the timer with it and
saves any correction. Room schema v2 adds `matches.tie_breaker` with a manual, non-destructive
migration (existing rows stay null: their coin was never recorded); the backup format goes to
v2 so an older app refuses a file instead of silently dropping the coin, and v1 files still
import losslessly. The heroes-in-play lookup stays (owner's choice); `match-setup.json` also
carries the rulebook's per-player values and `MatchSetup.houseRuleDifference` drives "House
rule" labels wherever the two disagree.
**Consequences:** One coin from draft to timer, recorded per match for later use. Rules data
stays the single source of every number and label. Older matches have no coin (shown as
unknown); flips during play are not tracked on the match.

## ADR-017 — Draft: the setup form lives in DataStore and becomes the next game's default (2026-10-17)
**Context:** The draft setup wizard (players, teams, method, pool, coin) lived in a ViewModel
`MutableStateFlow`. Navigation 3 clears an entry's ViewModel when the entry leaves the displayed
back stack (tab switch, Back out of the tab), so the form was lost on every tab change, Back
from the board and process death, and the second game of the night cost about 25 taps.
**Decision:** `SetupForm` is `@Serializable` and is written to `DraftStore` (`setup_form`) on
every change; the ViewModel reads it on creation and drops players deleted since. After a draft
starts, the form becomes `forNextGame()`: same players, teams, captains, method and pool, a fresh
coin, stage back to Players, marked "Same players and teams as last game" with a Clear action.
`SavedStateHandle` was not used: it is cleared together with the ViewModel on a tab switch, so it
would not cover the main case, and DataStore already covers process death.
**Consequences:** The form survives whatever the shell does with tab state. Writes are small
(one JSON string per edit). The transient `starting` flag is not persisted.

## ADR-018 — Timer: Preset revisions, per-style alert channels, one-level undo (2026-10-17)
**Context:** QA found red "overtime" during every reveal/resolve, an ongoing notification hidden
on the lock screen, a 60 s looping alarm that ignored Settings → Sounds, and accidental Next taps
that lost the phase's time. Users already have stored copies of the bundled presets.
**Decision:** (1) `PhaseKind.RESOLVE` (untimed, per turn) is added to the bundled Table rules and
Relaxed presets; `TimerPreset.revision` lets `TimerPresetMigration` upgrade stored copies of a
bundled preset on read with additive, targeted changes only (user-made presets untouched). (2)
The countdown moves to a new default-importance silent public channel; the phase-over alert uses
a bundled original chime on alarm usage, with one channel per Sounds × Haptics combination
because channel sound is immutable. (3) The engine keeps one `UndoPoint` per Next (Back within
15 s restores it with time carried over) and a `stepEnteredAt` stamp for a 500 ms Next debounce;
both, the "Hurry up" shot clock and team time-outs live in the snapshot and are rebased with it.
**Consequences:** Four alert channels appear in system settings. A future data change to bundled
presets bumps `revision` and adds a migration step. Snapshots from older versions decode with
defaults (no undo, no shot clock).

## ADR-019 — Rules: a verbatim rules reference with a drift check against the pinned rulebook (2026-10-17)
**Context:** Players' most-asked feature is fast rule lookup at the table, above all the level
and defeat arithmetic (Life counters, coins, assist coins, level costs) that even experienced
groups get wrong. Rules accuracy is non-negotiable; forum quotes often predate the current
rulebook, and the PDF's two-column text layer can't be trusted blindly. A 3rd printing (2027)
will reword the rules.
**Decision:** `assets/catalog/rules-reference.json` holds (a) the level ladder as data with page
refs (the p.13, p.15 and p.23 tables) and (b) quick-reference topics made **only of verbatim
excerpts** with printed page numbers; anything in our own words is a `summary`, always shown
labelled "Summary". The rulebook PDF is pinned in `sources.lock.json` (`documents`) by URL +
SHA-256 and cached like the other sources; `tools/catalog-import --rules-only` (and every
import) extracts each page's text layer with PDFBox and fails unless every excerpt, heading and
table row appears on its page after whitespace/dash/quote/case normalisation. Passages the text
layer scrambles can be confirmed by hand in `rules-confirm.json`, tied to the reviewed PDF's
hash (none needed today). Cards that change a defeat are found by a catalog text query at
runtime and quoted, never applied. New `feature:rules` module; the ladder opens as a Navigation 3
bottom-sheet scene (`NavSheet` metadata, `BottomSheetSceneStrategy` in `app`), so the timer can
show it without depending on the feature.
**Consequences:** A reworded or replaced rulebook fails loudly (hash first, then every changed
excerpt) instead of drifting. The JSON is hand-edited but machine-checked. The check ignores
hyphens and dashes, so a flipped sign (+1/−1) is the one change it can't see; the ladder's
numbers are also guarded by unit tests that re-derive them from the rules they summarise.

## ADR-021 — Catalog: card-level search, derived attack style, printed colours (2026-10-17)
**Context:** QA: searching card text returned heroes (one card each), so "block" hid most Block
cards and "fast travel" ranked Emmitt (title "Traveler" + card "Fast Forward") first; the
"Melee attack" label came from the Gold card alone and called Swift the Sharpshooter melee;
Handicap cards and spellbook pages were stored as colour `other`; mid-sentence line breaks
were patched card by card.
**Decision:** (1) `CatalogSearchIndex.searchCards` returns every matching card with a
`MatchQuality` (hero name/alias/title > card phrase > card with all words > partial hero
match); the Heroes tab lists cards ahead of heroes whenever the best card beats the best hero,
filtered by the same hero filters and owned products. (2) Attack style is derived from all of
the hero's deck Attack cards (DOMAIN.md), never from one card. (3) The importer keeps printed
frame colours for Handicap cards and spells, and joins goa2-faq line wraps with a punctuation
heuristic instead of per-card overrides. (4) The grid/list choice is an `AppSettings` field
(`catalogListView`) so it survives restarts and backups of settings.
**Consequences:** `dataVersion` 3. Card text overrides are only for real wording fixes. Code
that needs to know a card is a Handicap card or spell uses its slot/ID, not `CardColor.OTHER`.

## ADR-020 — Shell+records: per-tab state, timer awareness, full backup, rematch, group stats (2026-10-17)
**Context:** QA found the game night broke at the seams: switching tabs reset half-filled
forms (the shell gave `NavDisplay` one back stack at a time, so the Nav3 decorators treated the
other tabs' entries as popped and cleared their state and ViewModels); notification taps opened
Heroes; nothing outside the timer showed a running game; logging a result was a generic form
with the winner two screens down and no feedback; pending matches were dead ends; backups
dropped settings, presets and custom methods and accepted ghost players; the Meta tab was ten
screens of cards.
**Decision:**
1. *Tab state:* each tab's back stack gets its own saveable-state and ViewModel-store
   decorators via `rememberDecoratedNavEntries`, composed every frame; `NavDisplay` takes the
   current tab's decorated entries. Content keys are prefixed with the tab. State is cleared
   only on pop. For shell decisions a bottom-sheet route (`NavSheet`) counts as the screen
   below it.
2. *Timer awareness:* the timer notification's content intent carries `EXTRA_OPEN_TAB`
   (core:navigation); `MainActivity` turns it, and a cold start with an active session, into
   "open the Timer tab at its root". `ShellViewModel` reads `TimerController.snapshot` (never
   writes) for a compact running-timer bar on other screens, which sets `FLAG_KEEP_SCREEN_ON`
   while running. The bottom bar is hidden on the running timer and the draft board: both are
   table tools whose main buttons sit where the tabs are; system back leaves them.
3. *Results:* `MatchEditRoute(result, prefillPlayedAt)` gives the "Who won?" layout; after a
   new result the editor shows rating deltas, ranks and Rematch. `Navigator.showMessage` gives
   app-wide snackbars (the draft's "Log result later").
4. *Rematch* is pure domain (`Rematch`, DOMAIN.md); "Draft again" goes through a new core:data
   contract `DraftSetupPrefill`, implemented by the draft feature on its persisted setup form
   (ADR-017) plus a push to a setup that is already open.
5. *Backup v3:* contributors for settings, timer presets and draft methods live in core:data
   and carry each store's own JSON (the owning features keep their schemas and migrations);
   `appVersion` filled; Merge keeps the phone's settings unless asked; Replace is preselected on
   an empty phone; a missing pilot rejects the file; unknown heroes import as Unknown hero.
6. *Group stats* (`GroupStats`) are computed with the ratings snapshot from the same rated
   matches (ADR-003) and stored nowhere.
**Consequences:** Every tab's screens stay alive until popped (a few more ViewModels in memory;
their flows stop 5 s after leaving). The app reads the timer controller directly (app already
depends on every feature). Older builds refuse a v3 backup instead of silently dropping its
settings.


## ADR-022 — Rules review: card-text starting hands, verbatim tables, derived Handicap labels (2026-10-17)
**Context:** An adversarial review of the rules-facing changes against the rulebook found the
draft peek dealing Takahide 7 starting cards (his extra golds print "(This card starts the game
in your deck.)"), excerpts cut before the sentence that limits them (card text applies only on
the Primary action, p.7), the p.20 double-lane counter table missing because excerpts could
only hold prose, and the Handicap tile stating "Replaces the Silver card" although no Handicap
card prints what it replaces.
**Decision:** (1) `StartingCards` in `core:domain` is the one place that selects a starting hand
(p.6 by tier and colour, minus basic cards whose own text starts them in the deck, p.3); the
draft peek and the catalog's "Starts in hand/deck" markers both use it, and a test checks every
shipped hero has exactly Gold, Silver and three Tier I. (2) `RulesExcerpt.table` holds a printed
table verbatim (header row first); the drift check requires its cells row by row on the page.
(3) A callout box printed inside a passage is its own excerpt, in printed order, so every
excerpt stays one contiguous passage. (4) What a Handicap card weakens is shown as derived
("Weaker version of the Silver card (frame colour)"); where the frame isn't gold (Takahide) the
hero page marks the 3-player rule ("replaces a gold card", p.20) as unresolved instead of
choosing. Cards have no per-card `verified` flag and the card data itself is right, so no
override was needed.
**Consequences:** One more optional field in `rules-reference.json` (older app builds would
reject a file using it, but the JSON ships with the app). The Takahide question stays open in
the UI until an official ruling exists; recording one goes through `fix-hero`/FAQ data.

## ADR-023 — Final data fixes: logged-result handoff, Block label, game nights, kind words in search (2026-10-17)
**Context:** The final hands-on test found Back on "Who won?" (opened from the timer's Log
result) threw the finished match and its duration away without asking; Takahide's Handicap
"Shameful Display" was labelled Block although it has no Block condition; "Last game night"
split a night at midnight; "brogan ultimate" found nothing.
**Decision:** (1) Without a linked match, Log result reserves a match id in the ended session
(`TimerSnapshot.loggingMatchId`) and opens the editor with it (`MatchEditRoute.newMatchId`). The
session and its summary stay until a match with that id is saved (the timer clears it then),
and the editor asks before leaving an unsaved timer result. No stub match is written, so the
match log stays the only record. (2) `Card.block` stays the printed "!" (cross-checked against
colefin8); the UI says "Block" only when the text states a Block condition (rulebook p.14) and
"No Block condition" otherwise. No data change, so no re-import and no `dataVersion` bump.
(3) A game night is the latest run of rated matches without a gap of 6 h or more, dated by its
first match. (4) Card kind words (colour, basic, tier, ultimate, handicap, spell) narrow
multi-word card queries but never match alone, and a hero is a result whenever one of its cards
is.
**Consequences:** An ended session can outlive its summary screen until its result is saved or
discarded. Older builds ignore `loggingMatchId` (default null). If a 3rd printing adds "!" cards
without a Block condition, the label follows automatically (the catalog test then needs its
count updated).
