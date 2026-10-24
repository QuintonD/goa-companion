# Decisions (ADR log)

Append-only. Format: `## ADR-NNN — Title (YYYY-MM-DD)` · Context · Decision · Consequences.
Supersede rather than edit.

## ADR-001 — Native Kotlin + Compose (2026-10-17)
**Context:** Android-only, polished, offline app with timers that must survive backgrounding.
**Decision:** Native Kotlin, Jetpack Compose, Material 3 with a custom theme.
**Consequences:** Best platform integration (foreground service, haptics, widgets later);
no iOS without a rewrite or KMP migration (core:model/domain kept pure Kotlin to keep that open).

## ADR-002 — Offline-first, no backend in v1 (2026-10-17) — sync part SUPERSEDED by ADR-025; "no backend" lifted by ADR-034
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

## ADR-006 — Private build; official sources and assets allowed (2026-10-17) — privacy rule SUPERSEDED by ADR-034
**Context:** The owner confirmed the app is for local, personal use only and will never be
sold or published.
**Decision:** Use official sources freely: verbatim card text, official hero art, card images
and game iconography may be bundled. Our own UI/brand assets stay original. Official files
live in `assets/official/` with a `SOURCES.md` record. No public remote, no store release.
**Consequences:** Much richer catalog with far less design work. If the app is ever
distributed, ADR-004's restrictions must be reinstated and `assets/official/` removed or licensed.
**Addendum (2026-10-17):** the owner shares the signed release APK with their own play group
(a handful of friends who play together) through a private, unlisted Google Drive link on their
personal account. That stays private, non-commercial use for one group. Still not allowed: public
releases (GitHub Releases, stores, public posts), links posted anywhere public, and the owner's
company accounts. Every friend installs the same signed APK, so updates install over it and keep
their data (versionCode must increase per update).

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
coin, stage back to Players, with a Clear action. The note "Same players and teams as last game"
(or "Same players as last game" when sides changed) shows only when the latest completed match
had exactly that group, never before a game was played (game-night QA, 2026-10-17).
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

**Addendum (2026-10-17, timer, game night):** the background alert was silent. Android 16
auto-groups the app's two notifications (countdown + alert) and gives the alert `FLAG_SILENT`,
so the channel chime never played on time; the system group summary chimed once, ~1.5 min late.
*Decision:* the alarm receiver sounds the alert itself (`PhaseAlertSounder`, under `goAsync`
until the 1.3 s chime ends): the bundled chime with `USAGE_ALARM` and a vibration with alarm
usage, each gated by Settings → Sounds / Haptics. The notification is the visual part on one
silent high-importance channel (`timer_phase_alert`); the four per-style channels are deleted
on start. In-app cues still replace the alert while the timer is visible (`uiVisible`), and
alerts stay deduped per phase end, so nothing sounds twice. *Alternatives tried on the emulator
(API 36):* explicit `setGroup` (the alert as its own group summary) let the system chime play on
time, but Android 16 still force-regroups the lone summary, and the system group summary then
chimed again when the alert was cleared (a late duplicate), so it was dropped; a foreground
service was not needed (ADR-007 stands). *Verified on emulator-5556:* Home, screen off and
forced Doze: `MediaPlayer` `USAGE_ALARM` `mutedState:none` on the speaker plus an alarm-usage
vibration at expiry; Sounds off → vibration only; timer visible → in-app cues only, no alert.
*Risk:* Android 17's background-audio hardening (developer.android.com/about/versions/17/changes/bg-audio)
silences playback by apps that have neither a visible activity nor a (non-short) foreground
service; the API 36 emulator only logs "AudioHardening background playback would be muted …
level: partial". On Android 17 the chime would be silenced (vibration still works). Before the
S25 Ultra gets Android 17, wrap the chime in a brief foreground service (the exact-alarm
broadcast may start one; `USAGE_ALARM` with the exact-alarm permission waives the while-in-use
rule) or revisit the channel sound if forced grouping changes.

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

