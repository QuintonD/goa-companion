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

**Addendum (2026-10-25, Android client, `feature/sync-client`):**
- *Ported from `feature/hub-sync-android`:* the OkHttp client (whole-call timeout, redirects refused
  so the bearer never follows one, no logging/cache/cookies, cancelled with the coroutine), the
  WorkManager scheduler and worker, the Group screen card, the Players tab dot, the network
  security configs and the address normalizer, renamed "sync server" (`SyncServer*`). The hub's
  server-side merge (`POST /api/v1/sync`), `h=` invite field and App Links are not ported.
- *Token:* `SyncServerToken` = base64url-no-pad(HKDF-SHA256(group key, groupId UTF-8,
  `goa-relay/token/v1`, 32)); `SyncServerTest` asserts SYNC-SERVER.md's vector (groupId
  `8c4f2b1e-…`, token `dFROVzWT…IQTI`, hash `cfb03117…9e25`) and a second one (key 0x00..0x1f,
  groupId `6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d` → `3LzQ_IsXcyHp8SiAV9FGqdB_TQmU1C8PXkjjvBbDTIA`),
  both also computed independently with Python's hmac/hashlib.
- *Same merge as a sync file:* the fetched document goes through `SyncRepository.apply` (re-read and
  merge in one transaction), so Nearby, sync files and the server share one write path. "Content
  differs" compares canonical `SyncDocument`s (`SyncMerge.normalize(remote)` vs the merge), never
  bytes, so a fresh envelope salt or `createdAt` doesn't cause an upload. A merge that fails
  `SyncValidation` is never uploaded: every phone would refuse the whole document.
- *Conditional GET:* after each success `group_sync` keeps the rev and the SHA-256 of the canonical
  document both sides then held. When this phone's data still hashes the same, the GET carries
  `If-None-Match: "<rev>"` and a `304` ends the sync (nothing new on either side); any local change
  means a plain GET, so a change is never skipped.
- *412:* three tries in one sync, then "Other phones kept syncing" (waits for the next trigger).
  Statuses: 401 wrong key; GET 404 = no document; any other 4xx (or a PUT 404) = no sync server at
  that address; 413 too big (also checked before sending, 8 MiB); 429/5xx/IO/timeout transient (the
  worker retries with exponential backoff from 30 s, eight attempts); a 200 without `ETag` is not
  trusted. `Retry-After` (seconds, or an HTTP date) on 429/503 is honoured: when it is longer than
  the next backoff, the worker queues a fresh run with that initial delay behind itself (APPEND on
  the unique work, so the current run succeeds) instead of `Result.retry()`. Error bodies are read
  only for a 503: `{"error":"relay_full"}` (the group cap) becomes `SERVER_FULL`, "The sync server
  is full", retried no sooner than its `Retry-After` (3600 s) and flagged with the Players dot,
  since only the server's owner can fix it. Version gaps come from `SyncFileException.version`
  (new, nullable), not message text.
- *Address:* BuildConfig `SYNC_SERVER_URL` from the Gradle property `goa.syncServer` (default in
  `gradle.properties`), bound as `SyncServerDefaults` in `app`. A per-group override and an on/off
  switch live in `group_sync`; the override is stored only when it differs from the default, and
  an invite carries `s=` only then and only while this phone's server sync is switched on. Re-joining the same group by an invite without `s=` keeps the
  override; another group starts from the default with a clean status. Release builds accept only
  https; debug also `http://10.0.2.2|localhost|127.0.0.1`.
- *Triggers:* the activity coming on screen (`MainActivity` lifecycle; at most once a minute, so a
  rotation doesn't sync) and every 5 min while visible, 15 s after the last change (Room
  invalidation tracker on the synced tables + rating constants), hourly with a network constraint,
  "Sync now" (no network constraint, so the outcome shows), and a new server or group.
- *One Sync now:* with the server switched on, the Group screen's primary Sync now and the Players
  tab's sync icon queue a server sync (`SyncServerControl`, core:data, bound to the scheduler);
  Nearby moves to a secondary "Sync at the table (no internet)". Without a server nothing changes.
  The card has no button of its own.
