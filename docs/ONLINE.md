# Online games (ADR-056)

People on different phones play one real game against each other, run by the rules engine on a
server, and rated games feed public leaderboards. This file is the contract: the wire types live in
`core/online`, and every stream builds against this page. Change it in the same commit as the code.

## Where things run

| Piece | Runs on | If it is down |
|---|---|---|
| The app (PWA), the APK | GitHub Pages (`quintond.github.io/goa-companion`) | Installed apps keep working offline |
| `network.json`: game hosts, lowest protocol, a status note | GitHub Pages (`quintond.github.io/goa-leaderboards`) | The app uses its built-in address, `goa.gameServer` |
| Leaderboards, read side | GitHub Pages, same repo, `leaderboard.json` | The app reads oldboi's `/v1/leaderboard`, else says how old its last copy is |
| Live rooms and games | oldboi, container `goa-game`, `https://oldboi.taila4d38d.ts.net:8443/game` | No online play. Running games resume from their saved answers when it returns. Everything else in the app still works |
| Group sync | oldboi, container `goa-relay`, unchanged | As today (ADR-036) |

GitHub Pages serves files and cannot run a game, hold a hidden card or check a result, so a live game
always needs a machine. **Pages carries all the static load and oldboi only what must be live**:
that is what "Pages first, oldboi as backup and overflow" means here. `network.json` is how another
live machine can join later without an app update (web builds also need it in their CSP).

**Publishing is a pull.** A scheduled GitHub Action in `goa-leaderboards` fetches `/v1/leaderboard`,
validates it and commits JSON only. oldboi holds no GitHub credential. Pages sites of one account
share a browser origin with the PWA and its IndexedDB (group keys, online tokens), so anything able
to publish HTML there could read them. The Action writes one validated JSON file, nothing else. It
also deploys the site (Pages source "GitHub Actions"), because a `GITHUB_TOKEN` push does not trigger
a Pages build from a branch; only `network.json` and `leaderboard.json` go on the site.

Ports: 443 and 10000 on oldboi are other apps' and are never touched. The game server is a new path
on the relay's public port, `tailscale funnel --bg --https=8443 --set-path /game http://127.0.0.1:8788`.
The server answers on both `/v1/...` and `/game/v1/...`, whichever way the path arrives.

## Rules of the mode

- **The server runs the rules.** A phone gets its own seat's view, its own question, and the public
  events. It never holds another hand, and never holds `SavedGame`.
- **No accounts with personal data.** `POST /v1/accounts {name}` returns an id, a token and a recovery
  code shown once. The server stores the display name and SHA-256 hashes. There is no email and no
  third-party sign-in. The recovery code links another device or a lost one.
- **A room:** the creator picks map, length, team size and ranked or not; gets an 8-character code and
  a link `https://quintond.github.io/goa-companion/#play=<code>`, also as a QR. Each joiner takes a free
  seat and an unpicked hero. The server flips the coin (p.5 step 3). Sizes: 1v1, 2v2, 3v3 on
  Forgotten Island, Quick or Long; 4v4, 5v5 on Across the River. Equal teams only.
- **Clocks (defaults, in `RoomRules`):** `PLAY_CARD` 60 s, anything else 45 s; a disconnected seat 15 s.
  On expiry the computer answers that one question (`AiPlayers.forStrength(4, seed)`: no search,
  bounded CPU, on one dispatcher). The person can return at any moment. If every seat is away for
  10 minutes the clocks pause; after 24 hours the game is `ABANDONED`, unrated. A server restart gives
  the current question its whole clock again, and so does coming back from a pause. A seat given up with
  `leave` is answered at once, not on a clock.
- **Rated:** a game from a ranked room, finished by the rules. Leaving does not dodge a result: the
  computer plays on, the team still wins or loses. A voided or abandoned game is unrated.
- **Ratings** are `EloCalculator.replay(matches, EloConfig.DEFAULT)` over the `matches` table and are
  never stored: the board is derived on demand, and deleting everything except `matches` reproduces it.
  A player or a hero appears on the board after 5 rated games; each board has at most 200 rows, by
  rating, then id. A deleted account keeps its match rows and shows as "Former player". The server
  remembers a replay in memory per log hash and reads names on every call. `eloConfig` in the snapshot
  is a version number that goes up when `EloConfig.DEFAULT` changes.
- **Off online:** undo, Coach, suggestions, pace and the post-game report. Local play, puzzles and
  group sync are untouched.
- **Accepted limits.** Nobody can be stopped from consulting another device's AI, so the board is
  community fun and not a secure competition. There is no push: a backgrounded app loses its socket
  while the clock runs, so an online game is something you sit down for.

## Simultaneous questions

