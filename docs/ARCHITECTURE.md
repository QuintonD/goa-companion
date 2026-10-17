# Architecture

## Stack (versions verified 2026-10-17 — re-check for patches at M0, then pin)
| Concern | Choice |
|---------|--------|
| Language / build | Kotlin 2.4.21 (AGP built-in Kotlin), AGP 9.4.1, Gradle 9.8.1, Java 17 bytecode on JBR 21 (ADR-010) |
| SDK | minSdk 26, compileSdk **37**, targetSdk **37** (set explicitly) |
| UI | Jetpack Compose, Material 3 (custom theme), **Navigation 3** 1.2 (`navigation3-runtime/ui`, `lifecycle-viewmodel-navigation3`, `@Serializable` keys) |
| DI | Hilt 2.60.x via **KSP** (no kapt — it doesn't work with AGP 9 built-in Kotlin) |
| Persistence | **Room 3** (`androidx.room3`, bundled `SQLiteDriver`, KSP) for user data; DataStore for settings and timer state |
| Catalog data | Bundled JSON in `assets/catalog/`, kotlinx.serialization, held in memory (no DB) |
| Async | Coroutines + Flow; `StateFlow<UiState>` per screen |
| Charts | Vico 3.x (`compose`, `compose-m3`) |
| Images | Coil 3 — `AsyncImage(model = "file:///android_asset/official/…")` |
| Quality | Android Lint, ktlint (`org.jlleitschuh.gradle.ktlint`), detekt 2.0 (`dev.detekt`, alpha — see ADR-008), Roborazzi, JUnit + Turbine + Truth |

Pin versions in `gradle/libs.versions.toml`; never inline a version in a module build file.

### AGP 9 gotchas (convention plugins)
Older Now-in-Android code breaks under AGP 9 as-is. Don't apply `org.jetbrains.kotlin.android`
(Kotlin is built in); configure via `kotlin { compilerOptions {} }`. `CommonExtension` has no
type parameters and `BaseExtension` casts throw; use `androidComponents { onVariants {} }`.
`resValues` is off by default. R8 keep rules are stricter (`-keep class A` no longer keeps
constructors). Pure JVM modules (`core:model`, `core:domain`) use `kotlin("jvm")`.

## Modules
```
app/                     # Application, MainActivity, Navigation 3 back stack + entry provider, DI root
build-logic/             # convention plugins (android-library, compose, feature, hilt, jvm-library)
core/model/              # JVM: data classes (Hero, Card, Player, Match, DraftMethod…)
core/domain/             # JVM: TimerEngine, EloCalculator, DraftEngine, MetaStats, MatchSetup
core/data/               # repositories; catalog loader; JSON/Room <-> model; export/import
core/database/           # Room 3 DB, DAOs, entities, migrations, schemas/
core/datastore/          # settings + persisted timer state
core/designsystem/       # theme, tokens, components, icons, sigils
core/testing/            # fakes, fixtures, test rules
core/navigation/         # Navigation 3 keys for every screen + Navigator (ADR-010)
feature/catalog/
feature/timer/           # timer UI, TimerScheduler (alarms), notification, receivers
feature/players/         # players, match log, Elo, meta dashboard
feature/draft/
feature/settings/        # settings, rating constants, export/import entry, About
tools/catalog-import/    # JVM CLI (reuses core:model): sources.lock.json, overrides.json,
                         #   cache/ (gitignored), REPORT.md → writes assets/catalog + assets/official
```
Dependency direction: `feature → core:data/domain/designsystem → core:model`.
Features never depend on each other; cross-feature navigation goes through `app`.

## Layering and patterns
- **UDF:** Screen composable ← `UiState` ← ViewModel ← repositories/engines. Events go up as
  sealed `Action`s. Stateless composables take state + lambdas; previews for each.
- **Engines are pure functions or state machines** with an injectable time source. No
  `android.*` imports in `core:model`/`core:domain`.
- **Derived data is computed, not stored:** ratings/meta are recomputed from the match log
  (optionally memoised in a Room cache table that can be dropped and rebuilt).
- **Catalog is read-only and in memory.** ~50 heroes × 18 cards fits comfortably; search is
  an in-memory index. Hero IDs are permanent: never rename or reuse one, since matches
  reference them. Unknown IDs (e.g. from an imported backup) render as "Unknown hero".
- **The app never goes online.** Catalog data and images are fetched at dev time by
  `tools/catalog-import` and bundled. No `INTERNET` permission.

## Timer design (no foreground service — ADR-007)
1. **State** in DataStore: preset, phase index, turn/round, `phaseEndElapsed` (+ wall-clock
   copy for reboot), paused remaining. UI derives remaining time from
   `SystemClock.elapsedRealtime()` — never counts ticks. Every write re-anchors the
   (elapsed, wall) pair at the current clocks, so a wall-clock change (`TIME_SET` → re-sync)
   is absorbed before a later reboot rebases the session.
2. **Phase end** via `AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP)` with
   `USE_EXACT_ALARM` (auto-granted, timer use case).
3. **Ongoing notification**: `setOngoing`, `setWhen(end)`, `setUsesChronometer(true)`,
   `setChronometerCountDown(true)`; actions → `BroadcastReceiver` (update state, repost,
   reschedule). On Android 16+: `POST_PROMOTED_NOTIFICATIONS`,
   `setRequestPromotedOngoing(true)`, optionally `ProgressStyle` with one segment per phase.
   No custom RemoteViews or colorized style (blocks promotion). Channel `timer_running`:
   `IMPORTANCE_DEFAULT`, no sound, no vibration, `VISIBILITY_PUBLIC` (a low-importance channel
   was hidden on the keyguard and folded into "Silent"); the old `timer_ongoing` channel is
   deleted. Text names what comes next ("Turn 1 of 4 · Next: Cards revealed").
   - **Actions**: Pause/Resume stays one tap, also from the lock screen (harmless and
     reversible, and the table needs it fast). Next uses `setAuthenticationRequired(true)`
     (API 31+: a lock-screen tap asks for an unlock first). There is **no Stop**: ending a
     match is irrecoverable, so it stays in the app behind a confirmation.
   - **Status-bar chip**: the running countdown sets **no** `setShortCriticalText` (a static
     string would freeze the chip); `setWhen` + chronometer count-down drive it. Static states
     set one: "Paused", "Over" (overtime).
4. **Alarm fires** → receiver advances phase, posts a high-importance "phase over" alert
   ("Time's up: Turn 2 · Round 1"; channel audio `USAGE_ALARM` so Do Not Disturb's alarm lane
   lets it through — required for background audio on API 37), schedules next alarm. The sound
   is a short bundled original chime (`res/raw/phase_chime.wav`, plays once; it replaced the
   looping alarm ringtone). Channel sound can't change after creation, so there is one alert
   channel per Settings → Sounds × Haptics combination (`timer_phase_over`, `_sound`,
   `_vibrate`, `_silent`); the controller picks one from the settings. Any user command clears
   a stale alert; it also times out after 5 min. A re-sync that auto-advances past a phase end also alerts (e.g. a
   cold start whose startup re-sync runs before the alarm broadcast); both paths share the
   controller mutex and dedupe by step key, so each phase end alerts at most once.
5. **Robustness**: reschedule on app start, `BOOT_COMPLETED`, `TIME_SET`,
   `MY_PACKAGE_REPLACED`. `setDeleteIntent` → the action receiver re-syncs, which re-posts
   the notification while a session runs (ongoing notifications are dismissible since
   Android 14); nothing is re-posted once the match has ended.
6. **Notification permission**: every start path (Timer tab, Match setup) requests
   `POST_NOTIFICATIONS` first via one shared helper and starts whatever the answer. If
   notifications are denied or blocked, the running timer shows a dismissible banner ("Lock-
   screen alerts are off") with a button to the app's notification settings.
7. Timer screen uses `Modifier.keepScreenOn()`. In-app cues (ticks, haptics) only run while
   the screen is visible. Tones use `STREAM_ALARM` and warning/zero vibration uses alarm usage,
   so they work on a phone set to silent/vibrate or with touch feedback off; the in-app Sounds
   and Haptics switches still gate them. The "Hurry up" shot clock cues in-app only (no alarm).
   Portrait: dial above extras; landscape (width > height): dial on the left half, header,
   phase, extras and controls on the right. Opt-in volume keys are read with
   `onPreviewKeyEvent` on a focused root while the timer screen shows.
8. **Match link**: `TimerController` is the single writer of the linked match's duration
   (rounded minutes, clamped to 1–600): on End match, and — only if still missing — before
   an ended session is replaced or cleared. "Log result" without a linked match saves
   nothing; it opens a new match pre-filled with the duration and, if legal for the
   session's hero count, its mode (`MatchEditRoute(prefillDurationMin, prefillMode)`).

**Device checks (S25 Ultra, Android 16)** — Robolectric can't prove these:
- Android 16 status-bar chip / Live Update keeps ticking down while running (no frozen
  text), shows "Paused" and "Over" in those states.
- Lock screen: Pause/Resume works with one tap without unlocking; Next prompts for unlock.
- Swiping the countdown away re-posts it; it stays gone after End match.
- First start from Match setup shows the notification prompt; after "Don't allow" the timer
  runs, the banner appears, and "Turn on" opens the app's notification settings.
- Phase-over alert after process death with auto-advance (force-stop is not representative:
  it drops alarms); Doze; reboot mid-phase; wall-clock change then reboot.

## Testing strategy
| Layer | Tests |
|-------|-------|
| core:domain | Unit + property tests (Elo maths, draft legality for every method/player count, timer with fake clock) |
| core:data | Catalog validation (schema, unique IDs, asset paths exist, every hero has a full deck), export/import round-trip |
| core:database | Room in-memory tests; migration tests for every schema bump |
| ViewModels | Turbine on `uiState` with fakes from `core:testing` |
| UI | Roborazzi (`recordRoborazziDebug` / `verifyRoborazziDebug`), light/dark × font scale 1.0/2.0 |
| End to end | `app/src/androidTest` (`connectedDebugAndroidTest`): real Hilt graph and bundled catalog, a scripted game night (`GameNight`: House draft → setup → timer round → result → ratings/meta), catalog, players, double pilot, settings, and ATF accessibility checks on every main screen. Runs through the AndroidX Test Orchestrator with `clearPackageData`, so each test starts from a fresh install |
| Device | Manual smoke checklist on S25 Ultra per milestone (timer: lock screen, Doze, reboot) |

## Data files
- `app/src/main/assets/catalog/index.json` — `dataVersion`, printing, product list, hero file list.
- `app/src/main/assets/catalog/heroes/<hero-id>.json` — one file per hero (schema in DOMAIN.md).
- `app/src/main/assets/catalog/draft-methods.json` — House draft + official selection methods (Pick and Ban orders per player count).
- `app/src/main/assets/catalog/match-setup.json` — wave/life counters per mode, keyed by heroes in play (rulebook table; `null` = not specified).
- `app/src/main/assets/catalog/timer-presets.json` — default presets (Table rules, Relaxed, Untimed); user presets live in DataStore.
- `app/src/main/assets/official/heroes/<hero-id>/portrait.webp` — official hero art.
- `app/src/main/assets/official/cards/<card-id>.webp` — official card images.
- `app/src/main/assets/official/SOURCES.md` — where each official file came from.

Images: WebP q80, portraits ≤ 512 px, cards ≤ 768 px on the long edge (≈ 650 card images
plus artwork — keep the APK under ~100 MB). Missing image → hero sigil fallback, never a crash or broken tile.
Generated files are never hand-edited; fix the importer or its override file instead.