- *Joining:* with a server switched on, joining queues the sync on the same unique work
  (`syncAndWatch`: no network constraint, expedited on API 31+ where no foreground service is
  needed, never retried) and the ViewModel only watches its `WorkInfo`; leaving the screen stops
  the watching, not the sync. The worker reports `synced` and a summary of what arrived in its
  output data. Only when data arrived does the Nearby first sync stay closed; nothing on the server
  yet, or the server unreachable: the Nearby first sync opens as before.
- *Manifest:* `INTERNET` declared; Google's datatransport logger (code scanner) stays off by
  removing its `CctBackendFactory` registration; WorkManager's startup initializer, foreground
  service and `FOREGROUND_SERVICE` removed (WorkManager starts from `GoaApplication` with Hilt).
- `SyncDocument.VERSION` unchanged (no synced type changed).
**Note (hands-on, 2026-10-25):** the sync file envelope carried the group's name in clear; uploads to
the server now leave it empty (`encodeFile(withGroupName = false)`), so the server sees only the group
id, version, time and ciphertext. Shared sync files keep the name (a stranger's phone says which group
to join).

## ADR-037 — Game-state review: the game so far, built by the engine from its own views (2026-10-25)
**Context:** The owner: "For puzzles and playing the game it is important to let players review the
game state e.g. what cards have been played." The game screen had a text log and a per-hero sheet, but
no view of the cards each hero played turn by turn, the round's state at a glance, or earlier boards.
The log's words could be parsed for some of it, but a card swapped, flipped facedown or played as a
second card (Emmitt) would be wrong or missing, and nothing there guarantees hidden information.
**Decision:**
1. **Engine contract** (additive, core/game/CONTRACT-CHANGES.md): `GameSession.review(viewers)` returns a
   `TurnReview` per turn (each hero's card at the reveal in acting order, the turn's events, the end of
   round) and `positionAt(round, turn, viewers)` the board at a turn's start; `GameView.actedThisTurn`.
   `EngineSession` keeps references to committed checkpoints at turn boundaries (they are never
   mutated, so this costs a pointer per boundary and nothing on the AI's forks) and builds every card
   through `Views`, the same code as the live table. So the review cannot show more than the viewer saw
   then (p.4: played cards hidden until the reveal; hands, tucked items and facedown cards hidden).
   `ReviewTest` proves it: no card id outside what the viewer's live views ever showed, and identical
   reviews for games that differ only in hidden cards or in which card was tucked.
2. **Viewers** merge like the screens' views: the person's heroes in play; every seat in a puzzle (its
   hands are open, PRODUCT §10).
3. **UI** (`feature/board` `play/review/`): a top-bar Game state action opens a full-height sheet with
   Now and Turns tabs and a read-only rewound board; the ViewModels build the review on the engine thread
   only while the sheet is open. Card taps go through one `onOpenCard(cards, index)` callback (the card
   sheet today) so a shared card viewer can replace it.
4. **Rewind** shows the start of each turn only (the kept checkpoints), not every decision: turn starts
   are where a player looks back, and finer steps would mean replaying or keeping many more states.
**Consequences:** A long game keeps about three states per turn in memory (a few MB at worst); they are
not saved (replay rebuilds them). The rulebook forbids notes at the table (p.4 Table Talk), and the
history of earlier rounds is more than a player would remember; it is the log's own content (already
shown) and the Expert AI remembers cards shown since each level up, so it is offered as a learning aid;
the owner may want a switch to hide earlier rounds for "table rules" play.