The engine asks `PLAY_CARD`, `UPGRADE_CARD` and `KEEP_UPGRADE` seat by seat (`Flow.kt`), but people
answer at the same time (p.7). No such question can depend on another seat's hidden answer. The room
forecasts each seat's question on a `fork()` and queues the answers:

1. **Window:** the run of these questions in one (round, turn, group). All-pass turns run straight into
   the next `PLAY_CARD` and a level-up runs into the next round, so a walk stops at the window's edge.
2. In the fork, a seat's **own** queued answers are applied, because `KEEP_UPGRADE` follows `UPGRADE_CARD`
   and Emmitt's extra card follows his first; **only other seats** get placeholders.
3. A seat answers its forecast; the answer is stored in `locks`; it is applied when `pending` reaches
   that seat. A lone `[Pass]` is answered by the server.
4. `Decision.id` counts questions asked, so it reveals how many single-option questions were skipped.
   **It never goes on the wire.** Questions carry a per-seat `ticket` in the `id` field.
5. A locked seat's own view is patched from its own fork's `view(viewer)`, only that hero. A fork's
   whole view is never sent: its placeholders would show others as having played.
6. When the real question arrives, compare it to the forecast, ignoring `id`. On a mismatch, log it,
   drop the lock and ask again.
7. The forecast fork halts when the engine's step changes (`EngineSession.forkHalting`). Without that,
   the last seat's answer would run on with placeholders into the reveal, or through the pity coin into
   the next round, and its own view would come from a future that never happens.
8. Others learn only that a seat is still asked or is done: `locked` lists a seat when it has **no**
   question left, a stub always names the window's one kind (`PLAY_CARD`, or `UPGRADE_CARD` for a whole
   level-up) and its clock is the window's fixed deadline. A seat's own clock restarts on its next
   question; showing that to others would count its questions. A frame goes out only when its content
   changes, so private steps don't show in `seq` either.

## Hidden information

| Surface | Rule |
|---|---|
| `Decision` options, prompt, context, source card | The deciding seat only. Everyone else gets a stub: `seat`, `team`, `kind`, `id = 0`, `options = []` |
| `Decision.id` | Never on the wire |
| `SavedGame`, `GameSnapshot.saved`, `GameState`, `AnswerRecord` | Never on the wire |
| `setup.humans`, `mergedView(setup.humans)`, `review(setup.humans)` | Banned online: with every seat human this merges every hand. Always the viewer's own seats |
| `view(seat)`, `review(setOf(seat))`, `positionAt(.., setOf(seat))` | Safe, routed to that seat's owner |
| `GameEvent` | Public by construction today; the event scan below keeps it so |
| `HeroView.controller` | Stays `Human` during a takeover; presence has its own field |

Four tests make this binding, in `server/game`:
1. Pairs of games that differ only in hidden information (`NoPeekingTest`, `ReviewTest` pairs) give each
   seat that must not see the difference **byte-identical** frames.
2. Over random games and all heroes, frames from the real session equal frames from a determinized copy
   for each seat of that team. A copy is sampled from the start of the current step, so it does not know
   what that step has already shown. During the reveal step the other team's played cards are left out of
   the comparison, and a question after an enemy named a card earlier in the step is not compared.
