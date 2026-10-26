# Architecture

## Stack (versions verified 2026-10-17 — re-check for patches at M0, then pin)
| Concern | Choice |
|---------|--------|
| Language / build | Kotlin 2.4.21 (AGP built-in Kotlin), AGP 9.4.1, Gradle 9.8.1, Java 17 bytecode on JBR 21 (ADR-010); the shared core is Kotlin Multiplatform jvm + js, its JS tests on Node 24 downloaded by the Kotlin plugin (ADR-043) |
| SDK | minSdk 26, compileSdk **37**, targetSdk **37** (set explicitly) |
| UI | Jetpack Compose, Material 3 (custom theme), **Navigation 3** 1.2 (`navigation3-runtime/ui`, `lifecycle-viewmodel-navigation3`, `@Serializable` keys) |
| DI | Hilt 2.60.x via **KSP** (no kapt — it doesn't work with AGP 9 built-in Kotlin) |
| Persistence | **Room 3** (`androidx.room3`, bundled `SQLiteDriver`, KSP) for user data; DataStore for settings, timer state and draft state (in-progress draft, custom methods, setup form) |
| Catalog data | Bundled JSON in `assets/catalog/`, kotlinx.serialization, held in memory (no DB) |
| Async | Coroutines + Flow; `StateFlow<UiState>` per screen |
| Charts | Vico 3.x (`compose`, `compose-m3`) |
| Images | Coil 3 — `AsyncImage(model = "file:///android_asset/official/…")` |
| Group sync | Google Nearby Connections 19.5.1 (`P2P_STAR`), Google code scanner 16.1.0 (invite QR, no CAMERA permission), ZXing core 3.5.4 (draws the QR), `javax.crypto` only (ADR-025; SHA-256/HMAC/HKDF are shared plain Kotlin and AES-GCM a platform `SyncCipher` since ADR-043); sync server client: OkHttp 5.5 + WorkManager 2.12 with Hilt workers (ADR-036) |
| Web app (ADR-043) | Kotlin/JS (IR) + Compose HTML 1.12.2 (`html-core`, `runtime`), a PWA (service worker, IndexedDB) on GitHub Pages; Playwright (WebKit + Chromium) for browser tests |
| Quality | Android Lint, ktlint (`org.jlleitschuh.gradle.ktlint`), detekt 2.0 (`dev.detekt`, alpha — see ADR-008), Roborazzi, JUnit + Turbine + Truth |

Pin versions in `gradle/libs.versions.toml`; never inline a version in a module build file.

### AGP 9 gotchas (convention plugins)
Older Now-in-Android code breaks under AGP 9 as-is. Don't apply `org.jetbrains.kotlin.android`
(Kotlin is built in); configure via `kotlin { compilerOptions {} }`. `CommonExtension` has no
type parameters and `BaseExtension` casts throw; use `androidComponents { onVariants {} }`.
`resValues` is off by default. R8 keep rules are stricter (`-keep class A` no longer keeps
constructors). The shared core (`core:model`, `core:domain`, `core:game`, `core:data-contracts`,
`core:data-testing`) is Kotlin Multiplatform (`goa.kmp.library`, jvm + js, ADR-043); the Android
modules consume its `jvm` variant like any JVM library. `tools:catalog-import` stays `kotlin("jvm")`.

### Shared core: jvm + js (ADR-043)
The web app runs the same rules as the phone, compiled to JavaScript. To let the branches that edit
these modules keep merging, the directories did not move: `src/main/kotlin` **is** `commonMain`,
`src/test/kotlin` is `jvmTest` (the big JUnit 4 + Truth suites run on the JVM only), and
`src/commonTest/kotlin` holds the parity tests that run on the JVM **and** on Node (`allTests`).
What needs a platform lives in `src/jvmMain` / `src/jsMain`:

| Module | jvmMain (Android) | jsMain (browser) | Why |
|---|---|---|---|
| core:domain | `SyncCrypto` (javax.crypto), `SyncCodec` + `JvmSyncCipher` (sync API over the shared `SyncFileCodec`), `sync/protocol/` (the Nearby protocol: `SecureChannel`, `SyncHost`, `SyncClient`), `SecureRandom` | `crypto.getRandomValues` | AES-GCM and gzip are platform code (WebCrypto is async, hence the `suspend` `SyncCipher`); Nearby doesn't exist on the web |
| core:domain | `synchronizedOn` = `synchronized`, `concurrentMap` = `ConcurrentHashMap`, `monotonicNanos` = `System.nanoTime` | plain call, `HashMap`, `performance.now` | the phone's AI runs on several threads; a Web Worker on one |
| core:domain | `jvmDoubleString` = `Double.toString` | Java's layout on JS digits | synced Doubles must be written as the phones write them (`CanonicalDoubleSerializer`, the merge tie-break) |
| core:game | `ControlSignal` without stack traces | plain `RuntimeException` | the engine throws it for control flow |

Shared code never uses `java.*`: dates are kotlinx-datetime (`GroupStats`, the validators),
UUIDs `kotlin.uuid`, Base64 `kotlin.io.encoding`, URL encoding `FormUrl`, origins `UriHost`,
SHA-256/HMAC/HKDF `SyncHash` (plain Kotlin, the same bytes as javax.crypto, `SharedSyncJvmTest`),
`exp`/`10^x` in ratings and the AI `PortableMath` (fdlibm, the same bits on every platform;
WebKit's `Math.exp` differs from V8's). Puzzle answer
keys stay files under `core/game/src/main/resources`; `EmbedTextFiles` (build-logic) compiles them
into `PuzzleKeyFiles`, so both platforms read them without a classpath. Never iterate a `HashMap`
or `HashSet` where order matters (JVM and JS order them differently); `mutableMapOf`/`LinkedHashMap`
keep insertion order on both. How the web consumes the core: `D:\.goa-qa\web\F1-api.md` (stream
notes) and ADR-043.

**Data contracts (`core:data-contracts`, ADR-043 F3).** The repository interfaces every screen reads
through (`Repositories.kt`, `SyncServerContracts.kt`, `rating/RatingContracts.kt`), their pure types
(`GroupState`, `ServerSyncState`, `SyncedPhone`, `EditClock`, `GameInProgress`/`StoredGame`,
`PuzzleProgress`, `RatingsSnapshot`, `SyncServerError`, …) and the implementations that are pure
logic over other contracts: `ReplayRatingsRepository` (ratings, meta and group stats replayed from
the match log), `PlayerNames` and `PlayerMergeRules` (unique names, when two players may merge), and
the stored formats `EloConfigJson`, `TriviaJson`, `GameJson`, `PuzzleProgressJson`. Package
`dev.quinton.goa.core.data`, as before the move, so no Android import changed; `core:data` re-exports
it (`api`) and implements the rest over Room/DataStore/OkHttp, binding the moved classes with
`@Provides` (no `javax.inject` in shared code). The web app implements the same interfaces over
IndexedDB (`web/app/.../data/WebServices.kt`). `core:data-testing` holds the in-memory fakes and
`TestCatalog` (package `dev.quinton.goa.core.testing`), re-exported by `core:testing`, so the phone's
and the web's tests use the same fakes.

## Modules
```
app/                     # Application, MainActivity, Navigation 3 back stack + entry provider, DI root,
                         #   BottomSheetSceneStrategy (entries tagged NavSheet.metadata open as a sheet)
build-logic/             # convention plugins (android-library, compose, feature, hilt, jvm-library,
                         #   kmp-library) and EmbedTextFiles (files → Kotlin source for shared code)
core/model/              # KMP jvm+js: data classes (Hero, Card, Player, Match, DraftMethod…)
core/domain/             # KMP jvm+js: TimerEngine, EloCalculator, DraftEngine, MetaStats, MatchSetup, TriviaEngine,
                         #   trivia/ TriviaChallenge + TriviaLeaderboard (derived, ADR-032),
                         #   catalog/ (the Heroes tab's search, filters, deck layout and card wording,
                         #   CatalogSearchIndex + catalogResults + CardIcons; shared with the web, ADR-043 W1),
                         #   sync/ (SyncMerge, PlayerMerges, SyncHash, SyncFileCodec + SyncCipher, SyncVectors);
                         #   jvmMain: SyncCrypto, SyncCodec, the Nearby protocol (SyncHost/SyncClient)
core/domain-testfixtures/ # JVM: in-memory SyncTransport + data port (sources in core/domain/src/testFixtures)
core/game/               # KMP jvm+js: the rules engine (ADR-035): GameApi.kt contract, engine/ (re-execution session,
                         #   flow, board/combat/minion rules, views, determinize), effects/ (hooks for card
                         #   scripts), heroes/ (one module per hero), position/ (PositionBuilder); see
                         #   core/game/ENGINE.md, EFFECTS.md, RULINGS.md
core/data-contracts/     # KMP jvm+js (ADR-043 F3): the repository interfaces + their pure types and logic
                         #   (ReplayRatingsRepository, PlayerNames, PlayerMergeRules, stored-format codecs);
                         #   package dev.quinton.goa.core.data; implemented by core:data and by web/app
core/data-testing/       # KMP jvm+js: in-memory fakes of the contracts + TestCatalog (re-exported by core:testing)
core/group-presentation/ # KMP jvm+js (ADR-043 W2): what the Players and Group sync screens show, built from the
                         #   contracts (Leaderboard, PlayerDetail, MatchRows, MetaBoard, MatchForm/MatchValidation/
                         #   MatchEditing, RatingSettingsText, GroupText, SyncServerText, SyncWords); packages are
                         #   feature:players'/feature:sync's own; used by both features and by web/app
core/data/               # repositories; catalog and board-map loaders; JSON/Room <-> model; export/import;
                         #   game/ (ADR-035): the game in progress (DataStoreGameSaveRepository) and
                         #   PlayEngineModule, the one place GameFactory, the AI per level, the Coach and the
                         #   PuzzleBook are bound; puzzle/ (DataStorePuzzleProgressRepository)
core/database/           # Room 3 DB (schema v4: players, matches, drafts, trivia_rounds, deletions,
                         #   player_merges), DAOs, entities, migrations, schemas/
core/datastore/          # settings, persisted timer state, trivia progress, boards (datastore/boards),
                         #   the game against the computer in progress (datastore/games, one document),
                         #   puzzle progress (datastore/puzzle_progress, one document; local only)
core/designsystem/       # theme, tokens, components, icons, sigils; cardtext/ (CardTextView, GameIcons:
                         #   card text with inline game icons, shared by the catalog and the board);
                         #   component/OfficialArt (deck symbols, card scans, parchment, board symbols)
core/testing/            # Android test helpers (BundledAssets for screenshots) + core:data-testing's fakes
core/navigation/         # Navigation 3 keys for every screen + Navigator (ADR-010), NavSheet metadata
feature/catalog/
feature/timer/           # timer UI, TimerScheduler (alarms), notification, receivers
feature/players/         # players, match log, Elo, meta dashboard; shareable HTML stats page
                         #   (pure StatsPageRenderer + its own StatsPageFileProvider, ADR-027)
feature/draft/
feature/settings/        # settings, rating constants, export/import entry, About
feature/rules/           # rules reference: topic list + search, topic pages, Levels & defeats sheet
feature/trivia/          # card trivia (ADR-028): home (Playing as, progress + setup, Challenge), quiz and
                         #   summary, leaderboard (ADR-032); questions from core:domain/trivia, progress and
                         #   the Playing-as pick in datastore/trivia_progress, rounds in Room (synced)
feature/board/           # board sandbox (ADR-033): Boards list, New board, Board. map/ = MapLayout (axial →
                         #   screen, turned in 30° steps so the Lane runs up the screen in portrait),
                         #   MapViewport (fit/zoom/pan), BoardMapView (Canvas layers + piece composables +
                         #   one TalkBack node per space); measuring only through core:domain/board;
                         #   boards in datastore/boards (DataStoreBoardRepository, one JSON document per board).
                         #   map/MapLayers (MapBase, ZoneLabels) is shared with play/ (ADR-035): table/ = the
                         #   reusable game table (GameMapView, DecisionPanel, hero status, log) over a TableUi;
                         #   setup/ = New game; game/ = GameViewModel (drives the GameSession on one engine
                         #   thread: the person's answers, paced AI turns, take back, Coach, autosave), game over.
                         #   puzzles/ = the puzzle list and a puzzle (PuzzleViewModel drives a PuzzleSession on one
                         #   engine thread: answers, narrated replies, miss/undo/restart, nudges, the replay).
                         #   Builds against core:game's GameApi.kt only (tests use the real EnginePuzzleBook)
feature/sync/            # group sync (ADR-025): Group screen, sync sheet, Who are you, sync-file import;
                         #   NearbySyncTransport, invite QR + scanner, FileProvider, permissions;
                         #   server/ (ADR-036): SyncServerScheduler + SyncServerWorker, SyncServerCard
tools/catalog-import/    # JVM CLI (reuses core:model): sources.lock.json, overrides.json,
                         #   cache/ (gitignored), REPORT.md → writes assets/catalog + assets/official;
                         #   --rules-only: rules-reference drift check vs the pinned rulebook (PDFBox)
                         #   --maps-only: board maps (catalog/maps) + overlay check images in cache/maps
web/app/                 # Kotlin/JS + Compose HTML PWA (ADR-043): nav/ (hash Router), ui/ (design system,
                         #   goa.css), storage/ (KeyValueStore on IndexedDB), pwa/ (service worker client,
                         #   offline download), data/ (WebServices: the contracts per stream, stubs in
                         #   data/stub/; WebCatalogRepository), screens/; resources/ (index.html,
                         #   goa.css, sw.js, manifest, icons). Catalog, images, fonts copied in at build time
web/e2e/                 # Playwright tests of the built site (WebKit iPhone 15, Chromium Pixel 7), serve.mjs
server/relay/            # NOT in the root build: standalone Gradle build (own wrapper + pinned catalog), the Ktor
                         #   (CIO) sync relay of ADR-036 / docs/SYNC-SERVER.md: one sealed blob per group,
                         #   ETag/If-Match, token-hash TOFU, never parses bodies. Docker deploy kit: deploy/relay/
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
  reference them. Unknown IDs render as "Unknown hero"; imported or synced ones are stored as
  they are and read as Unknown-hero seats (DOMAIN.md *Backup import*, ADR-025).
- **Catalog data is bundled.** Catalog data and images are fetched at dev time by
  `tools/catalog-import` and bundled. The only network use is the group's sync server
  (ADR-036, below): the app declares `INTERNET`, release builds speak HTTPS only to system CAs
  (`res/xml/network_security_config.xml`; the debug copy adds plain HTTP to `10.0.2.2` /
  `localhost`), and the manifest removes the datatransport `CctBackendFactory` registration, so
  the code scanner's Google usage logger has no backend to upload through.

## Group sync (ADR-025)
- **Layers.** `core:domain/sync` is pure Kotlin: `SyncDocument` (version 2: players, matches,
  drafts, `triviaRounds`, `deletions`, `playerMerges`, stamped `EloConfig`), `SyncMerge`,
  `PlayerMerges`, `UnknownHeroes`,
  `SyncValidation` (the backup import's rules), `SyncHash` (SHA-256, HMAC, HKDF in plain Kotlin),
  `GroupInvite`, `SyncFileCodec` (gzip payloads, sealed sync file, over a platform `SyncCipher`)
  and `SyncVectors`/`SyncCipherConformance` (bytes every cipher must reproduce), all shared with
  the web app (ADR-043); JVM only: `SyncCrypto` (javax.crypto HKDF, HMAC-SHA256, AES-256-GCM),
  `SyncCodec` (the same codec, synchronous, on `JvmSyncCipher`) and the protocol
  (`SyncHost`/`SyncClient` over `SyncTransport`/`SyncLink`). `core:data` owns
  `RoomSyncRepository` (export raw; `apply` = re-read, merge, write the difference in one write
  transaction, then the rating constants) and `DataStoreGroupRepository` (`datastore/group_sync`:
  group id/key/name, install id, device name, "who am I", phones last synced). `feature:sync`
  has `NearbySyncTransport` and the UI.
- **Writes.** Repositories stamp `updatedAt` = `EditStamp.next(now, previous, tombstone)`; a delete
  writes its tombstone in the same transaction; merges are rows, never rewrites. Reads resolve
  merged player ids (`RoomMatchRepository`, `RoomPlayerRepository`, `RoomTriviaRoundRepository`),
  and the replay applies the
  Unknown-hero rule (`ReplayRatingsRepository`). Backup Merge and sync share `LocalSyncData`.
- **Protocol.** Wire messages are `'G' ‖ kind ‖ body`: HELLO (JSON: protocol, role, device id/name,
  16-byte nonce, document version) → PROOF (HMAC over role, the link's auth token, both nonces,
  both device ids) → SEALED chunks (AES-GCM, per-session key, per-direction label;
  `type ‖ id ‖ index ‖ count ‖ data`). Client: UPLOAD → host: RECEIVED … MERGED → client: ACK.
  ABORT carries a readable reason. Timeouts per stage (`SyncTimeouts`); a link-send returns only
  once Nearby reports the payload delivered. The host closes after the ACK; a client waits for the
  hang-up before closing, so the last message isn't cut off.
- **Sync server (ADR-036, [SYNC-SERVER.md](SYNC-SERVER.md)).** `core:domain`: `SyncServerToken`
  (bearer = base64url HKDF of the group key, info `goa-relay/token/v1`), `SyncServerAddress`
  (origin normalizer), the invite's `s=` field. `core:data`: `OkHttpSyncServerRepository` runs the
  phone algorithm (GET → `SyncCodec.decodeFile` → `SyncRepository.apply`, the sync-file import
  path → PUT the sealed merge with `If-Match`/`If-None-Match` only when the canonical document
  differs from the server's → 412 retried, three tries; a conditional GET with the last rev when this
  phone's data still hashes to the document both sides held then, so `304` ends an idle sync) and
  records time/rev/document hash/error through
  `DataStoreGroupRepository` in `group_sync` (also the per-group server override and the on/off
  switch); `SyncServerHttp` is a dedicated OkHttp client (whole-call timeout, no redirects, no
  logging/cache/cookies, cancelled with the coroutine). The build default comes from BuildConfig
  `SYNC_SERVER_URL` (Gradle property `goa.syncServer`) through `SyncServerDefaults`, bound in
  `app` (`di/SyncServerModule`). `feature:sync`: `SyncServerScheduler` (unique work `server-sync`,
  REPLACE for the debounce and APPEND_OR_REPLACE behind a running one; `server-sync-hourly`
  periodic with a network constraint; on screen via `MainActivity`'s lifecycle and every 5 min
  while visible; 15 s after `localChanges`, the Room invalidation tracker on the synced tables plus
  the rating constants; `syncAndWatch` for joining: the same unique work, expedited on API 31+,
  watched through `getWorkInfoByIdFlow`), `SyncServerWorker` (`@HiltWorker`; transient problems
  retry with exponential backoff from 30 s, eight attempts; a `Retry-After` longer than the backoff
  queues a fresh run with that initial delay behind the current one), the Group screen's
  `SyncServerCard`. `SyncServerControl` (core:data, bound to the scheduler) lets the Group screen's
  Sync now and the Players tab's sync icon sync through the server. WorkManager
  starts from `GoaApplication` (`Configuration.Provider`, `HiltWorkerFactory`); its startup
  initializer, foreground service and `FOREGROUND_SERVICE` are removed from the manifest.
- **Shell.** Sync files reach `MainActivity` as VIEW/SEND intents and open `SyncFileImportRoute` on
  the Players tab. A sheet entry can hold itself open (`NavSheet.LocalDismissible`) while merged
  data is written. The sync sheet keeps the screen on.

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
4. **Alarm fires** → receiver advances phase, raises the "phase over" alert, schedules next
   alarm (ADR-018 addendum). The app makes the noise itself, from the alarm receiver
   (`goAsync`, held until the chime ends): `PhaseAlertSounder` plays the short bundled original
   chime (`res/raw/phase_chime.wav`, 1.3 s, once) with `USAGE_ALARM` (works on a phone set to
   silent or vibrate, and in Do Not Disturb's alarm lane) when Settings → Sounds is on, and
   vibrates with alarm usage when Haptics is on. The notification ("Time's up: Turn 2 · Round 1")
   is only the visual part: one high-importance channel `timer_phase_alert` with no sound or
   vibration. (Android 16 auto-groups it with the countdown and marks it `SILENT`, which muted
   the old per-style sounding channels; it also made the group summary chime late.) Turning the
   "Phase over" channel off in system settings silences the alert completely; with notifications
   denied the app still chimes and vibrates. Any user command clears a stale alert; it also
   times out after 5 min. A re-sync that auto-advances past a phase end also alerts (e.g. a
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
   phase, extras and controls on the right. A running Hurry up is pinned outside the scrolling
   area: above the controls in portrait, under the dial in landscape. The bottom controls are Pause and
   Next only; "Previous phase" sits beside the step chip, away from the bottom edge. Opt-in volume keys are read with
   `onPreviewKeyEvent` on a focused root while the timer screen shows. The round view
   (`AppSettings.timerLayout = ROUND`, ADR-026) swaps only the body: header, pinned Hurry up,
   controls, volume keys and `keepScreenOn` are shared; "Previous phase" sits beside the current
   step's title. Its "Jump to" is `TimerCommand.JumpTo(round, step)` → `TimerEngine.jumpTo`, which
   the controller ignores once the session has moved to another round.
8. **Match link**: `TimerController` is the single writer of the linked match's duration
   (rounded minutes, clamped to 1–600): on End match, and — only if still missing — before
   an ended session is replaced or cleared. "Log result" opens the focused "Who won?" editor
   (`MatchEditRoute(result = true, prefillPlayedAt = session start)`): a linked pending match
   defaults to Completed and is dated from the session start; without a linked match nothing
   is saved until the user saves, and the new match is pre-filled with the duration and, if
   legal for the session's hero count, its mode. That new match gets an id reserved in the
   session (`TimerSnapshot.loggingMatchId`, `MatchEditRoute.newMatchId`): the ended session
   and its "Match over" summary stay until a match with that id exists, so Back from "Who
   won?" (which asks first) loses nothing (ADR-023). After saving a result the editor shows
   each player's rating change and rank and offers Rematch.
9. **Shell** (`app`, ADR-020): the notification's content intent carries `EXTRA_OPEN_TAB =
   TIMER`, and a cold start with a running or paused session also opens the Timer tab. Other
   tabs show a compact "timer running" bar (phase, round context, clock; tap → timer) that
   keeps the window awake while the session runs. The bottom bar (and the timer bar) is hidden
   on the running timer, the draft board, a sandbox board (`BoardRoute`) and a game against the
   computer (`GameRoute`).

## App shell and navigation (ADR-020)
One `NavBackStack` per tab (`AppNavigator`). Every tab's back stack is decorated with its own
saveable-state and ViewModel-store decorators on every composition (`rememberDecoratedNavEntries`),
and `NavDisplay` shows the current tab's entries, so leaving a tab keeps its screens, scroll
positions, half-filled forms and ViewModels; state is dropped only when an entry is popped.
Content keys are prefixed with the tab, so the same key in two tabs never shares state.
`Navigator.showMessage` posts app-wide snackbars (e.g. "Saved as a pending match").
Cross-feature contracts for this: `DraftSetupPrefill` (core:data, implemented by draft) fills
the draft setup for "Draft again".

**Device checks (S25 Ultra, Android 16)** — Robolectric can't prove these:
- Android 16 status-bar chip / Live Update keeps ticking down while running (no frozen
  text), shows "Paused" and "Over" in those states.
- Lock screen: Pause/Resume works with one tap without unlocking; Next prompts for unlock.
- Swiping the countdown away re-posts it; it stays gone after End match.
- First start from Match setup shows the notification prompt; after "Don't allow" the timer
  runs, the banner appears, and "Turn on" opens the app's notification settings.
- Phase-over alert after process death with auto-advance (force-stop is not representative:
  it drops alarms); Doze; reboot mid-phase; wall-clock change then reboot.

## Web app (ADR-043)
The same app for iPhones (and any browser): a PWA at `https://quintond.github.io/goa-companion/`,
built from `:web:app` and published by `.github/workflows/web-pages.yml`. It runs the core modules'
Kotlin (once they target JS, F1) under a DOM UI in Compose HTML; rules never live in the web layer.
- **Shell.** `Main.kt` renders `App()`: the current route's screen (`screens/Screens.kt`, one
  composition per history entry), the bottom `TabBar` (Heroes, Timer, Draft, Players, as on
  Android; hidden for full-screen table tools via `Route.showsTabBar`) and snackbars, including the
  service worker's "new version — Reload". `AppServices` (router, store, data, theme, messages,
  service worker, offline) is handed down as `LocalServices`, the web's stand-in for Hilt.