## ADR-038 — oldboi is a second build machine (2026-10-25)
**Context:** The owner asked to move the project from the Windows PC (`Megalithic`) to the home server
`oldboi` (Ubuntu 26.04, x86_64, 8 cores, 7.6 GB RAM, KVM), which already runs the sync relay (ADR-036)
and T3 Code, keeping Megalithic intact until oldboi is proven.
**Decision:** oldboi gets the same toolchain: OpenJDK 21 (the build ran on the Studio JBR 21 so far,
ADR-010), the Android SDK in `~/Android/Sdk` (platform 37.0, build-tools 37.0.0 and 36.1.0, NDK
28.2.13676358 — AGP 9.4.1's default, which strips the native libraries from dependencies; without it
the APK is 600 KB larger), the release keystore in `~/.goa-signing/` (mode 600), the catalog-import
cache, and a `goa_api36` emulator. Builds run through `goa-gradle` (`~/.local/bin`), which
starts `./gradlew` in a `systemd-run --user --scope` with MemoryHigh 4G / MemoryMax 5G / swap 2G, and
`~/.gradle/gradle.properties` allows 2 workers: the first full check run (4 workers, uncapped, each
Robolectric JVM up to 2 GB) filled RAM and swap and the OOM killer took Claude Desktop and T3 Code.
The checkout fetches through the read-only deploy key and pushes through `gh` (HTTPS). `gradlew` is now
committed as executable. Claude Code and T3 Code run there with this repo registered as a project.
**Verification:** 0.6.0 (`8f9d36f7`) built on oldboi has the same 1,009 entries with the same CRCs as
the APK Megalithic published, an identical zip body and central directory, and the same signing
certificate (SHA-256 `076d927e…24ee95`); only the signing block's bytes differ. On oldboi, through
`goa-gradle`: all 3,163 JVM tests pass (Android modules plus `core:model`/`domain`/`game` and
`catalog-import`), `verifyRoborazziDebug` finds no screenshot differences, and lint, detekt and ktlint
are clean. Non-interactive ssh has no locale, which made Java write ASCII-only file names (the test
report for "Hold My Saké" failed), so `~/.goa-env` sets `LANG=C.UTF-8`. systemd-oomd watches the
user session (memory pressure over 50% for 20 s kills the busiest cgroup), so builds from other
projects on oldboi have to take turns with this one.
**End-to-end tests don't fit:** the headless emulator (software rendering of the 1344×2992 screen)
exceeded a 4 GB cgroup with a 2 GB guest and 4.5 GB with a 1.5 GB guest, partway through
`AccessibilityChecksTest`; only `AppSmokeTest` passed. `goa-emulator`/`goa-e2e` stay on oldboi for
smoke checks; the full `connectedDebugAndroidTest` run stays on Megalithic or the phone.
**Consequences:** Either machine can build and publish an update that installs over the other's. One
Gradle build at a time on oldboi, always through `goa-gradle`; it is slower than Megalithic. Megalithic
keeps everything until the owner decides to clear it.

## ADR-039 — The computer's strength is a 1–10 slider; a tuned evaluation (2026-10-25)
**Context:** The owner asked to "improve the ai and give the player a difficulty slider". The computer
played at four levels (ADR-035); Expert beat Hard only 60–68%, more search bought little, and the
evaluation's weights were set by hand.
**Decision:**
- **Contract (additive, CONTRACT-CHANGES.md):** `Controller.Ai` gains `strength: Int` (1..10,
  `AiStrength`), defaulting to its level's `AiLevel.strength`, so a save that carries only the level
  loads as Easy 2, Normal 6, Hard 8, Expert 10. `AiLevel` stays as the band, for the words: 1–3 Easy,
  4–6 Normal, 7–8 Hard, 9–10 Expert (`AiLevel.of`). `AiPlayers.forStrength` / `deterministic(strength)`;
  `PlayEngineModule`'s factory takes the strength (seeded per strength).
- **UI:** New game's level switch becomes a Material 3 slider with a tick per step, the value and band
  above it ("7 · Hard"), Gentler / Stronger under the ends and the band's note under it (1 and 10 have
  their own); TalkBack reads "Computer strength, Strength 7 of 10, Hard". Default 5; after that a
  new game starts at the strength of the last game started (`AppSettings.computerStrength`, a device
  setting; a form restored after process death keeps its own). Advanced: one
  slider per computer seat. The strength shows wherever the level did ("Computer · Hard 8").
- **Evaluation:** the score is a sum of named terms with scales. Fitted to self-play outcomes
  (logistic regression, `FitTest`), a hero's static exposure said almost nothing about who wins; the
  searching strengths use exposure × 0.3 and minion opportunity × 2 (`TUNED`), which won 61% / 64% /
  59% against the old Normal / Hard / Expert.