3. Every card in an event must be in the spectator view just before or just after it. Two exceptions,
   both open by the rules: Bain's "Guess that card's color, then reveal it", and a card discarded faceup
   and retrieved within one run of the engine (Tali's Commune with Spirits into Guardian Spirit).
4. The code of `core/online` and `server/game/.../room/SeatProjector` must not mention `SavedGame`,
   `GameState`, `AnswerRecord` or `.humans`. Comments may warn against them.

## A rules fix during a running game

Replay throws only if an answer no longer fits, so a rules fix can silently change a running game's
past. Each game stores `rules_build` (a hash of the rules sources and catalog) and each answer a rolling
hash of the event text; each `TurnStarted`, a hash of the state. On restart: replay and compare. On a
match, carry on, even across builds. On a mismatch or a throw, void the game, unrated, and tell the
players in words. A deploy drains first: new rooms are refused for up to `GOA_GAME_DRAIN_HOURS`.
The signal is a file, `drain` in the data directory, which can come and go while the server runs. While
it exists the server refuses new rooms (`Refusal.DRAINING`) and `/v1/health` says `"status":"draining"`;
`rooms` there counts what the deploy waits for, so it must reach 0 once running games have finished.

## Protocol versions

`OnlineProtocol.VERSION` goes up with any new `GameEvent`, `Choice`, `DecisionKind` or other sealed
subtype or enum value that can reach a client (old decoders fail on them; Android updates by hand,
ADR-048). A plain added field with a default does not need it. `Hello` carries the protocol and the
catalog's `dataVersion`; too old gets `Refused(UPDATE_APP)`.

## Routes

REST, under the base address. Bearer token on all but the ones marked.

| Route | Result |
|---|---|
| `GET /v1/health` (open) | `HealthInfo` |
| `POST /v1/accounts` (open) `{name}` | `201 AccountCreated`. Per-client limit |
| `POST /v1/accounts/link` (open) `{recoveryCode}` | `200 AccountCreated`, burns the code, issues a new one |
| `GET`, `PATCH`, `DELETE /v1/me` | `Me`; rename; delete anonymises the name and keeps match rows |
| `POST /v1/rooms` `CreateRoom` | `201 RoomCreated`; `503 {"error":"rooms_full"}` + `Retry-After` |
| `POST /v1/rooms/{code}/join`, `/leave` | `RoomView` |
| `GET /v1/me/games` | `MyGames`: games to go back to |
| `GET /v1/leaderboard` (open) | `LeaderboardSnapshot`, with `ETag` |

WebSocket `GET /v1/play`: `Hello` is the first frame, within 5 s (browsers cannot set a header on a
WebSocket and a URL would be logged). Inbound frames up to 4 KiB, a ping every 20 s, and an outbound
channel per connection that keeps only the latest `Table`. `Origin` must be on the list when present;
native clients send none. CORS follows the relay: an exact list, no credentials.

- **Refused, then closed:** protocol or catalog too old (`UPDATE_APP`), a bad token (`BAD_TOKEN`).
- **Closed with no frame:** no `Hello` in time or a first frame that is not one (1008), a frame over the
  limit (1009), an unreadable frame (1003), a queue that fell too far behind or a socket stuck for 30 s
  (1013): reconnect and `Resume` from the events you hold.
- **Refused, socket stays:** too many frames (`RATE_LIMITED`, with seconds), and anything the lobby says no
  to, followed by a `Lobby` frame with the room as it stands: `NOT_HOST`, `NOT_READY`, `TOO_MANY_GAMES`,
  `TOO_MANY_ROOMS`, and `ROOM_TAKEN` for a seat or hero that is gone or a request the lobby can't use.
- **The outbound queue** keeps the latest `Table` of each game and moves it to the tail, with the events of
  the tables it replaced in front of its own: a client that misses an event can't get it back. So a `Table`
  can start earlier than the last one ended; apply it from `eventsFrom` and skip what you hold.
- A person has at most 4 sockets (the newest wins) and each watches its 8 latest rooms. A socket gets the
  frames of every room its person sits in, and is linked to a room's game when it starts.
- A repeat of the same answer (game, ticket, choice) on one socket gets the first `Ack` again; the room
  is not asked twice. Answering with another choice is a new answer.

REST errors are `ApiError`. Beyond those above: `bad_request`, `bad_name`, `bad_recovery_code`, `bad_room`,
`map_unavailable`, `no_such_room`, `room_taken`, `too_many_games`, `too_many_rooms`, `too_large`,
`unauthorized`, `rate_limited`, `store_unavailable`, `draining`.

## The lobby

- The creator sits down with the room, in the first Red seat; `join` then changes nothing for them, and
  joining seats anyone else on the emptier team. One seat per person, one hero per game.
- A change of seat or hero takes back your Ready; Ready needs a hero. The host starts, once every seat is
  filled and ready. Leaving passes the host on; the last one out closes the room.
- An open room lasts 2 hours. A person hosts at most 3 open rooms and plays at most 3 running games.
- **Drain:** while `GOA_GAME_DRAIN_FILE` (default `<data>/drain`) exists, no room opens and no room starts,
  because a game begun now could outlast the drain and be replayed under new rules. Running games continue
  and health says `status: "draining"`.
## The client

`OnlineGameDriver` (in `core/play/.../online/`) turns frames into the `GameUiState` both game screens
already draw; the home and lobby presenters do the rest; screens implement `GameConnector`,
`OnlineGateway` and `OnlineIdentityStore`. What it relies on from the server:

- A connection starts with `GameStart`, then a `Table` with events from `Hello.resume`'s `haveEvents`.
  A table whose `eventsFrom` is past what the client holds makes it reconnect and ask again, so a
  conflated frame must not lose events for good. The online log only grows: an overlap is not replayed.
- A locked seat gets a stub or its next forecast question, with a new ticket; never the question it has
  answered, unless with the same ticket and itself in `locked`. A new ticket is always answerable.
- A question is real when `id` is not 0 and it has options, and only then can it be answered.
- `POST /v1/rooms/{code}/join` is also how the creator and someone already seated come in, and changes
  nothing for them: home calls it after creating a room.
- `Welcome.activeGames` lists the person's running games. A game not listed that sends no frame within
  5 s is shown as gone; a game that sends its frames is fine whatever the list says.
- A frame the client can't decode means a newer server: it shows `Refused(UPDATE_APP)` and stops.
- A room code is 8 characters of Crockford base 32 in upper case (no I, L, O or U), so that what people
  retype reads back: O as 0, I and L as 1.

### The web client

`web/app/.../play/online/` implements the seams over the browser. What is particular to it:

- **Which host.** `OnlineHosts` reads `network.json` before connecting and keeps it 5 minutes. It takes the first
  host that isn't `closed` and that this page's `connect-src` names, else the built-in `goa.gameServer`; every
  listed host closed means no host, and the screens say the server can't be reached. A host only `network.json`
  knows of can't be reached from a browser until a site rebuild with `-Pgoa.gameServer` adds it to the CSP, so it is
  not tried until then. The leaderboard's fetcher skips closed hosts the same way, and reads no `network.json` while
  the Pages file answers.
- **CSP.** `connect-src` names the game server explicitly as `wss://`, even beside the relay's `https://` on the same
  origin: not every browser lets `https://` cover `wss://`. The leaderboard files share the PWA's Pages origin, so
  they need no source. `web/e2e/serve.mjs` adds local `ws://` for tests, and Pages' origin since a test isn't on it.
- **An invite** `#play=<code>` normalises to `#/online/join/<code>` in place, then the room's `#/online/room/<code>`,
  so a reload at any point finds the same place. The code isn't a secret, so nothing needs index.html's trick for
  `#join=`. On an Android browser the join screen offers `goa-companion://play/<code>`.
- **Storage.** The account is `online/identity` in IndexedDB, in no export. Safari may clear it unless the app is on
  the Home Screen, and the screens say plainly that the recovery code is how to get back. `online/testServer`, a test
  server's address, is honoured only on a localhost page: on Pages, other sites of the account can write this storage.
- **The table** is `GameTable` (`play/game/GameScreen.kt`), the same one a game against the computer uses. The
  resolution flow view shows when the game supplies a flow; `GameController` doesn't publish one yet, so a game against
  the computer on the web keeps its recent lines.

A new account is saved on the device only after "I've saved it": if the app dies before that, the person
starts again with nothing lost. Linking a device burns the old code, so its new one must be saved too.

## Storage

SQLite at `/var/lib/goa-game/games.sqlite`, WAL, `synchronous=FULL`, foreign keys on, on one connection on
one thread. An answer is durable before any frame about it is sent. No rating column anywhere. Schema in
`server/game/.../store/Schema.kt`. A repeat of a stored answer or lock changes nothing, and a different
answer at the same `seq` is an error: the past is not rewritten. sqlite-jdbc unpacks its native library into
the data directory, which must allow execution (a container's `/tmp` may not), unless `-Dorg.sqlite.tmpdir`
says otherwise; SQLite's own temporary tables stay in memory.

## Streams

Foundation (this commit): `core/online`, `server/game` skeleton, `GameEngine.restoreFrom`, the seam in
`core/play`, this page, ADR-056.

| Stream | Branch | Owns | Must not touch |
|---|---|---|---|
| Room core | `feature/online-room-core` | `server/game/.../room/`: `RoomCore`, `SimultaneousWindow`, `SeatProjector`, clocks, takeover, replay, voiding; the four hidden-information tests | `core/game` rules, HTTP, SQL, clients |
| Server | `feature/online-server` | `server/game/.../http/`, `store/`, `lobby/`, `Main.kt` | `room/`, `core/*`, `server/relay` |
| Leaderboard | `feature/online-leaderboard` | `server/game/.../ratings/`, `core/play/.../online/leaderboard/` | room, lobby, game screens |
| Client core | `feature/online-client` | `core/play/.../online/` except `leaderboard/`: driver, lobby presenter, words | `GameViewModel.kt`, `GameController.kt`, platform code |
| Deploy | `feature/online-deploy` | `deploy/game/`, the `goa-leaderboards` repo template | `deploy/relay`, Funnel on 443 and 10000 |
| Android | `feature/online-android` | `core/data/.../online/`, `feature/board/.../online/`, new routes, hides in the game screen | `GameViewModel.kt`, sync, `core/game` |
| Web | `feature/online-web` | `web/app/.../play/online/`, the `GameScreen` split, the `ResolutionFlowView` port, routes, CSP, `online.spec.ts` | `GameController.kt`, `EngineHost`, sync |
| End to end | `feature/online-e2e` | `server/game/src/test/.../EndToEndTest.kt` | production code unless it finds a bug |

Android and Web start once Client core has landed, so they build on the real driver. End to end is last.
