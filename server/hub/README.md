# The hub (`:server:hub`) — guide for feature authors

The group's own server (ADR-030): it syncs with the Android phones over HTTPS and serves the
same app as web pages for iPhones and any browser. Kotlin/JVM, Ktor 3 (CIO), kotlinx.html, a
vendored htmx and a little plain JS. It runs the same `:core:domain` and `:core:presentation` as
the phones, so **pages render what those builders return; HTML and JS never compute a rule or a
statistic.**

Read ADR-030 and ADR-031 in `docs/DECISIONS.md` first; the deploy side is in `deploy/README.md`.

## Run it locally

```
# Git Bash; C: is nearly full
export GRADLE_USER_HOME="D:\\.gradle-home" TMP="D:\\.tmp-build" TEMP="D:\\.tmp-build"
./gradlew.bat :server:hub:installDist
cd server/hub/build/install/goa-hub/bin
sh ./goa-hub join "goa-sync:1?g=…&k=…&n=Dev&h=http%3A%2F%2Flocalhost%3A8080" --data D:\\.goa-hub-dev
sh ./goa-hub serve --data D:\\.goa-hub-dev --port 8080
```

Then open `http://localhost:8080/join#<the same invite>` in a browser: `join.js` signs it in and
lands on Heroes. (`Secure` cookies are accepted from `http://localhost`.) `--args` work too:
`./gradlew.bat :server:hub:run --args="serve --data D:\.goa-hub-dev"`.

On Windows, `goa-hub.bat` runs through cmd.exe, which splits an unquoted invite at every `&`:
pipe it in instead — `Get-Content invite.txt | goa-hub.bat join --data …` (`join` with no text, or
`-`, reads standard input).