- **Ten steps**, each adding one lever that measured, all on mirrored tournaments over all 32 heroes
  (AI.md "Strengths", "Measured strength"): three static steps with fewer slips each (1 gentler than
  the old Easy), a static player that sees every enemy, the first look-ahead, Normal, every choice
  played to the end of the turn, Hard, Expert's samples and memory, Expert. Every adjacent pair's
  rate is above 50% on line-ups not used for tuning (58.9% to 77.1% below the top step; the top step
  (10 over 9) 55.7%, 50.7–60.6% over 384 games).
**Alternatives:** interpolating the old knobs evenly between 2 / 4 / 6 / 8 (measured: steps of 47–55%
between Hard and Expert, inverted pairs); more samples (24) and wider candidate lists above Expert,
a longer horizon, no mixing, a sharper opponent model, played-out upgrade choices: all measured even
or worse with Expert (AI.md "What changed in v0.7"), so 10 is Expert and the old levels moved up
to the strengths their profiles measure at.
**Consequences:** Every saved game against the computer keeps loading; its seats play the tuned
evaluation (stronger than before at Normal, Hard and Expert; Easy unchanged). The coach (Hard
analysis) uses the tuned evaluation. Device budgets are unchanged (Expert 4 s per card play at most;
AI.md "Budgets"). The top end is flat: the next gains need a better evaluation of set-up heroes and
round plans, not more search.

## ADR-040 — Gydion's spellbook at the game table (2026-10-25)
**Context:** The owner: "Missing the spell cards for gideon". The catalog has Gydion's 22 spell cards
(hero JSON `extras`, landscape scans) and the engine plays them, but at the table a Gydion player saw
only counters ("Spellbook 19 · Spells removed 3"): not which spells were in the spellbook, which were
cast, or which Prepare Spells would add. Card images drawn in a portrait frame shrank a landscape spell
to a strip with empty bands, in the cast question and the card focus viewer alike.
**Decision:** The engine's views carry a hero's extra cards by place: `HeroView.extras:
List<ExtraPlaceView>` (`place`, `count`, `cardIds`, `open`), built in `Views` from `Zone.EXTRA` and
`CardInst.tag`, so the live table, merged views, the Game state review (ADR-037) and puzzles share one
visibility. A hero module declares its places (`Registry.extraPlaces`, `ExtraPlace(tag, open)`); a place
that is not open names its cards to the hero's own seat only. Gydion: `spellbook` (not open — Prepare
Spells: "Only Gydion can look at the cards in the spellbook", so not even a teammate, p.4), `removed`
(open — "remove it from the spellbook faceup"), `box` (open — the spell cards are public components;
the game starts with an empty spellbook, designer on BGG 3491113). The engine still keeps no hidden
information here (rulings/gydion.md 3: the spellbook's contents follow from the removed and unadded
spells); the views follow the card's words. The UI (`play/table/SpellbookSection.kt`) shows the groups
as the official images on the hero sheet and the review's Now tab; the cast question shows the spells
as cards ("Cast Magic Missile"). Card shape: the catalog has no orientation field, so
`ui/cards/CardShape.kt` takes the spellbook id scheme (`<hero>-spell-…`, the importer's
`CardMapping.spellId`) as landscape and `CardShapeTest` checks every bundled scan against it; a
landscape card keeps a row's height and is wider, and the card focus viewer sizes each card at its own
aspect. The board sandbox lists Gydion's spells after his deck to measure with (his deck cards carry no
Movement, Range or Radius of their own). An unlocked Ultimate is shown as its card (p.16: it lies
faceup on the dashboard).
**Alternatives:** an orientation field in the catalog measured by the importer (touches the import
pipeline and every hero JSON for one hero; the test guards the id rule instead); measuring each image
when it loads (the card would jump in size as it grows out of its thumbnail); Gydion-named view fields
(a future hero with extras would need another contract change); the spellbook open to all since it is
derivable (the card says only Gydion looks).
**Consequences:** Additive contract change (CONTRACT-CHANGES.md); old saves load unchanged (views are
rebuilt, never saved). A future hero with extras declares its places and, if its scans are landscape,
extends the shape rule (CardShapeTest fails until it does). The hero chip shows no spellbook count (the
strip is S3's and has no room; the counts are on the sheet).
**Extended 2026-10-26 (`feature/v08-spellbook-anytime`), "at any point":** the owner: "The player
choosing Gideon should be able to view their spell deck at any point." Reaching the book meant finding
Gydion's piece or chip (while a question makes his piece an option, the tap answers it instead) and
scrolling the hero sheet. Now:
1. **The table:** a Spellbook control per hero in `TableUi.mine` with spell cards, in the map buttons'
   column under Fit and Flip (never over the round at a glance, the turn track or the hero strip; shown
   with the panel hidden too): the hero's official deck symbol (ADR-042) and the spellbook's count on a
   small landscape card. It opens `PlaySheet.Spellbook`, the groups above from the same `extrasGroups`,
   so visibility stays the views' (`TableUi.mine` decides only whether the control is there; a teammate
   or an opponent gets none). An opponent's Gydion: "Open the spellbook" on his hero sheet, the same
   sheet with a facedown count. The review's rewound board carries the control built from that board's
   view (`positionAt`), never the live one.