- **Data.** `AppServices.data` is `WebServices`: the web's implementations of `core:data-contracts`,
  one holder per stream in its own file (`ContentData` W1: catalog, settings, trivia progress, rules
  reference; `GroupData` W2: players, matches, drafts, trivia rounds, Elo constants, ratings, group,
  sync; `TableData` W3: draft and timer; `PlayData` W4: maps, boards, game save, puzzle progress,
  the engine worker). Screens read `services.data.group.matches`, never storage directly. The
  catalog is `WebCatalogRepository`: the bundled JSON fetched (heroes in parallel) and parsed by the
  phone's strict `CatalogFiles.load`. Contracts a stream hasn't built yet are stubs that throw
  `NotImplementedError("Wn: …")` when used.
- **Navigation.** `nav/Route.kt` lists every screen with its hash (`#/heroes/arien`), tab and parent;
  `Router` keeps History API entries (Back/Forward and swipe-back work like a website, the top bar's
  Back is Up), each tab's last screen, scroll per entry and per screen, and `rememberRetained` state.
- **Design system.** `goa.css` (tokens as CSS custom properties, dark/light/system by
  `<html data-theme>`, rem sizes that follow iOS Dynamic Type) + Kotlin components in `ui/`
  (`Screen`, `GoaCard`, `SectionHeader`, `ListRow`, `Chip`, `Segmented`, `GoaButton`, `BottomSheet`,
  `AlertDialog`, `EmptyState`/`LoadingState`/`ErrorState`, `rememberLoad`). Icons are inline SVG generated from the SVG files (`:web:app:iconMarkup`).
