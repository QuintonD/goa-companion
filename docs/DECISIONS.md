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