Make a test invite with `GroupInvite.format(GroupCredentials.create("Dev"), "http://localhost:8080")`
(a phone's "Share as text" works too).

### Commands (the deploy kit relies on these exactly)

```
goa-hub serve  [--data DIR] [--host 127.0.0.1] [--port 8080]
goa-hub join   [--data DIR] "<invite text>"      (or the invite on stdin)
goa-hub config [--data DIR] [--public-url https://x.ts.net] [--android-cert-sha256 AA:BB:…]
               [--time-zone Europe/London|system]
goa-hub status [--data DIR]                      (read-only)
goa-hub version
```

Data directory: `--data`, else `$GOA_HUB_DATA`, else `/var/lib/goa-hub`. Exit code 0 on success,
1 on failure, 2 on a usage error. `config` changes only the options given.

**Time zone.** Every date the web shows or reads (match times, "today", game nights grouped by
gap, group stats, backup file names) uses `HubClock.zone`: `hub.json`'s `timeZone`, else the
machine's zone. A phone uses its own zone, so the two agree only when they are the same: on a
server that runs in UTC (most VPSs and containers), set the group's zone with
`goa-hub config --time-zone Europe/London`; `serve` and `status` print the zone in use. Stored
times are always epoch milliseconds, so the zone never changes data or ratings, only how dates are
shown and how a typed date-time is read.
`GET /api/v1/health` → `200 {"status":"ok"}` always (also before a group is joined).

## How the hub is exposed — and what that means for your code

The hub binds `127.0.0.1:8080`. **Tailscale Funnel** (primary) or **Caddy** (alternative) on the
same machine publishes it over HTTPS. So *every public request arrives from loopback*:

- **Never trust a loopback address** for anything (no admin shortcuts, no "local only" pages).
  There is no web first-run page: until `goa-hub join` has run on the server, every page says
  "This hub hasn't joined a group yet — run goa-hub join on the server", from any address.
- The hub's own scheme and host come from the **configured public URL** (`HubContext.config.publicUrl`,
  set by `goa-hub config --public-url`, or from the invite's `h=` at `goa-hub join`), never from
  request headers. Use `call.publicOrigin` if you need an absolute URL (e.g. an invite link).
- `X-Forwarded-For` is used for one thing: telling clients apart in the join throttle (its last hop,
  the one the local proxy appended). Without it all clients share a budget; there is also an
  overall cap. Don't use it for anything else.

## Data directory

```
hub.json                        config (public URL, Android cert fingerprints, time zone) — goa-hub config
group.json                      group id, key, name (0600) — goa-hub join
data.json                       the group's SyncDocument (SyncJson) — GroupStore only
backups/data-YYYY-MM-DD.json    last state of each day, 30 days
sync-state.json                 when phones last synced
vapid.json                      Web Push key pair (0600)
devices/<deviceId>/device.json  a signed-in browser (the id is a hash of its cookie)
devices/<deviceId>/<slot>.json  that browser's own state, one file per slot
app/goa.apk                     the latest APK (deploy/linux/update.sh copies it in)
```

Atomic writes go through `AtomicFiles.write` (temp `*.tmp` in the same directory, fsync, rename);
the backup script skips `*.tmp`. The hub writes nothing outside this directory. On Linux the
directory is `0700` and every file the hub writes `0600`, whatever the umask.

## Adding your area: `HubFeature`

1. Make an object in your own package, e.g. `dev.quinton.goa.hub.players.PlayersFeature`:

   ```kotlin
   object PlayersFeature : HubFeature {
       override fun Route.routes(ctx: HubContext) {
           get("/players") {
               val data = ctx.groupStore.data()                 // merged players resolved
               call.respondPage(HubPage("Players", Tab.PLAYERS)) {
                   data.players().forEach { p ->
                       listRow(p.name, href = "/players/${p.id}") { playerAvatar(p) }
                   }
               }
           }
       }
   }
   ```

2. Register it in [`feature/HubFeatures.kt`](src/main/kotlin/dev/quinton/goa/hub/feature/HubFeatures.kt):
   **replace your area's `ComingSoon.X` line** with your object. Keep the comment lines between
   entries — they let parallel branches merge without conflicts. Don't touch other lines.

Routes inside `routes()` are in the signed-in area: a browser without a valid `goa_device` cookie
gets the friendly "Scan the group's invite QR code to sign in" page (or `401` JSON under `/api/`),
and every POST/PUT/PATCH/DELETE has already passed the same-origin check. `call.device` (or
`ctx.device(call)`) is the browser making the request.

Tabs and paths (the phone's navigation, `app/…/GoaApp.kt`): bottom tabs **Heroes** `/heroes`,
**Timer** `/timer`, **Draft** `/draft`, **Players** `/players`; top-bar actions **Rules** `/rules`,
**Card trivia** `/trivia`, **Settings** `/settings` (`TopActions.forTab`); **Group** `/group` under
Settings; **Get the app** `/app` (built). Keep your area's pages under its prefix.

`HubFeature.shellBar(ctx, call)` may return a bar shown above the tabs on every page for this
browser (the phone's "timer running" bar); null = nothing.

### The Players area's links (`players/PlayersPaths`)

Other areas may link to: `/players`, `/players/{id}`, `/players/log`, `/players/meta`,
`/players/same-people`, `/players/matches/new` (optional `?result=1&playedAt=<ms>&durationMin=<m>&mode=quick`),
`/players/matches/{id}` and `/players/matches/{id}/result` ("Who won?" for a pending match, optional
`?playedAt=<ms>`; with `?new=1&…` for an id that doesn't exist yet it opens a new result that saves
under that id). It links out to `/draft?rematch={id}`, `/timer/setup?rematch={id}&kind=SAME_SEATS|SWAP_HEROES|SWAP_TEAMS`,
`/timer/setup?match={id}`, `/group/invite?player={id}` and `/settings/ratings`.
For a realistic local data set: `GOA_PLAYERS_EXPORT=D:\some\dir ./gradlew.bat :server:hub:test --tests "*DevDataExport*"`
writes a joined data directory (46 matches) plus `invite.txt` (public URL `GOA_PLAYERS_URL`, default port 8093).

## `HubContext`

| Member | What |
|--------|------|
| `groupStore: GroupStore` | the group's shared data (below) |
| `deviceStore: DeviceStore` | this browser's own state, in typed slots (below) |
| `devices: Devices` | signed-in browsers: `list()`, `get(id)`, `rename(id, name)`, `signOut(id)` |
| `catalog: HubCatalog` | `catalog` (the hero `Catalog`, strict loader), `readText("catalog/draft-methods.json")`, `open(path)`, `exists(path)`; `HubCatalog.url(path)` → `/assets/<path>` for images |
| `clock: HubClock` | `nowMillis()` (stamps), `elapsedMillis()`, `zone`, `today()` — the core:domain `WallClock`/`ElapsedClock` |
| `push: PushService` | Web Push (below) |
| `config: HubConfig` | `publicUrl`, `androidCertSha256` |
| `group: GroupCredentials?` | the group (id, name; the key — never render it) |
| `syncState: SyncStateStore` | when phones last synced (`read().clients`) |
| `device(call)` / `deviceOrNull(call)` | the browser of a call |

Also handy: `call.hub` (the whole `Hub`), `call.isHtmx`, `call.publicOrigin`, `call.clientAddress`.

## Shared data: read with `GroupData`, write with the stamping helpers

The group's data is one canonical `SyncDocument`. **Every write must stamp exactly like the
phones' Room repositories**, or a web edit could lose to the copy it edited at the next sync.
`GroupStore` does it for you:

| Phone repository | Hub (`ctx.groupStore.…`) |
|------------------|--------------------------|
| `PlayerRepository.upsert` | `upsertPlayer(player): Player` (trimmed name, blank refused) |
| `PlayerRepository.archive` | `archivePlayer(id, archived)` (players are never deleted) |
| `findByName` / `ensurePlayer` | `findPlayerByName(name)` / `ensurePlayer(candidate): Player` |
| `MatchRepository.upsert` / `delete` | `upsertMatch(match): Match` / `deleteMatch(id)` (tombstone) |
| `DraftRecordRepository.save` | `saveDraft(record): DraftRecord`, `deleteDraft(id)` (tombstone) |
| `PlayerMergeRepository.merge` | `mergePlayers(fromId, toId): PlayerMergeOutcome` (`Merged` / `PlayedTogether(matches)` / `NotPossible`) |
| `EloConfigRepository.update` / `reset` | `updateEloConfig { it.copy(…) }` / `resetEloConfig()` |

Each one runs under the store's mutex, stamps `updatedAt = EditStamp.next(now, previous, tombstone)`,
normalizes, runs `SyncValidation` and writes atomically. A write that would break a sync rule throws
`InvalidGroupDataException` (its message is user-readable) and stores nothing. Pass the record
**as the user edited it** (don't set `updatedAt` yourself; it is overwritten).

Reading: `ctx.groupStore.data()` returns `GroupData` — `players(includeArchived)`, `player(id)`
(follows merges), `matches` (newest first, pilots resolved), `match(id)`, `drafts`, `eloConfig`,
`merges` — the same view the phone's screens get. Apply the Unknown-hero rule
(`UnknownHeroes.clear…` with the catalog's ids) where ratings/stats are computed, as
`ReplayRatingsRepository` does. `groupStore.read()` is the raw document; `groupStore.document` is
a `StateFlow` (a new value after each change — use it as a cache key for expensive replays).

`update { doc -> … }` is the low-level escape hatch; don't use it for records the helpers cover.

## Per-browser state: `DeviceSlot`

What a phone keeps to itself (who am I, settings, timer, draft in progress, trivia progress) lives
per browser. Declare a slot next to your code; no shared class needs editing:

```kotlin
@Serializable data class TimerWebState(val presetId: String, val endsAt: Long?)
val TimerSlot = DeviceSlot("timer", TimerWebState.serializer())

ctx.deviceStore.read(call.device.id, TimerSlot)                       // T?
ctx.deviceStore.write(call.device.id, TimerSlot, state)
ctx.deviceStore.update(call.device.id, TimerSlot) { old -> next }     // under the device's lock; null deletes
ctx.deviceStore.read(id, "timer", TimerWebState.serializer())         // string form also works
```

Slot names: a lower-case letter then letters, digits or `-` (don't declare two that differ only
in case). A slot that can't be decoded reads as null. Slots in use: `theme` (`ShellSlots.Theme`,
`ThemeChoice` — written by Settings, read by every page), `invitedPlayerId`
(`SignInSlots.InvitedPlayerId`, set when a browser joins with a personal invite `p=`; the Group
pages ask "You're <name>?" and delete it once answered), `push`, `push-schedule` (PushService),
`players-different`, `players-undo`, `players-flash` (`PlayersSlots`).

`me` (`SharedSlots.Me`, a raw player id: read it through the merges, e.g. `GroupFeature.me(ctx, id, data)`)
and `settings` (`SharedSlots.Settings`, `AppSettings`; missing = the app's defaults), written only by
Group and Settings, `group-pending-merge` (Group), `backup-import` (Settings). A signed-in browser
without a `theme` is dark (`ThemeChoice.DEFAULT`), as the app is.

`timer` and `timer-presets` (`TimerSlots`, the Timer area: the running session and this browser's
presets; the Timer's push schedules use the keys `timer`, `timer-2`, …). The running timer's view
switch also writes `settings.timerLayout` (only that field, under the device lock), as the phone's
top-bar switch saves to AppSettings.

**Timer links** (keep them): `/timer`, `/timer/setup?match=<id>` (a pending match, e.g. after a
draft; `&rematch=<id>&kind=SAME_SEATS|SWAP_HEROES|SWAP_TEAMS` for a rematch), `/timer/run`. Log
result sends a linked match to `/players/matches/<id>/result`; without one it reserves an id and
sends `/players/matches/<new id>/result?new=1&durationMin=<n>&playedAt=<epoch ms>[&mode=quick|long|double_lane]`
— the timer's summary stays until a match with that id is saved.

`hubButton`'s / `primaryButton`'s trailing block runs before the icon and label are written, so it
is the place for attributes (`attributes["hx-post"] = "/x"`, `data-*`, `hidden`); don't add content there.

Draft (`DraftSlots`, owner `draft/`): `draft-setup` (the setup form, the next game's default),
`draft-methods` (custom methods, never synced), `draft-active` (the draft in progress),
`draft-board` (the board's screen state and step clock), `draft-notes`, `draft-method-edit`.

`trivia` (Card trivia: the phone's two `trivia_progress` documents, progress and last setup, in the
phone's JSON via `TriviaJson`) and `trivia-round` (the round on screen, restarted from question 1
after a hub restart). Heroes changes only `catalogListView` in `SharedSlots.Settings`, atomically with
`update`, as the phone's Heroes tab does.

Shared catalog pieces for any page (card text with game icons, card tiles, portraits, the
Unverified badge): `dev.quinton.goa.hub.heroes.CatalogHtml` with `static/heroes/heroes.css`
(`HeroesFeature.styles`). Links other areas may use: `/heroes/{heroId}`,
`/heroes/{heroId}/cards/{cardId}`, `/rules/levels?heroes=a,b` (Levels & defeats for a match's heroes),
`/rules/{topicId}`, `/trivia?hero={heroId}`.

## Pages and components

`call.respondPage(HubPage(title, tab, back = "/x", actions = …, showTabs = true, scripts = listOf("timer.js"))) { … }`
renders the whole document (top bar, `<main>`, the bottom tabs) with `Cache-Control: no-store`.
`call.respondFragment { … }` sends a piece of HTML for htmx.

Components (`web/ui/Components.kt`, styled in `static/app.css`): `card {}`, `sectionHeader`,
`listRow(title, subtitle, href, trailing) { leading }`, `hubButton` / `primaryButton` /
`linkButton(label, href, ButtonStyle.X, icon)`, `chip` / `chipRow`, `playerAvatar(player, "sm|md|lg")`,
`playerName(player)`, `teamBadge(team)` (◆ Red / ● Blue, never colour only), `emptyState`,
`errorState`, `notice`, `textField` / `textAreaField` / `selectField` / `checkboxField`, `postForm`,
`hubDialog(id, title) {}` + `dialogButton(label, id)`, `icon(Icons.X)`.

Rules that keep the CSP happy (`default-src 'self'`, no inline script or style):

- **No `style="…"`, no `<script>` without `src`, no `onclick=`.** Use classes from `app.css` (add
  new ones there: tokens are CSS custom properties, light and dark are handled). Player colours are
  classes (`PlayerPalette.cssClass(player.color)` → `pc-0…pc-9`), crests are `crest-NN`.
- Page-specific JS goes in `static/<area>.js`, loaded with `HubPage(scripts = listOf("area.js"))`
  (versioned URL, `defer`). Hook into elements with `data-*` attributes.
- Dialogs: `data-dialog-open="<id>"` / `data-dialog-close` (handled by `hub.js`); an htmx response
  with `HX-Trigger: closeDialogs` closes open dialogs.
- Mobile first at 390 px; tap targets ≥ 48 px; `font-size` ≥ 16 px in inputs (iOS zoom).

## htmx patterns

htmx 2 is on every page, configured for the CSP (`includeIndicatorStyles:false`, `allowEval:false`,
`allowScriptTags:false`, `selfRequestsOnly:true`, no history cache). Typical uses:

```kotlin
// Partial update: the button posts, the hub answers a fragment that replaces #list.
button(classes = "btn btn-tonal") {
    attributes["hx-post"] = "/players/${p.id}/archive"
    attributes["hx-target"] = "#list"
    attributes["hx-swap"] = "outerHTML"
    +"Archive"
}
// In the route: if (call.isHtmx) call.respondFragment { playerList(data) } else call.respondRedirect("/players")
```

- Make every action work without JS too where it's cheap (a plain `postForm` + redirect), and
  answer htmx requests with a fragment (`call.isHtmx`).
- Confirmations: `hx-confirm="Delete this match?"` (uses the browser's dialog), or a `hubDialog`.
- Loading state: put `class="htmx-indicator"` on a spinner/label inside the trigger.
- Errors: answer `422` with an `errorState` fragment and `HX-Retarget`/`HX-Reswap` if needed.
- A signed-out browser gets `401` + `HX-Refresh: true`, so the page reloads into the sign-in page.

## Requests that change state: the origin rule

Every POST/PUT/PATCH/DELETE (except `POST /api/v1/sync`, which uses a bearer token) must carry
`Origin` = the configured public URL (browsers do this for forms, htmx and `fetch`); otherwise
`403`. There are no CSRF tokens to add. In tests, send `header(HttpHeaders.Origin, HUB_URL)`.
Never change state on GET.

Request bodies are capped at 256 KB (`web/BodyLimit.kt`, `413` above it, chunked or not) before any
handler reads them; only `POST /api/v1/sync` and the backup upload read up to 8 MB themselves. A
feature that needs a bigger upload adds its path there with its own limit.

## Web Push

```kotlin
ctx.push.schedule(deviceId, at = endsAtMillis, PushPayload("Time's up", "Turn 2 · Round 1", url = "/timer", tag = "timer"), key = "timer")
ctx.push.cancel(deviceId, key = "timer")
ctx.push.send(deviceId, payload)            // now; returns PushResult(sent, removed, failed)
ctx.push.subscriptions(deviceId)
```

Schedules persist per device and are re-armed at start-up (one more than 60 s late is dropped);
the same key replaces the previous schedule. Browser side: load `push.js`
(`HubPage(scripts = listOf("push.js"))`) and call, **from a tap**, `goaPush.subscribe()` →
`"granted" | "denied" | "unsupported"`; also `goaPush.unsubscribe()`, `goaPush.test()`,
`goaPush.supported()`. iOS allows push only in a home-screen app (16.4+). Endpoints are limited to
the browsers' push services. The service worker (`/sw.js`) shows `title`/`body` and opens `url`.

## Sync API (Android ↔ hub)

`POST /api/v1/sync`, `Authorization: Bearer <HubToken.derive(group)>`, body =
`SyncCodec.encodeFile(doc, group, createdAt)` (`Content-Type: application/vnd.dev.quinton.goa.sync`).
`200` + the merged document sealed the same way · `401` wrong token or another group's file ·
`409` another `SyncDocument.VERSION` (text says which side to update) · `422` not a sync file,
can't be opened, or invalid data (text = `SyncValidation`'s message) · `413` over 8 MB · `503`
no group joined yet. Idempotent; bodies are never logged. Optional `X-Goa-Device-Id` /
`X-Goa-Device-Name` headers name the phone in "last synced" (`ctx.syncState`).

## Testing

JUnit 4 + Truth + Ktor's `testApplication`. Helpers in `src/test/…/TestHub.kt`:

```kotlin
@get:Rule val tmp = TemporaryFolder()

@Test
fun `players page lists the group`() {
    val hub = testHub(tmp.root.toPath())                 // joined to TestGroup, public URL HUB_URL
    runBlocking { hub.groupStore.upsertPlayer(player("ann", "Ann")) }
    hubTest(hub) {
        val token = plainClient().signIn()                 // POST /join with the right Origin
        val page = client.get("/players") { header(HttpHeaders.Cookie, cookie(token)) }
        assertThat(page.bodyAsText()).contains("Ann")
        val post = client.post("/players/ann/archive") {
            header(HttpHeaders.Cookie, cookie(token))
            header(HttpHeaders.Origin, HUB_URL)
        }
    }
}
```

`FakeClock` moves time by hand; `testHub(dir, clock, joined = false)` gives an unjoined hub;
`PushTransport { endpoint, headers, body -> 201 }` fakes push services. Gate before you're done:

```
./gradlew.bat :server:hub:test :server:hub:ktlintCheck :server:hub:detekt
```

## Code map

```
Main.kt, HubCli.kt          the goa-hub command
Hub.kt, HubClock.kt         composition root, clocks
config/                     HubPaths (data dir), HubConfig(Store) (hub.json), MembershipStore (group.json)
store/                      AtomicFiles, GroupStore (runs core:domain SharedWrites) + GroupData + GroupRatings, DeviceStore + DeviceSlot, Devices, SyncStateStore
catalog/HubCatalog.kt       the bundled catalog and images (built from app/src/main/assets)
push/                       WebPushCrypto (RFC 8291), Vapid (RFC 8292), PushService
web/HubServer.kt            hubModule: headers, origin check, status pages, auth, routes
web/                        SyncRoutes, JoinRoutes, DeviceAuth (cookie), Requests (client, origin), StaticRoutes, PushRoutes, Pages, RateLimiter/JoinThrottle
web/ui/                     HubPage (layout, Tab, TopActions, ThemeChoice), Components, PlayerPalette, StaticFiles, IconRenderer
feature/                    HubFeature + HubContext, HubFeatures (the list), ComingSoon, AppDownloadFeature (/app)
resources/static/           app.css, htmx.min.js (vendored), hub.js, join.js, push.js, sw.js, material/ (Material Symbols)
```

The build (`bundleAppFiles`) adds to the resources: `goa-assets/catalog` and `goa-assets/official`
from the app's assets, `static/fonts` (the app's OFL fonts + licences), `static/crests`,
`static/sigils`, `static/icons`, `static/brand` from `design/`. When adding Material icons, put the
pinned SVG in `static/material/`, add an `Icons` constant and its `.icon-<name>` line in `app.css`
(the generated block at the end), and list it in `third_party/ASSETS.md`.
