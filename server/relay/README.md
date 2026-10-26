# goa-relay — the group sync server

A dumb, encrypted mailbox for Group sync (ADR-036). It stores one sealed blob per group and hands
it back; phones do all the reading, merging and validating. It never has a group key, never parses
a body and runs none of the app's rules. The protocol is [docs/SYNC-SERVER.md](../../docs/SYNC-SERVER.md);
the production deployment is [deploy/relay/](../../deploy/relay/README.md).

Kotlin/JVM 21, Ktor 3 on CIO, kotlinx.serialization (for `meta.json` only), slf4j-simple.

## Build and test

This directory is its **own Gradle build**, not part of the Android build (which needs the
Android SDK). It carries a copy of the root Gradle wrapper and pins its versions in
`gradle/libs.versions.toml` (Kotlin and kotlinx.serialization match the root catalog). Any JDK 21.

```sh
cd server/relay
./gradlew test            # Windows: .\gradlew.bat test (set GRADLE_USER_HOME/TMP first, ADR-010)
./gradlew installDist     # → build/install/goa-relay/bin/goa-relay
```

If the root wrapper's Gradle version changes, copy `gradlew`, `gradlew.bat` and `gradle/wrapper/`
here too. The Docker image (`deploy/relay/Dockerfile`) builds from this directory alone and runs the
tests as part of the build.

## Run

```sh
GOA_RELAY_DATA=/tmp/goa-relay build/install/goa-relay/bin/goa-relay
curl -s http://127.0.0.1:8787/v1/health   # {"ok":true,"protocol":1}
```

| Variable | Default | Meaning |
|---|---|---|
| `GOA_RELAY_DATA` | `/var/lib/goa-relay` | Data directory (must be writable; otherwise every route answers 503) |
| `GOA_RELAY_HOST` | `127.0.0.1` | Bind address. The container uses `0.0.0.0` and publishes on host loopback only |
| `GOA_RELAY_PORT` | `8787` | Port |
| `GOA_RELAY_MAX_GROUPS` | `200` | Groups that may register; a new group beyond it gets 503 (`Retry-After: 3600`) |
| `GOA_RELAY_RATE_PER_CLIENT` | `60` | Requests per minute per client (token bucket, burst = the same number) |
| `GOA_RELAY_RATE_GLOBAL` | `600` | Requests per minute for everyone together |
| `GOA_RELAY_CORS_ORIGINS` | `https://quintond.github.io` | Browser origins allowed to call the relay (CORS, comma-separated, exact `scheme://host[:port]`); empty = none |

**Browsers (ADR-043).** The web app is a group member like a phone. For a request carrying an
allowed `Origin`, every answer (errors included) gets `Access-Control-Allow-Origin: <origin>`,
`Access-Control-Expose-Headers: ETag, Retry-After` and `Vary: Origin`; a preflight (`OPTIONS`)
for `GET`/`PUT` with only `Authorization`, `If-Match`, `If-None-Match`, `Content-Type` on a relay
path gets `204` (`Access-Control-Max-Age: 600`), anything else `403`. Preflights touch no data and
are not rate limited. No cookies: credentials are never allowed. Phones send no `Origin`; nothing
changes for them.

The client is the **last** `X-Forwarded-For` hop when present (Tailscale Serve/Funnel adds it),
else the peer address. Limits: 8 MiB per document (counted on the bytes received, chunked or not),
20 revisions of history per group.

Logs go to stdout, one line per request: method, path template, status, duration
(`PUT /v1/groups/{groupId}/document 412 4ms`). Bodies, tokens, group ids and client addresses are
never logged; healthy `/v1/health` calls log at debug only.

## Data layout

```
$GOA_RELAY_DATA/groups/<groupId>/
  meta.json        {"tokenHash":"<sha256 hex of the bearer token>","rev":7,"updatedAt":<epoch ms>,"size":12345}
  document.bin     the current sealed document (rev 7)
  history/<rev>.bin  the last 20 accepted revisions, the current one included
```

Every write goes to a temp file, is fsynced and renamed into place under the group's lock;
`meta.json` is written last, so a crash mid-upload leaves the previous revision current. Leftover
`.tmp-*` files are removed at start-up. Everything here is ciphertext, token hashes and counters.

## Backup and restore

Stop-free backup: copy the directory (`sudo tar -C /var/lib -czf goa-relay-$(date +%F).tgz goa-relay`).
Restore: stop the relay, put the directory back (owned by the uid the relay runs as, 10001 in the
container), start it. A lost or wiped relay loses nothing that matters: the next phone to sync
uploads its merged log as revision 1 of a fresh group entry.

## Wipe a group / roll one back

- **Wipe:** `sudo rm -rf /var/lib/goa-relay/groups/<groupId>` (no restart needed). The next phone
  that syncs registers the group again with its own data. Use this if a group's token must change.
- **Roll back** (a bad upload): with the relay stopped, copy `history/<rev>.bin` over
  `document.bin` and raise `meta.json`'s `rev` by one (so no phone takes it for a revision it
  already has; keep `tokenHash`). Phones merge it on their next sync.
  Rolling back never removes data from the phones; it only changes what the server hands out.