## ADR-024 — Shell+records: Start timer resets the Timer tab; rematches are saved on Start timer (2026-10-17)
**Context:** At game night, Draft → Set up match → Start timer landed on whatever the Timer tab
had left on top (Timer presets, or last game's 5-player Match setup with "A timer is already
running; starting replaces it", one tap from starting the wrong match). Every Rematch option
saved a pending match before setup, so backing out piled up identical "Quick · 4 heroes" rows.
**Decision:** (1) `Navigator.openTimer()` is the one way to show the running timer: the Timer
tab's stack becomes exactly its root. Match setup's "Start timer" uses it. (2)
`navigateToTab(tab, key)` cuts the target stack back below an older screen of the same type
(and everything above it) before pushing `key`, so a stale Match setup can't resurface. (3) A
rematch opens `MatchSetupRoute(rematchOf, rematchKind)`; Match setup builds it with the pure
`Rematch.create` and saves it as a pending match only on Start timer, together with the mode
and the coin placed at setup. A draft still saves its pending match when it finishes (it is the
only record of the draft, and the draft → match link and coin must survive a back-out). (4)
Pending rows on the Timer tab show each team's heroes and the time they were set up, with
Discard (confirmed; deletes only a still-pending match).
**Consequences:** A rematch backed out of leaves nothing; existing pending matches keep working
(resume, Who won?, Discard). The Timer tab loses whatever was stacked on it when a timer starts.

## ADR-025 — Group sync: phone to phone at the table over Nearby, LWW merge, no server (2026-10-17) — "no INTERNET" constraint lifted by ADR-034
Supersedes the "no cross-device sync" part of ADR-002 (everything else in ADR-002 stands: all
data is local, no accounts, no backend).

**Context:** The owner shares the APK with the play group (ADR-006 addendum), so each friend
now has their own phone and their own copy of the match log, and asked to "sync the data, and
optionally add the players through them downloading the app". Data is only ever created when
the group is together at the table. The app has no INTERNET permission and that stays
non-negotiable. Options weighed:
- *A PC or home server / LAN sync:* needs a machine and a router on game night, the INTERNET
  permission, and someone to run it. Rejected.
- *Cloud (Drive, Firebase):* accounts, network access and a third party holding the group's data,
  all non-goals. Rejected.
- *QR codes only:* fine for a few hundred bytes (an invite), far too small for a match log (tens
  to hundreds of KB, even gzipped). Kept for the invite only.
- *Phone to phone over Google Nearby Connections:* Bluetooth + Wi-Fi Direct, no router, no
  internet, works on every friend's Android phone, encrypted per connection. Chosen.

**Decision:**
1. *Transport:* Nearby Connections (`play-services-nearby`, `Strategy.P2P_STAR`), service id =
   application id, behind a pure-Kotlin `SyncTransport` interface (core:domain) so the protocol
   is unit-tested over an in-memory transport. Endpoint names carry a non-secret group tag (first
   8 hex of SHA-256(groupId)) and the device's name; connections are auto-accepted for the tag.
2. *Membership and key:* "Start a group" makes a UUID `groupId` and a 256-bit random `groupKey`.
   The invite QR (`goa-sync:1?g=…&k=<base64url>&n=…`, drawn with ZXing) is read with Google's code
   scanner, which runs in Play services, so the app needs no CAMERA permission. Membership, key,
   this install's id, "who am I" and phones last synced live in their own DataStore
   (`group_sync`), never in AppSettings, never synced or backed up in the export file.
3. *Authentication and encryption (javax.crypto only):* before any data both phones send a nonce
   and then HMAC-SHA256(authKey, role ‖ Nearby raw auth token ‖ both nonces ‖ device ids); a
   wrong MAC ends the link. Every data message is AES-256-GCM with a per-session key derived by
   HKDF from the group key, both nonces and the token, with a per-direction label. Keys are always
   derived (auth, session, file), never used raw.
4. *Session:* one phone hosts; the others join (a joiner finds its group's host on its own). Each
   joiner sends its whole `SyncDocument`; the host merges them into its own data in one
   transaction and sends the result back; each joiner merges that in transactionally. Every stage
   has a timeout; a phone that drops leaves its data untouched; an upload that arrived before a
   drop is still merged. Payloads are gzip'd and chunked into BYTES payloads ≤
   `MAX_BYTES_DATA_SIZE`. Phones sync only with the same `SyncDocument.VERSION` (an older app
   would drop new fields and its copy could win a tie).
5. *Merge model (pure, core:domain `SyncMerge`):* every write stamps `updatedAt` = max(now,
   previous + 1, any tombstone + 1). Same id: higher `updatedAt` wins; an exact tie keeps the copy
   whose canonical JSON sorts last. Deletions are tombstones (`deletions` table, written in the
   delete's transaction) that beat any copy edited at or before them; a later edit resurrects.
   Players are never deleted. Duplicates are `player_merges` resolved at read time (cycles from
   two phones' opposite merges resolve to the smallest id). The group's rating constants travel
   with their own `updatedAt`. Commutative, associative and idempotent (property-tested), so any
   order of syncs converges, and ratings replay identically on every phone.
6. *Read-time rules:* merged ids and the Unknown-hero rule are applied when reading (ratings,
   stats, screens), never when storing, so a phone with an older catalog can't erase a newer
   hero's id by winning a tie.
7. *Fallback:* "Share sync file" exports the document sealed with a key derived from the group
   key and a random salt; the file carries the group id and name in clear so a file from another
   group gets a clear error. Opened from other apps (VIEW/SEND for
   `application/vnd.dev.quinton.goa.sync`) or the in-app picker; merged exactly like a sync.
8. *Backup v4* carries `updatedAt`, tombstones, merges and the constants' change time; backup
   Merge now follows the sync rules (later edit wins) instead of "the file overwrites", and a
   restored old backup never overrides the group's newer data at the next sync.

**Consequences:**
- A Google Play services dependency for the first time (Nearby, code scanner). A phone without
  Play services can still use the sync file. The code scanner brings Google's `datatransport`
  usage logging into the process and its `transport-backend-cct` manifest declares INTERNET; the
  app manifest removes it (`tools:node="remove"`, checked in the merged release manifest), so the
  logger can't send anything. `ACCESS_NETWORK_STATE` stays (Play services' job scheduling needs
  it; it opens no connection).
- New runtime permission group ("Nearby devices" on Android 12+, Location before), asked only
  when a sync starts, with a rationale, a settings link after a refusal, and the file fallback.
  The manifest follows Google's Nearby list with three changes: `ACCESS_COARSE_LOCATION` is
  declared up to API 31 (Google: 28) and asked together with FINE on Android 12, which ignores a
  FINE request made alone (lint `CoarseFineLocation`); `ACCESS_WIFI_STATE` and
  `CHANGE_WIFI_STATE` are declared on every version (Google: up to 31), because Play services'
  Nearby refused to advertise or discover without `ACCESS_WIFI_STATE` on Android 12+ (8032); and
  `BLUETOOTH_SCAN` carries `neverForLocation` (as `NEARBY_WIFI_DEVICES` does), because without it
  Nearby demanded COARSE and then FINE location on Android 12+ (8034, 8036), which the app never
  asks for there. Found in the 0.2.0 hands-on pass (2026-10-18): with Google's list as written,
  every Sync now failed on Android 12+.
- Last writer wins per record: two phones editing the same match before syncing keep only the
  later edit (no field-level merge). Phones with badly wrong clocks win concurrent edits; an edit
  after a sync always beats what it edited. On an exact tie a match with more known heroes wins
  (pre-sync data: a backup import on an older catalog had stored a newer hero as null).
- Phones on different app versions converge on the same data but may rate differently (a missing
  hero reads as Unknown hero); the greeting carries the app version and the sheet warns.
- Adversarial review (2026-10-18) fixed: the tie above; Nearby links registered at
  `onConnectionInitiated` so a first message arriving before the local result isn't dropped; links
  removed by identity; phones still connecting when Sync is tapped are hung up on; the rating
  constants re-merged with the stored ones after the transaction; a backup's explicit "use the
  file's settings" stamped as a new change; a cancelled session releases the radios before the
  next starts; backups refuse merges naming missing players; COARSE location on Android 12; the
  host's wording for a phone that dropped after its data was in.
- Tombstones are kept forever (tens of bytes each). Key rotation isn't built: leaving and
  re-inviting with a new group is the way to cut someone out.
- Nearby runs between two API 36 emulators over the emulator's virtual Bluetooth (2026-10-18:
  host + join, a full sync, a duplicate merge, the permission flows). The protocol, crypto and
  merge are proven by JVM tests; real radios (range, Wi-Fi Direct upgrade, a phone walking away)
  on two physical phones are the open check (ROADMAP M6).
- Hands-on pass (2026-10-18) also fixed: a phone is named by Android's device name ("Galaxy S25
  Ultra"), not `Build.MODEL` ("SM-S938B"), until its owner picks themselves; a first refusal of
  the permission says so and offers the sync file; a permission turned on in system settings is
  noticed on return; "Which player are you?" follows any finished sync until it is answered;
  "Share as text" sends the invite with instructions, and the paste field finds the invite in a
  whole pasted message; the app is left out of its own sync-file share sheet.

## ADR-026 — Timer: optional whole-round view, chosen per device (2026-10-17)
**Context:** The owner asked for a view of the running timer that shows every phase of the round
on one screen instead of stepping through them, with the player choosing the view.
**Decision:** (1) `AppSettings.timerLayout: TimerLayout { STEPS, ROUND }`, default `STEPS`, in the
settings DataStore (`timer_layout`; unknown values read as the default) and so in backup v3 via
the settings contributor. A file from before this field imports with the default; the settings
contributor now also coerces unknown enum values (`coerceInputValues`), so a newer build's layout
falls back instead of dropping every backed-up setting. It is chosen in Settings → Running timer
and by a top-bar switch on the running timer that saves immediately. The header measures
"Round N", the coin chip and its Undo; what doesn't fit beside them moves into the overflow menu,
the view switch first, then Levels (on a 384 dp S25 Ultra with the coin tracked the switch is in
the menu; above font scale 1.3 both are). (2) The round view is a second body for the same `ActiveUiState`:
the mapper adds one `RoundRowUi` per step of the current round's `TimerPlan` (only while the
layout is `ROUND`), everything else — header, alerts banner, Hurry up, time-outs, controls,
volume keys, auto-advance, cues — is shared with the step view, so the two can't drift. (3)
Tapping another row confirms, then `TimerEngine.jumpTo(stepIndex)`: pure, within the round,
navigates exactly like Next/Back (fresh running step, Hurry up cleared, the Action heroes kept only
inside the same turn, an `UndoPoint` so Back within the undo window restores the step left with
its time). The command carries the round it was confirmed in and is dropped if the session has
moved on. (4) The list scrolls the current row into view on each step change (with the step before
it for context, or less when the current card is tall) and skips that while the user is scrolling.
**Consequences:** No change to the persisted timer snapshot. The bottom controls stay Pause and
Next as in the step view; "Previous phase" sits beside the current step's title (in its card in
portrait, above its clock in landscape). Landscape pins Hurry up and time-outs above the controls too,
so they and the Level Up rule fit a short window, and sizes the clock digits (36–56 dp) to the
room left (an S25 Ultra held sideways leaves about 130 dp); the step view puts them under the dial. Rows only cover the current round: jumping to
another round is not offered.