2. **The cast question says why** (engine, additive, CONTRACT-CHANGES.md): `DecisionContext
   .unavailableCards` lists the asking card's spells that are not options, from the same checks
   (`Spellbook.whyNot`: removed faceup, Prepare Spells; not added yet, designer on BGG 3491113; its action
   forbidden or unable to start, `Game.primaryUnavailable`, which `actionAvailability` now shares). Built
   only while the question is pending (`askAlways`), so replays and the AI's runs skip it. The panel
   shows those spells dimmed with one line each; the sheet marks the options "Can be cast now", only on
   the person's own question (another seat's options would name its spellbook).
3. **Before the game**, where a player chooses Gydion: New game's "You play" row and the line-up chip's
   menu (New game and New board share it) open the 22 spell cards by tier from the catalog in the same
   sheet; the draft's hero peek (feature/draft can't use feature/board) opens the catalog's hero page at
   its Spellbook (`HeroDetailRoute(heroId, section = SPELLBOOK)`), reusing that layout rather than a
   third copy.
**Alternatives:** a top-bar action (the bar already holds Take back, Game state, Log and More, and a
word would be squeezed); a tab in the Game state sheet (two taps, in a sheet about something else);
letting the sheet answer the cast question (two places to answer one question; the panel stays the only
one); a count on the hero chip (the strip gives way to the turn track after the reveal).