- **Offline.** `sw.js` precaches the shell and every JSON per build, caches images on demand by
  content hash, and waits for "Reload" to switch builds. `pwa/OfflineAssets` downloads every image
  with progress from `asset-manifest.json` (written by `:web:app:site`).
- **Storage.** `storage/KeyValueStore` over IndexedDB; each stream keeps its data under its own prefix.
- **Content (W1).** `web/catalog/`: the Heroes tab, hero page and card viewer over core:domain's
  `catalog/` (the phone's `CatalogViewModel` logic is `catalogResults`, the deck `DeckLayout`, the
  spoken card `CardIcons`); `CatalogBrowse` (in `ContentData`) keeps the search index and the
  search, filters and sort for the page's lifetime. Card text renders `game_<token>.svg` icons inline
  (the Android glyphs, `third_party/ASSETS.md`); the viewer swipes with native CSS scroll snapping and
  zooms with its own pinch/double-tap (`PinchZoom`). Data other streams own (ratings, pilots,
  players) is read through `optional(...)`, which falls back when a contract fails. `web/rules/`,
  `web/settings/` (settings, About) and `web/trivia/` complete the stream.
- **Table tools (W3).** `timer/` and `draft/` build their state with the same presenters as the
  phone's view models (core:domain, ADR-043 "Table"). `WebTimerController` is the phone's
  `TimerController` without alarms or notifications: every command runs `TimerEngine` on the stored
  snapshot, the clock is wall time that never steps back, and the page re-syncs when it is shown
  again. The running timer keeps the screen on with the Screen Wake Lock, sounds cues with Web Audio
  and flashes instead of vibrating. Drafts and the setup form are stored at every change.