## ADR-027 — Players: a shareable, self-contained HTML stats page for friends without the app (2026-10-17)
**Context:** Some of the play group use iPhones and can't run this Android-only, sideloaded app,
yet they are players: their matches are logged on an Android phone. They want to see ratings,
the meta and the match log too. The app has no `INTERNET` permission and must keep it, and group sync (ADR-025) needs the
Android app on every phone, so there is nothing for an iPhone to host or sync to.
**Decision:** (1) "Share stats page" (share icon in the Players top bar, shown on the Meta tab
only; there is no overflow menu) writes **one self-contained HTML file** and opens the Android
share sheet (`text/html`), so it travels by any messenger or mail and opens in Safari or Files.
(2) Content is built only from what the screens already compute, through their own builders over
one `RatingsSnapshot`: `PlayersViewModel.leaderboard` (Elo, games, W–L, "last N" trend,
provisional; archived players hidden as on the tab), `PlayerDetailViewModel.build` ("Best
heroes" or "Most played"), `MetaViewModel.build` (hero meta with the same min-sample "Not enough
games yet" split; group stats: last game night, movers, streaks, how games end, sides, average
length, rivalries, teammates, hero pairs) and `MatchLogViewModel.toRow` (the newest 100 matches,
one line per hero seat, so a double pilot appears on each of their seats with one rating
change). No new statistic. Unknown heroes and players read "Unknown hero" / "Unknown player" as
in the app. (3) Rendering is pure Kotlin (`StatsPageRenderer`, no Android imports, unit-tested):
every data-derived string is HTML-escaped; inline CSS with the DESIGN.md tokens, light by
default and for print, dark on `prefers-color-scheme: dark`; semantic tables with `scope`,
`lang`, viewport, a dated title; our inline brand-mark SVG; **no** scripts, fonts, images,
official card art or external references (system fonts stand in for Cinzel and Inter). (4) The
file goes to `cache/stats/` (only the latest page is kept; cache is never backed up) and is
shared through its own `StatsPageFileProvider` (a `FileProvider` subclass, authority
`<applicationId>.statspage`, declared in `feature/players`' manifest). No FileProvider existed,
and a subclass can't clash with one another feature adds later.
**Consequences:** The page is a snapshot: it is right as of its footer ("ratings as of the last
logged match") and goes stale until shared again. 500 matches render to about 125 KB. Player and
hero names, notes and maps leave the phone in whichever chat the owner picks at the share sheet.
Matches beyond the 100 shown still count in every number, and the page says so.

## ADR-028 — Card trivia: learn the printed numbers, mastery kept on this phone (2026-10-18)
**Context:** The owner asked for "a trivia feature … to help you learn all the numbers on
characters cards". Knowing a card's Initiative, Attack, Defense, Movement, Range and Radius
without looking speeds up play and helps read the table (who acts first, who can reach whom).
The 32 heroes' deck cards print about 2,150 such numbers, too many for a fixed question list.
**Decision:** (1) Questions are generated, never written: `TriviaFacts` (pure, `core:domain`)
turns every non-null printed value on a hero's deck cards (Basic, Tier I–III, Ultimate; not the
Handicap card or Gydion's spellbook) into a fact `<cardId>/<stat>` = value (with the printed `+`).
Two kinds of question: *card number* ("Mad Dash — Initiative?") answered from four nearby values
or, in **Recall**, from every value that stat takes anywhere in the catalog; and an optional
*initiative duel* ("Which has the higher initiative?" — first, second or the same). A duel only
asks which number is higher; it makes no claim about turn order. (2) Learning is a Leitner box per
fact: a right card-number answer moves it up one box (max 5), a wrong one back to 0, three right
in a row = **learned**. A right answer only moves the box on the fact's first card-number
question of the round or once at least 4 other questions were answered since it was last asked
(`MIN_SPACING`); sooner, it still counts as seen and right but the box stays, so a small pool
can't pump a number to learned from short-term memory. Duels don't move boxes (a rough sense of
order can answer them), but a missed duel queues both cards' Initiative as card-number
questions. A missed fact comes back after three other questions in the same round, and only a
card-number question on it uses that retry up (a duel touching the card doesn't); the next
question is drawn weighted towards missed, new and still-learning facts, never the same card
twice in a row when the pool has others. "Practise the missed ones" asks each missed number about
twice: 2 × missed numbers, capped by the round's length, never endless.
(3) Progress is stored per fact with the value that was learned; if a catalog fix changes that
value the fact counts as new again. It lives in its own DataStore file (`trivia_progress`) with
the last quiz setup: personal practice, so it is **not** synced (ADR-025) and **not** in the
backup file; Android Auto Backup covers it. (4) `:feature:trivia` (Card trivia: progress per
hero and the setup; the quiz with its summary) is reached from the Heroes tab's top bar and from
a hero's page ("Learn <hero>'s card numbers", that hero preselected). A quizzed hero whose data
is `verified: false` carries the design system's Unverified badge on the question card, duel
tiles, hero picker and progress list (AGENTS.md: unverified → the UI marks it).
**Consequences:** New heroes, a corrected card or the 3rd printing need no trivia work: the
questions follow the catalog. "All heroes" means the owned products (Settings). The round itself
is not saved: every answer's progress is stored as it is given (a write that survives leaving
the screen), but after process death the quiz restarts from question 1 from its saved route.
Every round can be ended (End → summary); Back once a question is answered asks "Leave this
round?" (See results / Leave), since leaving drops the round's results.

## ADR-029 — Timer: every hero resolving gets their own countdown, on by default (2026-10-18)
**Context:** The owner reported the timer "not interacting correctly": each turn of each round
needs a 30 s timer per player resolving their card. The bundled presets shipped the per-hero
Action timer switched off, so every turn ended in one untimed, count-up Resolve step (ADR-018).
Where the Action timer was on, its clock started at the reveal, so the time spent working out who
had the highest initiative came out of the first hero's 30 s.
**Decision:** (1) Table rules (0:30) and Relaxed (0:45) ship with the Action phase on; the bundled
presets move to `revision` 2 and `TimerPresetMigration` switches the Action phase on in stored
copies (adding it if missing), keeping a stored length. An off Action in a stored copy is the old
default and can't be told from a deliberate choice; the owner asked for it on. Untimed is
unchanged. Resolve stays in the presets for whoever turns the Action timer off. (2)
`TimerEngine.heroResolves` restarts the slot's countdown when it names the slot's first hero, so
each hero's full time starts when they start resolving; a paused timer stays paused. Next, Back
(undo), the auto-picked last hero and the per-turn actor list are unchanged. (3) Action steps show
a rules hint (highest unresolved initiative next; no strategy talk, p.4 and p.7), and the tile
prompt says a tap starts that hero's time.
**Consequences:** A round has 1 + 4 × (1 + heroes) + 6 steps (27 at 4 heroes), so the round view is
longer and the Timer tab shows the "Heroes at the table" stepper by default. Every hero's countdown
ends with the phase-over alert, so a full round can fire up to 4 × heroes extra alerts (the cues are
0:05 and 0:00). No snapshot change: running sessions keep the preset they started with.

## ADR-030 — Timer: a bundled "Speed guards" preset, offered once to saved preset lists (2026-10-18)
**Context:** The owner asked for a speed version of the game: 30 s to strategise and 10 s to
resolve cards. A saved preset list replaces the bundled list (`TimerRepository.presets`), so a new
bundled preset would never reach a phone where presets had been edited, deleted or restored.
**Decision:** (1) A bundled preset `speed-guards` ("Speed guards"), second in the catalog: Table
rules with Pre-round strategy 0:30 (cues 0:10, 0:05, 0:00) and the per-hero Action timer at 0:10
(cues 0:05, 0:00). "Strategise" is the pre-round team talk: Play a card is already 0:30 at the
table, so it stays; Upgrade (2:00), Hurry up (0:30) and the rest are unchanged, all editable.
(2) Bundled presets move to `revision` 3. `TimerPresetMigration` adds a bundled preset introduced
at a later revision (`ADDED_IN`) to a saved list whose newest bundled copy predates it, after the
bundled presets that precede it in the catalog. Every write stores the upgraded copies, so once
the user deletes it, the list's copies are at revision 3 and it stays deleted. A list with no
bundled copy at all (only the user's presets) gets nothing. No phase changes in revision 3.
**Consequences:** No new store key, so backups need no change; restoring a backup made before
this change offers Speed guards again, which is right for that list. At 0:10 per hero the
phase-over alert fires for most heroes; the Action cues can be trimmed in the preset if it gets
noisy.

## ADR-032 — Trivia leaderboard: rounds played as a player, synced like matches (2026-10-19)
**Context:** The owner asked for a leaderboard for the card quiz. Trivia progress is this phone's
own (ADR-028), with no idea who is playing, and rounds of different setups (one easy hero, 4 choices
vs Recall, 10 vs endless) can't be compared. Friends play on their own phones and at the table.
**Decision:** (1) A round is played *as* someone: Card trivia's "Playing as" (any player, or Guest);
the pick is remembered on the phone (`trivia_progress` store). (2) A fixed **Challenge** (20
questions, all heroes regardless of owned products, all numbers and tiers, 4 choices, duels on,
uniform draw without repeats, answer time summed) is the comparable score; everything else is
Practice. (3) Each round reaching its summary as a player becomes an immutable `TriviaRound`
(core:model) in Room (`trivia_rounds`, database v4), stamped, deletable by tombstone
(`DeletionKind.TRIVIA_ROUND`), merged-player aware, in the sync document and the backup file. The
leaderboard is derived from the rounds (core:domain `TriviaLeaderboard`), never stored. (4)
Learning progress stays per phone and moves only for rounds played as this phone's "You" (or while
no "You" is set), so passing the phone round the table doesn't pollute the owner's mastery.
**Consequences:** `SyncDocument.VERSION` 1 → 2 and the backup format gains `triviaRounds` (older
files still import): every phone in the group must update before syncing again; an old phone
refuses with "update the app". A Challenge is only as fair as the catalog: the round records the
catalog `dataVersion`, so a later data fix doesn't silently change what an old score meant. Answer
time measures from question shown to answer, so a slow card-scan reader isn't penalised.
Alternatives rejected: ranking by numbers learned (per-phone, can't sync without merging mastery
per fact); a per-setup board for every quiz configuration (too many boards to mean anything).

## ADR-033 — Board sandbox: community hex maps checked against the official art; measure, never play (2026-10-19)
**Context:** The owner asked for a virtual board to try out board states, e.g. the opening for a
line-up, with set-up "as easy and intuitive as possible". The rulebook shows the board only in
perspective, so a playable hex grid can't be read from it. Its rules give everything needed to
*measure* (distance, movement, Fast Travel, Zone adjacency, opening placement, pushing a lane), but
card effects are open-ended text. PRODUCT's non-goals excluded board simulation.
**Decision:** (1) **Map data** comes through the import pipeline from a new pinned source,
`PedroVIOliv/goa2-backend` (`src/goa2/data/maps/*.json`: axial hexes, zones, spawn tags, lanes),
normalised to our `BoardMap` (core:model) in `assets/catalog/maps/`. A map is `verified` only when
an overlay on the official map art matches space by space and the rulebook checks pass (DOMAIN.md
*Board map JSON*): three hero spawn points per team (p.6/p.20); single-lane maps: 4 Melee, 1 Ranged,
1 Heavy per team in the starting Battle Zone (p.6); double-lane maps: two Lanes, each with its own
starting Battle Zone (p.20). p.8's "the defending team has more minion spawn points in their
Pre-Throne Zone" describes the core map's example and is a note, not a check. Community zone names
are not shown. (2) **Rules are pure Kotlin** in `core:domain/board`
(`HexGrid`, `BoardGeometry`, `BoardRules`, `BoardSetup`), page-referenced and tested: obstacles
(p.18), movement through empty spaces and Ignoring Obstacles (p.11, p.18), Fast Travel (p.11), Zone
adjacency (p.17), distance counting obstacles (p.17, p.18), opening (p.6, p.20), Push the Lane steps 2–3
(p.8). (3) The app **measures, never plays**: no turns, card resolution, coins or Life counters;
the user moves pieces freely (one per space) and reads overlays. (4) Boards are local
(`datastore/boards`), autosaved, in the backup file, not synced. (5) Drawn in an original vector
style (no map art bundled), so it stays legible on a phone and owes nothing to the art's layout.
**Consequences:** A new feature module `:feature:board`; the PRODUCT non-goal now excludes only a
full rules engine. The community map data has no licence (like the other sources: private build,
never redistribute). A map we can't verify ships badged Unverified or not at all. Card effects
("move 2 in a straight line", pushes, swaps) are quoted for the table to apply by hand.
**Addendum (2026-10-19, map import):** Ship the four official maps (Wolff Designa's two
double-sided mats): Forgotten Island and Across the River (the 2nd-printing core board) verified;
Narrow Passages and Magma and Ice badged Unverified, because the community data placed many
Pre-Throne spawn points wrongly and was corrected from photos of the mats alone. goa2-backend's
Vexing Cliffs is fan-made and left out. "Verified" needs the community data and the art to agree
space by space (two sources), like the hero catalog's rule.
**Addendum (2026-10-19, review):** Rules gaps closed: minions carry the Lane they spawned in
(`MinionPiece.lane`, p.20 "bound to the Battle Zone they were originally spawned in"), used by
"Minions Outside of the Battle Zone" and by Push the Lane, which also clears that Lane's minions
still waiting in the tray (p.8 step 3); movement has three paths (normal, through tokens, ignoring
obstacles; p.11, p.18) and a straight-line option (p.19); Range/Radius cover the water around the
board, one ring deep (p.19 Terrain, [APP] extent); a push with no straight line has no direction.
Set-up is one tap from the latest draft or match; untouched duplicates are reopened. The board opens
on the Lane at a tappable hex size rather than on the whole map. A board open in the back stack
follows its stored copy (adopts external changes, closes on deletion) so it never overwrites a
backup import or resurrects a deleted board. `lane` has a default and boards are not synced, so no
`SyncDocument` bump; the backup round-trips it.

## ADR-034 — No privacy or offline rule: the app may go online and be published (2026-10-19)
**Context:** Two rules have bounded every choice so far: "keep it private" (ADR-006 and its
addendum: no public remote, no public APK, shared only by a private Drive link) and "the app is
offline" (no `INTERNET` permission, no server; ADR-002, ADR-025, ADR-027). The owner lifted both
while weighing a hosted backend for sync.
**Decision:** Neither is a rule any more. A feature may use the network, a server or a
third-party service, and the repo and APK may be published; each such step is judged on its own
merits in its own ADR. This ADR changes nothing in the app: it still uses no network, Nearby sync
and the sync file stay, and the manifest still removes `INTERNET` until a feature needs it.
**Consequences:**
- `ship-check` no longer checks the merged manifest for `INTERNET` or `git remote -v`.
- Dropping the manifest's `INTERNET` removal also lets the code scanner's Play services usage
  logger (`datatransport`, ADR-025) send to Google; the first online feature decides whether to
  accept that or turn the logger off.
- Official assets stay allowed in our builds. They belong to Wolff Designa, and the community
  sources (catalog, maps) have no licence, so a public release of the app or repo with
  `assets/official/`, verbatim card text or community data bundled needs permission, or those
  files removed; ADR-006's note on ADR-004 still describes that path.
- Synced data stays last-writer-wins by `updatedAt` (ADR-025); a server copy joins the same merge.
- The hub branches (ADR-030: "the group's own hub is the only server") carry their own version of
  the offline rule; reconcile it with this ADR when they merge.

## ADR-035 — Play the game: a full rules engine, AI opponents and puzzles (2026-10-24)
**Context:** The owner asked to learn by playing: expand the board so the real game can be played
against the computer at several difficulty levels, plus 50 difficult chess-style puzzles ("get to
victory or make the best move") with gentle nudges that can be switched off and an explanation at
the end. Until now a full rules engine was a non-goal (ADR-033: "measure, never play").
**Decision:**
1. **Engine** in a new pure-Kotlin module `:core:game` (no `android.*`), on top of
   `core:domain/board` geometry. Every rule of the pinned rulebook's turn, action, combat, minion,
   push, end-of-round and level-up sections; card text beats the rules (p.3). Deterministic: no
   randomness after setup. Every choice is a `Decision` with a finite option list, so humans, the AI
   and the puzzle solver all answer the same questions. Card effects are Kotlin scripts per card,
   interpreted from the verbatim card text, FAQ rulings (source 3) and the rulebook; the community
   engine `goa2-backend` (source 7, no licence) is a cross-check for interpretations only, never
   copied. A hero is playable only when every card of it has a tested implementation.
2. **Public contract** `core/game/.../GameApi.kt` (setup, decisions, choices, views that respect
   hidden information p.4, events, sessions, AI, coach, puzzles). Changes are additive; anything
   else is recorded in `core/game/CONTRACT-CHANGES.md`.
3. **AI** never reads hidden cards: it searches on determinized copies (`GameSession.determinize`).
   Four levels (Easy, Normal, Hard, Expert) trade search time for strength.
4. **Puzzles** are positions built in code with a goal, nudges (gentle → strong) and an
   explanation; a solver proves each one against the best replies and accepts any winning line.
5. **Play and puzzles live in `:feature:board`**, beside the sandbox; games in progress and puzzle
   progress are local (DataStore), not synced and not in the backup file.
**Consequences:** PRODUCT's "no full rules engine" non-goal is gone. Rules accuracy now covers ~630
card texts: every card needs a test, and each hero batch gets an independent rules audit before it
is offered. Hidden-information rules bind the AI, not just the UI.
**Addendum (2026-10-24, engine core):** The engine re-executes: the state is plain serializable data;
each step of the flow (one card resolution, one end-of-round step) runs from a checkpoint with the
answers given so far, and the first unanswered `Game.ask` suspends it (core/game/ENGINE.md). Card
effects are ordinary Kotlin per card plus stateless hook objects (`Behavior`) found from the state's
ids; saves are the start state plus the answers. Readings the rulebook leaves open are listed in
core/game/RULINGS.md; contract additions in core/game/CONTRACT-CHANGES.md. core:game alone widens
detekt's complexity limits (core/game/detekt.yml).
**Addendum (2026-10-24, puzzles):** The solver is an exact AND-OR search over the engine's own
decisions (opponent hands open, the game halted at the goal's deadline). Proving that a wrong move
loses can take seconds on a phone, so each puzzle ships an **answer key**: every winning option at
every player decision of every winning line, and the reply played at every other decision there,
generated by the solver and regenerated and compared by the tests. The runtime needs no search
while the player is on a winning line and searches with a small budget after a miss. Puzzle ids are
`p` + the CONCEPTS.md number so encoders never collide; the book numbers the playable ones
(core/game/puzzles/ENCODING.md).
**Addendum (2026-10-24, play UI):** The screens build against `GameApi.kt` only. (1) The engine's
three entry points (`GameFactory`, the AI per `AiLevel` as `AiPlayers`, the `Coach`) are bound in one
Hilt module, `core:data` `PlayEngineModule`: core:data can see the catalog and map loaders the real
`GameEngine` needs, and features never depend on each other. Until the engine, its AI and its coach
are offered, placeholders seat no hero and the UI says play is coming. Screens get the factory via a
`Provider` on `Dispatchers.Default`, so a provider that waits for the catalog never blocks the main
thread. (2) One game in progress (`datastore/games`): the `SavedGame` plus what only the app counts
(take backs, nudges, Coach on/off, pace); written after every answer; local, not synced, not in the
backup. A document that can't be read, or a save `restore` throws on, is offered for deletion. (3)
The session is touched on one engine thread (`Dispatchers.Default.limitedParallelism(1)`): the
person's answers, the AI's `choose` (cancelled through its `cancelled` lambda on take back, rematch
or leaving), the coach and `save`. (4) The computer's answers are paced: each stays one pace (Slow
2.6 s, Normal 1.5 s, Fast 0.65 s); a hidden card play a quarter of one; the reveal two. The person can
answer as soon as their question is up. (5) Coach: an answer is shown to `Coach.nudge` first with a
1.5 s timeout; with no nudge in time it goes through. A nudge waits for "Choose anyway" or "Rethink"
and never answers. (6) Map taps select and a Confirm button answers; buttons (actions, options,
yes/decline, numbers) answer at once. (7) Defend marks each card "Holds" when its previewed total is
at least the Attack (p.14). (8) Boards hosts Play and Puzzles, so it no longer jumps to New board
when there are no boards (PRODUCT §8).
**Addendum (2026-10-24, puzzle UI):** (1) `PuzzleBook` is bound in `PlayEngineModule` on the same
`GameEngine` as play and reached through a `Provider` on `Dispatchers.Default`; the list shows
`PuzzleBook.puzzles` as it is, so puzzles appear as their heroes become playable. (2) The other
side's replies come back inside one `PuzzleSession.answer`; to show them paced, the screen answers a
`game.fork()` with the player's choice first (the board just after the player's own move), then
narrates the remaining events line by line (0.7 s each, at most 3.5 s) before showing the session's
board. (3) The miss reason is derived from the events that followed (an enemy's successful
`Defended`, a player hero `Defeated`), else from the goal; the encoded wrong tries' labels are not in
the contract. (4) Nudges: one preference (`AppSettings.puzzleNudges`, on by default, also backed up
as a setting); with it on, the next nudge comes with a miss or after 90 s without progress on the
current question; the last nudge outlines `bestChoice()` for the current question. (5) Progress is
one JSON document in `datastore/puzzle_progress` (solved, solvedAt, tries, most nudges, solution
seen) keyed by `PuzzleInfo.id`; local only, not synced, not in the backup file. A try is counted at
its first move; Try again and Restart start a new one. (6) The replay drives a fresh session with
`bestChoice()` at every player question, so it shows a winning line even when the key has several.
**Addendum (2026-10-24, coach looks ahead):** Replaces (5) of the play-UI addendum. A hands-on tester
saw no nudge in a whole game: the Hard-level analysis ran after the tap, and on a phone (3–4× the
desktop time, more under load) it could pass the 1.5 s timeout, while every tap waited for it. Now
the analysis starts as soon as the person's question is asked (`Coach.prepare`, additive with a
default; core/game/CONTRACT-CHANGES.md), on a `fork()` made on the engine thread and searched on
`Dispatchers.Default`: the session itself is still touched only on the engine thread, and the table
stays responsive while the coach looks. One look at a time (a new one waits for the cancelled one to
stop). The answer is checked with `Coach.nudge(analysis, …)`, instant once the look has finished
(only the person's choice is played out, when the pruning had left it out); an unfinished look is
waited for at most 300 ms, then the answer goes through unnudged — under 0.3 s still reads as the
tap's own response, and the look has usually finished while the person thought. The look is dropped
and its search cancelled when the question is answered, on take back, rematch, Coach off, deleting
the game and leaving. Same seed, same nudge: `CoachAheadTest` compares both ways on every answer of
a game.
**Addendum (2026-10-25, card focus):** Amends (6) of the play-UI addendum for cards: the owner asked
for a first tap that zooms a card, a second tap that confirms, back or an upward swipe to deselect
and a sideways swipe through the options. (1) Card questions no longer select then Confirm: the first
tap opens the card focus viewer and answers nothing (the ViewModel's selection is untouched), the
second tap on the focused card or its button sends the same `Answer` the Confirm button sent, so the
Coach's nudge (5) intercepts it unchanged; board questions keep tap-to-select + Confirm. (2) The viewer
is its own full-screen dialog window (platform dim and window animation off, its own scrim and
animations), not an overlay inside the screen: it must show above the board panel, a hidden panel's
peek bar and the hero sheet (a separate window) without each screen re-wrapping its scaffold; its
back gesture goes to the dialog's own dispatcher, where `PredictiveBackHandler` gets the progress.
The cost: no `SharedTransitionLayout` across windows, so the card grows from its thumbnail by a
scale-and-translate from the thumbnail's window bounds. (3) It lives in `:feature:board`
(`ui/cards`): its content is the board's (`TableCard`, the defense verdicts, item stats); the catalog
keeps its own viewer and could adopt this one by moving it to `core:designsystem` with a card slot.
(4) When every option of a card question is in the deciding hero's hand, the whole hand is shown, the
rest marked "Not an option" (the engine gives no reason, so the reason is generic per kind).

## ADR-036 — A sync server on the owner's machine: an encrypted mailbox, phones do the work (2026-10-25)
**Context:** The owner set up a home server (`oldboi`, Ubuntu, on their tailnet) and asked for it to
act as the app's sync server, keeping as much processing on the phones as possible. An earlier,
unmerged line (`feature/web-hub`, ADR-030 there) built a full Kotlin hub that merges server-side and
renders a web app; that puts the group key and the rules on the server, the opposite of this ask.
**Decision:** A relay that stores one sealed blob per group and nothing else (protocol in
[SYNC-SERVER.md](SYNC-SERVER.md)): phones fetch, open, merge (`SyncMerge`), apply, seal and upload
with optimistic concurrency (`ETag`/`If-Match`); the server sees ciphertext, a group id and a token
hash. The Android client reuses `feature/hub-sync-android`'s plumbing (OkHttp, WorkManager, Group
screen status) against this protocol. Hosting: Docker on oldboi bound to loopback, published by
Tailscale Funnel on port 8443 (443 stays the owner's tailnet-only service). Nearby sync stays.
**Consequences:** No server-side web app for iPhones (the hub line stays unmerged). A server that is
wiped loses nothing: the next phone to sync re-uploads the merged log. Whoever runs the server can
see group sizes and sync times, never content. A `SyncDocument.VERSION` bump still needs every phone
updated before they sync again.
**Addendum (server as built, 2026-10-25):** `server/relay/` is a standalone Gradle build (its own
copy of the wrapper, versions pinned locally: Kotlin 2.4.21, Ktor 3.6.0 on CIO, kotlinx.serialization,
slf4j-simple) so the root Android build and the relay never constrain each other, and the Docker
image builds from that directory alone. Beyond the original contract it adds, all documented in
SYNC-SERVER.md: an optional `304` for `GET` with `If-None-Match: "<rev>"`, `If-Match: *`, a cap on
registered groups (`GOA_RELAY_MAX_GROUPS`, default 200; a first upload past it gets `503`) so an open
trust-on-first-use endpoint can't fill the disk, and a published token test vector. Rate limits are
token buckets (60/min per client keyed on the last `X-Forwarded-For` hop, which Tailscale adds and
which was checked on the deployed relay; 600/min overall). Token and revision are checked under the
group's lock before the body is read, so a doomed upload costs nothing. Deployed on oldboi as
container `goa-relay` (read-only root, uid 10001, data in `/var/lib/goa-relay`,
`restart: unless-stopped`); Funnel on port 8443 was enabled the same day.