## ADR-041 — The game table after the reveal: a turn track of the played cards, and the card as the panel's content (2026-10-25)
**Context:** The owner: "We are still missing a clear indication of the game state after everyone has
played their cards", and "in the board game we don't need to rewrite text that is clearly displayed on
the card, rather we just need to improve how we serve and show the cards". After the reveal the panel
said "Revealed: Wasp 10 · Tigerclaw 9 · …" in one grey line, and once the next question came the
table no longer showed which card each hero played, who had resolved, who acts now and next. The
choose-action panel restated the rules beside big buttons ("Movement 4 — Move up to this many
spaces (p.11)") and the panel repeated the log ("Wasp chooses movement…").
**Decision:**
1. **Turn track, built from the engine's view.** `TurnTrack.kt` maps `GameView` (`initiativeOrder` =
   the unresolved heroes in acting order, `acting`, `actedThisTurn`, `HeroView.played` / `alsoPlayed`
   / `resolved` / `initiative`) and the current turn's events (`CardsRevealed` for the order and
   initiatives at the reveal, `ActionChosen`, `Defeated`) to slots: DONE (the card on this turn's slot,
   so a card resolved facedown stays facedown), NOW, NEXT, LATER, PASSED (no card), SKIPPED (revealed
   but neither resolved nor still to resolve: discarded unresolved, p.10). Ties are marked among the
   heroes still to act (the acting hero's tie is settled). No engine field was added: everything is in
   the views and the events, which are what the person may see (p.4) and the log's own content.
   `TableUi.turnLog` carries the events of the latest two turns (both view models pass it).
2. **Cards as the strip** (`TurnTrackView.kt`): the official card scans with the initiative badge over
   the printed number, the portrait, name and team shape, and the state as a word on a band across the
   card's foot, never colour alone. It replaces the hero strip from the reveal to the end of the turn,
   and unlike the strip it stays when the panel is hidden (compact), because the turn's state is what a
   player looks for with the whole board in view. Tap = the card focus viewer over the turn's cards;
   long press = the hero (the strip's old tap); TalkBack has both. A "NOW" tag marks the acting hero's
   piece on the map (fixed size, outside the zoom).
3. **Moments in words** (`tableMoment`): state-derived, not timed (no toast to miss, nothing a test
   clock can skip): the reveal until the first hero chooses an action (who acts first, p.7 ties), the
   end of a round while its steps run (p.8), a turn over with its cards on their slots, a round over
   with its push and level ups, until the person has played their card. Shown at the top of the panel
   (a polite live region) and in the hidden panel's peek while the computer acts.
4. **The card is the panel's content.** A question asked by a card shows the official card beside its
   answers; action chips carry the game's icon and the card's value, and only rules no card prints
   (Fast Travel's condition, Hold, Clear, primary-only text) in a few words with the page. Tap targets
   over the card's printed action icons were considered and rejected: the scans differ in crop and the
   icon column's position, so the hit areas would not be reliable. Defend shows the attacker's card and
   the hand's cards with the verdict under each; few cards are shown larger; the computer's move shows
   its card. `panelWorthy` keeps out of the panel the log lines the table already shows (the reveal, the
   action chosen, moves); the log sheet keeps every line.
**Consequences:** the top of the portrait table is about 40 dp taller after the reveal (cards with names
instead of chips); the map's inset follows it. Level, coins and hand size are on the strip before the
reveal and in the hero sheet (long press) during the turn. The scripted test game (`FakeGame`) now
reports `actedThisTurn`, the engine's shrinking `initiativeOrder` and `initiative`, and plays cards in
its skipped-to turn 4. Large text puts the asking card above its answers.
**Extended after the hands-on pass (2026-10-25, `fix/v07-handson`):**
5. **A missing action says why, in the engine's words.** Playing Gydion's Cantrip on turn 1 offered
   only Hold with no word why (the spellbook starts empty, rulings/gydion.md 1), and a player thinks the
   app is broken. `Actions.actionAvailability` now builds the options and, for each action the card
   prints that is left out (its primary; a printed secondary Movement or Attack), an
   `UnavailableAction` (`DecisionContext.unavailable`, CONTRACT-CHANGES.md) from the same checks: the
   effect that forbids it (`actionForbiddenBy`, named by its faceup card: "Its Skill is forbidden by
   Arien's Spell Break"), or the step where the primary stops (`primaryBlocker`: the probe's `Fizzle`
   reason, "Its Attack can't start: no target in range", source "p.10, FAQ" — p.10 "Mandatory and
   Optional Steps" and the FAQ's "you may not perform the action at all"; a `Fizzle` may carry its own
   source, as Gydion's empty book does: "designer, BGG 3491113"), or p.14 for a played Defense card. One
   pass gives both, so a reason can't drift from the options; the AI's other queries skip the words
   (`explain = false`). The reasons only name what the person may see: effects in force come from faceup
   cards, Ultimates, markers and pieces (a facedown card loses its effect, p.10), fizzle reasons describe
   the board, and Gydion's book count is public (ADR-040); `UnavailableActionsTest` checks that enemy
   hands that differ give the same reasons. The panel shows each as one line under the chips (a forbidding
   card opens on tap); the UI's own guess ("No Attack: no unit you may target is in reach…") is gone.
   The attack helpers now say where the target was missing ("no target adjacent to you" / "no target in
   range"; the log's "… stops:" lines read the same).
6. **The card focus viewer no longer retypes the card.** The sheet under the big card repeated its
   name, "Basic Gold", the values and the whole text. It now keeps only what the card doesn't print (the
   question, caption, verdict, why it is dimmed, what choosing it means, the next Tier's cards, its item,
   a Handicap card's basis) and "Read the card as text", collapsed, like the catalog viewer (ADR-042 §3).
   The sheet is as tall as its words, at the bottom, and the card takes up to 60 % of the portrait height
   (50 % with large text). TalkBack reads the card's name, kind, values and text on the card's node.

## ADR-042 — Styled on the official look: the cards as the cards, the rulebook's banner and heads (2026-10-25)
**Context:** The owner: "Lean on the original assets throughout the app, the app should be styled
similarly, and only deviate where it clearly improves ui/ux. E.g. in the board game we dont need to
rewrite text that is clearly displayed on the card, rather we just need to improve how we serve and
show the cards to the user." The app's look ("Sunken bronze") was original throughout; the hero page
restated every card as a text tile ("Card RED-2A · Skill · 3+ · Ranged · +1 Attack").
**Decision:**
1. **Official graphics come in through the importer**, never by hand, each listed in
   `official/SOURCES.md` with its source (`tools/catalog-import`, `UiAssets.kt`):
   - **Deck symbols** — source 1's hero logos (`src/lib/images/logos/<id>.png`, ≤ 256 px). Accepted
     only when, against all 32 card backs (source 2's last scan per hero), the logo's own card back
     is the nearest by ≥ 1.3× (cropped to the symbol, brightness standardised plus colour, 12×12
     grid; the logo's dark outline eroded first). All 32 pass, lowest margin 1.42 (Xargatha); the
     card back for Arien also matches the dashboard on rulebook p.6. `Hero.symbol` (null when a
     logo fails), REPORT.md "Deck symbols".
   - **Item icons** — source 1's `item_*.png` (the sketched icons at the foot of every card; checked
     by eye against the scans' item slot).
   - **Board symbols** from the pinned rulebook PDF (SHA-256 pinned), by PDF page and image object
     with their pixel size checked: hero Spawn points (Red's gear, Blue's star) and the six minion
     Spawn point symbols (printed p.6, "Place Hero Miniatures" / "Place Minion Miniatures", in the
     captions' order Ranged, Melee, Heavy), and both faces of the Tie Breaker coin (p.5). PDFBox
     extracts them with their soft masks (transparency).
   - **Licences:** the community repos have no licence and the content is Wolff Designa's, like the
     card scans and portraits already bundled (ADR-006, ADR-034): fine for our builds, a public
     release needs permission. The rulebook graphics are Wolff Designa's own, same terms.
2. **The look** (docs/DESIGN.md "Steel and bronze"): the rulebook's steel-blue banner is every top
   bar (both themes); section heads are copper Cinzel small caps like the rulebook's; text the game
   prints (card text, rulebook excerpts) is set in **Crimson Pro** (OFL, an open Minion look-alike;
   Modesto and Minion are commercial) and card text sits on the cards' parchment. Cinzel stays as
   the Copperplate/Modesto stand-in, Inter for the app's own words. New tokens are checked by
   `ContrastTest`.
3. **Show the cards, don't restate them.** The hero page's Deck is the card scans in a grid by colour
   and tier (tap = viewer); the card viewer shows the scan and, under it, only what the card doesn't
   print (the Handicap basis, what its item icon gives) with "Read the card as text" for the values
   and text; the draft's hero peek shows the five starting cards as scans. The data stays for search,
   filters and TalkBack: each scan is one node reading the card's values and full text. Trivia asks
   about printed numbers, so its question stays text (the scan would answer it) and the answer shows
   the scan (as it already did).
4. **Deck symbols** where a hero is named: hero page header, catalog tiles and rows, draft peek. The
   original sigils remain only as the fallback.
5. **The board** draws the official Spawn point symbols as markings under the pieces: faded and
   partly desaturated toward the Zone fill and smaller than a piece (a piece on one hides it), so
   empty Spawn points never read as units (integrator review: full-colour symbols at piece size did).
   Hero Spawn points are the gear/star (shape tells the teams apart); minion Spawn points add a thin
   outlined team shape with the type's original glyph, because the printed symbols of one team are
   too alike at ~38 dp hexes to tell melee, ranged and heavy apart. Minion pieces keep their original team disc and
   type glyph for the same reason. The board screens' compact top bar takes the steel banner.
**Deviations kept on purpose:** dark canvas by default (table lighting, not the white page); lighter
copper and dimmer parchment in the dark theme for contrast; the app's vector icons inside card text
(the official item icons are grey sketches that vanish at text size and can't take a tint); card
names repeated under a scan only while it is under 140 dp wide.
**Consequences:** the catalog's `dataVersion` is 4 (`symbol` added to every hero file). 48 small
images added (≈ 420 KB). Every screen's screenshots changed (banner, heads); the game table (S3's
play/table) inherits banner, heads and tokens without layout changes. `core:testing` gained
`BundledAssetImages`/`bundledHero` for screenshot tests that show official images.
**Extended after the hands-on pass (2026-10-25, `fix/v07-handson`):** the Tie Breaker coin is the
official coin face wherever the game table shows it, not only in the draft: the HUD (22 dp, about the
face's own 76 px) and New game's coin-flip dialog (64 dp, where it was a plain team-coloured disc),
with the app's team shape on a small badge at the coin's foot, as on the board's pieces (team never
by colour alone; the printed gear and star are shapes too, but the rest of the app speaks diamond and
circle). The old shape-on-a-bronze-ring stays as the fallback while the face loads.

## ADR-044 — A hero's game state: the hero sheet answers what a player asks, and a long press reaches it (2026-10-26)
**Context:** The owner: "Include game state information about the enemy or player when clicking on their
character e.g. item and played cards." The hero sheet listed sections (level, coins, an items line, played,
resolved, discarded…) but never showed an enemy's or a teammate's items: the screen read `HeroView.items`
(the tucked card ids, the owner's only) instead of the public `itemStats`. It did not say where the hero
stood in the turn, and when a hero's piece was a decision option (a target) a tap answered the question,
so the sheet could not be opened from the map at all.
**Decision:**
1. **No contract change.** Items are open as their icon only (p.16 "only the item section of the card is
   visible", "Your items must be clearly visible to the opposing team"; p.4 "the part of the card covered
   by your hero dashboard" is hidden; designer, BGG 3590819). `HeroView.itemStats` already carries every
   hero's icons, built in `Views` for every seat, the review and puzzles; `items` stays the owner's. A test
   (`HeroStateViewTest`) pins both and that games differing only in which card of a pair was tucked look
   the same to every other seat. One leak found and fixed: Min's Perfect Self named the Tier II card it
   tucks in the (public) log; it now says the item it gives, like the level-up line.
2. **The sheet is a game state, most asked first** (`play/table/HeroState.kt`): where the hero is in this
   turn — the card large (a card back before the reveal unless it is the person's), after the reveal the
   turn track's state (ADR-041: NOW / NEXT / LATER / DONE / PASS / OUT), the action chosen and the
   initiative as it counts with the printed one when they differ; this round's four Turn slots as on the
   dashboard and the discard; items as the official icons with what they add ("+1 Defense ×2"), the tucked
   cards for the owner; where the hero stands (Zone, or off the board and when it may respawn, p.7); then
   the Ultimate, Active effects, markers and tokens, spellbook and hand. Everything comes from the view and
   the turn track, so the sheet cannot name more than the table shows; a screen test checks an enemy's
   tucked cards, hand and facedown card are never on it. Landscape puts this turn beside the round.
3. **Reaching it:** a long press on any hero's piece (or another figure of that hero) opens the sheet, even
   when a tap there answers the question — the tap keeps answering (rejected: a second tap
   target or a menu would slow every answer). Detected in the map's own gesture loop, only on a hero's
   space, so pans, pinches and double taps are unchanged; a haptic tick confirms it and the rest of the
   press does nothing else. TalkBack gets "Show <hero>'s game state" as a custom action on the hero's
   space, and the same words on the hero strip and the turn track. Discoverable without a tutorial: when a
   hero's piece is an option, the board hint adds "Long-press a hero for their game state." The moment
   card's recap of the turn track no longer offers a long press that did nothing.
**Consequences:** the hero and spellbook sheet screenshots and the screens whose hint gained the line were
re-recorded. The sheet's hero name is the sheet title only (the header repeated it). A future "table
rules" switch (ADR-037) would hide only earlier rounds; this turn and the dashboard are what the table shows.