- **Group (W2).** `group/`: the group's synced data is one canonical `SyncDocument` under
  `group/document` (`GroupDocumentStore`; each write re-reads, applies, stores, then publishes, under a
  Web Lock shared by the site's tabs, and a `BroadcastChannel` tells other tabs to reload), with
  `Web*Repository` classes keeping the phone's rules (stamps, tombstones, merges, orders); membership
  in `group/prefs` through the shared `PrefsGroupRepository`; sync through the shared
  `RelaySyncRepository` over `FetchRelayHttp` and `WebCryptoCipher`; `WebSyncServerControl` schedules
  it. Screens in `players/` (the Players tab, match editor, ratings) and `sync/` (Group sync, Which
  player are you?, invite QR with qrcode-generator, camera scanning with jsQR, the `#join=` link)
  draw what `core:group-presentation` builds.
- **Build.** `:web:app:webAssets` copies the bundled catalog, `official/` images, fonts, licences and
  shared icons from the Android sources at build time; `:web:app:site` writes the deployable
  `web/app/build/site` (stamped build id, asset manifest, no source maps).

## Testing strategy
| Layer | Tests |
|-------|-------|
| core:game | Rule tests by rulebook page (setup, turn, movement, combat, minions, end of round, card rules), one test class per hero following each card clause by clause, session tests (save/restore mid-card, fork, undo, determinize), property tests over 1 200 random games, a games-per-second benchmark (core/game/ENGINE.md "Tests") |
| core:domain | Unit + property tests (Elo maths, draft legality for every method/player count, timer with fake clock) |
| Shared core on JVM and JS (ADR-043) | `commonTest`, run by `allTests` on the JVM and on Node: `DomainParityTest` / `GameParityTest` hash a transcript of each engine's output (Elo + meta + group stats on a seeded log, every bundled draft method, seeded trivia, merges of random phones, the timer, the shipped catalog decoded strictly, a seeded random game, a seeded AI game, one puzzle per difficulty solved, every answer key loading and fitting its position) and must match the hash the JVM produced; `SyncVectorsTest` (FIPS/RFC vectors, the relay token vectors, the phones' JSON), `SyncCodecParityTest` (envelope, errors, invites, server addresses). JVM only: `SharedSyncJvmTest` (shared hashes vs javax.crypto, `FormUrl` vs java.net, `UriHost` vs `java.net.URI` on a fuzz corpus, `SyncCipherConformance` on `JvmSyncCipher`, the old code's file bytes), `PortableMathTest` (bit for bit `StrictMath.exp`) |
| core:data-contracts | `commonTest` on the JVM and Node: `ReplayRatingsRepositoryTest` (recompute on log/config change, unknown heroes, one consistent snapshot, shared between collectors) with `core:data-testing`'s fakes. The shared relay algorithm (`RelaySyncRepository`) is held by `core:data`'s `SyncServerRepositoryTest` (JVM) and the web's `WebRelaySyncTest` (Node) |
| core:group-presentation | `GroupPresentationParityTest` (`commonTest`, JVM and Node): the same log gives the same leaderboard, match rows, meta board, validation words and sync/rating words; the features' ViewModel and screenshot tests cover the rest |
| Web group (W2) | `:web:app:jsTest`: `WebCryptoCipherTest` (`SyncCipherConformance`, a phone's upload, the relay token vector), `WebGroupDataTest` (the Room repositories' tests ported: names, stamps, merges, tombstones and undo, three browsers converging with equal ratings, sync files, reload, unreadable data kept aside), `WebRelaySyncTest` (fake relay: first upload, a second browser, 304, a phone's upload, the 412 race, refusals in words, invalid data never sent), `WebSyncServerControlTest` (schedule and retries). `web/e2e/tests/group.spec.ts` against a fake relay on a real socket, production blocked; `scripts/interop-live.mjs` + `LiveRelayInteropTest` (phone → real relay → browser → phone); `web/e2e/tests/table-group.spec.ts` where the match log and the table tools hand over (Resume and Rematch → Match setup, Log result's pre-fill, Draft again) |
| core:data | Catalog validation (schema, unique IDs, asset paths exist, every hero has a full deck), export/import round-trip |
| core:database | Room in-memory tests; migration tests for every schema bump (v1 → v2: `matches.tie_breaker`; v2 → v3: `updated_at` backfill, `deletions`, `player_merges`; v3 → v4: `trivia_rounds`) |
| Group sync | `core:domain`: merge properties (commutative, associative, idempotent, convergence, identical ratings), examples (ties, tombstones, merge chains and cycles, unknown heroes), crypto/codec (RFC 5869 vector, tamper, other group), protocol over the in-memory transport (host + 3, drops mid-upload and mid-download, wrong key, relay, Nearby unavailable). `core:data`: real Room phones syncing (game night, deletions, duplicates, trivia rounds), stamping, backup v5; the sync server client against a fake relay on MockWebServer (every branch and status, `SyncServerRepositoryTest`) and its `group_sync` state. `core:domain`: the token's fixed vector. `feature:sync`: VM tests, screenshots, `SyncServerSchedulerTest` on WorkManager's test driver. Real Nearby: two physical phones only |
| ViewModels | Turbine on `uiState` with fakes from `core:testing` |
| Puzzles | `PuzzleViewModelTest` on the real `EnginePuzzleBook` (each listed puzzle solved with its stored line through the table's model, wrong tries missed and tried again, nudge timing on virtual time, nudges off, replay, progress), the list VM; `PuzzleScreenshotTest` drives the real ViewModel on a test scheduler before drawing; `DataStorePuzzleProgressRepositoryTest` |
| Play UI | `feature:board` test sources only: `FakeGameSession`, a scripted 2v2 on Forgotten Island that asks every `DecisionKind` (never shipped); `GameViewModelTest` (answers, AI pacing on a test dispatcher, take back, Coach nudge shown/skipped, the Coach's look ahead used at once and cancelled on take back and Coach off, save/restore, corrupt and incompatible saves, game over, rematch), `NewGameViewModelTest`; `PlayScreenshotTest` (the table in every decision kind, New game, game over) |
| UI | Roborazzi (`recordRoborazziDebug` / `verifyRoborazziDebug`), light/dark × font scale 1.0/2.0 |
| End to end | `app/src/androidTest` (`connectedDebugAndroidTest`): real Hilt graph and bundled catalog, a scripted game night (`GameNight`: House draft → setup → timer round → "Who won?" → rating changes → ratings/meta), a rematch night (`RematchTimerFlowTest`: logged match → Rematch "Same seats" → setup → timer with the rules sheet, rules screen and a notification tap back to the Timer tab → second result), catalog, players, double pilot, settings, card trivia and the trivia Challenge → leaderboard (`TriviaChallengeFlowTest`), the board sandbox (`BoardFlowTest`: House draft → Try on the board → move a hero to the space named 1 space from it → Boards), and ATF accessibility checks on every main screen. Runs through the AndroidX Test Orchestrator with `clearPackageData`, so each test starts from a fresh install |
| Web app | `:web:app:jsTest` (Node/Mocha): routes, router (Up vs Back, tabs, scroll, retained state), the IndexedDB store on `fake-indexeddb`, offline planning, the Markdown reader, the catalog read from the shipped files exactly as the phone reads it (`WebCatalogRepositoryTest`: same strict result, offline then Try again, one fetch, an unknown key fails), stubs that never break start-up and name their stream (`WebServicesTest`), the timer and draft surviving a reload, a locked phone (resync with auto-advance) and a linked match's duration (`TableDataTest`); the timer's and draft's words match the phone's (`TablePresentationParityTest`, core:domain commonTest). `web/e2e` (Playwright, WebKit "iPhone 15" + Chromium "Pixel 7") against the built site under `/goa-companion/`: every destination without console errors or sideways scrolling, tabs, deep link across reload, Back/Forward, scroll restoration, theme, sheets, offline reload, the full offline download, the update prompt, the timer and a whole draft at the table (`table.spec.ts`: full screen, reload, round view, running bar, SE/landscape/200 % text); review screenshots in `D:\.goa-qa\web\shots` |
| Device | Manual smoke checklist on S25 Ultra per milestone (timer: lock screen, Doze, reboot); the web app on an iPhone (Safari tab and Home Screen) |

## Data files
- `app/src/main/assets/catalog/index.json` — `dataVersion`, printing, product list, hero file list.
- `app/src/main/assets/catalog/heroes/<hero-id>.json` — one file per hero (schema in DOMAIN.md).
- `app/src/main/assets/catalog/draft-methods.json` — House draft + official selection methods (Pick and Ban orders per player count).
- `app/src/main/assets/catalog/match-setup.json` — wave/life counters per mode, keyed by heroes in play (rulebook table; `null` = not specified).
- `app/src/main/assets/catalog/timer-presets.json` — default presets (Table rules, Relaxed, Untimed); user presets live in DataStore.
- `app/src/main/assets/catalog/rules-reference.json` — level & defeat ladder (p.13/p.15/p.23 tables) and quick-reference topics: verbatim rulebook excerpts with printed pages (schema in DOMAIN.md). Hand-curated, unlike the generated hero files; the importer's `--rules-only` check verifies every excerpt against the pinned rulebook PDF, so never paraphrase in it.
- `app/src/main/assets/catalog/maps/<map-id>.json` + `maps/index.json` — board maps for the board sandbox (ADR-033): Zones, Lanes, spaces with spawn points, enclosed terrain, `verified` (schema and rules in DOMAIN.md *Board map JSON*). Generated by the importer's maps stage from goa2-backend + `overrides.json` (`maps`); the overlays on official art it renders for the space-by-space check stay in `tools/catalog-import/cache/maps/` (no map art is bundled). Validated by `MapValidationTest` (core:data).
- `app/src/main/assets/official/heroes/<hero-id>/portrait.webp` — official hero art.
- `app/src/main/assets/official/cards/<card-id>.webp` — official card images.
- `app/src/main/assets/official/heroes/<hero-id>/symbol.webp` — the hero's deck symbol (ADR-042).
- `app/src/main/assets/official/icons/item-<stat>.webp` — the item icons printed on the cards.
- `app/src/main/assets/official/board/*.webp` — Spawn point symbols and Tie Breaker coin faces from the rulebook.
- `app/src/main/assets/official/SOURCES.md` — where each official file came from.

Images: WebP q80, portraits ≤ 512 px, cards ≤ 768 px, deck symbols ≤ 256 px on the long edge (≈ 650 card images
plus artwork — keep the APK under ~100 MB). Missing image → hero sigil fallback, never a crash or broken tile.
Generated files are never hand-edited; fix the importer or its override file instead.
